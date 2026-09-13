package com.example

import com.example.data.reports.EmergencyReport
import com.example.data.reports.NdrfEmergencyReportService
import com.example.data.reports.ReportKind
import com.example.data.routing.GeoPoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NDRF report workflow behind the EmergencyReportService abstraction.
 */
class NdrfEmergencyReportServiceTest {

  private val service = NdrfEmergencyReportService()

  @Test
  fun `sos broadcast is accepted with receipt id, eta and battery note`() = runTest {
    val receipt = service.submit(
      EmergencyReport(
        kind = ReportKind.SOS_BROADCAST,
        reporterId = "SARANA-AP-89241",
        reporterName = "Aditya Vardhan",
        message = "NEED ASSISTANCE — requesting immediate help.",
        location = GeoPoint(9.8478, 76.9422),
        batteryPercent = 76,
        isCharging = true,
        medicalTag = "Asthma / Inhaler"
      )
    )

    assertTrue(receipt.accepted)
    assertTrue(receipt.reportId.startsWith("NDRF-SOS-"))
    assertEquals(10, receipt.etaMinutes)
    assertTrue(receipt.note.contains("NDRF ward dispatcher"))
    assertTrue(receipt.note.contains("76%"))
  }

  @Test
  fun `situation report is accepted with a longer eta`() = runTest {
    val receipt = service.submit(
      EmergencyReport(
        kind = ReportKind.SITUATION_REPORT,
        reporterId = "SARANA-AP-89241",
        reporterName = "Aditya Vardhan",
        message = "[Need water] Roof leaking, three of us on the first floor.",
        photoUri = "content://media/picker/1",
        location = GeoPoint(9.8478, 76.9422),
        batteryPercent = 51
      )
    )

    assertTrue(receipt.accepted)
    assertTrue(receipt.reportId.startsWith("NDRF-SIT-"))
    assertEquals(25, receipt.etaMinutes)
  }

  @Test
  fun `blank report is rejected without an eta`() = runTest {
    val receipt = service.submit(
      EmergencyReport(
        kind = ReportKind.SITUATION_REPORT,
        reporterId = "SARANA-AP-89241",
        reporterName = "Aditya Vardhan",
        message = "   ",
        location = GeoPoint(9.8478, 76.9422)
      )
    )

    assertFalse(receipt.accepted)
    assertEquals(null, receipt.etaMinutes)
    assertTrue(receipt.note.contains("cannot be empty"))
  }
}
