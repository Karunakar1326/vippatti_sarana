package com.example.data.disaster

/**
 * Network boundary for one disaster-data provider. Providers return
 * NORMALIZED [DisasterEvent] objects with full provenance; the repository
 * handles validation, dedup, expiry and caching — never the provider.
 */
interface DisasterDataProvider {
  /** Stable identity of this provider (labels, refresh cadence). */
  val providerId: DisasterSource

  /**
   * Fetch current events within the India bounding box (or map bounds when
   * the provider supports geographic queries natively).
   */
  suspend fun fetchIndiaEvents(): ProviderResult
}

/** Outcome of one provider call. */
sealed class ProviderResult {
  data class Success(
    val events: List<DisasterEvent>,
    val fetchedAtMillis: Long
  ) : ProviderResult()

  data class Failure(
    val reason: String,
    val isAuthProblem: Boolean = false
  ) : ProviderResult()
}
