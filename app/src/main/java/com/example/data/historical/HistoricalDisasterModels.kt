package com.example.data.historical

import com.example.data.model.DataClassification

/**
 * ============================================================================
 * HISTORICAL DISASTER INTELLIGENCE - models (EM-DAT)
 * ============================================================================
 *
 * Why a SEPARATE model instead of reusing `DisasterEvent`:
 * `DisasterEvent` is the LIVE event shape - it requires a provider source event
 * id, a geometry, a hazard severity and an observation time, and it is what the
 * radar maps into current hazard zones. An EM-DAT record is none of those
 * things: it is a historical occurrence with impact statistics, an imprecise
 * location and no current status. Modelling it as a live event would let a
 * 1987 flood behave like an active hazard.
 *
 * Honesty rules encoded here:
 *  - Every impact number is nullable. EM-DAT leaves a field EMPTY when it never
 *    collected it, so an absent value stays null and renders as
 *    "Not available" - never 0.
 *  - Coordinates are taken ONLY from the dataset's own Latitude/Longitude
 *    columns. Nothing is geocoded from location text, and no placeholder is
 *    substituted, so [spatialPrecision] states exactly what is known.
 *  - The classification is [DataClassification.HISTORICAL] for every record:
 *    EM-DAT is a historical archive, not a live feed, even though the file is
 *    periodically updated.
 */

/**
 * What the dataset actually gives us to locate a record. Drives whether the
 * record may appear on the map at all - see [canPlaceOnMap].
 */
enum class HistoricalSpatialPrecision(
  val label: String,
  val canPlaceOnMap: Boolean,
  val explanation: String
) {
  /** The dataset itself supplied Latitude/Longitude for this record. */
  SOURCE_COORDINATES(
    label = "Source coordinates (EM-DAT)",
    canPlaceOnMap = true,
    explanation = "The dataset supplied this record's own coordinates. The marker shows " +
      "where EM-DAT placed the event, not a current hazard zone."
  ),

  /** Only administrative names (state/district) are known. */
  ADMIN_UNIT_ONLY(
    label = "Administrative area only",
    canPlaceOnMap = false,
    explanation = "Only administrative names are recorded. The exact impact location is " +
      "not known, so no marker is placed."
  ),

  /** Only free-text location, possibly a list of districts or states. */
  LOCATION_TEXT_ONLY(
    label = "Location text only",
    canPlaceOnMap = false,
    explanation = "Only free-text location is recorded, which may list several districts at " +
      "once. No coordinates are invented from it."
  ),

  /** Nothing usable to place the record. */
  NOT_SUFFICIENT(
    label = "Not sufficient for mapping",
    canPlaceOnMap = false,
    explanation = "This record carries no location information that could support a marker. " +
      "It is kept as historical context only."
  )
}

/**
 * A calendar date as far as the dataset states it. EM-DAT often records only a
 * year, or a year and month. Missing parts stay null so nothing is invented -
 * [label] shows exactly the precision the source has.
 */
data class HistoricalDate(
  val year: Int,
  val month: Int? = null,
  val day: Int? = null
) : Comparable<HistoricalDate> {

  /** Sortable key; missing month/day sort as the start of the period. */
  val sortKey: Int get() = year * 10_000 + (month ?: 1) * 100 + (day ?: 1)

  /** e.g. "1988-11-29", "2001-07", "2020". */
  val label: String
    get() = buildString {
      append(year)
      month?.let { append("-").append(it.toString().padStart(2, '0')) }
      day?.let { if (month != null) append("-").append(it.toString().padStart(2, '0')) }
    }

  override fun compareTo(other: HistoricalDate): Int = sortKey.compareTo(other.sortKey)
}

/**
 * Impact statistics, each independently nullable. A record with no affected
 * figure is NOT a record with zero affected people.
 *
 * Damage is kept in the dataset's own unit - thousands of US dollars - because
 * silently multiplying it would invite unit errors; [damageLabel] states the
 * unit when values are shown.
 */
data class HistoricalImpacts(
  val totalDeaths: Long? = null,
  val injured: Long? = null,
  val affected: Long? = null,
  val homeless: Long? = null,
  val totalAffected: Long? = null,
  val damageThousandUsd: Long? = null,
  /** EM-DAT's inflation-adjusted series, when present. */
  val damageAdjustedThousandUsd: Long? = null
) {
  val hasAny: Boolean
    get() = listOf(
      totalDeaths, injured, affected, homeless, totalAffected,
      damageThousandUsd, damageAdjustedThousandUsd
    ).any { it != null }

  /** True when the record reports deaths and they are zero (a real zero). */
  val deathsRecordedAsZero: Boolean get() = totalDeaths == 0L

  companion object {
    val NONE = HistoricalImpacts()
  }
}

/**
 * One EM-DAT record, with the original values preserved alongside the app's
 * normalized view. [group]/[subgroup]/[type]/[subtype] are the dataset's own
 * strings, untouched, so nothing is lost by the app's own categorisation.
 */
data class HistoricalDisasterEvent(
  /** EM-DAT's `DisNo.` - the stable identifier used for deduplication. */
  val id: String,
  /** EM-DAT's own `Historic` flag, kept verbatim (true/false/null = not stated). */
  val historicFlag: Boolean? = null,
  val group: String,
  val subgroup: String,
  /** EM-DAT disaster type, e.g. "Flood", "Storm", "Mass movement (wet)". */
  val type: String,
  val subtype: String,
  val eventName: String? = null,
  val iso: String? = null,
  val country: String,
  /** Free text, e.g. "Idukki, Kottayam ... (Kerala state)". */
  val locationText: String? = null,
  /** Administrative names parsed from the dataset's `Admin Units` column. */
  val adminUnitNames: List<String> = emptyList(),
  val startDate: HistoricalDate,
  val endDate: HistoricalDate? = null,
  val impacts: HistoricalImpacts = HistoricalImpacts.NONE,
  val magnitude: Double? = null,
  val magnitudeScale: String? = null,
  /** Dataset coordinates, or null. Never inferred, never a placeholder. */
  val latitude: Double? = null,
  val longitude: Double? = null,
  /** Dataset bookkeeping: when EM-DAT entered and last revised the record. */
  val entryDate: String? = null,
  val lastUpdate: String? = null,
  val classification: DataClassification = DataClassification.HISTORICAL,
  val source: String,
  val datasetVersion: String,
  val accessedOn: String? = null,
  val spatialPrecision: HistoricalSpatialPrecision = HistoricalSpatialPrecision.NOT_SUFFICIENT,
  /** Per-record limitations worth showing (e.g. dropped coordinates). */
  val notes: List<String> = emptyList()
) {
  val startYear: Int get() = startDate.year
  val endYear: Int get() = endDate?.year ?: startDate.year

  /** True when this record is genuinely placeable on a map. */
  val isMappable: Boolean
    get() = spatialPrecision.canPlaceOnMap && latitude != null && longitude != null

  /**
   * Areas this record is associated with, for area-level (not coordinate-level)
   * matching: the parsed admin names plus the free-text location split on the
   * separators EM-DAT actually uses.
   */
  val areaTokens: List<String>
    get() = (adminUnitNames + (locationText ?: "")
      .split(',', ';', '/', '(', ')')
      .map { it.trim() })
      .filter { it.isNotBlank() }
      .distinct()

  /** One-line label for lists: "2020 — Mass movement (wet) — Idukki district". */
  val listLabel: String
    get() = "${startYear} — $type" +
      (locationText?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: " — Location not stated")
}

/**
 * Attribution block, read from the export's own "EM-DAT Info" sheet rather than
 * hardcoded in the app, so a refreshed dataset updates its own version string.
 */
data class HistoricalDatasetInfo(
  val source: String,
  val sourceUrl: String? = null,
  val glossaryUrl: String? = null,
  val version: String? = null,
  val fileCreated: String? = null,
  val tableType: String? = null,
  /** The record count the export itself declares, for cross-checking. */
  val declaredRecordCount: Int? = null,
  /** How many records this app actually parsed from the prepared file. */
  val parsedRecordCount: Int = 0
) {
  /** Shown next to every historical surface. Never optional in the UI. */
  val attributionLine: String
    get() = listOfNotNull(
      source,
      version?.let { "version $it" },
      glossaryUrl?.let { "glossary: $it" }
    ).joinToString(" • ")

  val accessLine: String
    get() = listOfNotNull(
      fileCreated?.let { "File created $it" },
      tableType?.let { "table: $it" }
    ).joinToString(" • ")

  companion object {
    /**
     * Used only when the dataset has no info sheet at all: the source is still
     * named (it is a fact about the file), but no version is invented.
     */
    const val UNKNOWN_SOURCE = "EM-DAT (source attribution unavailable in file)"
  }
}
