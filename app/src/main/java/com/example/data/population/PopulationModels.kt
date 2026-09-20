package com.example.data.population

import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.capacity.ResourceDataState

/**
 * ============================================================================
 * POPULATION DATA MODEL (SIH 26191)
 * ============================================================================
 *
 * A household size is NOT a habitation population, and a baseline census count
 * is NOT a disaster relocation demand. This model keeps those three concepts in
 * separate records so nothing can be silently converted into something else.
 *
 * Rules encoded here:
 *  - Every record says WHAT it counts ([PopulationRole]), over WHAT area
 *    ([PopulationScope]) and HOW it was produced ([PopulationClassification]).
 *  - A record with no value stays null - it is never 0 and never guessed.
 *  - Baseline population is labelled "not confirmed relocation demand" wherever
 *    it is displayed.
 *  - There is no census provider connected in this build: the interface exists
 *    ([PopulationDataProvider]) and returns nothing until a real source is
 *    wired in.
 */

/** Administrative area a population record describes, narrowest first. */
enum class PopulationScope(val label: String) {
  HOUSEHOLD("Household"),
  HABITATION("Habitation"),
  WARD("Ward"),
  VILLAGE("Village"),
  MUNICIPALITY("Municipality / town"),
  DISTRICT("District"),
  STATE("State"),
  NATIONAL("National"),
  UNKNOWN("Unspecified area");

  /** Narrower scopes are preferred when two records describe the same demand. */
  val specificity: Int get() = ordinal
}

/** What a population figure represents. These are NOT interchangeable. */
enum class PopulationRole(val label: String) {
  /** Baseline demographic total: people who LIVE there. Never a demand. */
  BASELINE_TOTAL("Baseline population"),
  /** People currently affected by the event (not necessarily relocated). */
  AFFECTED_POPULATION("Affected population"),
  /** People who must be relocated. This is the only true demand. */
  RELOCATION_DEMAND("Population requiring relocation")
}

/** How a population figure was produced. Drives the honest labelling. */
enum class PopulationClassification(val label: String) {
  VERIFIED("Verified"),
  ESTIMATED("Estimated"),
  USER_DECLARED("User-declared"),
  SIMULATED("Simulated"),
  NOT_PROVIDED("Not provided");

  /** Mapping into the capacity engine's resource vocabulary. */
  fun asResourceDataState(): ResourceDataState = when (this) {
    VERIFIED -> ResourceDataState.MEASURED
    ESTIMATED -> ResourceDataState.ESTIMATED
    USER_DECLARED -> ResourceDataState.USER_DECLARED
    SIMULATED -> ResourceDataState.SIMULATED
    NOT_PROVIDED -> ResourceDataState.NOT_PROVIDED
  }
}

/** Where the record came from - drives the demand priority order. */
enum class PopulationSourceKind(val label: String) {
  OFFICIAL_CENSUS("Official census / registrar data"),
  AUTHORITY_ASSESSMENT("Disaster authority assessment"),
  FIELD_SURVEY("Field survey"),
  SATELLITE_ESTIMATE("Remote-sensing estimate"),
  CITIZEN_DECLARED("Citizen declaration"),
  DEMO_SIMULATED("Demonstration data"),
  UNKNOWN("Unspecified source")
}

/**
 * One population record. [value] is null when the source did not supply a
 * figure (never 0, never a placeholder).
 */
data class PopulationRecord(
  val id: String,
  val role: PopulationRole,
  val scope: PopulationScope,
  /** Human area name, e.g. a ward or village name supplied by the source. */
  val areaName: String? = null,
  val value: Int? = null,
  val classification: PopulationClassification = PopulationClassification.NOT_PROVIDED,
  val sourceKind: PopulationSourceKind = PopulationSourceKind.UNKNOWN,
  val source: String,
  /** When the source collected/referenced the figure; null = not stated. */
  val referenceMillis: Long? = null,
  /** Provider-supplied reliability (0..1) when it states one. */
  val confidence: Double? = null,
  val notes: List<String> = emptyList()
) {
  val hasValue: Boolean get() = value != null && value > 0

  /** True when the record describes exactly one household (personal input). */
  val isHouseholdLevel: Boolean get() = scope == PopulationScope.HOUSEHOLD

  val scopeLabel: String
    get() = areaName?.takeIf { it.isNotBlank() }?.let { "$it (${scope.label})" } ?: scope.label

  /** Honest one-line attribution, e.g. "Verified • Ward 4 (Ward) • authority". */
  val attribution: String
    get() = "${classification.label} • $scopeLabel • ${sourceKind.label}"

  companion object {
    /**
     * Personal household demand declared by the citizen. Explicitly scope
     * HOUSEHOLD so it can never be mistaken for a habitation population.
     */
    fun householdDeclaration(
      householdSize: Int,
      source: String,
      note: String? = null
    ): PopulationRecord = PopulationRecord(
      id = "population-household-declared",
      role = PopulationRole.RELOCATION_DEMAND,
      scope = PopulationScope.HOUSEHOLD,
      value = householdSize.takeIf { it > 0 },
      classification = PopulationClassification.USER_DECLARED,
      sourceKind = PopulationSourceKind.CITIZEN_DECLARED,
      source = source,
      notes = listOfNotNull(
        note ?: "Citizen-declared household size — NOT a census figure and NOT " +
          "the population of the area.",
        "Village/ward population requires a census or relief-registry source."
      )
    )
  }
}

/**
 * Population picture for one assessment run: the baseline total, the affected
 * count and the resolved relocation demand, each kept distinct.
 *
 * [provenance] is null when no source supplied any of them - there is nothing
 * to attribute, and inventing a classification here would be a lie.
 */
data class PopulationAssessment(
  val baseline: PopulationRecord? = null,
  val affected: PopulationRecord? = null,
  val relocationDemand: PopulationRecord? = null,
  /** Human description of the area the demand applies to. */
  val areaDescription: String? = null,
  val records: List<PopulationRecord> = emptyList(),
  val provenance: DataProvenance? = null
) {
  val baselineLabel: String
    get() = baseline?.let { record ->
      "${record.value} (${record.classification.label.lowercase()})" +
        " — not confirmed relocation demand"
    } ?: "Not provided — no census source connected"

  val affectedLabel: String
    get() = affected?.let { record ->
      "${record.value} (${record.classification.label.lowercase()})" +
        " — not confirmed relocation demand"
    } ?: "Not provided — no affected-population source connected"

  val demandLabel: String
    get() = relocationDemand?.value?.let { "$it" } ?: "Not available"

  companion object {
    /**
     * Aggregates the records that exist into the three distinct figures. The
     * narrowest, most recent record wins for each role - the same deterministic
     * rule the demand resolver uses. Nothing is derived that the source did not
     * state: a missing role stays null.
     */
    fun of(
      records: List<PopulationRecord>,
      resolution: PopulationDemandResolution
    ): PopulationAssessment {
      fun best(role: PopulationRole): PopulationRecord? = records
        .filter { it.role == role && it.hasValue }
        .minWithOrNull(
          compareBy<PopulationRecord> { it.scope.specificity }
            .thenByDescending { it.referenceMillis ?: Long.MIN_VALUE }
            .thenByDescending { it.confidence ?: 0.0 }
            .thenBy { it.id }
        )

      val selected = resolution.selected
      // An unclassified record can never be selected (the resolver rejects it),
      // so this returns null only for the impossible case - and then there is
      // simply no provenance to report rather than a made-up classification.
      val classification = selected?.let { record ->
        when (record.classification) {
          // A VERIFIED count used as demand under approval is an assumption, so
          // it is reported as derived rather than observed.
          PopulationClassification.VERIFIED -> if (resolution.derivedFrom != null) {
            DataClassification.DERIVED
          } else {
            DataClassification.OBSERVED
          }
          PopulationClassification.ESTIMATED -> DataClassification.ESTIMATED
          PopulationClassification.USER_DECLARED -> DataClassification.CONFIGURED
          PopulationClassification.SIMULATED -> DataClassification.SIMULATED
          PopulationClassification.NOT_PROVIDED -> null
        }
      }
      return PopulationAssessment(
        baseline = best(PopulationRole.BASELINE_TOTAL),
        affected = best(PopulationRole.AFFECTED_POPULATION),
        relocationDemand = selected,
        areaDescription = resolution.demand.scopeLabel,
        records = records,
        provenance = if (selected != null && classification != null) {
          DataProvenance(
            source = selected.source,
            status = DataProvenance.STATUS_FIELD,
            confidence = selected.confidence ?: 0.0,
            // Only a VERIFIED source counts as verified. A citizen declaration
            // and a demonstration record never do.
            isVerified = selected.classification == PopulationClassification.VERIFIED,
            classification = classification
          )
        } else {
          null
        }
      )
    }
  }
}
