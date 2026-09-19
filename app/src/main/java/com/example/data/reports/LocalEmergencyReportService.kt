package com.example.data.reports

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ============================================================================
 * LOCAL EMERGENCY REPORT RECORDER
 * ============================================================================
 *
 * HONESTY CONTRACT — this class replaces the previous `NdrfEmergencyReportService`,
 * which slept for 1.2 s and then returned an invented receipt claiming the report
 * had been "accepted by NDRF ward dispatcher via NDRF 112 • Cellular + Satellite
 * Mesh — response ETA ~10 min". No such transmission exists in this build.
 *
 * What this service actually does:
 *   - validates the report,
 *   - mints a stable LOCAL reference id,
 *   - reports exactly what happened, with NO authority, delivery, ETA or
 *     acknowledgment claim of any kind.
 *
 * The [EmergencyReportService] abstraction is unchanged, so a real backend
 * relay (SMS gateway / REST gateway / NDRF-SACHET integration) can replace this
 * implementation later without touching the ViewModel or any UI code. When that
 * happens, set [IS_BACKEND_RELAY_AVAILABLE] to true and the UI copy switches
 * from "local record" to "transmitted" only then.
 */
class LocalEmergencyReportService : EmergencyReportService {

  override suspend fun submit(report: EmergencyReport): ReportReceipt {
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(report.timestampMillis))
    val reportId = "LOCAL-${report.kind.tag}-$stamp"

    if (report.message.isBlank()) {
      return ReportReceipt(
        reportId = reportId,
        accepted = false,
        relayChannel = RELAY_CHANNEL,
        // Never invent a response time.
        etaMinutes = null,
        note = "Not saved: the description is empty. Nothing was transmitted or recorded."
      )
    }

    val batteryNote = report.batteryPercent?.let { percent ->
      " Device battery was at $percent%" + if (report.isCharging) " (charging)." else "."
    } ?: ""

    return ReportReceipt(
      reportId = reportId,
      accepted = true,
      relayChannel = RELAY_CHANNEL,
      // A local record cannot have an arrival/response estimate.
      etaMinutes = null,
      note = "${report.kind.label} saved on THIS DEVICE as $reportId.$batteryNote " +
        "No authority has received it — this build has no relief-network backend. " +
        NO_RELAY_ACTION
    )
  }

  companion object {
    /** Relay channel shown in the UI — deliberately explicit about no delivery. */
    const val RELAY_CHANNEL = "LOCAL DEVICE RECORD — NOT TRANSMITTED"

    /** The single actionable instruction when no backend relay exists. */
    const val NO_RELAY_ACTION =
      "To reach emergency services, dial 112 (police/fire/health) or 108 (ambulance)."

    /**
     * False while this build cannot transmit a report to any authority.
     * Every user-facing "delivered / acknowledged / ETA" claim must stay behind
     * this flag so it can only ever appear when a real relay is wired in.
     */
    const val IS_BACKEND_RELAY_AVAILABLE = false
  }
}