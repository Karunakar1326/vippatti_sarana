package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Landslide
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Tsunami
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.instructions.CommonModule
import com.example.data.instructions.DisasterCategory
import com.example.data.instructions.DisasterInstructions
import com.example.data.instructions.InstructionItem
import com.example.data.instructions.InstructionPhase
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.VippattiUiState

/**
 * HIERARCHICAL INSTRUCTIONS MODULE.
 *
 * Structure (data-driven from DisasterInstructions ? no instruction text
 * lives in this UI file):
 *
 *   Disaster Category (Flood / Landslide / Fire / Earthquake / ...)
 *     -> Before / During / After
 *       -> Instruction items
 *   Common Modules (Emergency Contacts / Evacuation / Emergency Kit)
 *
 * Extensible: adding a category or item in DisasterInstructions.kt makes it
 * appear here automatically. Emergency quick actions stay accessible at the
 * bottom (flashlight / siren).
 */
@Composable
fun InstructionsScreen(
  uiState: VippattiUiState,
  onToggleTheme: () -> Unit,
  onToggleOfflineAccess: (Boolean) -> Unit,
  onOpenInteractiveBag: () -> Unit,
  onToggleFlashlight: () -> Unit,
  onToggleSiren: () -> Unit,
  modifier: Modifier = Modifier
) {
  var selectedCategoryId by rememberSaveable { mutableStateOf(DisasterInstructions.categories.first().id) }
  var selectedPhaseId by rememberSaveable { mutableStateOf("during") }

  val category = DisasterInstructions.categories.firstOrNull { it.id == selectedCategoryId }
    ?: DisasterInstructions.categories.first()
  val phase: InstructionPhase = when (selectedPhaseId) {
    "before" -> category.before
    "after" -> category.after
    else -> category.during
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(ObsidianSurface)
  ) {
    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
    ) {
      // 1. Header
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurface)
            .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.3f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                text = "Hierarchical disaster instructions ? India pilot",
                fontSize = 11.sp,
                color = TacticalOnSurfaceVariant
              )
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                contentDescription = "Theme",
                tint = if (uiState.isDarkTheme) WarningAmber else TacticalOnSurface,
                modifier = Modifier.size(17.dp)
              )
            }
          }
        }
      }

      // 2. Offline cache toggle
      item {
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
          Column {
            Text("OFFLINE MANUAL CACHE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
            Text("Keep all instructions readable without network", fontSize = 10.sp, color = TacticalOnSurfaceVariant)
          }
          Switch(
            checked = uiState.is100PercentOfflineCached,
            onCheckedChange = onToggleOfflineAccess,
            colors = SwitchDefaults.colors(checkedTrackColor = NeonEmerald),
            modifier = Modifier.testTag("instructions_offline_switch")
          )
        }
      }

      // 3. Disaster Category selector (horizontal chips).
      item {
        LazyRow(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(DisasterInstructions.categories, key = { it.id }) { cat ->
            val selected = cat.id == selectedCategoryId
            CategoryChip(cat, selected) {
              selectedCategoryId = cat.id
            }
          }
        }
      }

      // 4. Phase selector: Before / During / After.
      item {
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
                .clickable { selectedPhaseId = id }
                .padding(vertical = 8.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selectedPhaseId == id) FontWeight.Black else FontWeight.Bold,
                color = if (selectedPhaseId == id) Color(0xFF003822) else TacticalOnSurfaceVariant,
                letterSpacing = 0.6.sp,
                modifier = Modifier.testTag("phase_${id}_tab")
              )
            }
          }
        }
      }

      // 5. Phase content: instruction items from the data model.
      item {
        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
              imageVector = categoryIcon(category.id),
              contentDescription = null,
              tint = TacticalCyan,
              modifier = Modifier.size(20.dp)
            )
            Column {
              Text(
                text = "${category.title} ? ${phase.title}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = TacticalOnSurface
              )
              Text(
                text = category.subtitle,
                fontSize = 10.sp,
                color = TacticalOnSurfaceVariant
              )
            }
          }
        }
      }

      items(phase.items.size) { idx ->
        val itemData = phase.items[idx]
        InstructionItemCard(itemData)
      }

      // 6. Common modules.
      item {
        Text(
          text = "COMMON MODULES",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = TacticalOnSurfaceVariant,
          letterSpacing = 0.6.sp,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
      }
      itemsIndexed(DisasterInstructions.commonModules, key = { _, m -> "cm-" + m.id }) { _, module ->
        CommonModuleCard(module)
      }

      // 7. Go-bag quick access.
      item {
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
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Work, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(20.dp))
            Column {
              Text("Interactive Emergency Kit", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
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

    // 8. Sticky Emergency Quick Trigger bottom row (SOS tools stay accessible).
    EmergencyQuickTriggers(
      isFlashlightOn = uiState.isFlashlightOn,
      isSirenOn = uiState.isSirenOn,
      onToggleFlashlight = onToggleFlashlight,
      onToggleSiren = onToggleSiren
    )
  }
}

// ============================================================================
// Sub-components
// ============================================================================

private fun categoryIcon(categoryId: String): ImageVector = when (categoryId) {
  "flood" -> Icons.Default.Tsunami
  "landslide" -> Icons.Default.Landslide
  "fire" -> Icons.Default.LocalFireDepartment
  "earthquake" -> Icons.Default.CrisisAlert
  else -> Icons.Default.MenuBook
}

@Composable
private fun CategoryChip(category: DisasterCategory, isSelected: Boolean, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .background(if (isSelected) NeonEmerald else ObsidianContainer)
      .border(
        1.dp,
        if (isSelected) NeonEmerald else TacticalOutlineVariant.copy(alpha = 0.5f),
        RoundedCornerShape(12.dp)
      )
      .clickable { onClick() }
      .padding(horizontal = 12.dp, vertical = 8.dp)
      .testTag("category_chip_${category.id}"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Icon(
      imageVector = categoryIcon(category.id),
      contentDescription = null,
      tint = if (isSelected) Color(0xFF003822) else TacticalCyan,
      modifier = Modifier.size(15.dp)
    )
    Text(
      text = category.title,
      fontSize = 12.sp,
      fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
      color = if (isSelected) Color(0xFF003822) else TacticalOnSurface
    )
  }
}

@Composable
private fun InstructionItemCard(item: InstructionItem) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 4.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(
        if (item.isCritical) EmergencyRedContainer.copy(alpha = 0.3f)
        else ObsidianContainer.copy(alpha = 0.7f)
      )
      .border(
        1.dp,
        if (item.isCritical) EmergencyRed.copy(alpha = 0.55f) else TacticalOutlineVariant.copy(alpha = 0.3f),
        RoundedCornerShape(12.dp)
      )
      .padding(10.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.Top
  ) {
    Box(
      modifier = Modifier
        .size(22.dp)
        .clip(CircleShape)
        .background(if (item.isCritical) EmergencyRed else TacticalCyan.copy(alpha = 0.25f)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = if (item.isCritical) Icons.Default.CrisisAlert else Icons.Default.CheckCircle,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(13.dp)
      )
    }
    Column {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = item.title,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = TacticalOnSurface
        )
        if (item.isCritical) {
          Text(
            text = "CRITICAL",
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            color = EmergencyRedBright,
            letterSpacing = 0.5.sp
          )
        }
      }
      Text(
        text = item.detail,
        fontSize = 11.sp,
        color = TacticalOnSurfaceVariant,
        lineHeight = 15.sp
      )
      if (item.region != null) {
        Text(
          text = "Regional: ${item.region}",
          fontSize = 9.sp,
          color = TacticalCyan
        )
      }
    }
  }
}

@Composable
private fun CommonModuleCard(module: CommonModule) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 4.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(ObsidianContainerLow)
      .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Icon(
        imageVector = when (module.id) {
          "emergency_contacts" -> Icons.Default.Emergency
          "evacuation" -> Icons.Default.WaterDrop
          "emergency_kit" -> Icons.Default.Work
          else -> Icons.Outlined.MenuBook
        },
        contentDescription = null,
        tint = TacticalCyan,
        modifier = Modifier.size(18.dp)
      )
      Column {
        Text(
          text = module.title,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = TacticalOnSurface
        )
        Text(
          text = module.subtitle,
          fontSize = 10.sp,
          color = TacticalOnSurfaceVariant
        )
      }
    }
    module.items.forEach { sub ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(ObsidianContainer.copy(alpha = 0.6f))
          .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
      ) {
        Box(
          modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(if (sub.isCritical) EmergencyRedBright else NeonEmerald),
          contentAlignment = Alignment.Center
        ) { }
        Column {
          Text(
            text = sub.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TacticalOnSurface
          )
          Text(
            text = sub.detail,
            fontSize = 10.sp,
            color = TacticalOnSurfaceVariant
          )
        }
      }
    }
  }
}

@Composable
private fun EmergencyQuickTriggers(
  isFlashlightOn: Boolean,
  isSirenOn: Boolean,
  onToggleFlashlight: () -> Unit,
  onToggleSiren: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(EmergencyRed)
      .padding(horizontal = 14.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon(
        imageVector = Icons.Default.Emergency,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(20.dp)
      )
      Text(
        text = "EMERGENCY QUICK TRIGGER",
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = Color.White,
        letterSpacing = 0.6.sp
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(if (isFlashlightOn) Color.White else Color(0x33000000))
          .clickable { onToggleFlashlight() }
          .padding(horizontal = 10.dp, vertical = 6.dp)
          .testTag("emergency_flashlight_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Icon(
          imageVector = Icons.Default.FlashlightOn,
          contentDescription = null,
          tint = if (isFlashlightOn) EmergencyRed else Color.White,
          modifier = Modifier.size(14.dp)
        )
        Text(
          text = if (isFlashlightOn) "ON" else "Light",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = if (isFlashlightOn) EmergencyRed else Color.White
        )
      }

      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .background(if (isSirenOn) Color.Yellow else Color.White)
          .clickable { onToggleSiren() }
          .padding(horizontal = 10.dp, vertical = 6.dp)
          .testTag("emergency_siren_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Icon(
          imageVector = Icons.Default.VolumeUp,
          contentDescription = null,
          tint = if (isSirenOn) Color.Black else EmergencyRed,
          modifier = Modifier.size(14.dp)
        )
        Text(
          text = if (isSirenOn) "SIREN ON" else "SOS SIREN",
          fontSize = 11.sp,
          fontWeight = FontWeight.Black,
          color = if (isSirenOn) Color.Black else EmergencyRed,
          letterSpacing = 0.4.sp
        )
      }
    }
  }
}
