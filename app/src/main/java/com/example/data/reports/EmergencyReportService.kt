package com.example.data.reports

import com.example.data.routing.GeoPoint

/**
 * What kind of emergency report is being relayed to authorities.
 */
enum class ReportKind(val tag: String, val label: String) {
  SOS_BROADCAST("SOS", "SOS distress broadcast"),
  SITUATION_REPORT("SIT", "Situation report")
}

/**
 * One citizen report en route to the disaster-response authorities.
 * Carries everything a ward dispatcher needs: who, where, device battery,
 * medical tags, and free-form (voice/typed) description + photo evidence.
 */
data class EmergencyReport(
  val kind: ReportKind,
  val reporterId: String,
  val reporterName: String,
  val message: String,
  val photoUri: String? = null,
  val location: GeoPoint,
  val batteryPercent: Int? = null,
  val isCharging: Boolean = false,
  val medicalTag: String = "",
  val timestampMillis: Long = System.currentTimeMillis()
)

/**
 * Dispatcher acknowledgment for a submitted [EmergencyReport].
 */
data class ReportReceipt(
  val reportId: String,
  val accepted: Boolean,
  val relayChannel: String,
  val etaMinutes: Int?,
  val note: String
)

/**
 * Abstraction for the emergency report pipeline. The UI/ViewModel only ever
 * talk to this interface, so the pilot NDRF simulation can later be swapped
 * for a real gateway (REST/SMS/satellite) without touching any caller.
 */
interface EmergencyReportService {
  suspend fun submit(report: EmergencyReport): ReportReceipt
}
