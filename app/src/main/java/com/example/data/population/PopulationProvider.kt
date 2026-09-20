package com.example.data.population

/**
 * ============================================================================
 * POPULATION DATA BOUNDARY (integration contract only)
 * ============================================================================
 *
 * There is NO census, registrar or relief-registry population API connected in
 * this build. This interface is the single place a real source will plug in -
 * official census/registrar data (baseline), a state disaster authority's
 * affected-population assessment, a field survey, or a remote-sensing estimate.
 *
 * Until then [NoPopulationProvider] returns an empty list: the honest outcome is
 * "no population source available", which flows through as
 * INSUFFICIENT_DATA. Nothing here invents a figure, and nothing claims a source
 * is live when it is not.
 */
fun interface PopulationDataProvider {
  /**
   * Records for the whole area of interest. Implementations must return real
   * records only; an empty list means "nothing available", never "zero people".
   */
  suspend fun fetchRecords(): List<PopulationRecord>
}

/**
 * Default provider: no source connected. Kept explicit (rather than a nullable
 * provider) so every call site states what it means.
 */
object NoPopulationProvider : PopulationDataProvider {
  override suspend fun fetchRecords(): List<PopulationRecord> = emptyList()
}
