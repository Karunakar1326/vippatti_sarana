package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber

@Composable
fun SafeZoneDetailDialog(
  zone: com.example.data.model.SafeZone,
  evaluation: com.example.data.shelters.SafeZoneEvaluation?,
  onDismiss: () -> Unit,
  onSelectAndRoute: () -> Unit
) {
  val capacity = com.example.data.shelters.ShelterCapacityService.report(zone)
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = ObsidianSurface,
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(20.dp))
    ) {
      Column(
        modifier = Modifier
          .padding(20.dp)
          .fillMaxWidth()
          // Long safe-zone intelligence (capacity + resources + reasons)
          // scrolls instead of clipping on short screens.
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "SAFE ZONE INTELLIGENCE",
              fontSize = 10.sp,
              fontWeight = FontWeight.Black,
              color = NeonEmerald,
              letterSpacing = 0.8.sp
            )
            Text(
              text = zone.name,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TacticalOnSurface
            )
            Text(
              text = zone.locationNote,
              fontSize = 10.sp,
              color = TacticalOnSurfaceVariant
            )
            Text(
              text = String.format(
                java.util.Locale.US,
                "%.4f N, %.4f E • ${zone.availableCapacity}/${zone.capacityTotal} spots free",
                zone.lat,
                zone.lon
              ),
              fontSize = 10.sp,
              color = TacticalOnSurfaceVariant
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TacticalOnSurfaceVariant)
          }
        }

        // --- Carrying capacity block ---
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianContainerHigh)
            .border(1.dp, NeonEmerald.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text("CARRYING CAPACITY", fontSize = 10.sp, fontWeight = FontWeight.Black, color = NeonEmerald, letterSpacing = 0.6.sp)
          Text("Total Capacity: ${capacity.totalCapacity}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          Text("Current Occupancy: ${capacity.currentOccupancy}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          Text(
            "Available Capacity: ${capacity.availableCapacity}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = if (capacity.availableCapacity > 0) NeonEmerald else EmergencyRedBright
          )
          Text(
            "Status: ${capacity.statusLabel}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = when (capacity.status) {
              com.example.data.model.CapacityStatus.AVAILABLE -> NeonEmerald
              com.example.data.model.CapacityStatus.NEAR_CAPACITY -> WarningAmber
              else -> EmergencyRedBright
            }
          )
          LinearProgressIndicator(
            progress = { capacity.occupancyPercent / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = when (capacity.status) {
              com.example.data.model.CapacityStatus.AVAILABLE -> NeonEmerald
              com.example.data.model.CapacityStatus.NEAR_CAPACITY -> WarningAmber
              else -> EmergencyRed
            },
            trackColor = ObsidianContainerHigh
          )
          Text("${capacity.occupancyPercent}% occupied", fontSize = 10.sp, color = TacticalOnSurfaceVariant)
        }

        // --- Resources ---
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("FACILITY RESOURCES", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TacticalCyan, letterSpacing = 0.6.sp)
          ResourceRow("Water", zone.waterAvailable)
          ResourceRow("Food", zone.foodAvailable)
          ResourceRow("Electricity", zone.electricityAvailable)
          ResourceRow("Sanitation", zone.sanitationAvailable)
          ResourceRow("Medical support", zone.medicalSupport)
          ResourceRow("Women & children suitability", zone.womenChildrenSuitability)
          InfoLine("Accessibility", zone.accessibility)
          InfoLine("Elevation", zone.elevationNote)
          InfoLine("Operating status", zone.operatingStatus)
        }

        // --- Why this safe zone (evaluation reasons) ---
        if (evaluation != null && evaluation.isFeasible) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("WHY THIS SAFE ZONE?", fontSize = 10.sp, fontWeight = FontWeight.Black, color = NeonEmerald, letterSpacing = 0.6.sp)
            evaluation.reasons.forEach { reason ->
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Check, contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(12.dp))
                Text(reason.text, fontSize = 11.sp, color = TacticalOnSurface, lineHeight = 14.sp)
              }
            }
            Text("Rank score: ${evaluation.score}", fontSize = 10.sp, color = TacticalOnSurfaceVariant)
          }
        } else if (evaluation != null) {
          Text(
            text = "NOT RECOMMENDED: ${evaluation.rejectionReason?.label ?: "Ineligible"}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = EmergencyRedBright
          )
        }

        Text(
          text = "Verification: ${zone.verificationStatus}",
          fontSize = 9.sp,
          color = TacticalCyan
        )

        Button(
          onClick = {
            onSelectAndRoute()
            onDismiss()
          },
          colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("safe_zone_route_button")
        ) {
          Text("Route To This Safe Zone", fontWeight = FontWeight.Bold, color = OnNeonEmerald)
        }
      }
    }
  }
}

@Composable
private fun ResourceRow(label: String, available: Boolean) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(label, fontSize = 12.sp, color = TacticalOnSurface)
    Text(
      text = if (available) "AVAILABLE" else "NOT AVAILABLE",
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      color = if (available) NeonEmerald else EmergencyRedBright
    )
  }
}


@Composable
private fun InfoLine(label: String, value: String) {
  Column {
    Text(label.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurfaceVariant, letterSpacing = 0.5.sp)
    Text(value, fontSize = 11.sp, color = TacticalOnSurface)
  }
}

// ============================================================================
// SOS CONFIRM GATE - the "Are you sure?" dialog shown before any distress
// broadcast is actually transmitted (radar SOS icon, hero broadcast button,
// NEED ASSISTANCE switch all route through here).
// ============================================================================
