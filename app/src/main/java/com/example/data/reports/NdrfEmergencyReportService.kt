package com.example.data.reports

import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pilot NDRF implementation of [EmergencyReportService].
 *
 * Simulates the NDRF-112 ward-dispatcher uplink (validation + round-trip
 * latency + acknowledgment), exactly like the rest of the pilot's simulated
 * intelligence (mock news desk, offline OSRM fallback). The
 * [EmergencyReportService] interface lets a real NDRF gateway replace this
 * class later without touching the ViewModel or any UI code.
 */
class NdrfEmergencyReportService : EmergencyReportService {

  override suspend fun submit(report: EmergencyReport): ReportReceipt {
    // Simulated dispatcher round-trip.
    delay(DISPATCHER_LATENCY_MS)

    val stamp = SimpleDateFormat("HHmmss", Locale.getDefault()).format(Date(report.timestampMillis))
    val reportId = "NDRF-${report.kind.tag}-$stamp"

    if (report.message.isBlank()) {
      return ReportReceipt(
        reportId = reportId,
        accepted = false,
        relayChannel = RELAY_CHANNEL,
        etaMinutes = null,
        note = "Rejected: report description cannot be empty"
      )
    }

    val etaMinutes = if (report.kind == ReportKind.SOS_BROADCAST) SOS_ACK_MINUTES else SITUATION_ACK_MINUTES
    val batteryNote = report.batteryPercent?.let { percent ->
      " • device battery $percent%" + if (report.isCharging) " (charging)" else ""
    } ?: ""

    return ReportReceipt(
      reportId = reportId,
      accepted = true,
      relayChannel = RELAY_CHANNEL,
      etaMinutes = etaMinutes,
      note = "${report.kind.label} $reportId accepted by NDRF ward dispatcher " +
        "via $RELAY_CHANNEL — response ETA ~$etaMinutes min$batteryNote"
    )
  }

  companion object {
    const val RELAY_CHANNEL = "NDRF 112 • Cellular + Satellite Mesh"
    private const val DISPATCHER_LATENCY_MS = 1_200L
    private const val SOS_ACK_MINUTES = 10
    private const val SITUATION_ACK_MINUTES = 25
  }
}
