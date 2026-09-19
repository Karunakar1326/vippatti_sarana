package com.example.data.disaster

import com.example.data.model.DataClassification
import com.example.data.model.DataProvenance
import com.example.data.model.GeoMath
import com.example.data.model.HazardSeverity
import com.example.data.model.HazardTrend
import com.example.data.model.HazardType
import com.example.data.model.HazardZone
import com.example.data.model.SafeZone
import com.example.data.routing.GeoPoint

/**
 * ============================================================================
 * CENTRALIZED INDIA MOCK DISASTER NETWORK — single source of truth for ALL
 * mock geography shown on the radar map.
 * ============================================================================
 *
 * 14 danger zones across 13 states / 14 different districts (all coordinates
 * inside India — see [IndiaGeo]). Zones are deliberately SMALL (1.5–2.5 km
 * radius) and WIDELY SEPARATED (closest danger pair ≈169 km apart, far beyond
 * any zone radius), so no two zones ever share the same map tile and no
 * danger zone sits upon another danger zone.
 *
 * Every danger zone has exactly one paired safe zone in the SAME district,
 * 4–9 km away — nearby, but always OUTSIDE the danger radius with ≥3.8 km of
 * margin, so each paired shelter is genuinely safe and the evaluator + OSRM
 * routing can always determine the SHORTEST distance to a feasible safe zone.
 *
 * Visibility rule: mock zones render ONLY while the "DATA: CACHED" mock
 * toggle is ON. When it is OFF the map is EMPTY (see VippattiViewModel).
 *
 * Honesty rules:
 *  - Coordinates anchor on real, named Indian town centers (public
 *    references); radii, capacities, occupancies and resource flags are
 *    CONFIGURED/SIMULATED mock values.
 *  - Nothing here claims to be a live or verified government feed.
 */
object PilotRegionData {

  // ------------------------------------------------------------------ region
  /**
   * Controlled India-based fallback location — the geographic centre of India
   * (approx. 20.5937 N, 78.9629 E). Used ONLY when real hardware GPS is
   * unavailable so the whole mock network is visible at overview zoom.
   * Never presented as live GPS; UI labels it "(FALLBACK)".
   */
  val FALLBACK_USER_LOCATION = GeoPoint(IndiaGeo.CENTER_LAT, IndiaGeo.CENTER_LON)

  /** Default map center — India overview so every mock state is visible. */
  val DEFAULT_MAP_CENTER = GeoPoint(IndiaGeo.CENTER_LAT, IndiaGeo.CENTER_LON)
  const val DEFAULT_MAP_ZOOM = 5.0

  private val fieldProvenance = DataProvenance(
    source = "Vippatti Sarana India simulated network (multi-state)",
    status = DataProvenance.STATUS_FIELD,
    confidence = 0.6,
    isVerified = false,
    classification = DataClassification.SIMULATED
  )

  // ---------------------------------------------------------------- hazards
  /**
   * 14 small, widely-separated danger zones — one per district across 13
   * states, mixed disaster types (flood, heavy rainfall, landslide,
   * earthquake, cyclone, fire, severe weather alert, high-altitude hazard).
   * Radii are intentionally compact (1.5–2.5 km) so each zone stays on its
   * own map tile and no zone ever contains another. All centres lie inside
   * [IndiaGeo].
   */
  val hazardZones: List<HazardZone> = listOf(
    HazardZone(
      id = "hz-flood-assam-dibrugarh",
      name = "Brahmaputra Flood Pocket — Dibrugarh, Assam",
      type = HazardType.FLOOD,
      severity = HazardSeverity.EXTREME,
      center = GeoPoint(27.4728, 94.9120),
      radiusMeters = 2000.0,
      riskLevel = "RED",
      trend = HazardTrend.WORSENING,
      sourceStatus = "Simulated flood watch — Brahmaputra monsoon scenario (Assam)",
      lastUpdatedMillis = 0L,
      provenance = fieldProvenance
    ),
    HazardZone(
      id = "hz-rain-kerala-idukki",
      name = "Idukki Heavy Rainfall Cell — Kerala",
      type = HazardType.HEAVY_RAINFALL,
      severity = HazardSeverity.HIGH,
      center = GeoPoint(9.85000, 76.96000),
      radiusMeters = 2500.0,
      riskLevel = "ORANGE",
      trend = HazardTrend.WORSENING,
      sourceStatus = "Simulated rainfall cell over central Idukki (Kerala)",
      lastUpdatedMillis = 0L,
      provenance = fieldProvenance
    ),
    HazardZone(
      id = "hz-landslide-himachal-shimla",
      name = "Shimla Slope Landslide Watch — Himachal Pradesh",
      type = HazardType.LANDSLIDE,
      severity = HazardSeverity.HIGH,
      center = GeoPoint(31.1048, 77.1734),
      radiusMeters = 2000.0,
      riskLevel = "ORANGE",
      trend = HazardTrend.STABLE,
      sourceStatus = "Simulated slope-stability watch — Himalayan foothills (Himachal Pradesh)",
      lastUpdatedMillis = 0L,
      provenance = fieldProvenance
    ),
    HazardZone(
      id = "hz-earthquake-gujarat-bhuj",
      name = "Bhuj Earthquake Alert Pocket — Kutch, Gujarat",
      type = HazardType.EARTHQUAKE,
      severity = HazardSeverity.EXTREME,
      center = GeoPoint(23.2420, 69.6669),
      radiusMeters = 2500.0,
      riskLevel = "RED",
      trend = HazardTrend.STABLE,
      sourceStatus = "Simulated seismic alert pocket — Kutch region (Gujarat)",
      lastUpdatedMillis = 0L,
      provenance = fieldProvenance
    ),
    HazardZone(
      id = "hz-cyclone-odisha-puri",
      name = "Puri Cyclone Landfall Watch — Odisha",
      type = HazardType.CYCLONE,
      severity = HazardSeverity.HIGH,
      center = GeoPoint(19.8135, 85.8312),
      radiusMeters = 2500.0,
      riskLevel = "ORANGE",
      trend = HazardTrend.WORSENING,
      sourceStatus = "Simulated cyclone landfall watch — Bay of Bengal coast (Odisha)",
      lastUpdatedMillis = 0L,
      provenance = fieldProvenance
    ),
    HazardZone(
      id = "hz-fire-maharashtra-thane",
      name = "Thane Forest Fire Pocket — Maharashtra",
      type = HazardType.FIRE,
      severity = HazardSeverity.MODERATE,
      center = GeoPoint(19.2183, 72.9781),
      radiusMeters = 1500.0,
      riskLevel = "YELLOW",
      trend = HazardTrend.STABLE,
      sourceStatus = "Simulated dry-forest fire pocket (Maharashtra)",
      lastUpdatedMillis = 0L,
      provenance = fieldProvenance
    ),
    HazardZone(
      id = "hz-flood-uttarakhand-kedarnath",
      name = "Mandakini Flash Flood Pocket — Rudraprayag, Uttarakhand",
      type = HazardType.FLOOD,
      severity = HazardSeverity.HIGH,
      center = GeoPoint(30.7346, 79.0669),
      radiusMeters = 2000.0,
      riskLevel = "ORANGE",
      trend = HazardTrend.WORSENING,
      sourceStatus = "Simulated flash-flood pocket — Mandakini valley (Uttarakhand)",
      lastUpdatedMillis = 0L,
      provenance = fieldProvenance
    ),
    HazardZone(
      id = "hz-flood-tamilnadu-chennai",
      name = "Adyar Flood Pocket — Chennai, Tamil Nadu",
      type = HazardType.FLOOD,
      severity = HazardSeverity.HIGH,
      center = GeoPoint(13.0827, 80.2707),
      radiusMeters = 2000.0,
      riskLevel = "ORANGE",
      trend = HazardTrend.WORSENING,
      sourceStatus = "Simulated urban flood pocket — Adyar basin (Tamil Nadu)",
      lastUpdatedMillis = 0L,
      provenance = fieldProvenance
    ),
    HazardZone(
      id = "hz-earthquake-rajasthan-jaipur",
      name = "Jaipur Tremor Alert Pocket — Rajasthan",
      type = HazardType.EARTHQUAKE,
      severity = HazardSeverity.MODERATE,
      center = GeoPoint(26.9124, 75.7873),
      radiusMeters = 1800.0,
      riskLevel = "YELLOW",
      trend = HazardTrend.STABLE,
      sourceStatus = "Simulated tremor alert pocket (Rajasthan)",
      lastUpdatedMillis = 0L,
      provenance = fieldProvenance
    ),
    HazardZone(
      id = "hz-landslide-bengal-darjeeling",
      name = "Darjeeling Slope Slide Watch — West Bengal",
      type = HazardType.LANDSLIDE,
      severity = HazardSeverity.HIGH,
      center = GeoPoint(27.0410, 88.2663),
      radiusMeters = 2000.0,
      riskLevel = "ORANGE",
      trend = HazardTrend.STABLE,
      sourceStatus = "Simulated slope-slide watch — Darjeeling Himalayas (West Bengal)",
      lastUpdatedMillis = 0L,
      provenance = fieldProvenance
    ),
    HazardZone(
      id = "hz-rain-karnataka-kodagu",
      name = "Kodagu Heavy Rainfall Cell — Karnataka",
      type = HazardType.HEAVY_RAINFALL,
      severity = HazardSeverity.HIGH,
      center = GeoPoint(12.4244, 75.7382),
      radiusMeters = 2000.0,
      riskLevel = "ORANGE",
      trend = HazardTrend.WORSENING,
      sourceStatus = "Simulated rainfall cell — Western Ghats (Karnataka)",
      lastUpdatedMillis = 0L,
      provenance = fieldProvenance
    ),
    HazardZone(
      id = "hz-flood-bihar-patna",
      name = "Ganga Flood Pocket — Patna, Bihar",
      type = HazardType.FLOOD,
      severity = HazardSeverity.HIGH,
      center = GeoPoint(25.5941, 85.1376),
      radiusMeters = 2200.0,
      riskLevel = "ORANGE",
      trend = HazardTrend.WORSENING,
      sourceStatus = "Simulated riverine flood pocket — Ganga bank (Bihar)",
      lastUpdatedMillis = 0L,
      provenance = fieldProvenance
    ),
    HazardZone(
      id = "hz-alert-rajasthan-jodhpur",
      name = "Severe Dust-Storm Alert — Jodhpur, Rajasthan",
      type = HazardType.WEATHER_ALERT,
      severity = HazardSeverity.MODERATE,
      center = GeoPoint(26.2389, 73.0243),
      radiusMeters = 2000.0,
      riskLevel = "YELLOW",
      trend = HazardTrend.STABLE,
      sourceStatus = "Simulated severe weather alert — dust-storm cell (Rajasthan)",
      lastUpdatedMillis = 0L,
      provenance = fieldProvenance
    ),
    HazardZone(
      id = "hz-other-uttarakhand-pithoragarh",
      name = "High-Altitude Avalanche Watch — Pithoragarh, Uttarakhand",
      type = HazardType.OTHER,
      severity = HazardSeverity.HIGH,
      center = GeoPoint(29.5833, 80.2183),
      radiusMeters = 2000.0,
      riskLevel = "ORANGE",
      trend = HazardTrend.STABLE,
      sourceStatus = "Simulated high-altitude avalanche watch (Uttarakhand)",
      lastUpdatedMillis = 0L,
      provenance = fieldProvenance
    )
  )

  // -------------------------------------------------------------- safe zones
  /**
   * 14 safe zones — exactly one per danger zone, in the SAME district but on
   * its own spot 4–9 km away, always OUTSIDE the danger radius with ≥3.8 km
   * of margin, so every paired shelter is genuinely safe. Deliberate
   * capacity exceptions exercise the rejection paths without placing any
   * shelter inside danger:
   *  - sz-odisha-puri-shelter is FULL (rejected on capacity).
   *  - sz-maharashtra-thane-school is NEAR CAPACITY.
   * Capacities are planning placeholders sized to town class (metro shelters
   * hold more than village halls) until a live SDMA / census-based shelter
   * registry replaces them.
   * All coordinates lie inside [IndiaGeo] (India only).
   */
  val safeZones: List<SafeZone> = listOf(
    SafeZone(
      id = "sz-assam-dibrugarh-hall",
      name = "Dibrugarh University Relief Hall",
      lat = 27.5300,
      lon = 94.9700,
      locationNote = "Dibrugarh, Assam — ~8.6 km from flood pocket",
      capacityTotal = 500,
      capacityCurrent = 180,
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = true,
      sanitationAvailable = true,
      medicalSupport = true,
      accessibility = "Highway accessible • ambulance bay",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated record — not yet verified by ASDMA",
      elevationNote = "Above Brahmaputra floodplain",
      provenance = fieldProvenance
    ),
    SafeZone(
      id = "sz-kerala-idukki-bypass",
      name = "Idukki Bypass Relief Shelter",
      lat = 9.9100,
      lon = 77.0000,
      locationNote = "Kizhakkethala, Idukki, Kerala — ~8.0 km from rainfall cell",
      capacityTotal = 300,
      capacityCurrent = 75,
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = true,
      sanitationAvailable = true,
      medicalSupport = false,
      accessibility = "Highway accessible • vehicle convoy staging",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated record — not yet verified by KSDMA",
      elevationNote = "Ridge shoulder above valley cell",
      provenance = fieldProvenance
    ),
    SafeZone(
      id = "sz-himachal-shimla-ridge",
      name = "Shimla Ridge Relief Centre",
      lat = 31.1500,
      lon = 77.2100,
      locationNote = "Shimla, Himachal Pradesh — ~6.1 km from slide watch",
      capacityTotal = 350,
      capacityCurrent = 90,
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = true,
      sanitationAvailable = true,
      medicalSupport = true,
      accessibility = "District HQ road • wheelchair ramps",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated record — not yet verified by HPSDMA",
      elevationNote = "Ridge line above slide slopes",
      provenance = fieldProvenance
    ),
    SafeZone(
      id = "sz-gujarat-bhuj-relief",
      name = "Bhuj Civic Relief Camp",
      lat = 23.2800,
      lon = 69.7200,
      locationNote = "Bhuj, Kutch, Gujarat — ~6.9 km from quake pocket",
      capacityTotal = 600,
      capacityCurrent = 210,
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = true,
      sanitationAvailable = true,
      medicalSupport = true,
      accessibility = "Highway accessible • ambulance bay",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated record — not yet verified by GSDMA",
      elevationNote = "Stable ground outside alert radius",
      provenance = fieldProvenance
    ),
    SafeZone(
      id = "sz-odisha-puri-shelter",
      name = "Puri Cyclone Shelter",
      lat = 19.8500,
      lon = 85.8800,
      locationNote = "Puri, Odisha — ~6.5 km from landfall watch",
      capacityTotal = 400,
      capacityCurrent = 400, // FULL (mock) — shows the Full status
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = true,
      sanitationAvailable = true,
      medicalSupport = true,
      accessibility = "Coastal highway • wheelchair ramps",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated record — FULL, pick another zone",
      elevationNote = "Raised cyclone shelter (+12 m)",
      provenance = fieldProvenance
    ),
    SafeZone(
      id = "sz-maharashtra-thane-school",
      name = "Thane Hill View School Shelter",
      lat = 19.2600,
      lon = 73.0200,
      locationNote = "Thane, Maharashtra — ~6.4 km from fire pocket",
      capacityTotal = 200,
      capacityCurrent = 196, // NEAR CAPACITY (mock)
      waterAvailable = true,
      foodAvailable = false,
      electricityAvailable = true,
      sanitationAvailable = true,
      medicalSupport = false,
      accessibility = "City road access",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated record — not yet verified by MSDMA",
      elevationNote = "Urban high ground",
      provenance = fieldProvenance
    ),
    SafeZone(
      id = "sz-uttarakhand-guptkashi-hall",
      name = "Guptkashi Community Hall",
      lat = 30.7800,
      lon = 79.1000,
      locationNote = "Guptkashi, Rudraprayag, Uttarakhand — ~6.0 km from flood pocket",
      capacityTotal = 250,
      capacityCurrent = 60,
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = false,
      sanitationAvailable = true,
      medicalSupport = true,
      accessibility = "Hill road • steep approach",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated record — not yet verified by USDMA",
      elevationNote = "Valley shoulder above flood line",
      provenance = fieldProvenance
    ),
    SafeZone(
      id = "sz-tamilnadu-sholinganallur-hall",
      name = "Sholinganallur Relief Hall",
      lat = 13.0300,
      lon = 80.2700,
      locationNote = "Sholinganallur, Tamil Nadu — ~5.9 km from flood pocket",
      capacityTotal = 800,
      capacityCurrent = 260,
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = true,
      sanitationAvailable = true,
      medicalSupport = true,
      accessibility = "City highway • wheelchair ramps",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated record — not yet verified by TNSDMA",
      elevationNote = "Raised urban ground",
      provenance = fieldProvenance
    ),
    SafeZone(
      id = "sz-rajasthan-sanganer-hall",
      name = "Sanganer Community Shelter",
      lat = 26.8600,
      lon = 75.8000,
      locationNote = "Sanganer, Jaipur, Rajasthan — ~6.0 km from tremor pocket",
      capacityTotal = 300,
      capacityCurrent = 80,
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = true,
      sanitationAvailable = true,
      medicalSupport = false,
      accessibility = "City road • ambulance bay",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated record — not yet verified by RSDMA",
      elevationNote = "Stable urban ground",
      provenance = fieldProvenance
    ),
    SafeZone(
      id = "sz-bengal-siliguri-shelter",
      name = "Siliguri Bypass Shelter",
      lat = 26.9900,
      lon = 88.2900,
      locationNote = "Siliguri, West Bengal — ~6.1 km from slide watch",
      capacityTotal = 350,
      capacityCurrent = 110,
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = true,
      sanitationAvailable = true,
      medicalSupport = true,
      accessibility = "Highway accessible • wheelchair ramps",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated record — not yet verified by WBSDMA",
      elevationNote = "Plains foot below slide slopes",
      provenance = fieldProvenance
    ),
    SafeZone(
      id = "sz-karnataka-kushalnagar-hall",
      name = "Kushalnagar Relief Hall",
      lat = 12.4700,
      lon = 75.8000,
      locationNote = "Kushalnagar, Kodagu, Karnataka — ~8.4 km from rainfall cell",
      capacityTotal = 280,
      capacityCurrent = 70,
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = true,
      sanitationAvailable = true,
      medicalSupport = true,
      accessibility = "District road • ambulance bay",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated record — not yet verified by KSDMA",
      elevationNote = "Plateau above valley cell",
      provenance = fieldProvenance
    ),
    SafeZone(
      id = "sz-bihar-danapur-shelter",
      name = "Danapur Relief Shelter",
      lat = 25.6200,
      lon = 85.2100,
      locationNote = "Danapur, Patna, Bihar — ~7.8 km from flood pocket",
      capacityTotal = 400,
      capacityCurrent = 150,
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = true,
      sanitationAvailable = false,
      medicalSupport = true,
      accessibility = "Highway accessible • vehicle convoy staging",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated record — not yet verified by BSDMA",
      elevationNote = "Embankment high ground",
      provenance = fieldProvenance
    ),
    SafeZone(
      id = "sz-rajasthan-jodhpur-hall",
      name = "Jodhpur Storm Shelter Hall",
      lat = 26.1900,
      lon = 73.0700,
      locationNote = "Jodhpur, Rajasthan — ~7.1 km from storm alert cell",
      capacityTotal = 350,
      capacityCurrent = 95,
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = true,
      sanitationAvailable = true,
      medicalSupport = false,
      accessibility = "City road • bus bay",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated record — not yet verified by RSDMA",
      elevationNote = "Urban high ground",
      provenance = fieldProvenance
    ),
    SafeZone(
      id = "sz-uttarakhand-pithoragarh-hall",
      name = "Pithoragarh Valley Shelter",
      lat = 29.6200,
      lon = 80.2700,
      locationNote = "Pithoragarh, Uttarakhand — ~6.5 km from avalanche watch",
      capacityTotal = 180,
      capacityCurrent = 45,
      waterAvailable = true,
      foodAvailable = true,
      electricityAvailable = true,
      sanitationAvailable = true,
      medicalSupport = true,
      accessibility = "Hill road • steep approach",
      womenChildrenSuitability = true,
      operatingStatus = "OPEN",
      verificationStatus = "Simulated record — not yet verified by USDMA",
      elevationNote = "Valley floor below slide paths",
      provenance = fieldProvenance
    )
  )

  // ------------------------------------------------- shortest-distance logic
  /**
   * SHORTEST straight-line distance (meters) from [origin] to any of [zones]
   * (default: the whole mock network). Null when the list is empty (e.g. mock
   * hidden) so the UI can render an honest empty state instead of "0 m".
   */
  fun shortestDistanceMeters(origin: GeoPoint, zones: List<SafeZone> = safeZones): Double? =
    zones.minOfOrNull { GeoMath.distanceMeters(origin, it.point) }

  /** The single nearest safe zone to [origin], or null when none exist. */
  fun nearestSafeZone(origin: GeoPoint, zones: List<SafeZone> = safeZones): SafeZone? =
    zones.minByOrNull { GeoMath.distanceMeters(origin, it.point) }

  /**
   * India-only guard: true only when EVERY mock hazard centre and safe zone
   * lies inside [IndiaGeo].
   */
  fun allCoordinatesInIndia(): Boolean =
    hazardZones.all { IndiaGeo.contains(it.center) } &&
      safeZones.all { IndiaGeo.contains(it.point) }

  /**
   * Separation guard: true only when every danger centre is at least
   * [minSeparationMeters] from every other danger centre AND every safe zone
   * lies OUTSIDE its paired danger radius (genuinely safe). The pairing is
   * positional — safeZones[i] pairs with hazardZones[i].
   */
  fun allZonesSeparatedAndSafe(minSeparationMeters: Double = 50_000.0): Boolean {
    for (i in hazardZones.indices) {
      for (j in i + 1 until hazardZones.size) {
        if (GeoMath.distanceMeters(hazardZones[i].center, hazardZones[j].center) < minSeparationMeters) {
          return false
        }
      }
    }
    if (safeZones.size != hazardZones.size) return false
    return hazardZones.indices.all { i ->
      GeoMath.distanceMeters(hazardZones[i].center, safeZones[i].point) > hazardZones[i].radiusMeters
    }
  }
}
