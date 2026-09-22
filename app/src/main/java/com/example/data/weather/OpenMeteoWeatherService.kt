package com.example.data.weather

import com.example.data.disaster.WeatherMetrics
import com.example.data.routing.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.InterruptedIOException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * LIVE weather readings from Open-Meteo (keyless, no signup — same standing
 * as the USGS/FIRMS/IMD providers): current temperature, precipitation,
 * wind and a real 3-hour temperature trend for the radar bottom bar.
 *
 * PHASE 3: a fetch now returns a [WeatherReading] so the UI can distinguish
 * "could not reach the service" from "the service answered with an error" from
 * "the payload had no live reading". A failure never yields invented values —
 * the caller keeps whatever real reading it already had (marked STALE) or shows
 * the honest unavailable state.
 *
 * The provider's own observation time (`current.time` + `utc_offset_seconds`)
 * is carried through to [WeatherMetrics.observedAtMillis]; when it is missing
 * or unparsable it stays 0L, which the UI renders as "observation time unknown".
 */
object OpenMeteoWeatherService {

  const val ENDPOINT = "https://api.open-meteo.com/v1/forecast"

  private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(8, TimeUnit.SECONDS)
      .readTimeout(12, TimeUnit.SECONDS)
      .callTimeout(20, TimeUnit.SECONDS)
      .build()
  }

  /** Request URL for one coordinate — keyless, metric, provider-local timezone. */
  fun buildUrl(point: GeoPoint): String =
    "$ENDPOINT?latitude=${point.lat}&longitude=${point.lon}" +
      "&current=temperature_2m,precipitation,wind_speed_10m" +
      "&hourly=temperature_2m&past_days=1&forecast_days=1" +
      "&temperature_unit=celsius&wind_speed_unit=kmh&timezone=auto"

  /** Fetch a live reading, reporting honestly WHY it failed when it did. */
  suspend fun fetchReading(point: GeoPoint): WeatherReading = withContext(Dispatchers.IO) {
    try {
      val request = Request.Builder()
        .url(buildUrl(point))
        .header("User-Agent", "VippattiSarana-DisasterRelief/1.0 (Android; Open-Meteo)")
        .build()
      httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          return@withContext WeatherReading.Failure(
            kind = WeatherFailureKind.HTTP_ERROR,
            detail = "HTTP ${response.code}"
          )
        }
        val body = response.body?.string()
        if (body.isNullOrBlank()) {
          return@withContext WeatherReading.Failure(
            kind = WeatherFailureKind.BAD_PAYLOAD,
            detail = "empty response body"
          )
        }
        val metrics = parseResponse(body)
          ?: return@withContext WeatherReading.Failure(
            kind = WeatherFailureKind.BAD_PAYLOAD,
            detail = "no live temperature in the payload"
          )
        WeatherReading.Success(metrics)
      }
    } catch (timeout: InterruptedIOException) {
      WeatherReading.Failure(WeatherFailureKind.TIMEOUT, timeout.message)
    } catch (offline: IOException) {
      WeatherReading.Failure(WeatherFailureKind.NO_CONNECTION, offline.message)
    } catch (unexpected: Exception) {
      WeatherReading.Failure(
        kind = WeatherFailureKind.REQUEST_FAILED,
        detail = unexpected::class.java.simpleName
      )
    }
  }

  /**
   * Pure payload parser (unit-testable): current block -> temp/rain/wind and
   * the provider observation time, hourly series -> 3-hour trend. Null when the
   * payload lacks live values.
   */
  fun parseResponse(body: String): WeatherMetrics? {
    return try {
      val root = JSONObject(body)
      val current = root.optJSONObject("current") ?: return null
      if (current.isNull("temperature_2m")) return null
      val temp = current.optDouble("temperature_2m", Double.NaN)
      if (temp.isNaN()) return null
      val precip = current.optDouble("precipitation", 0.0)
      val wind = current.optDouble("wind_speed_10m", Double.NaN)

      val trend = parseTrend3h(root.optJSONObject("hourly"))
      WeatherMetrics(
        currentTemp = String.format(Locale.US, "%.1f°C", temp),
        rainfallIntensity = String.format(Locale.US, "%.1f mm/h", precip),
        windGust = if (wind.isNaN()) "" else String.format(Locale.US, "%.0f km/h", wind),
        trend3h = trend,
        surgeForecast = "",
        observedAtMillis = observationEpochMillis(
          localTime = current.optString("time", ""),
          utcOffsetSeconds = root.optInt("utc_offset_seconds", Int.MIN_VALUE)
        )
      )
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Open-Meteo returns `current.time` as local wall-clock ("2026-09-20T07:45")
   * plus the zone offset in seconds. Returns the real epoch millis, or 0L when
   * either piece is missing or unparsable — never a guessed timestamp.
   */
  private fun observationEpochMillis(localTime: String, utcOffsetSeconds: Int): Long {
    if (localTime.isBlank() || utcOffsetSeconds == Int.MIN_VALUE) return 0L
    return try {
      val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
      format.timeZone = TimeZone.getTimeZone("UTC")
      val asIfUtc = format.parse(localTime)?.time ?: return 0L
      asIfUtc - utcOffsetSeconds * 1000L
    } catch (_: Exception) {
      0L
    }
  }

  private fun parseTrend3h(hourly: JSONObject?): String {
    if (hourly == null) return ""
    val temps = hourly.optJSONArray("temperature_2m") ?: return ""
    // Last entries are the most recent hours; compare now vs 3 hours back.
    val vals = buildList {
      for (i in 0 until temps.length()) {
        val v = temps.optDouble(i, Double.NaN)
        if (!v.isNaN()) add(v)
      }
    }
    if (vals.size < 4) return ""
    val delta = vals.last() - vals[vals.size - 4]
    return when {
      delta > 0.5 -> "Warming +${String.format(Locale.US, "%.1f", delta)}°/3h"
      delta < -0.5 -> "Cooling ${String.format(Locale.US, "%.1f", delta)}°/3h"
      abs(delta) <= 0.5 -> "Steady"
      else -> ""
    }
  }
}
