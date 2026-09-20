package com.example.data.historical

import org.json.JSONArray
import org.json.JSONObject

/**
 * ============================================================================
 * EM-DAT PREPARED-CSV IMPORTER
 * ============================================================================
 *
 * Parses the trimmed CSV produced from an EM-DAT public export (see
 * `tools/emdat_prepare.py`). The parser is pure and deterministic, so the import
 * is repeatable and idempotent: the same text always produces the same catalog,
 * with the same rejections and the same reasons.
 *
 * Rules:
 *  - Columns are resolved BY HEADER NAME, never by position, so a re-exported
 *    file with an extra column still imports correctly.
 *  - An EMPTY field stays null. EM-DAT uses empty for "never collected", and
 *    turning that into 0 would fabricate a death toll of zero.
 *  - A record is rejected only when it cannot be identified or dated - that is,
 *    when it has no `DisNo.`, repeats one already imported, or has no usable
 *    start year. Everything else is imported with an explicit note.
 *  - Numbers that would overflow [Long] or land outside a valid coordinate
 *    range are dropped with a note rather than silently wrapped.
 */
object EmdatCsvParser {

  /** Columns the importer needs; a file missing any of them is rejected whole. */
  val REQUIRED_COLUMNS = listOf(
    "disno", "group", "subgroup", "type", "subtype", "country",
    "start_year", "total_deaths", "total_affected"
  )

  /** Earliest year EM-DAT itself documents; anything outside is a data error. */
  private const val MIN_YEAR = 1800
  private const val MAX_YEAR = 2200

  data class RejectedRecord(val lineNumber: Int, val id: String?, val reason: String)

  data class Result(
    val events: List<HistoricalDisasterEvent>,
    val rejected: List<RejectedRecord>,
    val source: String,
    val version: String?,
    val accessedOn: String?,
    val notes: List<String>
  ) {
    val acceptedCount: Int get() = events.size
  }

  /**
   * @param csvText  the prepared CSV, header row included.
   * @param infoJson the optional `emdat_dataset_info.json` attribution block.
   * @param accessedOn the date this dataset was obtained, supplied by the
   *   caller (the app never invents a date from the device clock at parse time).
   */
  fun parse(
    csvText: String,
    infoJson: String? = null,
    accessedOn: String? = null
  ): Result {
    val records = parseRows(csvText)
    require(records.isNotEmpty()) { "EM-DAT CSV is empty" }
    val header = records.first().map { it.trim().lowercase() }
    val missing = REQUIRED_COLUMNS.filterNot { it in header }
    require(missing.isEmpty()) {
      "EM-DAT CSV is missing required columns: ${missing.joinToString(", ")}"
    }
    val index = header.withIndex().associate { (position, name) -> name to position }

    val info = parseInfo(infoJson)
    val source = info?.source ?: HistoricalDatasetInfo.UNKNOWN_SOURCE
    val version = info?.version
    val rejected = mutableListOf<RejectedRecord>()
    val accepted = mutableListOf<HistoricalDisasterEvent>()
    val seen = mutableSetOf<String>()
    val notes = mutableListOf<String>()

    records.drop(1).forEachIndexed { offset, row ->
      // +2: one for the header row, one because humans count from 1.
      val lineNumber = offset + 2
      fun cell(name: String): String? =
        index[name]?.let { position -> row.getOrNull(position)?.trim() }
          ?.takeIf { it.isNotEmpty() }

      val id = cell("disno")
      if (id == null) {
        rejected += RejectedRecord(lineNumber, null, "No EM-DAT identifier (DisNo.)")
        return@forEachIndexed
      }
      if (!seen.add(id)) {
        rejected += RejectedRecord(lineNumber, id, "Duplicate identifier: already imported")
        return@forEachIndexed
      }

      val startYear = cell("start_year")?.toIntOrNull()
      if (startYear == null) {
        rejected += RejectedRecord(lineNumber, id, "Start year is missing or not a number")
        return@forEachIndexed
      }
      if (startYear < MIN_YEAR || startYear > MAX_YEAR) {
        rejected += RejectedRecord(lineNumber, id, "Start year $startYear is outside a usable range")
        return@forEachIndexed
      }

      val startDate = HistoricalDate(
        year = startYear,
        month = monthOrNull(cell("start_month")),
        day = dayOrNull(cell("start_day"))
      )
      val endYear = cell("end_year")?.toIntOrNull()
      val endDate = if (endYear != null && endYear in MIN_YEAR..MAX_YEAR) {
        HistoricalDate(
          year = endYear,
          month = monthOrNull(cell("end_month")),
          day = dayOrNull(cell("end_day"))
        )
      } else {
        null
      }

      val recordNotes = mutableListOf<String>()
      fun counted(name: String): Long? {
        val raw = cell(name) ?: return null
        val parsed = raw.toLongOrNull()
        if (parsed == null) {
          recordNotes += "Field '$name' held an unreadable value ($raw) and was left empty."
          return null
        }
        if (parsed < 0) {
          recordNotes += "Field '$name' held a negative value ($raw) and was left empty."
          return null
        }
        return parsed
      }

      val latitude = coordinate(cell("latitude"), -90.0, 90.0, "latitude", recordNotes)
      val longitude = coordinate(cell("longitude"), -180.0, 180.0, "longitude", recordNotes)
      // A record needs BOTH coordinates to be placeable; one alone is unusable.
      val placeable = latitude != null && longitude != null
      if (!placeable && (latitude != null || longitude != null)) {
        recordNotes += "Only one coordinate was present, so the record is not mapped."
      }

      val adminUnits = parseAdminUnits(cell("admin_units"), recordNotes)
      val locationText = cell("location")
      val precision = when {
        placeable -> HistoricalSpatialPrecision.SOURCE_COORDINATES
        adminUnits.isNotEmpty() -> HistoricalSpatialPrecision.ADMIN_UNIT_ONLY
        locationText != null -> HistoricalSpatialPrecision.LOCATION_TEXT_ONLY
        else -> HistoricalSpatialPrecision.NOT_SUFFICIENT
      }

      accepted += HistoricalDisasterEvent(
        id = id,
        historicFlag = cell("historic")?.let { it.equals("yes", ignoreCase = true) },
        group = cell("group") ?: "Not stated",
        subgroup = cell("subgroup") ?: "Not stated",
        type = cell("type") ?: "Not stated",
        subtype = cell("subtype") ?: "Not stated",
        eventName = cell("event_name"),
        iso = cell("iso"),
        country = cell("country") ?: "Not stated",
        locationText = locationText,
        adminUnitNames = adminUnits,
        startDate = startDate,
        endDate = endDate,
        impacts = HistoricalImpacts(
          totalDeaths = counted("total_deaths"),
          injured = counted("no_injured"),
          affected = counted("no_affected"),
          homeless = counted("no_homeless"),
          totalAffected = counted("total_affected"),
          damageThousandUsd = counted("damage_thousand_usd"),
          damageAdjustedThousandUsd = counted("damage_adjusted_thousand_usd")
        ),
        magnitude = cell("magnitude")?.toDoubleOrNull(),
        magnitudeScale = cell("magnitude_scale"),
        latitude = latitude,
        longitude = longitude,
        entryDate = cell("entry_date"),
        lastUpdate = cell("last_update"),
        source = source,
        datasetVersion = version ?: "not stated in dataset",
        accessedOn = accessedOn,
        spatialPrecision = precision,
        notes = recordNotes
      )
    }

    if (info?.declaredRecordCount != null && info.declaredRecordCount != accepted.size) {
      // Surfaced rather than swallowed: a mismatch means the prepared file and
      // its attribution disagree, which the operator must know about.
      notes += "Dataset declares ${info.declaredRecordCount} records; " +
        "${accepted.size} were imported (${rejected.size} rejected)."
    }

    return Result(
      events = accepted,
      rejected = rejected,
      source = source,
      version = version,
      accessedOn = accessedOn,
      notes = notes
    )
  }

  /** Parses the export's own attribution sheet. Never invents a value. */
  fun parseInfo(infoJson: String?): HistoricalDatasetInfo? {
    if (infoJson.isNullOrBlank()) return null
    return try {
      val json = JSONObject(infoJson)
      fun text(vararg keys: String): String? = keys
        .firstNotNullOfOrNull { key -> json.optString(key).takeIf { it.isNotBlank() } }
      HistoricalDatasetInfo(
        source = text("Source") ?: HistoricalDatasetInfo.UNKNOWN_SOURCE,
        sourceUrl = text("Source URL", "URL", "https://www.emdat.be"),
        glossaryUrl = text("Glossary"),
        version = text("Version"),
        fileCreated = text("File creation"),
        tableType = text("Table type"),
        declaredRecordCount = text("# of records")?.toIntOrNull()
      )
    } catch (_: Exception) {
      null
    }
  }

  // ------------------------------------------------------------ primitives

  /**
   * Minimal RFC 4180 reader: quoted fields, doubled quotes, and separators or
   * newlines inside quotes. EM-DAT location text contains commas and quoted
   * admin-unit JSON, so a naive `split(",")` would corrupt records.
   */
  internal fun parseRows(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var index = 0
    while (index < text.length) {
      val character = text[index]
      when {
        quoted -> when {
          character == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
            field.append('"')
            index++
          }
          character == '"' -> quoted = false
          else -> field.append(character)
        }
        character == '"' -> quoted = true
        character == ',' -> {
          row.add(field.toString())
          field.setLength(0)
        }
        character == '\r' -> Unit
        character == '\n' -> {
          row.add(field.toString())
          field.setLength(0)
          rows.add(row)
          row = mutableListOf()
        }
        else -> field.append(character)
      }
      index++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) {
      row.add(field.toString())
      rows.add(row)
    }
    // Trailing blank line at EOF is not a record.
    return rows.filterNot { cells -> cells.size == 1 && cells.first().isBlank() }
  }

  private fun coordinate(
    raw: String?,
    minimum: Double,
    maximum: Double,
    name: String,
    notes: MutableList<String>
  ): Double? {
    if (raw == null) return null
    val value = raw.toDoubleOrNull()
    if (value == null) {
      notes += "Field '$name' held an unreadable value ($raw) and was left empty."
      return null
    }
    if (value < minimum || value > maximum) {
      notes += "Field '$name' ($raw) is outside the valid range and was left empty."
      return null
    }
    return value
  }

  private fun monthOrNull(raw: String?): Int? =
    raw?.toIntOrNull()?.takeIf { it in 1..12 }

  private fun dayOrNull(raw: String?): Int? =
    raw?.toIntOrNull()?.takeIf { it in 1..31 }

  /**
   * The dataset stores admin units as a JSON array of objects with adm1/adm2
   * names. Malformed JSON is noted, not fatal - the free-text location still
   * gives the record an area.
   */
  private fun parseAdminUnits(raw: String?, notes: MutableList<String>): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
      val array = JSONArray(raw)
      (0 until array.length())
        .mapNotNull { position -> array.optJSONObject(position) }
        .flatMap { unit ->
          listOfNotNull(
            unit.optString("adm1_name").takeIf { it.isNotBlank() },
            unit.optString("adm2_name").takeIf { it.isNotBlank() }
          )
        }
        .filterNot { it.equals("Administrative unit not available", ignoreCase = true) }
        .distinct()
    } catch (_: Exception) {
      notes += "Administrative units in this row could not be read; location text is used instead."
      emptyList()
    }
  }

  /** Convenience for tests and callers holding an already-read info object. */
  fun describe(info: HistoricalDatasetInfo?): String =
    info?.attributionLine ?: HistoricalDatasetInfo.UNKNOWN_SOURCE
}
