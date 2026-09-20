package com.example.data.reports

import com.example.data.routing.GeoPoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Honest-contract tests for the local emergency report recorder.
 *
 * Replaces the previous NdrfEmergencyReportServiceTest, whose assertions LOCKED IN
 * the fabricated behaviour (it asserted `etaMinutes == 10` and a note mentioning
 * the "NDRF ward dispatcher"). The new contract is the opposite: no authority,
 * no delivery, no ETA claim may ever appear while no backend relay exists.
 */
class LocalEmergencyReportServiceTest {

  private val service = LocalEmergencyReportService()

  private fun report(
    kind: ReportKind = ReportKind.SOS_BROADCAST,
    message: String = "Requesting immediate assistance.",
    photoUri: String? = null,
    batteryPercent: Int? = 76,
    isCharging: Boolean = true
  ) = EmergencyReport(
    kind = kind,
    reporterId = "TEST-CITIZEN",
    reporterName = "Test Citizen",
    message = message,
    photoUri = photoUri,
    location = GeoPoint(9.8478, 76.9422),
    batteryPercent = batteryPercent,
    isCharging = isCharging,
    medicalTag = "Asthma / Inhaler"
  )

  @Test
  fun `sos broadcast is recorded locally with a local reference id`() = runTest {
    val receipt = service.submit(report())

    assertTrue("a valid report must be recorded", receipt.accepted)
    assertTrue(
      "reference id must be clearly local: ${receipt.reportId}",
      receipt.reportId.startsWith("LOCAL-SOS-")
    )
    assertEquals(LocalEmergencyReportService.RELAY_CHANNEL, receipt.relayChannel)
    assertTrue(receipt.note.contains("THIS DEVICE"))
    assertTrue(receipt.note.contains("76%"))
  }

  @Test
  fun `no receipt ever claims an authority, delivery or response time`() = runTest {
    val receipts = listOf(
      service.submit(report(kind = ReportKind.SOS_BROADCAST)),
      service.submit(report(kind = ReportKind.SITUATION_REPORT, message = "Roof leaking."))
    )

    for (receipt in receipts) {
      // No response estimate is ever invented for a local record.
      assertNull("etaMinutes must be null for a local record", receipt.etaMinutes)
      val text = "${receipt.note} ${receipt.relayChannel}".lowercase()
      for (forbidden in listOf(
        "ndrf", "sarama", "sarana", "satellite mesh", "ward dispatcher",
        "transmitted to", "delivered", "accepted by", "acknowledg"
      )) {
        assertFalse(
          "receipt must not claim '$forbidden' while no backend relay exists: $text",
          text.contains(forbidden)
        )
      }
      // The honest escape hatch is present instead.
      assertTrue(
        "receipt must tell the user how to actually reach help: $text",
        text.contains("112")
      )
    }
  }

  @Test
  fun `a situation report is recorded locally with its own reference id`() = runTest {
    val receipt = service.submit(
      report(
        kind = ReportKind.SITUATION_REPORT,
        message = "[Need water] Roof leaking, three of us on the first floor.",
        photoUri = "content://media/picker/1",
        batteryPercent = 51,
        isCharging = false
      )
    )

    assertTrue(receipt.accepted)
    assertTrue(receipt.reportId.startsWith("LOCAL-SIT-"))
    assertNull(receipt.etaMinutes)
    assertTrue(receipt.note.contains("51%"))
  }

  @Test
  fun `blank report is rejected without claiming anything was recorded`() = runTest {
    val receipt = service.submit(report(message = "   "))

    assertFalse(receipt.accepted)
    assertNull(receipt.etaMinutes)
    assertTrue(receipt.note.contains("empty"))
    assertTrue(receipt.note.contains("Nothing was transmitted"))
  }

  /** Guards the flag that every "delivered" UI claim must stay behind. */
  @Test
  fun `backend relay is reported as unavailable in this build`() {
    assertFalse(
      "no backend relay exists yet — UI must not claim delivery",
      LocalEmergencyReportService.IS_BACKEND_RELAY_AVAILABLE
    )
  }
}