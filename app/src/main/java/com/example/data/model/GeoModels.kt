package com.example.data.model

import com.example.data.routing.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

/**
 * Intelligent map layer registry. Only layers whose data actually exists in the
 * application are rendered; planned layers document which Indian authority
 * dataset will feed them once integration is connected. No live dataset is
 * claimed until it is really wired in.
 */
enum class MapLayerId(
  val label: String,
  val isAvailableNow: Boolean,
  val plannedIntegration: String
) {
  HAZARD_ZONES("Hazard Zones", true, "IMD / KSDMA / NRSC hazard feeds"),
  SAFE_ZONES("Safe Zones", true, "KSDMA shelter registry"),
  SHELTER_CAPACITY("Shelter Capacity", true, "Dynamic shelter occupancy updates"),
  ROADS("Roads", true, "OpenStreetMap base data"),
  EVACUATION_CORRIDOR("Evacuation Corridor", true, "OSRM road routing"),
  HOSPITALS("Hospitals", false, "State health directory"),
  SCHOOLS("Schools", false, "UDISE+ education dataset"),
  EMERGENCY_SERVICES("Emergency Services", false, "NDMA / district control rooms"),
  RIVERS("Rivers", false, "CWC river monitoring"),
  BRIDGES("Bridges", false, "PWD bridge inventory"),
  TERRAIN_SLOPE("Terrain & Slope", false, "GSI / NRSC terrain products"),
  HISTORICAL_DISASTER_AREAS("Historical Disaster Areas", false, "GSI landslide atlas / CWC flood archive"),
  LIVE_INCIDENTS("Live Incidents", false, "NDMA SACHET / KSDMA live alerts")
}
