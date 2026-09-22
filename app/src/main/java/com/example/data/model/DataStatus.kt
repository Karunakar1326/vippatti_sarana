package com.example.data.model

/**
 * ============================================================================
 * PHASE 2 — UNIFIED DATA-STATUS + PROVENANCE MODEL
 * ============================================================================
 *
 * One vocabulary for every data surface (disaster events, news, weather,
 * routing, shelters, reports). Built on the existing [DataProvenance] /
 * [DataClassification] — nothing is replaced, and no status is ever claimed
 * unless the inputs support it.
 *
 * Precedence rule (single status per record):
 *   Error > NotConfigured > Unavailable > Simulated > Live-verified >
 *   Live-unverified > Stale > Empty. A record can only be LIVE when it has a
 *   real observed time;
 *   a record with `lastUpdatedMillis <= 0` means "time unknown", never "fresh"
 *   (the old `DataProvenance.isFresh` treated 0 as fresh — kept for compat, not reused).
 */
enum class DataStatus(val label: String) {
  LOADING("Loading"),
  SUCCESS("Live"),
  VERIFIED("Verified"),
  STALE("Stale"),
  EMPTY("No data"),
  /**
   * The optional source has no credential/configuration in this build. It is
   * NOT an error and NOT live: it simply was never switched on here.
   */
  NOT_CONFIGURED("Not configured"),
  UNAVAILABLE("Unavailable"),
  ERROR("Error"),
  SIMULATED("Simulated"),
  NOT_VERIFIED("Not verified"),
  /**
   * Archived records (EM-DAT). Distinct from SUCCESS: a periodically updated
   * archive is still history, and calling it "Live" would be false.
   */
  HISTORICAL("Historical")
}

/**
 * Display metadata for one record: where it came from, when it was observed
 * and retrieved, what it covers, how fresh it is, and what went wrong.
 * Fields that do not exist stay null/blank — nothing is invented to fill UI.
 */
data class RecordStamp(
  val sourceName: String,
  val sourceUrl: String? = null,
  /** When the PROVIDER observed/recorded the event; null/<=0 = unknown. */
  val eventAtMillis: Long? = null,
  /** When THIS app retrieved it; null/<=0 = unknown. */
  val retrievedAtMillis: Long? = null,
  /** Human coverage, e.g. "Idukki district" or "India-wide". */
  val coverage: String? = null,
  val status: DataStatus,
  /** Provider-supplied confidence, e.g. "Source confidence: high". */
  val confidenceNote: String? = null,
  /** Failure reason when status is ERROR/UNAVAILABLE. */
  val errorMessage: String? = null,
  val classification: DataClassification = DataClassification.SIMULATED
) {
  /** Honest relative age of the observation, or "Time unknown". */
  fun eventAgeLabel(nowMillis: Long): String = when (val at = eventAtMillis) {
    null -> "Time unknown"
    else -> if (at <= 0L) "Time unknown"
    else com.example.data.news.NewsPresentation.relativeAge(at, nowMillis).let { "Observed $it" }
  }

  /** Honest relative age of the retrieval, or "Time unknown". */
  fun retrievedAgeLabel(nowMillis: Long): String = when (val at = retrievedAtMillis) {
    null -> "Time unknown"
    else -> if (at <= 0L) "Time unknown"
    else com.example.data.news.NewsPresentation.relativeAge(at, nowMillis).let { "Fetched $it" }
  }
}

/**
 * Single-status derivation from the existing provenance record.
 *
 *  - [DataClassification.SIMULATED] (mock network) -> SIMULATED, always.
 *  - explicit provider failure -> ERROR.
 *  - verified source feed (non-user, non-mock) with a real observed time -> SUCCESS.
 *  - anything else with no usable timestamps -> NOT_VERIFIED (never "live").
 */
fun statusOf(
  provenance: DataProvenance,
  eventAtMillis: Long?,
  hasError: Boolean = false,
  errorMessage: String? = null
): RecordStamp = when {
  hasError -> RecordStamp(
    sourceName = provenance.source,
    eventAtMillis = eventAtMillis,
    status = DataStatus.ERROR,
    errorMessage = errorMessage,
    classification = provenance.classification
  )
  provenance.classification == DataClassification.SIMULATED || !provenance.isVerified ->
    RecordStamp(
      sourceName = provenance.source,
      eventAtMillis = eventAtMillis,
      status = if (provenance.classification == DataClassification.SIMULATED) {
        DataStatus.SIMULATED
      } else {
        DataStatus.NOT_VERIFIED
      },
      classification = provenance.classification
    )
  else -> RecordStamp(
    sourceName = provenance.source,
    eventAtMillis = eventAtMillis,
    // A verified record with no real observed time is NOT live: the time is
    // unknown, so it reads NOT_VERIFIED rather than implying freshness.
    status = if (eventAtMillis != null && eventAtMillis > 0L) {
      DataStatus.SUCCESS
    } else {
      DataStatus.NOT_VERIFIED
    },
    classification = provenance.classification
  )
}

/** Freshness used by list/row states: fresh (<15 min), stale (older), unknown (no time). */
enum class Freshness { FRESH, STALE, UNKNOWN }

fun freshnessOf(retrievedAtMillis: Long?, nowMillis: Long): Freshness = when {
  retrievedAtMillis == null || retrievedAtMillis <= 0L -> Freshness.UNKNOWN
  nowMillis - retrievedAtMillis < 15L * 60L * 1000L -> Freshness.FRESH
  else -> Freshness.STALE
}