package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Landslide
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Tsunami
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.instructions.DisasterCategory
import com.example.data.instructions.DisasterInstructions
import com.example.data.instructions.InstructionItem
import com.example.data.instructions.InstructionPhase
import com.example.data.risk.RiskLevel
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonEmeraldContainer
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.VippattiUiState

// ============================================================================
// INSTRUCTIONS HOME
// ============================================================================

/**
 * INSTRUCTIONS HOME â€” ordered exactly per the new information architecture:
 * header -> offline cache -> disaster selector -> phase selector ->
 * disaster summary -> Critical Actions Now -> instruction categories ->
 * essential resources. Never one giant wall of instructions.
 */
@Composable
internal fun InstructionsHome(
  uiState: VippattiUiState,
  category: DisasterCategory,
  phase: InstructionPhase,
  selectedCategoryId: String,
  selectedPhaseId: String,
  onToggleTheme: () -> Unit,
  onToggleOfflineAccess: (Boolean) -> Unit,
  onOpenInteractiveBag: () -> Unit,
  onNavigate: (String) -> Unit,
  onSelectCategory: (String, String) -> Unit
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    // 1. Compact header â€” SURVIVAL MANUAL + theme toggle.
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f) // title yields space; toggle never pushed off
      ) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(EmergencyRedContainer.copy(alpha = 0.3f))
            .border(1.dp, EmergencyRed.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.CrisisAlert,
            contentDescription = null,
            tint = EmergencyRedBright,
            modifier = Modifier.size(22.dp)
          )
        }
        Column {
          Text(
            text = "SURVIVAL MANUAL",
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            color = TacticalOnSurface,
            letterSpacing = 0.5.sp
          )
          Text(
            text = "Be informed. Be prepared. Be safe.",
            fontSize = 11.sp,
            color = TacticalOnSurfaceVariant
          )
        }
      }
      IconButton(
        onClick = onToggleTheme,
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(ObsidianContainer)
          .testTag("instructions_theme_toggle_button")
      ) {
        Icon(
          imageVector = if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
          contentDescription = if (uiState.isDarkTheme) "Switch to light mode" else "Switch to dark mode",
          tint = TacticalOnSurface,
          modifier = Modifier.size(18.dp)
        )
      }
    }

    // 2. Offline Manual Cache â€” compact functional card (switch stays functional).
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(ObsidianContainerLow)
        .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
        .padding(horizontal = 12.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f) // label wraps; switch never pushed off-screen
      ) {
        Icon(
          imageVector = Icons.Default.CloudOff,
          contentDescription = null,
          tint = TacticalCyan,
          modifier = Modifier.size(20.dp)
        )
        Column {
          Text("OFFLINE-FIRST MODE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          Text("Keeps browsed map tiles; no bulk download in this build", fontSize = 12.sp, color = TacticalOnSurfaceVariant)
        }
      }
      Switch(
        checked = uiState.isOfflineFirstMode,
        onCheckedChange = onToggleOfflineAccess,
        colors = SwitchDefaults.colors(checkedTrackColor = NeonEmerald),
        modifier = Modifier.testTag("instructions_offline_switch")
      )
    }

    // 3. Disaster type selector â€” horizontally scrollable; chips never clip.
    LazyRow(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
      contentPadding = PaddingValues(horizontal = 14.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(DisasterInstructions.categories, key = { it.id }) { cat ->
        val selected = cat.id == selectedCategoryId
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) NeonEmerald else ObsidianContainer)
            .border(
              1.dp,
              if (selected) NeonEmerald else TacticalOutlineVariant.copy(alpha = 0.5f),
              RoundedCornerShape(12.dp)
            )
            .clickable { onSelectCategory(cat.id, selectedPhaseId) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("category_chip_${cat.id}"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = categoryIcon(cat.id),
            contentDescription = null,
            tint = if (selected) OnNeonEmerald else TacticalCyan,
            modifier = Modifier.size(15.dp)
          )
          Text(
            text = cat.title,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
            color = if (selected) OnNeonEmerald else TacticalOnSurface,
            maxLines = 1
          )
        }
      }
    }

    // 4. Phase selector â€” BEFORE | DURING | AFTER (strong selected state).
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 4.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(ObsidianContainerLowest)
        .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
        .padding(3.dp),
      horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
      listOf(
        "before" to "BEFORE",
        "during" to "DURING",
        "after" to "AFTER"
      ).forEach { (id, label) ->
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selectedPhaseId == id) NeonEmerald else Color.Transparent)
            .clickable { onSelectCategory(selectedCategoryId, id) }
            .padding(vertical = 8.dp)
            // Tag on the clickable container (not the inner Text) so the tag
            // survives semantics-merging and the tab stays test-clickable.
            .testTag("phase_${id}_tab"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selectedPhaseId == id) FontWeight.Black else FontWeight.Bold,
            color = if (selectedPhaseId == id) OnNeonEmerald else TacticalOnSurfaceVariant,
            letterSpacing = 0.6.sp
          )
        }
      }
    }

    // 5. Disaster summary â€” icon, title+phase, existing subtitle, REAL risk level.
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(ObsidianContainerLow)
        .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(NeonEmeraldContainer.copy(alpha = 0.25f))
          .border(1.dp, NeonEmerald.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = categoryIcon(category.id),
          contentDescription = null,
          tint = NeonEmerald,
          modifier = Modifier.size(24.dp)
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "${category.title} Â· ${phase.title}",
          fontSize = 15.sp,
          fontWeight = FontWeight.Black,
          color = TacticalOnSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = category.subtitle,
          fontSize = 11.sp,
          color = TacticalOnSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }
      DisasterRiskBadge(riskLevel = uiState.personalRisk?.level)
    }

    // 6. CRITICAL ACTIONS NOW â€” only the top existing critical instructions,
    //    highly scannable; never a duplicate of the full category detail.
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
    ) {
      Icon(Icons.Default.Emergency, contentDescription = null, tint = EmergencyRedBright, modifier = Modifier.size(16.dp))
      Text(
        "CRITICAL ACTIONS NOW",
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = EmergencyRedBright,
        letterSpacing = 0.6.sp
      )
    }
    Text(
      "What to do immediately",
      fontSize = 10.sp,
      color = TacticalOnSurfaceVariant,
      modifier = Modifier.padding(horizontal = 14.dp)
    )
    val criticalItems = phase.items.filter { it.isCritical }
    if (criticalItems.isEmpty()) {
      Text(
        "No critical actions flagged for this phase â€” see categories below.",
        fontSize = 11.sp,
        color = TacticalOnSurfaceVariant,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
      )
    } else {
      criticalItems.take(3).forEach { item -> CriticalActionRow(item) }
    }

    // 7. Instruction Categories â€” grouped navigation into detail screens.
    Text(
      "INSTRUCTION CATEGORIES",
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = TacticalOnSurfaceVariant,
      letterSpacing = 0.6.sp,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
    )
    val groups = buildInstructionGroups(category, phase)
    groups.forEach { group ->
      val (icon, accent) = groupVisual(group.id)
      CategoryCard(
        title = group.title,
        subtitle = group.subtitle,
        icon = icon,
        accent = accent,
        testTag = "category_card_${group.id}",
        onClick = { onNavigate(InstructionsRoutes.group(category.id, selectedPhaseId, group.id)) }
      )
    }

    // 8. Essential Resources â€” contacts, evacuation module, kit + interactive.
    Text(
      "ESSENTIAL RESOURCES",
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = TacticalOnSurfaceVariant,
      letterSpacing = 0.6.sp,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
    )
    CategoryCard(
      title = "Emergency Contacts",
      subtitle = "Important helplines",
      icon = Icons.Default.Call,
      accent = EmergencyRed,
      testTag = "category_card_contacts",
      onClick = { onNavigate(InstructionsRoutes.CONTACTS) }
    )
    // Existing "Evacuation Essentials" common module keeps its own detail view.
    DisasterInstructions.commonModules.firstOrNull { it.id == "evacuation" }?.let { evac ->
      CategoryCard(
        title = evac.title,
        subtitle = evac.subtitle,
        icon = Icons.Default.DirectionsWalk,
        accent = NeonEmerald,
        testTag = "category_card_evacuation",
        onClick = { onNavigate(InstructionsRoutes.module(evac.id)) }
      )
    }
    CategoryCard(
      title = "Emergency Kit",
      subtitle = "72-hour self-reliance pack",
      icon = Icons.Default.Backpack,
      accent = NeonEmerald,
      testTag = "category_card_kit",
      onClick = { onNavigate(InstructionsRoutes.KIT) }
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(ObsidianContainerLow)
        .border(1.dp, NeonEmerald.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        .clickable { onOpenInteractiveBag() }
        .padding(12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f)
      ) {
        Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(20.dp))
        Column {
          Text("Interactive Evacuation Kit", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          Text("Check off items before you relocate", fontSize = 10.sp, color = TacticalOnSurfaceVariant)
        }
      }
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = EmergencyRedBright,
        modifier = Modifier.size(18.dp)
      )
    }
  }
}


private fun categoryIcon(categoryId: String): ImageVector = when (categoryId) {
  "flood" -> Icons.Default.Tsunami
  "landslide" -> Icons.Default.Landslide
  "fire" -> Icons.Default.LocalFireDepartment
  "earthquake" -> Icons.Default.CrisisAlert
  else -> Icons.Default.MenuBook
}

// ============================================================================
// SHARED UI PIECES
// ============================================================================

/** Compact risk badge driven by the REAL personal risk engine. */
@Composable
private fun DisasterRiskBadge(riskLevel: RiskLevel?) {
  val label = when (riskLevel) {
    RiskLevel.RED -> "HIGH RISK"
    RiskLevel.ORANGE -> "ELEVATED RISK"
    RiskLevel.YELLOW -> "WATCH"
    RiskLevel.GREEN -> "LOW RISK"
    null -> "NO DATA"
  }
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(
        when (riskLevel) {
          RiskLevel.RED, RiskLevel.ORANGE -> EmergencyRed.copy(alpha = 0.85f)
          RiskLevel.YELLOW -> WarningAmber.copy(alpha = 0.85f)
          RiskLevel.GREEN -> NeonEmerald.copy(alpha = 0.85f)
          null -> ObsidianContainer
        }
      )
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Text(
      text = label,
      fontSize = 9.sp,
      fontWeight = FontWeight.Black,
      color = when (riskLevel) {
        RiskLevel.RED, RiskLevel.ORANGE -> Color.White
        // Near-black on the amber fill â€” readable in BOTH themes (the old
        // theme-aware value vanished on amber in dark mode and light mode).
        RiskLevel.YELLOW -> Color(0xFF201500)
        RiskLevel.GREEN -> OnNeonEmerald
        null -> TacticalOnSurfaceVariant
      },
      letterSpacing = 0.4.sp,
      maxLines = 1
    )
  }
}
// ============================================================================
// SHARED CATEGORY CARD + CRITICAL ACTION ROW
// ============================================================================

/** Compact category card used for all section navigation. */
@Composable
private fun CategoryCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  accent: Color,
  testTag: String,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 4.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(ObsidianContainerLow)
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
      .clickable { onClick() }
      .padding(horizontal = 12.dp, vertical = 10.dp)
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(accent.copy(alpha = 0.18f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
      Text(subtitle, fontSize = 10.sp, color = TacticalOnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = accent,
      modifier = Modifier.size(18.dp)
    )
  }
}

/** Scannable critical action row for the CRITICAL ACTIONS NOW strip. */
@Composable
private fun CriticalActionRow(item: InstructionItem) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 3.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(EmergencyRedContainer.copy(alpha = 0.3f))
      .border(1.dp, EmergencyRed.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
      .padding(horizontal = 10.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    Box(
      modifier = Modifier
        .size(22.dp)
        .clip(CircleShape)
        .background(EmergencyRed),
      contentAlignment = Alignment.Center
    ) {
      Icon(Icons.Default.Emergency, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
    }
    Text(
      text = item.title,
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      color = TacticalOnSurface,
      modifier = Modifier.weight(1f),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis
    )
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = EmergencyRedBright,
      modifier = Modifier.size(16.dp)
    )
  }
}
