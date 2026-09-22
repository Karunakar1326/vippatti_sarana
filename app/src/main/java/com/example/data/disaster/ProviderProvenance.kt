package com.example.data.disaster

import com.example.data.model.DataClassification
import com.example.data.model.DataStatus
import com.example.data.model.RecordStamp

/**
 * ============================================================================
 * PHASE 2 - PROVIDER STATE -> UNIFIED DATA STATUS
 * ============================================================================
 *
 * One mapping from the disaster repository's [ProviderState] to the shared
 * [DataStatus] vocabulary, so the map, the Profile card and the detail dialogs
 * all describe a source the same way instead of each re-deciding it.
 *
 * Honesty rules encoded here:
 *  - SUCCESS means "this session fetched it from the provider and no failure
 *    was reported for that provider". Only then may a surface say LIVE.
 *  - Cached data is never SUCCESS, even inside its freshness window: the events
 *    are real, but they were not fetched now, so they read STALE. The provider's
 *    own reason string travels untouched in [ProviderState.statusMessage], and
 *    [cacheNote] explains which kind of cache is on screen.
 *  - An unconfigured optional source reads NOT_CONFIGURED, not ERROR: nothing
 *    failed, the credential was simply never supplied for this build.
 *  - A failed provider is never SUCCESS, and its reason is never rewritten.
 */
fun ProviderState.dataStatus(): DataStatus = when {
  failureKind == ProviderFailureKind.UNCONFIGURED && !isFromCache ->
    DataStatus.NOT_CONFIGURED
  failureKind != null && !isFromCache -> DataStatus.ERROR
  statusMessage != null -> DataStatus.STALE
  isFromCache -> DataStatus.STALE
  isLive -> DataStatus.SUCCESS
  else -> DataStatus.STALE
}

/**
 * Short, factual note describing how the shown events reached the device.
 * Null when the shard was fetched live in this session - there is nothing to
 * disclose beyond the fresh fetch itself.
 */
val ProviderState.cacheNote: String?
  get() = when {
    failureKind == ProviderFailureKind.UNCONFIGURED && !isFromCache ->
      "no live source configured in this build"
    failureKind == ProviderFailureKind.UNCONFIGURED && isFromCache ->
      "cached events, live source not configured"
    statusMessage != null && isFromCache -> "cached events, live source unreachable"
    statusMessage == null && isFromCache && isLive -> "cache within its freshness window"
    statusMessage == null && isFromCache -> "older cache, still usable"
    else -> null
  }

/**
 * Display metadata for one provider shard. Fields the provider never supplied
 * (event timestamp, confidence) stay null so the UI can say "not available"
 * rather than invent a value.
 */
fun ProviderState.recordStamp(): RecordStamp = RecordStamp(
  sourceName = source.label,
  retrievedAtMillis = fetchedAtMillis.takeIf { it > 0L },
  coverage = cacheNote ?: "India-wide provider query",
  status = dataStatus(),
  errorMessage = statusMessage?.takeIf { it.isNotBlank() },
  classification = DataClassification.OBSERVED
)
