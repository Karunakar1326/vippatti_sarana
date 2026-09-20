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

/**
 * Why a provider call did not return data. Kept separate from the free-text
 * [ProviderResult.Failure.reason] so the UI can distinguish "this optional
 * source was never configured" from "the source rejected our credential" from
 * "the source or the network failed" instead of showing one red ERROR for all
 * three.
 *
 * AVAILABLE is not a member: it is [ProviderResult.Success].
 */
enum class ProviderFailureKind(val label: String) {
  /** No credential configured for this build (e.g. FIRMS_MAP_KEY absent). */
  UNCONFIGURED("Not configured"),
  /** A credential was configured and the provider rejected it. */
  AUTHENTICATION_FAILED("Authentication failed"),
  /** Network, server, parsing or any other runtime failure. */
  FAILED("Failed")
}

/** Outcome of one provider call. */
sealed class ProviderResult {
  /** AVAILABLE: a valid provider response was received. */
  data class Success(
    val events: List<DisasterEvent>,
    val fetchedAtMillis: Long
  ) : ProviderResult()

  data class Failure(
    val reason: String,
    val kind: ProviderFailureKind = ProviderFailureKind.FAILED
  ) : ProviderResult()
}
