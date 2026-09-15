package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.GoBagItem
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber

@Composable
fun SosBroadcastDialog(
  locationLabel: String,
  batteryLabel: String,
  medicalTagLabel: String,
  relaysLabel: String,
  onDismiss: () -> Unit,
  onCancelSos: () -> Unit
) {
  val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.95f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(800, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "sos_scale"
  )

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = ObsidianSurface,
      modifier = Modifier
        .fillMaxWidth()
        .border(2.dp, EmergencyRed, RoundedCornerShape(24.dp))
    ) {
      Column(
        modifier = Modifier
          .padding(24.dp)
          .fillMaxWidth()
          // Emergency dialog content must remain reachable on short screens,
          // landscape and with the keyboard open — scroll instead of clip.
          .verticalScroll(rememberScrollState())
          .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.size(72.dp)
        ) {
          Box(
            modifier = Modifier
              .size(64.dp)
              .scale(pulseScale)
              .background(EmergencyRed.copy(alpha = 0.3f), CircleShape)
          )
          Box(
            modifier = Modifier
              .size(52.dp)
              .background(EmergencyRed, CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Warning,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(30.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = "BROADCASTING DISTRESS SOS",
          fontSize = 16.sp,
          fontWeight = FontWeight.Black,
          color = EmergencyRed,
          letterSpacing = 0.5.sp
        )

        Text(
          text = "Dispatched via Satellite & Cellular Mesh Network",
          fontSize = 12.sp,
          color = TacticalOnSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianContainerLow, RoundedCornerShape(16.dp))
            .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(16.dp))
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("GPS Location", fontSize = 12.sp, color = TacticalOnSurfaceVariant)
            Text(locationLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Battery Level", fontSize = 12.sp, color = TacticalOnSurfaceVariant)
            Text(batteryLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Medical Tag", fontSize = 12.sp, color = TacticalOnSurfaceVariant)
            Text(medicalTagLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TacticalCyan)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Priority Relays", fontSize = 12.sp, color = TacticalOnSurfaceVariant)
            Text(relaysLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
          onClick = onDismiss,
          colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("sos_keep_broadcasting_button")
        ) {
          Text("Keep Broadcasting Active", fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
          onClick = onCancelSos,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("sos_cancel_broadcast_button")
        ) {
          Text("Cancel SOS / False Alarm", color = TacticalOnSurfaceVariant, fontSize = 13.sp)
        }
      }
    }
  }
}

@Composable
fun InteractiveBagDialog(
  items: List<GoBagItem>,
  onToggleItem: (String) -> Unit,
  onDismiss: () -> Unit
) {
  val checkedCount = items.count { it.isChecked }
  val progress = if (items.isNotEmpty()) checkedCount.toFloat() / items.size else 0f
  val progressPercent = (progress * 100).toInt()

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
          // Adapts to short screens: the checklist scrolls INTERNALLY and the
          // bottom action button always stays visible — never covers items.
          .heightIn(max = 420.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "INTERACTIVE EVACUATION KIT",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = TacticalOnSurface
            )
            Text(
              text = "$checkedCount of ${items.size} items packed",
              fontSize = 11.sp,
              color = TacticalOnSurfaceVariant
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TacticalOnSurfaceVariant)
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
          color = NeonEmerald,
          trackColor = ObsidianContainerHigh,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "$progressPercent% packed",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = if (progressPercent == 100) NeonEmerald else TacticalOnSurfaceVariant,
          modifier = Modifier.align(Alignment.End)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Internally scrollable checklist — no item is ever clipped.
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(items) { item ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianContainerLow)
                .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .clickable { onToggleItem(item.id) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Checkbox(
                checked = item.isChecked,
                onCheckedChange = { onToggleItem(item.id) },
                colors = CheckboxDefaults.colors(
                  checkedColor = NeonEmerald,
                  uncheckedColor = TacticalOutlineVariant
                )
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = item.name,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = if (item.isChecked) NeonEmerald else TacticalOnSurface
                )
                Text(
                  text = item.detail,
                  fontSize = 11.sp,
                  color = TacticalOnSurfaceVariant
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
          onClick = onDismiss,
          colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("interactive_bag_done_button")
        ) {
          Text(
            if (progressPercent == 100) "Kit Complete — Done" else "Mark as Complete",
            fontWeight = FontWeight.Bold,
            color = OnNeonEmerald
          )
        }
      }
    }
  }
}

@Composable
fun AddContactDialog(
  onDismiss: () -> Unit,
  onAddContact: (name: String, relation: String, phone: String, location: String) -> Unit
) {
  var name by remember { mutableStateOf("") }
  var relation by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }
  var location by remember { mutableStateOf("") }

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
          // Form scrolls and stays clear of the keyboard on short screens.
          .verticalScroll(rememberScrollState())
          .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Add Emergency Kin Contact",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TacticalOnSurface
          )
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TacticalOnSurfaceVariant)
          }
        }

        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Full Name", fontSize = 12.sp) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonEmerald,
            unfocusedBorderColor = TacticalOutlineVariant,
            focusedTextColor = TacticalOnSurface,
            unfocusedTextColor = TacticalOnSurface
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("contact_name_input")
        )

        OutlinedTextField(
          value = relation,
          onValueChange = { relation = it },
          label = { Text("Relationship (e.g. Spouse, Brother, Neighbor)", fontSize = 12.sp) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonEmerald,
            unfocusedBorderColor = TacticalOutlineVariant,
            focusedTextColor = TacticalOnSurface,
            unfocusedTextColor = TacticalOnSurface
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("contact_relation_input")
        )

        OutlinedTextField(
          value = phone,
          onValueChange = { phone = it },
          label = { Text("Phone Number (+91 ...)", fontSize = 12.sp) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonEmerald,
            unfocusedBorderColor = TacticalOutlineVariant,
            focusedTextColor = TacticalOnSurface,
            unfocusedTextColor = TacticalOnSurface
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("contact_phone_input")
        )

        OutlinedTextField(
          value = location,
          onValueChange = { location = it },
          label = { Text("Proximity / Ward (e.g. Within 2km, Ward 3)", fontSize = 12.sp) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonEmerald,
            unfocusedBorderColor = TacticalOutlineVariant,
            focusedTextColor = TacticalOnSurface,
            unfocusedTextColor = TacticalOnSurface
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("contact_location_input")
        )

        Spacer(modifier = Modifier.height(4.dp))

        Button(
          onClick = {
            if (name.isNotBlank() && phone.isNotBlank()) {
              onAddContact(name, relation.ifBlank { "Family" }, phone, location.ifBlank { "Nearby" })
            }
          },
          enabled = name.isNotBlank() && phone.isNotBlank(),
          colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("save_contact_button")
        ) {
          Text("Save to Emergency Net", fontWeight = FontWeight.Bold, color = OnNeonEmerald)
        }
      }
    }
  }
}

// ============================================================================
// HAZARD ZONE DETAIL ? opens from a map hazard-circle tap
// ============================================================================

@Composable
fun HazardZoneDetailDialog(
  zone: com.example.data.model.HazardZone,
  onDismiss: () -> Unit
) {
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
          // Long hazard intelligence scrolls instead of clipping on short screens.
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
              text = zone.type.label.uppercase() + " HAZARD",
              fontSize = 10.sp,
              fontWeight = FontWeight.Black,
              color = EmergencyRedBright,
              letterSpacing = 0.8.sp
            )
            Text(
              text = zone.name,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TacticalOnSurface
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TacticalOnSurfaceVariant)
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          InfoPill("SEVERITY", zone.severity.label.uppercase(), EmergencyRedBright)
          InfoPill("RISK LEVEL", zone.riskLevel, EmergencyRedBright)
          InfoPill("TREND", zone.trend.name, TacticalCyan)
        }

        Text(
          text = "Affected area: ${com.example.data.model.GeoMath.formatKm(zone.radiusMeters)} radius from zone center",
          fontSize = 11.sp,
          color = TacticalOnSurface
        )
        Text(
          text = zone.sourceStatus,
          fontSize = 11.sp,
          color = TacticalOnSurfaceVariant
        )
        Text(
          text = "Source: ${zone.provenance.source} ? Status: ${zone.provenance.status} ? Classification: ${zone.provenance.classification.label}",
          fontSize = 9.sp,
          color = TacticalCyan,
          lineHeight = 12.sp
        )
      }
    }
  }
}

@Composable
private fun InfoPill(label: String, value: String, accent: Color) {
  Column(
    modifier = Modifier
      .clip(RoundedCornerShape(10.dp))
      .background(ObsidianContainerHigh)
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
      .padding(horizontal = 10.dp, vertical = 6.dp)
  ) {
    Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurfaceVariant)
    Text(value, fontSize = 12.sp, fontWeight = FontWeight.Black, color = accent)
  }
}

// ============================================================================
// DISASTER EVENT DETAIL - tapped USGS/FIRMS/IMD/user-report marker. Every
// field renders from the REAL event; missing data says "Not available" —
// nothing is invented.
// ============================================================================

@Composable
fun DisasterEventDetailDialog(
  event: com.example.data.disaster.DisasterEvent,
  onDismiss: () -> Unit
) {
  val freshness = com.example.data.disaster.DisasterCachePolicy.label(
    event.updatedAtMillis,
    System.currentTimeMillis()
  )
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
          // Detail content scrolls instead of clipping on short screens.
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = event.disasterType.label.uppercase(),
              fontSize = 10.sp,
              fontWeight = FontWeight.Black,
              color = EmergencyRedBright,
              letterSpacing = 0.8.sp
            )
            Text(
              text = event.title,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = TacticalOnSurface,
              lineHeight = 19.sp
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TacticalOnSurfaceVariant)
          }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          InfoPill("SEVERITY", event.severity.label.uppercase(), EmergencyRedBright)
          InfoPill("STATUS", event.status.label.uppercase(), TacticalCyan)
          InfoPill("DATA", freshness.uppercase(), TacticalCyan)
        }

        // Source / provider provenance.
        DetailLine("Source / provider", event.source.label)
        DetailLine(
          "Data state",
          when (event.origin) {
            com.example.data.disaster.EventOrigin.OBSERVED -> "Observed (provider measurement)"
            com.example.data.disaster.EventOrigin.REPORTED -> "Reported by a citizen - UNVERIFIED"
            com.example.data.disaster.EventOrigin.DERIVED -> "Derived from provider data"
            com.example.data.disaster.EventOrigin.SIMULATED -> "Simulated pilot data"
          }
        )
        DetailLine(
          "Observed at",
          if (event.observedAtMillis > 0L) {
            java.text.SimpleDateFormat(
              "MMM d, yyyy HH:mm",
              java.util.Locale.getDefault()
            ).format(java.util.Date(event.observedAtMillis))
          } else {
            "Not available"
          }
        )
        DetailLine(
          "Location",
          when (val g = event.geometry) {
            is com.example.data.disaster.EventGeometry.Point ->
              String.format(java.util.Locale.US, "%.4f, %.4f", g.lat, g.lon)
            is com.example.data.disaster.EventGeometry.MultiPoint ->
              "${g.points.size} points (first: " + String.format(
                java.util.Locale.US, "%.4f, %.4f", g.points.first().lat, g.points.first().lon
              ) + ")"
            is com.example.data.disaster.EventGeometry.Line ->
              "Track with ${g.points.size} points"
            is com.example.data.disaster.EventGeometry.Polygon ->
              "Alert area polygon (${g.ring.size} vertices)"
            is com.example.data.disaster.EventGeometry.RasterLayer ->
              "Raster layer: ${g.title}"
          }
        )
        when (val d = event.details) {
          is com.example.data.disaster.EventDetails.Quake -> {
            DetailLine("Magnitude", d.magnitude.toString())
            DetailLine("Depth", "${d.depthKm} km")
            DetailLine("Place", d.place.ifBlank { "Not available" })
          }
          is com.example.data.disaster.EventDetails.Fire -> {
            DetailLine("Satellite", d.satellite.ifBlank { "Not available" })
            DetailLine("Instrument", d.instrument.ifBlank { "Not available" })
            DetailLine(
              "Fire radiative power",
              d.frpMegawatts?.let { "$it MW" } ?: "Not available"
            )
            DetailLine("Day/night", d.dayNight ?: "Not available")
          }
          is com.example.data.disaster.EventDetails.OfficialAlert -> {
            DetailLine("Alert event", d.event)
            DetailLine("Issued by", d.senderName)
            DetailLine("Urgency / certainty", "${d.urgency} / ${d.certainty}")
            DetailLine("Instruction", d.instruction ?: "Not available")
          }
          is com.example.data.disaster.EventDetails.UserIncident -> {
            DetailLine("Category", d.categoryLabel)
            DetailLine("Reporter note", d.reporterNote.ifBlank { "Not available" })
          }
          com.example.data.disaster.EventDetails.Generic -> Unit
        }
        DetailLine(
          "Confidence",
          when (event.confidence) {
            com.example.data.disaster.EventConfidence.NOT_PROVIDED ->
              "Not provided by source"
            else -> event.confidence.label + (event.confidenceNote?.let { " ($it)" } ?: "")
          }
        )
        event.affectedAreaLabel?.let { DetailLine("Affected area", it) }
        event.description.takeIf { it.isNotBlank() }?.let {
          Text(
            text = it,
            fontSize = 11.sp,
            color = TacticalOnSurfaceVariant,
            lineHeight = 15.sp
          )
        }
        if (event.url != null) {
          val context = androidx.compose.ui.platform.LocalContext.current
          Button(
            onClick = {
              context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(event.url))
              )
            },
            colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OnNeonEmerald),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp)
          ) {
            Text("Open official source", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
private fun DetailLine(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Top
  ) {
    Text(
      text = label,
      fontSize = 11.sp,
      color = TacticalOnSurfaceVariant,
      modifier = Modifier.weight(0.42f)
    )
    Text(
      text = value,
      fontSize = 11.sp,
      fontWeight = FontWeight.SemiBold,
      color = TacticalOnSurface,
      lineHeight = 14.sp,
      modifier = Modifier.weight(0.58f)
    )
  }
}

// ============================================================================
// USER INCIDENT REPORT - "Add a Report" flow. Submissions are stored locally
// as USER_REPORT / REPORTED / unverified events with a TTL; they are never
// presented as verified disasters.
// ============================================================================

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun IncidentReportDialog(
  locationLabel: String,
  isGpsAvailable: Boolean,
  onDismiss: () -> Unit,
  onSubmit: (category: com.example.data.disaster.IncidentCategory, severityLabel: String, description: String) -> Unit
) {
  var selectedCategory by remember {
    mutableStateOf(com.example.data.disaster.IncidentCategory.ROAD_BLOCKED)
  }
  var selectedSeverity by remember { mutableStateOf("Moderate") }
  var description by remember { mutableStateOf("") }
  val severityOptions = listOf("Low", "Moderate", "High", "Extreme")

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(20.dp),
      color = ObsidianSurface,
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier
          .padding(20.dp)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          // Keep Submit reachable while the keyboard is open.
          .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Add a Report",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = TacticalOnSurface
            )
            Text(
              text = "Shown on the map as an UNVERIFIED user report",
              fontSize = 11.sp,
              color = TacticalOnSurfaceVariant
            )
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TacticalOnSurfaceVariant)
          }
        }

        if (!isGpsAvailable) {
          Text(
            text = "GPS unavailable — reports attach a generic India-centre location until a device fix is available.",
            fontSize = 10.sp,
            color = WarningAmber,
            lineHeight = 13.sp
          )
        }

        Text(
          "CATEGORY",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = TacticalOnSurfaceVariant,
          letterSpacing = 0.5.sp
        )
        // FlowRow wraps categories naturally at 360dp and large font scales.
        androidx.compose.foundation.layout.FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          com.example.data.disaster.IncidentCategory.entries.forEach { category ->
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                  if (category == selectedCategory) NeonEmerald.copy(alpha = 0.2f) else ObsidianContainerHigh
                )
                .border(
                  1.dp,
                  if (category == selectedCategory) NeonEmerald else TacticalOutlineVariant.copy(alpha = 0.5f),
                  RoundedCornerShape(8.dp)
                )
                .clickable { selectedCategory = category }
                .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text(
                text = category.label,
                fontSize = 11.sp,
                fontWeight = if (category == selectedCategory) FontWeight.Bold else FontWeight.Medium,
                color = if (category == selectedCategory) NeonEmerald else TacticalOnSurface
              )
            }
          }
        }

        Text(
          "SEVERITY",
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          color = TacticalOnSurfaceVariant,
          letterSpacing = 0.5.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          severityOptions.forEach { option ->
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (option == selectedSeverity) EmergencyRedContainer.copy(alpha = 0.3f) else ObsidianContainerHigh)
                .border(
                  1.dp,
                  if (option == selectedSeverity) EmergencyRedBright else TacticalOutlineVariant.copy(alpha = 0.5f),
                  RoundedCornerShape(8.dp)
                )
                .clickable { selectedSeverity = option }
                .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
              Text(
                text = option,
                fontSize = 11.sp,
                fontWeight = if (option == selectedSeverity) FontWeight.Bold else FontWeight.Medium,
                color = if (option == selectedSeverity) EmergencyRedBright else TacticalOnSurface
              )
            }
          }
        }

        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("What are you seeing? (optional)", fontSize = 12.sp) },
          minLines = 2,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonEmerald,
            unfocusedBorderColor = TacticalOutlineVariant,
            focusedTextColor = TacticalOnSurface,
            unfocusedTextColor = TacticalOnSurface
          ),
          modifier = Modifier.fillMaxWidth()
        )

        DetailLine("Reporting location", locationLabel)

        Button(
          onClick = { onSubmit(selectedCategory, selectedSeverity, description) },
          colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = OnNeonEmerald),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("submit_incident_button")
        ) {
          Text("Submit Report", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

// ============================================================================
// SAFE ZONE DETAIL ? full carrying-capacity & resource intelligence
// ============================================================================

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

@Composable
fun SosConfirmDialog(
  locationLabel: String,
  batteryLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit
) {
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = ObsidianSurface,
      modifier = Modifier
        .fillMaxWidth()
        .border(2.dp, EmergencyRed, RoundedCornerShape(24.dp))
    ) {
      Column(
        modifier = Modifier
          .padding(24.dp)
          .fillMaxWidth()
          // Keep the YES/CANCEL emergency actions reachable on short screens.
          .verticalScroll(rememberScrollState())
          .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Icon(
          imageVector = Icons.Default.Warning,
          contentDescription = null,
          tint = EmergencyRed,
          modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = "BROADCAST SOS DISTRESS SIGNAL?",
          fontSize = 16.sp,
          fontWeight = FontWeight.Black,
          color = EmergencyRed,
          letterSpacing = 0.5.sp
        )

        Text(
          text = "Are you sure? This will transmit your live location, device battery and medical tags to the NDRF 112 ward dispatcher and your emergency kin network.",
          fontSize = 12.sp,
          color = TacticalOnSurfaceVariant,
          lineHeight = 16.sp,
          modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianContainerLow, RoundedCornerShape(16.dp))
            .border(1.dp, TacticalOutlineVariant, RoundedCornerShape(16.dp))
            .padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("GPS Location", fontSize = 12.sp, color = TacticalOnSurfaceVariant)
            Text(locationLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Battery Level", fontSize = 12.sp, color = TacticalOnSurfaceVariant)
            Text(batteryLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
          onClick = onConfirm,
          colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("sos_confirm_button")
        ) {
          Text("YES - BROADCAST NOW", fontWeight = FontWeight.Black, color = Color.White)
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
          onClick = onDismiss,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("sos_confirm_cancel_button")
        ) {
          Text("CANCEL - I AM SAFE", color = TacticalOnSurfaceVariant, fontSize = 13.sp)
        }
      }
    }
  }
}

