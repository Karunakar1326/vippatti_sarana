package com.example.data.disaster

import com.example.data.model.GeoMath
import com.example.data.model.HazardType
import com.example.data.model.HazardZone
import com.example.data.model.SafeZone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ============================================================================
 * ZONE DETAIL MAPPING — disaster-aware, dynamic popup content.
 * ============================================================================
 *
 * Data flow:
 *   Backend/API disaster response (DisasterEvent + provider details)
 *       -> Zone model (HazardZone; live zones keep "live-<dedupeKey>" ids)
 *       -> Frontend zone selection (tapped zone + its source event)
 *       -> Disaster-type-specific detail mapping (this object)
 *       -> Dynamic detail popup (renders sections/rows verbatim)
 *
 * RULES:
 *  - Every row's value comes from the selected zone, its linked source event,
 *    or the live safe-zone list. Nothing is invented to fill the UI.
 *  - value == null means the backend has no such field: the popup renders a
 *    clear "Data unavailable" state instead of a fake number.
 *  - No disaster-type-specific field may appear under another type (no
 *    avalanche rows on a flood zone, no magnitude rows without a quake
 *    event, ...). Pure Kotlin, fully unit-tested.
 */
data class ZoneDetailField(
  val label: String,
  /** Null = backend has no such field -> UI shows "Data unavailable". */
  val value: String?
)

data class ZoneDetailSection(
  val heading: String,
  val fields: List<ZoneDetailField>
)

/** Nearest VIABLE (feasible, open, capacity-holding) safe zone, computed live. */
data class NearestViableSafeZone(
  val name: String,
  val distanceText: String,
  val capacityText: String
)

data class ZoneDetail(
  val sections: List<ZoneDetailSection>,
  /** Null = no viable safe zone -> UI shows "No viable safe zone identified". */
  val nearestSafeZone: NearestViableSafeZone?
)

object ZoneDetailMapper {

  /**
   * Links a tapped [HazardZone] back to its backend [DisasterEvent] (live
   * provider event or citizen report). Mock-network zones have no source
   * event -> null, and their event-derived rows render "Data unavailable".
   */
  fun findSourceEvent(
    zone: HazardZone,
    providerEvents: List<DisasterEvent>,
    reportEvents: List<DisasterEvent> = emptyList()
  ): DisasterEvent? {
    val key = zone.id.removePrefix("live-")
    if (key == zone.id) return null // mock-network zone: no backend event
    return (providerEvents + reportEvents).firstOrNull { it.dedupeKey == key }
  }

  /**
   * Builds the popup content for [zone]: one type-specific section (plus an
   * optional citizen-report section when the source is a user report) and
   * the dynamically computed nearest viable safe zone.
   */
  fun map(
    zone: HazardZone,
    event: DisasterEvent?,
    feasibleSafeZones: List<SafeZone>
  ): ZoneDetail {
    val areaText = GeoMath.formatKm(zone.radiusMeters) + " radius"
    val sections = mutableListOf(mapTypeSection(zone, event, areaText))
    if (event != null && event.source == DisasterSource.USER_REPORT) {
      sections += ZoneDetailSection(
        heading = "CITIZEN REPORT",
        fields = listOf(
          ZoneDetailField("Category", event.title),
          ZoneDetailField(
            "Reporter note",
            event.description.takeIf { it.isNotBlank() }
          ),
          ZoneDetailField(
            "Reported at",
            event.observedAtMillis.takeIf { it > 0L }?.let { formatTime(it) }
          )
        )
      )
    }
    return ZoneDetail(
      sections = sections,
      nearestSafeZone = nearestViable(zone, feasibleSafeZones)
    )
  }

  // ---------------------------------------------------------- type sections

  private fun mapTypeSection(
    zone: HazardZone,
    event: DisasterEvent?,
    areaText: String
  ): ZoneDetailSection {
    if (zone.type == HazardType.OTHER && !isAvalancheZone(zone)) {
      return genericSection(zone, event, areaText)
    }
    val heading = (if (isAvalancheZone(zone)) "AVALANCHE" else zone.type.label.uppercase()) +
      " INTELLIGENCE"
    val quake = event?.details as? EventDetails.Quake
    val fields: List<ZoneDetailField> = when {
      isAvalancheZone(zone) -> listOf(
        ZoneDetailField("Avalanche warning level", zone.riskLevel),
        ZoneDetailField("Severity", zone.severity.label),
        ZoneDetailField("Affected area", areaText),
        ZoneDetailField("Elevation / terrain", null),
        ZoneDetailField("Warning status", zone.sourceStatus.takeIf { it.isNotBlank() }),
        ZoneDetailField("Nearby affected areas", null)
      )
      zone.type == HazardType.FLOOD -> listOf(
        ZoneDetailField("Flood severity", zone.severity.label),
        ZoneDetailField("Warning status", zone.riskLevel),
        ZoneDetailField("Affected area", areaText),
        ZoneDetailField("Water level / flood extent", null),
        ZoneDetailField("Rainfall / water-level trend", zone.trend.label),
        ZoneDetailField("Affected habitations", null)
      )
      zone.type == HazardType.LANDSLIDE -> listOf(
        ZoneDetailField("Landslide severity", zone.severity.label),
        ZoneDetailField("Warning status", zone.riskLevel),
        ZoneDetailField("Susceptible area", areaText),
        ZoneDetailField("Slope / terrain indicator", null),
        ZoneDetailField("Road / access status", null),
        ZoneDetailField("Affected habitations", null)
      )
      zone.type == HazardType.HEAVY_RAINFALL -> listOf(
        ZoneDetailField("Rainfall intensity", null),
        ZoneDetailField("Warning level", zone.severity.label),
        ZoneDetailField("Duration / time window", null),
        ZoneDetailField("Risk trend", zone.trend.label),
        ZoneDetailField("Affected area", areaText),
        ZoneDetailField("Affected habitations", null)
      )
      zone.type == HazardType.CYCLONE -> listOf(
        ZoneDetailField("Cyclone / alert name", zone.name),
        ZoneDetailField("Alert level", zone.severity.label),
        ZoneDetailField("Wind speed / gusts", null),
        ZoneDetailField("Rainfall", null),
        ZoneDetailField("Direction / movement", null),
        ZoneDetailField("Affected coastal / land area", areaText),
        ZoneDetailField("Evacuation status", null)
      )
      zone.type == HazardType.EARTHQUAKE -> listOf(
        ZoneDetailField("Magnitude", quake?.magnitude?.toString()),
        ZoneDetailField("Depth", quake?.let { "${it.depthKm} km" }),
        ZoneDetailField(
          "Epicenter",
          String.format(Locale.US, "%.4f N, %.4f E", zone.center.lat, zone.center.lon)
        ),
        ZoneDetailField(
          "Time of event",
          event?.observedAtMillis?.takeIf { it > 0L }?.let { formatTime(it) }
        ),
        ZoneDetailField("Intensity / risk level", zone.severity.label),
        ZoneDetailField("Affected area", areaText),
        ZoneDetailField("Aftershock information", null),
        ZoneDetailField("Nearby affected habitations", null)
      )
      zone.type == HazardType.FIRE -> listOf(
        ZoneDetailField("Fire severity", zone.severity.label),
        ZoneDetailField("Fire / watched area", areaText),
        ZoneDetailField(
          "Detection time",
          event?.observedAtMillis?.takeIf { it > 0L }?.let { formatTime(it) }
        ),
        ZoneDetailField("Spread trend", zone.trend.label),
        ZoneDetailField("Wind information", null),
        ZoneDetailField("Nearby habitations", null),
        ZoneDetailField("Evacuation status", null)
      )
      zone.type == HazardType.WEATHER_ALERT -> listOf(
        ZoneDetailField("Alert severity", zone.severity.label),
        ZoneDetailField("Warning status", zone.riskLevel),
        ZoneDetailField("Alert area", areaText),
        ZoneDetailField("Valid time window", null),
        ZoneDetailField("Risk trend", zone.trend.label)
      )
      else -> return genericSection(zone, event, areaText)
    }
    return ZoneDetailSection(heading = heading, fields = fields)
  }

  /**
   * Generic fallback for OTHER disasters: ONLY fields that exist on the
   * backend model. No specialized rows are ever invented here.
   */
  private fun genericSection(
    zone: HazardZone,
    event: DisasterEvent?,
    areaText: String
  ): ZoneDetailSection = ZoneDetailSection(
    heading = "${zone.type.label.uppercase()} INTELLIGENCE",
    fields = listOf(
      ZoneDetailField("Disaster type", zone.type.label),
      ZoneDetailField("Severity", zone.severity.label),
      ZoneDetailField("Risk level", zone.riskLevel),
      ZoneDetailField("Status", event?.status?.label),
      ZoneDetailField("Affected area", areaText),
      ZoneDetailField(
        "Location",
        String.format(Locale.US, "%.4f N, %.4f E", zone.center.lat, zone.center.lon)
      ),
      ZoneDetailField("Trend", zone.trend.label),
      ZoneDetailField("Nearby affected habitations", null)
    )
  )

  private fun isAvalancheZone(zone: HazardZone): Boolean =
    zone.type == HazardType.OTHER &&
      (zone.name.contains("avalanche", ignoreCase = true) ||
        zone.name.contains("snow", ignoreCase = true))

  // ------------------------------------------------------------ safe zone

  /**
   * Nearest VIABLE safe zone to the DISASTER zone's centre, drawn from the
   * live feasible list (open + capacity-holding). Null when none exists.
   * Name, distance and free spots all come from live records — never fixed.
   */
  private fun nearestViable(
    zone: HazardZone,
    feasibleSafeZones: List<SafeZone>
  ): NearestViableSafeZone? {
    val nearest = feasibleSafeZones.minByOrNull {
      GeoMath.distanceMeters(zone.center, it.point)
    } ?: return null
    return NearestViableSafeZone(
      name = nearest.name,
      distanceText = GeoMath.formatKm(GeoMath.distanceMeters(zone.center, nearest.point)),
      capacityText = "${nearest.availableCapacity}/${nearest.capacityTotal} spots free"
    )
  }

  private fun formatTime(millis: Long): String =
    SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(millis))
}
