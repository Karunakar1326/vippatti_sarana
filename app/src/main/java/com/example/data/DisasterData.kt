package com.example.data

data class BreakingAlert(
  val title: String,
  val level: String,
  val zone: String,
  val description: String,
  val timeAgo: String,
  val agency: String,
  val verifiedBadge: String,
  val imageUrl: String
)

data class FeedDispatch(
  val id: String,
  val agency: String,
  val issuedTime: String,
  val tag: String,
  val tagType: DispatchTagType,
  val title: String,
  val description: String,
  val location: String,
  val actionLabel: String,
  val iconType: DispatchIconType
)

enum class DispatchTagType {
  HIGH_ALERT,
  SHELTER_READY,
  ROAD_CLOSED,
  CAPACITY_INFO
}

enum class DispatchIconType {
  RAIN,
  SHELTER,
  FLOOD,
  LOGISTICS
}

data class EmergencyContact(
  val id: String,
  val name: String,
  val role: String,
  val phone: String,
  val locationNote: String,
  val initials: String,
  val colorHex: Long
)

data class GoBagItem(
  val id: String,
  val name: String,
  val detail: String,
  val isChecked: Boolean = false,
  val iconName: String
)

// SIMULATION default weather readings for the India pilot (Idukki district, Kerala).
data class WeatherMetrics(
  val currentTemp: String = "24°C",
  val rainfallIntensity: String = "42mm/h",
  val windGust: String = "65km/h",
  val trend3h: String = "Worsening",
  val surgeForecast: String = "Peak reservoir discharge expected in 45 mins"
)

/**
 * SIMULATION dataset for the India pilot — Idukki district, Kerala.
 * All alerts, dispatches, evacuation centers and contacts below are static MOCK data
 * for the India pilot and do not represent real operational feeds.
 */
object MockDisasterRepository {

  val breakingAlert = BreakingAlert(
    title = "Heavy Rainfall / Flash Flood Warning Level Red",
    level = "Level Red",
    zone = "Idukki District Highlands & Periyar Valley Zones",
    description = "Kerala State Disaster Management Authority issues emergency evacuation orders for low-lying settlements within 2km of the Periyar river. Reservoir discharge and flash flood peaks expected between 14:00 and 18:00.",
    timeAgo = "15m ago",
    agency = "CRITICAL URGENT • KSDMA",
    verifiedBadge = "Verified KSDMA Dispatch",
    imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDJ8gt2TC26Btj6mgoPr7UkVwnVNY1_8QudZB20oynhUxEpMip7T-jRW_jdncpPf2W_Uxv7rbR2pPrSsQSsERTuy1yN0Vsbr1WXjVtxnWgXRImneCvYxfENTNaKmBoMIzLclWSdAbXUafZQmd_v72teBnrFcrd3o8VYbocfddZ1DKrj-BvbFdMmcFhUstFL62sThynAGGXmXbSvbaHTtvwOG8YWa0XErixCJV91I3BW-t-SEqLTGcouKA"
  )

  val mapSatelliteImageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuC6oGIEPHApoL_XjanXVtyvBu-0ZXubn1pb7XErXRwvf2Ukw8DzaT8fCxiftbtEiZwB2XYk1Ll91P5uNNClrOwQdzTaCmgw6BpxNnRGgty0dCG9epmKlLqYTpkZXWSC42ZVGhiqZWBR8DmIvVsw-Mcph684_twJn1azJhS534Upn0TqjyMvAKZuM0nOt7qcmleeFl_MgzDMZJ-iuEOPl5tQDwZqA2S3okRiQgxSuJ-cnT4nF5MRkI4POA"

  val feedDispatches = listOf(
    FeedDispatch(
      id = "disp-1",
      agency = "METEOROLOGICAL DEPT",
      issuedTime = "Issued 28 mins ago",
      tag = "High Alert",
      tagType = DispatchTagType.HIGH_ALERT,
      title = "Heavy rainfall warning extended for highland and valley sectors for 24 hours",
      description = "Precipitation exceeding 220mm forecast with flash flood propensity across Periyar and Cheruthoni river catchment territories.",
      location = "Idukki Highlands A & B, Periyar Valley",
      actionLabel = "Read Advisory",
      iconType = DispatchIconType.RAIN
    ),
    FeedDispatch(
      id = "disp-2",
      agency = "RED CROSS RELIEF OPS",
      issuedTime = "Issued 42 mins ago",
      tag = "Shelter Ready",
      tagType = DispatchTagType.SHELTER_READY,
      title = "Shelter Camp #4 at Cheruthoni opened with medical triage and clean water supply",
      description = "Accepting evacuees immediately. Equipped with 450 bed units, pediatrician staff, sanitary kits, and solar charging docks.",
      location = "Cheruthoni Community Relief Hall, Ward 4",
      actionLabel = "Get Directions",
      iconType = DispatchIconType.SHELTER
    ),
    FeedDispatch(
      id = "disp-3",
      agency = "HIGHWAY PATROL & IRRIGATION",
      issuedTime = "Issued 1 hour ago",
      tag = "Road Closed",
      tagType = DispatchTagType.ROAD_CLOSED,
      title = "Periyar water levels crossed Danger Mark at Cheruthoni Bridge — Hill Highway Closed",
      description = "Water gauge reads +1.4m over emergency threshold. Highway barrier deployed. Commuters diverted via Kattappana Bypass.",
      location = "Cheruthoni Bridge / Hill Highway Crossing",
      actionLabel = "Detour Map",
      iconType = DispatchIconType.FLOOD
    ),
    FeedDispatch(
      id = "disp-4",
      agency = "CIVIL DEFENSE LOGISTICS",
      issuedTime = "Issued 2 hours ago",
      tag = "Capacity Info",
      tagType = DispatchTagType.CAPACITY_INFO,
      title = "Carrying Capacity status: 6 new safe relocation hubs operational in Kattappana block",
      description = "Total secondary surge capacity expanded to 2,800 persons with emergency sanitation blocks, food rations, and backup satellite comms.",
      location = "Kattappana Block Civic Complexes",
      actionLabel = "Check Availability",
      iconType = DispatchIconType.LOGISTICS
    )
  )

  val evacuationCentersNote: String
    get() = "Superseded by PilotRegionData.safeZones (structured shelter model)"

  val defaultGoBagItems = listOf(
    GoBagItem("item-1", "Go-Bag", "Waterproof 15L", true, "backpack"),
    GoBagItem("item-2", "ID & Papers", "Sealed zip pouch", true, "description"),
    GoBagItem("item-3", "Prescription Meds", "7-day supply min", false, "medication"),
    GoBagItem("item-4", "Clean Water", "2L per person", true, "water_bottle"),
    GoBagItem("item-5", "LED Flashlight & Batteries", "Water-resistant torch", false, "flashlight"),
    GoBagItem("item-6", "Power Bank & Cable", "10,000mAh charged", true, "battery_charging_full"),
    GoBagItem("item-7", "Emergency Whistle", "High decibel signal", false, "notifications_active"),
    GoBagItem("item-8", "Non-perishable Rations", "High protein bars (3 days)", true, "restaurant")
  )

  val emergencyContacts = listOf(
    EmergencyContact(
      id = "c-1",
      name = "Sunitha K.",
      role = "Spouse",
      phone = "+91 98450 11234",
      locationNote = "Within 3km",
      initials = "SK",
      colorHex = 0xFF4338CA
    ),
    EmergencyContact(
      id = "c-2",
      name = "Raghav V.",
      role = "Brother",
      phone = "+91 94480 88901",
      locationNote = "Ward 14",
      initials = "RV",
      colorHex = 0xFF0D9488
    ),
    EmergencyContact(
      id = "c-3",
      name = "Ramesh B.",
      role = "Block Lead / Neighbor",
      phone = "+91 97312 45450",
      locationNote = "Ward 12",
      initials = "NL",
      colorHex = 0xFFD97706
    )
  )
}
