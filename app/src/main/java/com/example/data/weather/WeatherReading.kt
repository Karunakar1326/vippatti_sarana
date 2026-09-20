package com.example.data.weather

import com.example.data.disaster.WeatherMetrics
import com.example.data.model.DataStatus

/**
 * ============================================================================
 * PHASE 3 - HONEST WEATHER FETCH OUTCOME
 * ============================================================================
 *
 * The old weather call collapsed every problem (offline, timeout, HTTP error,
 * payload without a live reading, unexpected exception) into a bare `null`, so
 * the UI could only say "no feed" and never why. A fetch now reports what
 * actually happened, and nothing is invented to fill a failure.
 */
enum class WeatherFailureKind(val label: String) {
  /** The device could not reach Open-Meteo at all (offline, DNS, TLS). */
  NO_CONNECTION("No connection to Open-Meteo"),
  /** The request was sent but the service did not answer in time. */
  TIMEOUT("Open-Meteo did not answer in time"),
  /** Open-Meteo answered with a non-success status code. */
  HTTP_ERROR("Open-Meteo returned an error response"),
  /** A 2xx response whose body held no usable live reading. */
  BAD_PAYLOAD("Open-Meteo response had no live reading"),
  /** Anything else (unexpected exception) - reported, never guessed at. */
  REQUEST_FAILED("Open-Meteo request failed")
}

sealed interface WeatherReading {
  /** A real reading. [retrievedAtMillis] is stamped by the caller when it lands. */
  data class Success(val metrics: WeatherMetrics) : WeatherReading

  data class Failure(
    val kind: WeatherFailureKind,
    /** Provider/exception detail, shown verbatim. Null when there is none. */
    val detail: String? = null
  ) : WeatherReading
}

/**
 * Maps a failed fetch onto the Phase 2 status vocabulary.
 *
 *  - No connection / timeout -> UNAVAILABLE (the data simply is not reachable),
 *    or STALE when a real earlier reading is still on screen.
 *  - HTTP error / bad payload / unexpected failure -> ERROR (the service was
 *    reached and did not deliver a usable reading), or STALE with a reading.
 */
fun WeatherReading.Failure.dataStatus(hasUsableReading: Boolean): DataStatus = when {
  hasUsableReading -> DataStatus.STALE
  kind == WeatherFailureKind.NO_CONNECTION || kind == WeatherFailureKind.TIMEOUT ->
    DataStatus.UNAVAILABLE
  else -> DataStatus.ERROR
}

/** Honest one-line reason for the UI, e.g. "Open-Meteo returned an error response (HTTP 503)". */
val WeatherReading.Failure.reason: String
  get() = kind.label + (detail?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "")
