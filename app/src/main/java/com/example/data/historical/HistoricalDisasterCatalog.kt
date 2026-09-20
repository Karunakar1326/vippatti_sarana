package com.example.data.historical

/**
 * ============================================================================
 * HISTORICAL DISASTER CATALOG
 * ============================================================================
 *
 * The queryable view over an imported EM-DAT dataset: filtering, facets, a
 * timeline/trend series and impact summaries, all pure functions so the numbers
 * the UI shows are the same numbers the tests assert.
 *
 * Sums use [Long] and stay NULL when no record supplied a figure - a total of
 * "0 deaths" would be a fabricated claim, whereas "no record in this selection
 * states a death toll" is the truth.
 */
data class HistoricalFilters(
  val country: String? = null,
  /** Matches the record's free-text location OR its admin-unit names. */
  val areaText: String? = null,
  val type: String? = null,
  val yearFrom: Int? = null,
  val yearTo: Int? = null,
  /** Only records EM-DAT itself flagged as historic / not historic. */
  val historicFlag: Boolean? = null
) {
  val isActive: Boolean
    get() = country != null || areaText != null || type != null ||
      yearFrom != null || yearTo != null || historicFlag != null

  /** Human description of the active filters, for the "what am I seeing" line. */
  val description: String
    get() = if (!isActive) {
      "No filters — showing every imported record"
    } else {
      listOfNotNull(
        country?.let { "country: $it" },
        areaText?.let { "area contains \"$it\"" },
        type?.let { "type: $it" },
        when {
          yearFrom != null && yearTo != null -> "years: $yearFrom–$yearTo"
          yearFrom != null -> "from $yearFrom"
          yearTo != null -> "up to $yearTo"
          else -> null
        },
        historicFlag?.let { if (it) "EM-DAT historic flag: yes" else "EM-DAT historic flag: no" }
      ).joinToString(" • ")
    }
}

/** Totals for a selection. Null means "no record in this selection states it". */
data class HistoricalImpactSummary(
  val eventCount: Int,
  val deathsTotal: Long? = null,
  val deathsReportingRecords: Int = 0,
  val affectedTotal: Long? = null,
  val affectedReportingRecords: Int = 0,
  val homelessTotal: Long? = null,
  val homelessReportingRecords: Int = 0,
  val injuredTotal: Long? = null,
  val injuredReportingRecords: Int = 0,
  val damageThousandUsdTotal: Long? = null,
  val damageReportingRecords: Int = 0
) {
  /** Records that state no impact figure at all - shown, never zero-filled. */
  val recordsWithNoImpactFigure: Int
    get() = eventCount - maxOf(
      deathsReportingRecords,
      affectedReportingRecords,
      homelessReportingRecords,
      injuredReportingRecords,
      damageReportingRecords
    ).coerceAtLeast(0)

  val isEmpty: Boolean get() = eventCount == 0

  companion object {
    val EMPTY = HistoricalImpactSummary(eventCount = 0)
  }
}

/** One point on the historical timeline / trend series. */
data class HistoricalTrendPoint(val year: Int, val eventCount: Int)

class HistoricalDisasterCatalog(
  val events: List<HistoricalDisasterEvent>,
  val info: HistoricalDatasetInfo,
  /** Records the importer rejected, with reasons - surfaced for transparency. */
  val rejected: List<EmdatCsvParser.RejectedRecord> = emptyList(),
  val importNotes: List<String> = emptyList(),
  /** When this app loaded the dataset; supplied by the caller. */
  val loadedAtMillis: Long = 0L
) {
  val totalCount: Int get() = events.size

  /** The dataset's own date coverage, derived from the records. */
  val yearRange: IntRange?
    get() = events.minOfOrNull { it.startYear }?.let { first ->
      first..events.maxOf { it.endYear }
    }

  val countries: List<String>
    get() = events.map { it.country }.distinct().sorted()

  /** Because the app never maps a record without source coordinates. */
  val mappableCount: Int get() = events.count { it.isMappable }
  val contextOnlyCount: Int get() = totalCount - mappableCount

  fun apply(filters: HistoricalFilters): List<HistoricalDisasterEvent> = events.filter { event ->
    (filters.country == null || event.country.equals(filters.country, ignoreCase = true)) &&
      (filters.type == null || event.type.equals(filters.type, ignoreCase = true)) &&
      (filters.yearFrom == null || event.endYear >= filters.yearFrom) &&
      (filters.yearTo == null || event.startYear <= filters.yearTo) &&
      (filters.historicFlag == null || event.historicFlag == filters.historicFlag) &&
      (filters.areaText == null || HistoricalAreaMatcher.matches(event, filters.areaText))
  }

  /** Disaster types present, most frequent first, for the filter chips. */
  fun typeFacets(source: List<HistoricalDisasterEvent> = events): List<Pair<String, Int>> =
    source.groupingBy { it.type }.eachCount()
      .entries
      .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
      .map { it.key to it.value }

  fun countryFacets(source: List<HistoricalDisasterEvent> = events): List<Pair<String, Int>> =
    source.groupingBy { it.country }.eachCount()
      .entries
      .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
      .map { it.key to it.value }

  /** Per-year counts across the selection, ascending — the trend series. */
  fun yearlyTrend(source: List<HistoricalDisasterEvent> = events): List<HistoricalTrendPoint> =
    source.groupingBy { it.startYear }.eachCount()
      .entries
      .sortedBy { it.key }
      .map { HistoricalTrendPoint(it.key, it.value) }

  /** Decade buckets, for a readable long-range trend (1900–2026 here). */
  fun decadeTrend(source: List<HistoricalDisasterEvent> = events): List<HistoricalTrendPoint> =
    source.groupingBy { (it.startYear / 10) * 10 }.eachCount()
      .entries
      .sortedBy { it.key }
      .map { HistoricalTrendPoint(it.key, it.value) }

  /**
   * Impact totals over a selection. A figure is added only when the record
   * actually states it; the reporting-record counts let the UI say how much of
   * the selection the total covers.
   */
  fun impactSummary(source: List<HistoricalDisasterEvent> = events): HistoricalImpactSummary {
    if (source.isEmpty()) return HistoricalImpactSummary.EMPTY
    fun sum(select: (HistoricalImpacts) -> Long?): Pair<Long?, Int> {
      val values = source.mapNotNull { select(it.impacts) }
      return if (values.isEmpty()) null to 0 else values.sum() to values.size
    }
    val deaths = sum { it.totalDeaths }
    val affected = sum { it.totalAffected ?: it.affected }
    val homeless = sum { it.homeless }
    val injured = sum { it.injured }
    val damage = sum { it.damageThousandUsd }
    return HistoricalImpactSummary(
      eventCount = source.size,
      deathsTotal = deaths.first,
      deathsReportingRecords = deaths.second,
      affectedTotal = affected.first,
      affectedReportingRecords = affected.second,
      homelessTotal = homeless.first,
      homelessReportingRecords = homeless.second,
      injuredTotal = injured.first,
      injuredReportingRecords = injured.second,
      damageThousandUsdTotal = damage.first,
      damageReportingRecords = damage.second
    )
  }

  /** The most recent record in a selection, for "last recorded event" lines. */
  fun mostRecent(source: List<HistoricalDisasterEvent> = events): HistoricalDisasterEvent? =
    source.maxByOrNull { it.startDate.sortKey }
}

/**
 * Text-level area matching.
 *
 * This is deliberately NOT geospatial: EM-DAT's location text lists districts,
 * states or whole regions in free text, and only 94 of the 740 India records
 * carry coordinates. Matching a resolved district/state name against the
 * record's own text is honest - it answers "does EM-DAT associate this record
 * with this area?" - whereas inferring point coordinates from that text would
 * invent precision the data does not have.
 */
object HistoricalAreaMatcher {

  fun matches(event: HistoricalDisasterEvent, areaName: String): Boolean {
    val needle = areaName.trim()
    if (needle.isBlank()) return false
    return event.areaTokens.any { token -> sameArea(token, needle) }
  }

  /** Whole-word (boundary-aware) case-insensitive comparison. */
  fun sameArea(token: String, needle: String): Boolean {
    val left = token.lowercase().trim()
    val right = needle.lowercase().trim()
    if (left.isEmpty() || right.isEmpty()) return false
    if (left == right) return true
    if (!left.contains(right)) return false
    // Guard against partial matches inside a longer word, e.g. a needle "Ida"
    // matching "Idukki" would put an unrelated event in the user's area.
    val start = left.indexOf(right)
    val before = left.getOrNull(start - 1)
    val after = left.getOrNull(start + right.length)
    val startsCleanly = before == null || !before.isLetter()
    val endsCleanly = after == null || !after.isLetter()
    return startsCleanly && endsCleanly
  }
}
