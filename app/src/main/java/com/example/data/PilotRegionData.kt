package com.example.data

import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.model.HazardSeverity
import com.example.data.model.HazardTrend
import com.example.data.model.HazardType
import com.example.data.model.HazardZone
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint

/**
 * ============================================================================
 * CENTRALIZED INDIA PILOT REGION DATA — single source of truth for ALL geography.
 * ============================================================================
 *
 * Pilot region: Idukki district, Kerala, India (Western Ghats highlands).
 *
 * Honesty rules:
 *  - Zone coordinates anchor on real, named Kerala town centers (per public
 *    references); radii, capacities, occupancies, resource flags and hazard
 *    areas are CONFIGURED/SIMULATED static pilot data.
 *  - Nothing here claims to be a live or verified government feed. Provenance
 *    is attached to every record so verified datasets (IMD, KSDMA, NRSC/ISRO,
 *    CWC, GSI, NDMA/SACHET) can replace each item transparently later.
 *  - India-only geography: every coordinate in this file lies in Kerala, India.
 */
object PilotRegionData {

  // ------------------------------------------------------------------ region
  val REGION_NAME = "Idukki District, Kerala, India"
  val REGION_COUNTRY = "India"

  /**
   * Controlled India-based fallback location — Painavu, the administrative
   * headquarters of Idukki district, Kerala (approx. 9.8478 N, 76.9422 E).
   * Used ONLY when real hardware GPS is unavailable. Never presented as live
   * GPS; UI labels it "(FALLBACK)".
   */
  val FALLBACK_USER_LOCATION = GeoPoint(9.84778, 76.94222)

  /** Default map center — the map opens directly on the Indian pilot region. */
  val DEFAULT_MAP_CENTER = GeoPoint(9.84778, 76.94222)
  const val DEFAULT_MAP_ZOOM = 13.5

  private val staticProvenance = DataProvenance(
    source = "Vippatti Sarana India pilot configuration (Idukki, Kerala)",
    status = DataProvenance.STATUS_STATIC,
    confidence = 0.6,
    isVerified = false,
    classification = DataClassification.SIMULATED
  )

  val provenance: DataProvenance get() = staticProvenance

  // ---------------------------------------------------------------- hazards
  /**
   * Static hazard picture for the pilot: Periyar valley flash-flood areas, a
   * Western Ghats slope-runoff/landslide watch, a grassland fire watch and a
   * heavy rainfall cell. All SIMULATED for the pilot — these are NOT official
   * inundation or landslide maps.
   */
  val hazardZones: List<HazardZone> = listOf(
    HazardZone(
      id = "hz-flood-periyar-cheruthoni",
      name = "Periyar Valley Flash Flood Corridor",
      type = HazardType.FLOOD,
      severity = HazardSeverity.EXTREME,
      center = GeoPoint(9.77361, 77.03528), // near Cheruthoni, Idukki
      radiusMeters = 3500.0,
      riskLevel = "RED",
      trend = HazardTrend.WORSENING,
      sourceStatus = "Simulated flood watch — Periyar reservoir discharge scenario",
      lastUpdatedMillis = 0L,
      provenance = staticProvenance
    ),
    HazardZone(
      id = "hz-flood-periyar-painavu",
      name = "Periyar Upper Valley Inundation Watch",
      type = HazardType.FLOOD,
      severity = HazardSeverity.HIGH,
      center = GeoPoint(9.80600, 76.97200),
      radiusMeters = 2400.0,
      riskLevel = "RED",
      trend = HazardTrend.WORSENING,
      sourceStatus = "Simulated inundation watch upstream of Painavu",
      lastUpdatedMillis = 0L,
      provenance = staticProvenance
    ),
    HazardZone(
      id = "hz-landslide-ghats",
      name = "Western Ghats Slope Runoff & Landslide Watch",
      type = HazardType.LANDSLIDE,
      severity = HazardSeverity.HIGH,
      center = GeoPoint(9.92500, 77.10500), // highland slopes above Kattappana
      radiusMeters = 5000.0,
      riskLevel = "ORANGE",
      trend = HazardTrend.STABLE,
      sourceStatus = "Simulated slope-stability watch — Western Ghats highlands",
      lastUpdatedMillis = 0L,
      provenance = staticProvenance
    ),
    HazardZone(
      id = "hz-fire-munnar",
      name = "Munnar Grassland Fire Watch",
      type = HazardType.FIRE,
      severity = HazardSeverity.MODERATE,
      center = GeoPoint(10.08917, 77.05972), // near Munnar
      radiusMeters = 4200.0,
      riskLevel = "YELLOW",
      trend = HazardTrend.STABLE,
      sourceStatus = "Simulated dry-grass fire watch near Munnar plantations",
      lastUpdatedMillis = 0L,
      provenance = staticProvenance
    ),
    HazardZone(
      id = "hz-rain-idukki-cell",
      name = "Idukki Heavy Rainfall Cell",
      type = HazardType.HEAVY_RAINFALL,
      severity = HazardSeverity.HIGH,
      center = GeoPoint(9.85000, 76.96000),
      radiusMeters = 8000.0,
      riskLevel = "ORANGE",
      trend = HazardTrend.WORSENING,
      sourceStatus = "Simulated rainfall cell over central Idukki (42 mm/h sustained)",
      lastUpdatedMillis = 0L,
      provenance = staticProvenance
    )
  )

  // -------------------------------------------------------------- safe zones
  /**
   * Pilot shelter network. Shelter locations anchor on real, named Kerala town
   * centers; capacity, occupancy, resources and status are SIMULATED values
   * chosen so different shelters visibly show different occupancy levels
   * (Available / Near Capacity / Full). A verified government shelter registry
   * can replace this list later without touching any consumer code.
   */
  val safeZones: List<SafeZone> = listOf(
    SafeZone(
      id = "sz-cheruthoni-hall",
      name = "Cheruthoni Community Relief Hall",
      lat = 9.77361,
      lon = 77.03528,
      locationNote = "Cheruthoni town center, Idukki",
      capacityTotal = 500,
      capacityCurrent = 320,
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = true,
      sanitationAvailable = true,
      medicalSupport = true,
      accessibility = "Road accessible • four-wheeler pickup point",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated pilot record — not yet verified by KSDMA",
      elevationNote = "Upland ridge above Periyar flood corridor",
      provenance = staticProvenance
    ),
    SafeZone(
      id = "sz-kattappana-relief",
      name = "Kattappana Municipal Relief Camp",
      lat = 9.75417,
      lon = 77.11583,
      locationNote = "Kattappana, Idukki",
      capacityTotal = 400,
      capacityCurrent = 280,
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = true,
      sanitationAvailable = true,
      medicalSupport = true,
      accessibility = "Highway accessible • ambulance bay",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated pilot record — not yet verified by KSDMA",
      elevationNote = "Western Ghats highland terrain",
      provenance = staticProvenance
    ),
    SafeZone(
      id = "sz-thodupuzha-shelter",
      name = "Thodupuzha Highway Authority Shelter",
      lat = 9.89998,
      lon = 76.71920,
      locationNote = "Thodupuzha, Idukki",
      capacityTotal = 300,
      capacityCurrent = 75,
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = true,
      sanitationAvailable = false,
      medicalSupport = false,
      accessibility = "Highway accessible • vehicle convoy staging",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated pilot record — not yet verified by KSDMA",
      elevationNote = "Lowland foothill (+40 m)",
      provenance = staticProvenance
    ),
    SafeZone(
      id = "sz-munnar-school",
      name = "Munnar Hill View High School",
      lat = 10.08917,
      lon = 77.05972,
      locationNote = "Munnar, Idukki",
      capacityTotal = 200,
      capacityCurrent = 196,
      waterAvailable = true,
      foodAvailable = false,
      electricityAvailable = true,
      sanitationAvailable = true,
      medicalSupport = false,
      accessibility = "Ghat road access — landslide watch on approach",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated pilot record — not yet verified by KSDMA",
      elevationNote = "High elevation (+1,532 m)",
      provenance = staticProvenance
    ),
    SafeZone(
      id = "sz-painavu-college",
      name = "Painavu Govt College Relief Center",
      lat = 9.84900,
      lon = 76.94400,
      locationNote = "Painavu, Idukki",
      capacityTotal = 350,
      capacityCurrent = 60,
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = false,
      sanitationAvailable = true,
      medicalSupport = true,
      accessibility = "District HQ road • wheelchair ramps",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated pilot record — not yet verified by KSDMA",
      elevationNote = "Above reservoir ridge",
      provenance = staticProvenance
    ),
    SafeZone(
      id = "sz-nedumkandam-hall",
      name = "Nedumkandam Panchayat Community Hall",
      lat = 9.83917,
      lon = 77.00472,
      locationNote = "Nedumkandam, Idukki",
      capacityTotal = 150,
      capacityCurrent = 150, // FULL (simulated) — shows the Full status
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = true,
      sanitationAvailable = true,
      medicalSupport = false,
      accessibility = "Hill road access • steep approach",
      womenChildrenSuitability = false,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated pilot record — not yet verified by KSDMA",
      elevationNote = "Cardamom hill plateau",
      provenance = staticProvenance
    )
  )
}
