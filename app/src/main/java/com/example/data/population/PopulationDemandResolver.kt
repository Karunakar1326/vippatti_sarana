package com.example.data.population

import com.example.data.capacity.RelocationDemand
import com.example.data.capacity.ResourceDataState

/**
 * ============================================================================
 * RELOCATION DEMAND RESOLUTION (SIH 26191)
 * ============================================================================
 *
 * Picks the population figure that may be used as relocation demand, in this
 * order, and only when it is actually available and properly classified:
 *
 *   1. VERIFIED event-specific relocation demand
 *   2. A clearly labelled authority/field assessment of relocation demand
 *      (ESTIMATED, from an authority or field-survey source)
 *   3. AFFECTED population - only with explicit policy approval, and the
 *      derivation is recorded instead of being hidden
 *   4. BASELINE population - only with explicit policy approval, labelled as an
 *      assumption rather than a demand
 *   5. The citizen's own household declaration - personal scope only, never the
 *      population of an area
 *   6. Nothing available -> people = null, which the capacity engine reports as
 *      INSUFFICIENT_DATA. Never a guess.
 *
 * Baseline population is never converted into a demand silently: it requires
 * [PopulationDemandPolicy.allowBaselineAsDemand], and even then the resulting
 * demand is marked ESTIMATED with [PopulationRole.BASELINE_TOTAL] as its origin.
 */

/** Explicit approvals needed before a population role may stand in as demand. */
data class PopulationDemandPolicy(
  val allowAffectedAsDemand: Boolean = false,
  val allowBaselineAsDemand: Boolean = false
)

/** Why a record was not used as the demand. */
data class RejectedPopulation(
  val record: PopulationRecord,
  val reason: String
)

/** The outcome of one resolution run, fully explainable. */
data class PopulationDemandResolution(
  /** Capacity-engine-ready demand; people == null means INSUFFICIENT_DATA. */
  val demand: RelocationDemand,
  /** The record that produced the demand (null when nothing was available). */
  val selected: PopulationRecord?,
  /** Set when the demand was DERIVED from another role (affected/baseline). */
  val derivedFrom: PopulationRole?,
  val rejected: List<RejectedPopulation> = emptyList(),
  val assumptions: List<String> = emptyList()
) {
  val isAvailable: Boolean get() = demand.people != null

  val statusLabel: String
    get() = when {
      selected == null -> "INSUFFICIENT_DATA — no population source available"
      demand.state == ResourceDataState.SIMULATED ->
        "SIMULATED — demand comes from demonstration data"
      demand.state == ResourceDataState.USER_DECLARED ->
        "USER_DECLARED — personal household only, not an area population"
      else -> "${selected.classification.label.uppercase()} — ${selected.role.label}"
    }
}

object PopulationDemandResolver {

  private val NO_DEMAND_BASIS =
    "No verified, authority, affected-population or household population record " +
      "is available, so no relocation demand is stated."

  /**
   * Resolves the demand record. [householdDeclaration] is the citizen's own
   * personal input and is the LAST resort, never a habitation population.
   */
  fun resolve(
    records: List<PopulationRecord>,
    householdDeclaration: PopulationRecord? = null,
    policy: PopulationDemandPolicy = PopulationDemandPolicy()
  ): PopulationDemandResolution {
    val rejected = mutableListOf<RejectedPopulation>()
    val usable = records.filter { record ->
      when {
        record.value == null -> {
          rejected += RejectedPopulation(record, "No value supplied by the source")
          false
        }
        record.value <= 0 -> {
          rejected += RejectedPopulation(record, "Value ${record.value} is not a usable population")
          false
        }
        record.classification == PopulationClassification.NOT_PROVIDED -> {
          rejected += RejectedPopulation(record, "Record carries no classification")
          false
        }
        else -> true
      }
    }

    // 1) VERIFIED relocation demand (narrowest area first, then most recent).
    bestOf(usable.filter {
      it.role == PopulationRole.RELOCATION_DEMAND &&
        it.classification == PopulationClassification.VERIFIED &&
        !it.isHouseholdLevel
    })?.let { return from(it, derivedFrom = null, rejected) }

    // 2) Authority / field assessment of relocation demand (labelled estimated).
    bestOf(usable.filter {
      it.role == PopulationRole.RELOCATION_DEMAND &&
        it.classification == PopulationClassification.ESTIMATED &&
        it.sourceKind in AUTHORITY_SOURCES &&
        !it.isHouseholdLevel
    })?.let { return from(it, derivedFrom = null, rejected) }

    // 3) Affected population, only with explicit approval.
    val affectedCandidates = usable.filter {
      it.role == PopulationRole.AFFECTED_POPULATION &&
        it.classification in ASSERTED &&
        !it.isHouseholdLevel
    }
    if (affectedCandidates.isNotEmpty() && !policy.allowAffectedAsDemand) {
      affectedCandidates.forEach {
        rejected += RejectedPopulation(
          it,
          "Affected population is not relocation demand; workflow approval required"
        )
      }
    }
    if (policy.allowAffectedAsDemand) {
      bestOf(affectedCandidates)?.let { return from(it, derivedFrom = PopulationRole.AFFECTED_POPULATION, rejected) }
    }

    // 4) Baseline population, only with explicit approval.
    val baselineCandidates = usable.filter {
      it.role == PopulationRole.BASELINE_TOTAL &&
        it.classification in ASSERTED &&
        !it.isHouseholdLevel
    }
    if (baselineCandidates.isNotEmpty() && !policy.allowBaselineAsDemand) {
      baselineCandidates.forEach {
        rejected += RejectedPopulation(
          it,
          "Baseline population is not confirmed relocation demand; workflow approval required"
        )
      }
    }
    if (policy.allowBaselineAsDemand) {
      bestOf(baselineCandidates)?.let { return from(it, derivedFrom = PopulationRole.BASELINE_TOTAL, rejected) }
    }

    // 5) Simulated demonstration demand, only when nothing real exists.
    val simulated = usable.filter {
      it.role == PopulationRole.RELOCATION_DEMAND &&
        it.classification == PopulationClassification.SIMULATED
    }
    bestOf(simulated)?.let { return from(it, derivedFrom = null, rejected) }

    // 6) The citizen's own household declaration (personal, last resort).
    householdDeclaration?.let { declaration ->
      if (declaration.hasValue) {
        return from(declaration, derivedFrom = null, rejected)
      }
      rejected += RejectedPopulation(declaration, "Household declaration carries no size")
    }

    // 7) Nothing usable: INSUFFICIENT_DATA, with the reasons recorded.
    return PopulationDemandResolution(
      demand = RelocationDemand(
        people = null,
        state = ResourceDataState.NOT_PROVIDED,
        source = "No population source connected",
        basis = NO_DEMAND_BASIS,
        notes = listOf(
          "Connect a census/relief-registry population source to obtain a demand figure.",
          "Affected-population and baseline-population derivations require explicit approval."
        )
      ),
      selected = null,
      derivedFrom = null,
      rejected = rejected,
      assumptions = listOf(
        "Baseline population is never converted into relocation demand automatically.",
        "A household declaration is a personal input, not an area population."
      )
    )
  }

  /** Builds the capacity-ready demand from the chosen record. */
  private fun from(
    record: PopulationRecord,
    derivedFrom: PopulationRole?,
    rejected: List<RejectedPopulation>
  ): PopulationDemandResolution {
    val derived = derivedFrom != null
    val assumptions = buildList {
      if (derived) {
        add(
          "Demand derived from ${derivedFrom!!.label.lowercase()} under explicit workflow " +
            "approval — an assumption, not an official relocation decision."
        )
      }
      if (record.isHouseholdLevel) {
        add("Personal household demand only: this is NOT the population of the area.")
      }
      if (record.classification == PopulationClassification.SIMULATED) {
        add("Demand comes from demonstration data; the verdict is SIMULATED.")
      }
      record.notes.forEach { add(it) }
    }
    val basis = buildString {
      append("${record.role.label} ${record.value} people")
      append(" • ${record.scopeLabel}")
      if (derived) append(" • derived from ${derivedFrom!!.label.lowercase()}")
      append(" • ${record.attribution}")
    }
    return PopulationDemandResolution(
      demand = RelocationDemand(
        people = record.value,
        state = if (derived && record.classification == PopulationClassification.VERIFIED) {
          // The value is verified but serving as demand is our assumption.
          ResourceDataState.ESTIMATED
        } else {
          record.classification.asResourceDataState()
        },
        source = record.source,
        basis = basis,
        notes = assumptions,
        scope = record.scope,
        scopeName = record.areaName,
        classification = if (derived && record.classification == PopulationClassification.VERIFIED) {
          PopulationClassification.ESTIMATED
        } else {
          record.classification
        },
        role = record.role,
        referenceMillis = record.referenceMillis,
        confidence = record.confidence,
        derivedFromRole = derivedFrom
      ),
      selected = record,
      derivedFrom = derivedFrom,
      rejected = rejected,
      assumptions = assumptions
    )
  }

  /**
   * Narrowest area wins; among equal scopes the most recent reference wins, then
   * the higher declared confidence. Deterministic - never list order.
   */
  private fun bestOf(candidates: List<PopulationRecord>): PopulationRecord? =
    candidates.minWithOrNull(
      compareBy<PopulationRecord> { it.scope.specificity }
        .thenByDescending { it.referenceMillis ?: Long.MIN_VALUE }
        .thenByDescending { it.confidence ?: 0.0 }
        .thenBy { it.id }
    )

  /** Classifications that may serve as an asserted population figure. */
  private val ASSERTED = setOf(
    PopulationClassification.VERIFIED,
    PopulationClassification.ESTIMATED
  )

  /** Sources whose relocation-demand estimate counts as an authority assessment. */
  private val AUTHORITY_SOURCES = setOf(
    PopulationSourceKind.AUTHORITY_ASSESSMENT,
    PopulationSourceKind.FIELD_SURVEY
  )
}
