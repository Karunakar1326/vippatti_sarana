package com.example.data.disaster

import com.example.data.model.HazardSeverity
import com.example.data.routing.GeoPoint

/**
 * ============================================================================
 * USER INCIDENT REPORTS — citizen observations layered onto the disaster map.
 * ============================================================================
 *
 * Reports are ALWAYS visually and semantically distinct from AUTHORITATIVE
 * source events: source = USER_REPORT, origin = REPORTED, verification
 * status = UNVERIFIED, and each report carries a TTL so stale reports expire
 * honestly. Nothing a user reports is ever presented as confirmed fact.
 */

/** Categories supported by the "Add a Report" flow. */
enum class IncidentCategory(val label: String) {
  ROAD_BLOCKED("Road blocked"),
  FLOODED_ROAD("Flooded road"),
  LANDSLIDE("Landslide"),
  ROCKFALL("Rockfall"),
  BRIDGE_DAMAGED("Bridge damaged"),
  FIRE("Fire"),
  ACCIDENT("Accident"),
  HEAVY_CONGESTION("Heavy congestion"),
  OTHER("Other")
}

/** Verification lifecycle of a citizen report. */
enum class ReportVerification(val label: String) {
  UNVERIFIED("Unverified user report"),
  EXPIRED("Expired")
}

/** Default lifetime of a citizen report before it auto-expires. */
const val INCIDENT_TTL_MILLIS: Long = 12L * 60L * 60L * 1000L

/** One citizen-submitted incident. */
data class IncidentReport(
  val id: String,
  val category: IncidentCategory,
  val description: String,
  val location: GeoPoint,
  val reportedAtMillis: Long,
  val severity: HazardSeverity,
  val reporterName: String
) {
  fun toDisasterEvent(nowMillis: Long = System.currentTimeMillis()): DisasterEvent {
    val expired = reportedAtMillis + INCIDENT_TTL_MILLIS <= nowMillis
    return DisasterEvent(
      id = id,
      source = DisasterSource.USER_REPORT,
      sourceEventId = id,
      disasterType = category.disasterType(),
      title = category.label,
      description = description.ifBlank { "User-reported ${category.label.lowercase()}." },
      geometry = EventGeometry.Point(location.lat, location.lon),
      latitude = location.lat,
      longitude = location.lon,
      severity = severity,
      observedAtMillis = reportedAtMillis,
      updatedAtMillis = reportedAtMillis,
      expiresAtMillis = reportedAtMillis + INCIDENT_TTL_MILLIS,
      status = if (expired) EventStatus.EXPIRED else EventStatus.ACTIVE,
      origin = EventOrigin.REPORTED,
      details = EventDetails.UserIncident(
        categoryLabel = category.label,
        reporterNote = description
      )
    )
  }
}

fun IncidentCategory.disasterType(): DisasterType = when (this) {
  IncidentCategory.FLOODED_ROAD -> DisasterType.FLOOD
  IncidentCategory.LANDSLIDE, IncidentCategory.ROCKFALL -> DisasterType.LANDSLIDE
  IncidentCategory.FIRE -> DisasterType.WILDFIRE
  IncidentCategory.ACCIDENT, IncidentCategory.HEAVY_CONGESTION,
  IncidentCategory.ROAD_BLOCKED, IncidentCategory.BRIDGE_DAMAGED -> DisasterType.OTHER
  IncidentCategory.OTHER -> DisasterType.OTHER
}

/** Recommended severity defaults per category (user-editable). */
fun IncidentCategory.defaultSeverity(): HazardSeverity = when (this) {
  IncidentCategory.LANDSLIDE, IncidentCategory.ROCKFALL, IncidentCategory.FIRE,
  IncidentCategory.BRIDGE_DAMAGED -> HazardSeverity.HIGH
  IncidentCategory.FLOODED_ROAD, IncidentCategory.ACCIDENT -> HazardSeverity.MODERATE
  else -> HazardSeverity.LOW
}
