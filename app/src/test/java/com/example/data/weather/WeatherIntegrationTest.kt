package com.example.data.weather

import com.example.data.model.DataStatus
import com.example.data.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * PHASE 3 - live weather integration contracts:
 *  - the request stays keyless, metric and pointed at Open-Meteo;
 *  - the PROVIDER's observation time is carried through, and is 0 (unknown)
 *    rather than guessed when the payload omits it;
 *  - every failure mode maps to the Phase 2 status vocabulary and can never
 *    read as SUCCESS.
 */
class WeatherIntegrationTest {

  /** Munnar, Idukki (pilot region). */
  private val point = GeoPoint(10.0889, 77.0595)

  private val fullPayload = """
    {"latitude":10.09,"longitude":77.06,"utc_offset_seconds":19800,
     "current":{"time":"2026-09-20T07:45","temperature_2m":18.4,"precipitation":0.6,"wind_speed_10m":11.0},
     "hourly":{"temperature_2m":[16.0,16.5,17.0,18.4]}}
  """.trimIndent()

  private val payloadWithoutTime = """
    {"current":{"temperature_2m":18.4,"precipitation":0.6,"wind_speed_10m":11.0},
     "hourly":{"temperature_2m":[16.0,16.5,17.0,18.4]}}
  """.trimIndent()

  @Test
  fun `request is keyless, metric and pinned to the Open-Meteo endpoint`() {
    val url = OpenMeteoWeatherService.buildUrl(point)
    assertTrue(url.startsWith(OpenMeteoWeatherService.ENDPOINT))
    assertTrue(url.contains("latitude=10.0889"))
    assertTrue(url.contains("longitude=77.0595"))
    assertTrue(url.contains("temperature_unit=celsius"))
    assertTrue(url.contains("wind_speed_unit=kmh"))
    assertTrue(url.contains("timezone=auto"))
    assertFalse("the weather request must stay keyless", url.contains("key", ignoreCase = true))
  }

  @Test
  fun `provider observation time is carried through with its real offset`() {
    val metrics = OpenMeteoWeatherService.parseResponse(fullPayload)
    assertNotNull(metrics)
    val expected = LocalDateTime.of(2026, 9, 20, 7, 45)
      .toEpochSecond(ZoneOffset.ofHoursMinutes(5, 30)) * 1000L
    assertEquals(expected, metrics!!.observedAtMillis)
    assertEquals("18.4°C", metrics.currentTemp)
  }

  @Test
  fun `a payload without a provider timestamp reports unknown, never a guess`() {
    val metrics = OpenMeteoWeatherService.parseResponse(payloadWithoutTime)
    assertNotNull(metrics)
    assertEquals(0L, metrics!!.observedAtMillis)
  }

  @Test
  fun `unreachable service is UNAVAILABLE without a reading and STALE with one`() {
    val offline = WeatherReading.Failure(WeatherFailureKind.NO_CONNECTION)
    val timeout = WeatherReading.Failure(WeatherFailureKind.TIMEOUT)
    assertEquals(DataStatus.UNAVAILABLE, offline.dataStatus(hasUsableReading = false))
    assertEquals(DataStatus.UNAVAILABLE, timeout.dataStatus(hasUsableReading = false))
    assertEquals(DataStatus.STALE, offline.dataStatus(hasUsableReading = true))
    assertEquals(DataStatus.STALE, timeout.dataStatus(hasUsableReading = true))
  }

  @Test
  fun `a reachable service that did not deliver is ERROR, or STALE with a reading`() {
    listOf(
      WeatherFailureKind.HTTP_ERROR,
      WeatherFailureKind.BAD_PAYLOAD,
      WeatherFailureKind.REQUEST_FAILED
    ).forEach { kind ->
      val failure = WeatherReading.Failure(kind, "detail")
      assertEquals("$kind with no reading", DataStatus.ERROR, failure.dataStatus(false))
      assertEquals("$kind with a reading", DataStatus.STALE, failure.dataStatus(true))
    }
  }

  @Test
  fun `no failure can ever read as SUCCESS`() {
    WeatherFailureKind.entries.forEach { kind ->
      val failure = WeatherReading.Failure(kind)
      assertTrue(
        "$kind must not read as live",
        failure.dataStatus(hasUsableReading = false) != DataStatus.SUCCESS &&
          failure.dataStatus(hasUsableReading = true) != DataStatus.SUCCESS
      )
    }
  }

  @Test
  fun `failure reasons keep the provider or exception detail`() {
    assertEquals("No connection to Open-Meteo", WeatherReading.Failure(WeatherFailureKind.NO_CONNECTION).reason)
    assertEquals(
      "Open-Meteo returned an error response (HTTP 503)",
      WeatherReading.Failure(WeatherFailureKind.HTTP_ERROR, "HTTP 503").reason
    )
    assertEquals(
      "Open-Meteo request failed",
      WeatherReading.Failure(WeatherFailureKind.REQUEST_FAILED, "   ").reason
    )
  }
}
