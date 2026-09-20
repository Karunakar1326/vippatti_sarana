package com.example.data.historical

import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance

/**
 * ============================================================================
 * HISTORICAL CONTEXT (EM-DAT) — supporting evidence, never a risk verdict
 * ============================================================================
 *
 * What this is for: answering "what does the historical record say about this
 * area?" next to the live hazard picture, with the source, the years and the
 * precision stated.
 *
 * What this deliberately does NOT do:
 *  - It does not feed the risk score. The risk engine
 *    (`RiskAssessmentEngine`) keeps its existing deterministic weights and its
 *    existing meaning; no historical weight or threshold is invented here, and
 *    the score is bit-for-bit the same with or without a dataset loaded.
 *  - It does not create hazard zones, red zones or map polygons.
 *  - It does not place markers: only records whose own dataset coordinates
 *    exist are ever mappable, and that is decided in the catalog, not here.
 */

/** How the area was matched, so the UI can state the confidence honestly. */
enum class HistoricalMatchScope(val label: String) {
  DISTRICT("District"),
  STATE("State"),
  COUNTRY("Country"),
  /** The user's area could not be resolved, so nothing is claimed. */
  NO_AREA("Area not resolved")
}

data class HistoricalContext(
  val scope: HistoricalMatchScope,
  /** e.g. "Idukki (district)" — exactly what was matched on. */
  val areaLabel: String? = null,
  val events: List<HistoricalDisasterEvent> = emptyList(),
  val impactSummary: HistoricalImpactSummary = HistoricalImpactSummary.EMPTY,
  val typeCounts: List<Pair<String, Int>> = emptyList(),
  val yearRange: IntRange? = null,
  val mostRecent: HistoricalDisasterEvent? = null,
  /** How the match was performed, including what it is NOT. */
  val matchMethod: String,
  val limitations: List<String> = emptyList(),
  val provenance: DataProvenance? = null
) {
  val eventCount: Int get() = events.size
  val hasEvidence: Boolean get() = events.isNotEmpty()

  /** EM-DAT records in this area that can never be plotted (no coordinates). */
  val unplaceableCount: Int get() = events.count { !it.isMappable }

  /** One-line summary for a panel header, or the honest empty statement. */
  val summaryLine: String
    get() = when {
      events.isEmpty() -> "No historical record for this area in the loaded dataset"
      else -> "$eventCount historical record${if (eventCount == 1) "" else "s"}" +
        (yearRange?.let { " between ${it.first} and ${it.last}" } ?: "")
    }
}

object HistoricalContextService {

  /** Mandatory wording wherever historical evidence is shown. */
  const val DISCLAIMER: String =
    "Historical occurrence does not indicate current danger. EM-DAT is a " +
      "historical archive, not a live hazard feed, and this evidence does not " +
      "change the live risk verdict."

  /**
   * Builds the historical context for the user's resolved area.
   *
   * [district]/[state] come from runtime place resolution (the device's own
   * geocoder). Nothing is hardcoded, and when the platform cannot resolve a
   * place the result is an explicit NO_AREA rather than a guess.
   */
  fun contextFor(
    district: String?,
    state: String?,
    catalog: HistoricalDisasterCatalog?
  ): HistoricalContext {
    if (catalog == null || catalog.totalCount == 0) {
      return HistoricalContext(
        scope = HistoricalMatchScope.NO_AREA,
        matchMethod = "No historical dataset is loaded.",
        limitations = listOf(
          "Attach or refresh the EM-DAT dataset to see historical context for this area."
        )
      )
    }

    val districtName = district?.trim()?.takeIf { it.isNotBlank() }
    val stateName = state?.trim()?.takeIf { it.isNotBlank() }

    val districtMatches = districtName?.let { name ->
      catalog.events.filter { HistoricalAreaMatcher.matches(it, name) }
    }.orEmpty()
    val stateMatches = if (districtMatches.isEmpty() && stateName != null) {
      catalog.events.filter { HistoricalAreaMatcher.matches(it, stateName) }
    } else {
      emptyList()
    }

    val (scope, areaLabel, matched) = when {
      districtMatches.isNotEmpty() -> Triple(
        HistoricalMatchScope.DISTRICT,
        "$districtName (district)",
        districtMatches
      )
      stateMatches.isNotEmpty() -> Triple(
        HistoricalMatchScope.STATE,
        "$stateName (state)",
        stateMatches
      )
      else -> Triple(
        HistoricalMatchScope.NO_AREA,
        districtName?.let { "$it (district)" } ?: stateName?.let { "$it (state)" },
        emptyList()
      )
    }

    val method = if (areaLabel == null) {
      "The current area could not be resolved to a district or state, so no area " +
        "match was attempted."
    } else {
      "Text match of the resolved area name against EM-DAT's location text and " +
        "administrative-unit names — not a geospatial intersection."
    }

    val limitations = buildList {
      add(DISCLAIMER)
      if (matched.isNotEmpty()) {
        val unplaceable = matched.count { !it.isMappable }
        if (unplaceable > 0) {
          add(
            "$unplaceable of ${matched.size} matched records have no coordinates in the " +
              "dataset and are shown as area-level context only, not as map points."
          )
        }
        add(
          "Records are matched by area NAME, so an event affecting several districts " +
            "is counted here even if this district's own impact was smaller."
        )
        add(
          "Counting is limited to the loaded dataset " +
            "(version ${catalog.info.version ?: "not stated"}); absence of a record " +
            "is not evidence that nothing happened."
        )
      }
    }

    return HistoricalContext(
      scope = scope,
      areaLabel = areaLabel,
      events = matched.sortedByDescending { it.startDate.sortKey },
      impactSummary = catalog.impactSummary(matched),
      typeCounts = catalog.typeFacets(matched),
      yearRange = matched.minOfOrNull { it.startYear }?.let { first ->
        first..matched.maxOf { it.endYear }
      },
      mostRecent = catalog.mostRecent(matched),
      matchMethod = method,
      limitations = limitations,
      provenance = if (matched.isEmpty()) {
        null
      } else {
        DataProvenance(
          source = catalog.info.source,
          status = DataProvenance.STATUS_FIELD,
          confidence = 0.0,
          isVerified = false,
          classification = DataClassification.HISTORICAL
        )
      }
    )
  }
}
