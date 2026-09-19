package com.example.data.model

/**
 * Classification describing HOW a piece of operational data was produced.
 * Every major data model carries this so future verified feeds
 * (IMD, KSDMA, NRSC/ISRO, CWC, GSI, NDMA/SACHET) can replace field data
 * transparently, without changing any consumer code.
 */
enum class DataClassification(val label: String) {
  OBSERVED("Observed"),
  DERIVED("Derived"),
  ESTIMATED("Estimated"),
  CONFIGURED("Configured"),
  SIMULATED("Simulated")
}

/**
 * Provenance record attached to every major operational data model:
 * source, timestamp, last-updated, status, confidence, verified flag,
 * and observed/derived/estimated/simulated classification.
 */
data class DataProvenance(
  val source: String,
  val status: String = STATUS_FIELD,
  val confidence: Double = 0.6,
  val isVerified: Boolean = false,
  val classification: DataClassification = DataClassification.SIMULATED,
  val recordedAtMillis: Long = 0L,
  val lastUpdatedMillis: Long = 0L
) {
  /** A record with no timestamp is treated as always usable (field data). */
  val isFresh: Boolean
    get() = lastUpdatedMillis <= 0L ||
      (System.currentTimeMillis() - lastUpdatedMillis) < FRESHNESS_WINDOW_MS

  companion object {
    const val STATUS_FIELD = "FIELD RECORD"
    const val STATUS_OFFLINE_CACHE = "OFFLINE CACHE"
    const val STATUS_LIVE = "LIVE FEED"
    const val STATUS_PLANNED = "FUTURE INTEGRATION"
    private const val FRESHNESS_WINDOW_MS = 6L * 60L * 60L * 1000L
  }
}
