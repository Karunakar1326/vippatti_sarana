package com.example.data.weather

import com.example.data.disaster.WeatherMetrics
import com.example.data.routing.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * LIVE weather readings from Open-Meteo (keyless, no signup — same standing
 * as the USGS/FIRMS/IMD providers): current temperature, precipitation,
 * wind and a real 3-hour temperature trend for the radar bottom bar.
 *
 * Any failure (offline, timeout, bad payload) yields null and the UI keeps
 * its honest empty state — weather is never invented.
 */
object OpenMeteoWeatherService {

  private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(8, TimeUnit.SECONDS)
      .readTimeout(12, TimeUnit.SECONDS)
      .build()
  }

  suspend fun fetchNow(point: GeoPoint): WeatherMetrics? = withContext(Dispatchers.IO) {
    val url = "https://api.open-meteo.com/v1/forecast" +
      "?latitude=${point.lat}&longitude=${point.lon}" +
      "&current=temperature_2m,precipitation,wind_speed_10m" +
      "&hourly=temperature_2m&past_days=1&forecast_days=1" +
      "&temperature_unit=celsius&wind_speed_unit=kmh&timezone=auto"
    try {
      val request = Request.Builder()
        .url(url)
        .header("User-Agent", "VippattiSarana-DisasterRelief/1.0 (Android; Open-Meteo)")
        .build()
      httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return@withContext null
        val body = response.body?.string()
        if (body.isNullOrBlank()) return@withContext null
        parseResponse(body)
      }
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Pure payload parser (unit-testable): current block -> temp/rain/wind,
   * hourly series -> 3-hour trend. Null when the payload lacks live values.
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
        surgeForecast = ""
      )
    } catch (_: Exception) {
      null
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
