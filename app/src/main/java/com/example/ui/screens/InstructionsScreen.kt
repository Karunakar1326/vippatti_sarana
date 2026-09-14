package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Landslide
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tsunami
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.instructions.CommonModule
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
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.OnTacticalCyan
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalCyanContainer
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.ui.theme.WarningAmber
import com.example.viewmodel.VippattiUiState

/**
 * ============================================================================
 * INSTRUCTIONS MODULE — SURVIVAL MANUAL (complete ground-up rebuild)
 * ============================================================================
 * Hierarchical, emergency-first information architecture:
 *
 *   HOME  ->  Disaster type  ->  Phase (Before/During/After)
 *         ->  CRITICAL ACTIONS NOW (scannable top-priority actions)
 *         ->  Instruction Categories -> dedicated detail screens
 *         ->  Emergency Contacts / Emergency Kit / Interactive Kit
 *
 * All instruction content comes from the existing DisasterInstructions data
 * model — nothing is invented, nothing dropped. The EMERGENCY QUICK TRIGGER
 * (flashlight + SOS siren) is pinned OUTSIDE the scrolling list, permanently
 * visible above the bottom navigation, on every route of this module.
 *
 * Back navigation returns Home while PRESERVING the selected disaster and
 * phase (user context is never reset).
 */

/** In-module navigation routes (detail views are internal to this module). */
private object InstructionsRoutes {
  const val HOME = "home"
  const val CONTACTS = "contacts"
  const val KIT = "kit"
  fun group(categoryId: String, phaseId: String, groupId: String) =
    "group:$categoryId:$phaseId:$groupId"
  fun module(moduleId: String) = "module:$moduleId"
}

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
  // Survive tab switches and process death — selected disaster, phase AND
  // active detail route are all restored; user context is never reset.
  var selectedCategoryId by rememberSaveable { mutableStateOf(DisasterInstructions.categories.first().id) }
  var selectedPhaseId by rememberSaveable { mutableStateOf("during") }
  var route by rememberSaveable { mutableStateOf(InstructionsRoutes.HOME) }

  val category = DisasterInstructions.categories.firstOrNull { it.id == selectedCategoryId }
    ?: DisasterInstructions.categories.first()
  val phase: InstructionPhase = when (selectedPhaseId) {
    "before" -> category.before
    "after" -> category.after
    else -> category.during
  }

  // In-module back navigation returns Home WITHOUT resetting disaster/phase.
  BackHandler(enabled = route != InstructionsRoutes.HOME) {
    route = InstructionsRoutes.HOME
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(ObsidianSurface)
  ) {
    LazyColumn(
      modifier = Modifier
        .weight(1f) // scrolling content always ends ABOVE the emergency trigger
        .fillMaxWidth(),
      contentPadding = PaddingValues(bottom = 12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      item(key = route) {
        val goHome = { route = InstructionsRoutes.HOME }
        when {
          route == InstructionsRoutes.CONTACTS -> ContactsDetailScreen(onBack = goHome)
          route == InstructionsRoutes.KIT -> KitDetailScreen(onBack = goHome)
          route.startsWith("group:") -> GroupDetailScreen(route, category, phase, onBack = goHome)
          route.startsWith("module:") -> {
            val module = DisasterInstructions.commonModules.firstOrNull {
              it.id == route.removePrefix("module:")
            }
            if (module != null) ModuleDetailScreen(module, onBack = goHome) else InstructionsHome(
              uiState, category, phase, selectedCategoryId, selectedPhaseId,
              onToggleTheme, onToggleOfflineAccess, onOpenInteractiveBag,
              { r -> route = r }, { cid, pid -> selectedCategoryId = cid; selectedPhaseId = pid }
            )
          }
          else -> InstructionsHome(
            uiState = uiState,
            category = category,
            phase = phase,
            selectedCategoryId = selectedCategoryId,
            selectedPhaseId = selectedPhaseId,
            onToggleTheme = onToggleTheme,
            onToggleOfflineAccess = onToggleOfflineAccess,
            onOpenInteractiveBag = onOpenInteractiveBag,
            onNavigate = { newRoute -> route = newRoute },
            onSelectCategory = { cid, pid ->
              selectedCategoryId = cid
              selectedPhaseId = pid
            }
          )
        }
      }
    }

    // PERMANENT EMERGENCY QUICK TRIGGER — pinned above the bottom navigation
    // on every route of the Instructions module (never scrolls away).
    EmergencyQuickTrigger(
      isFlashlightOn = uiState.isFlashlightOn,
      isSirenOn = uiState.isSirenOn,
      onToggleFlashlight = onToggleFlashlight,
      onToggleSiren = onToggleSiren
    )
  }
}

// ============================================================================
// INSTRUCTIONS HOME
// ============================================================================

/**
 * INSTRUCTIONS HOME — ordered exactly per the new information architecture:
 * header -> offline cache -> disaster selector -> phase selector ->
 * disaster summary -> Critical Actions Now -> instruction categories ->
 * essential resources. Never one giant wall of instructions.
 */
@Composable
private fun InstructionsHome(
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
    // 1. Compact header — SURVIVAL MANUAL + theme toggle.
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

    // 2. Offline Manual Cache — compact functional card (switch stays functional).
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
          Text("OFFLINE MANUAL CACHE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          Text("All instructions available without network", fontSize = 10.sp, color = TacticalOnSurfaceVariant)
        }
      }
      Switch(
        checked = uiState.is100PercentOfflineCached,
        onCheckedChange = onToggleOfflineAccess,
        colors = SwitchDefaults.colors(checkedTrackColor = NeonEmerald),
        modifier = Modifier.testTag("instructions_offline_switch")
      )
    }

    // 3. Disaster type selector — horizontally scrollable; chips never clip.
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

    // 4. Phase selector — BEFORE | DURING | AFTER (strong selected state).
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

    // 5. Disaster summary — icon, title+phase, existing subtitle, REAL risk level.
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
          text = "${category.title} · ${phase.title}",
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

    // 6. CRITICAL ACTIONS NOW — only the top existing critical instructions,
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
        "No critical actions flagged for this phase — see categories below.",
        fontSize = 11.sp,
        color = TacticalOnSurfaceVariant,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
      )
    } else {
      criticalItems.take(3).forEach { item -> CriticalActionRow(item) }
    }

    // 7. Instruction Categories — grouped navigation into detail screens.
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

    // 8. Essential Resources — contacts, evacuation module, kit + interactive.
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

// ============================================================================
// CATEGORY GROUPING — classifies EXISTING instruction items into meaningful
// categories. No content is invented; every item of the selected phase lands
// in exactly one group (fallback: "More Safety Steps"). "After" phases get a
// single Recovery group (recovery wording dominates the data).
// ============================================================================

private data class InstructionGroup(
  val id: String,
  val title: String,
  val subtitle: String,
  val items: List<InstructionItem>
)

/** Theme-aware visual (icon + accent) for a group id — resolved in composition. */
@Composable
private fun groupVisual(groupId: String): Pair<ImageVector, Color> = when (groupId) {
  "immediate_safety" -> Icons.Default.Shield to EmergencyRed
  "evacuation" -> Icons.Default.DirectionsWalk to NeonEmerald
  "utilities" -> Icons.Default.Bolt to WarningAmber
  "vulnerable" -> Icons.Default.Groups to TacticalCyan
  "recovery" -> Icons.Default.HealthAndSafety to TacticalCyan
  else -> Icons.Default.MenuBook to TacticalCyan
}

private fun buildInstructionGroups(
  category: DisasterCategory,
  phase: InstructionPhase
): List<InstructionGroup> {
  data class RawGroup(val id: String, val title: String, val subtitle: String, val keywords: List<String>)

  val definitions = listOf(
    RawGroup("immediate_safety", "Immediate Safety", "Stay safe right now", listOf(
      "critical", "immediately", "never", "do not", "drop", "cover", "hold",
      "get out", "stay out", "moving water", "rumbling", "walking", "walk",
      "re-enter", "open ground", "windows", "lifts", "knock", "sweep", "stay away"
    )),
    RawGroup("evacuation", "Evacuation Guide", "Where to go and what to do", listOf(
      "evacuat", "higher ground", "route", "safe zone", "assembly", "leave", "leaving",
      "shelter", "register", "go-bag", "belongings", "move away", "sideways", "bag"
    )),
    RawGroup("utilities", "Electricity & Utilities", "Power, gas and water safety", listOf(
      "power", "electric", "gas", "water", "switch", "mains", "cylinder", "fuel", "wiring", "wet", "live wires"
    )),
    RawGroup("vulnerable", "Vulnerable People", "Children, elderly, disabled", listOf(
      "children", "elderly", "disabled", "neighbors", "neighbours", "family", "practice", "drill", "trapped", "carry"
    ))
  )

  val assigned = mutableMapOf<String, MutableList<InstructionItem>>()
  val unassigned = mutableListOf<InstructionItem>()
  phase.items.forEach { item ->
    val text = (item.title + " " + item.detail).lowercase()
    val hit = definitions.firstOrNull { def -> def.keywords.any { text.contains(it) } }
    if (hit != null) {
      assigned.getOrPut(hit.id) { mutableListOf() }.add(item)
    } else {
      unassigned.add(item)
    }
  }

  // "After" phases: recovery terminology dominates — one clean Recovery group
  // holding ALL of that phase's items (nothing dropped).
  if (phase.title.equals("After", ignoreCase = true)) {
    return listOf(
      InstructionGroup(
        id = "recovery",
        title = "After the ${category.title}",
        subtitle = "Recovery and health precautions",
        items = phase.items
      )
    )
  }

  val groups = mutableListOf<InstructionGroup>()
  definitions.forEach { def ->
    val items = assigned[def.id]
    if (!items.isNullOrEmpty()) {
      groups.add(
        InstructionGroup(id = def.id, title = def.title, subtitle = def.subtitle, items = items)
      )
    }
  }
  if (unassigned.isNotEmpty()) {
    groups.add(
      InstructionGroup(
        id = "more_safety",
        title = "More Safety Steps",
        subtitle = "Additional guidance for this phase",
        items = unassigned
      )
    )
  }
  return groups
}

/** Disaster icon by category id — preserved from the existing module. */
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
        RiskLevel.YELLOW, RiskLevel.GREEN -> OnNeonEmerald
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

// ============================================================================
// DETAIL SCREENS — back header + full existing instruction content
// ============================================================================

/** Shared detail-screen header: WORKING back button, icon, title, context subtitle. */
@Composable
private fun DetailHeader(
  icon: ImageVector,
  iconTint: Color,
  title: String,
  subtitle: String,
  onBack: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconButton(
      onClick = onBack,
      modifier = Modifier
        .size(34.dp)
        .clip(CircleShape)
        .background(ObsidianContainer)
        .testTag("instructions_back_button")
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back to Instructions",
        tint = TacticalOnSurface,
        modifier = Modifier.size(18.dp)
      )
    }
    Box(
      modifier = Modifier
        .size(38.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(iconTint.copy(alpha = 0.15f))
        .border(1.dp, iconTint.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
      contentAlignment = Alignment.Center
    ) {
      Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(title, fontSize = 15.sp, fontWeight = FontWeight.Black, color = TacticalOnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(subtitle, fontSize = 10.sp, color = TacticalOnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

/**
 * CATEGORY DETAIL — shows ALL items of the tapped instruction group with
 * the existing critical/safety visual language, nothing omitted.
 */
@Composable
private fun GroupDetailScreen(
  route: String,
  category: DisasterCategory,
  phase: InstructionPhase,
  onBack: () -> Unit
) {
  // route = "group:<categoryId>:<phaseId>:<groupId>"
  val parts = route.split(":")
  val groupId = parts.getOrNull(3) ?: "immediate_safety"
  val group = buildInstructionGroups(category, phase).firstOrNull { it.id == groupId }
    ?: buildInstructionGroups(category, phase).first()

  Column(modifier = Modifier.fillMaxWidth()) {
    val (icon, accent) = groupVisual(group.id)
    DetailHeader(
      icon = icon,
      iconTint = accent,
      title = group.title,
      subtitle = "${category.title} · ${phase.title}",
      onBack = onBack
    )
    Text(
      text = "Your safety comes first. Follow these instructions to reduce risk.",
      fontSize = 11.sp,
      color = TacticalOnSurfaceVariant,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
    )
    group.items.forEach { item ->
      InstructionDetailCard(item)
    }
  }
}

/**
 * Common-module detail (Evacuation Essentials and any future module) —
 * renders the existing common-module items with the same visual language.
 */
@Composable
private fun ModuleDetailScreen(module: CommonModule, onBack: () -> Unit) {
  Column(modifier = Modifier.fillMaxWidth()) {
    DetailHeader(
      icon = Icons.Default.DirectionsWalk,
      iconTint = NeonEmerald,
      title = module.title,
      subtitle = module.subtitle,
      onBack = onBack
    )
    Text(
      text = "Your safety comes first. Follow these instructions to reduce risk.",
      fontSize = 11.sp,
      color = TacticalOnSurfaceVariant,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
    )
    module.items.forEach { item ->
      InstructionDetailCard(item)
    }
  }
}

/** Full instruction card — critical items escalate to the red emergency look. */
@Composable
private fun InstructionDetailCard(item: InstructionItem) {
  Column(
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
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Box(
        modifier = Modifier
          .size(22.dp)
          .clip(CircleShape)
          .background(if (item.isCritical) EmergencyRed else TacticalCyanContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = if (item.isCritical) Icons.Default.CrisisAlert else Icons.Default.CheckCircle,
          contentDescription = null,
          tint = if (item.isCritical) Color.White else OnTacticalCyan,
          modifier = Modifier.size(13.dp)
        )
      }
      Text(
        text = item.title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = TacticalOnSurface,
        modifier = Modifier.weight(1f)
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

// ============================================================================
// EMERGENCY CONTACTS — dedicated detail screen with DIAL functionality
// ============================================================================

/**
 * Dedicated Emergency Contacts screen. Renders the EXISTING official lines
 * from the emergency_contacts common module; each phone-enabled entry gets a
 * real dial button (ACTION_DIAL) exactly like the Profile screen's quick-dial
 * tiles — nothing is transmitted without explicit user action.
 */
@Composable
private fun ContactsDetailScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val contactsModule = DisasterInstructions.commonModules.firstOrNull { it.id == "emergency_contacts" }
    ?: return

  fun dial(number: String) {
    try {
      context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${number.filter { it.isDigit() }}")))
    } catch (_: Exception) {
      // No dialer on this device — silently ignored, list stays informative.
    }
  }

  Column(modifier = Modifier.fillMaxWidth()) {
    DetailHeader(
      icon = Icons.Default.Call,
      iconTint = EmergencyRed,
      title = "Emergency Contacts",
      subtitle = "Official Indian emergency lines",
      onBack = onBack
    )
    contactsModule.items.forEach { item ->
      val number = item.title.takeWhile { it.isDigit() }
      val hasNumber = number.isNotEmpty()
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 4.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(if (item.isCritical) EmergencyRedContainer.copy(alpha = 0.3f) else ObsidianContainerLow)
          .border(
            1.dp,
            if (item.isCritical) EmergencyRed.copy(alpha = 0.5f) else TacticalOutlineVariant.copy(alpha = 0.35f),
            RoundedCornerShape(12.dp)
          )
          .clickable(enabled = hasNumber) { if (hasNumber) dial(number) }
          .padding(horizontal = 12.dp, vertical = 10.dp)
          .testTag("contact_card_${number.ifEmpty { "info" }}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (item.isCritical) EmergencyRed.copy(alpha = 0.15f) else TacticalCyan.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = if (hasNumber) Icons.Default.Call else Icons.Default.Emergency,
            contentDescription = null,
            tint = if (item.isCritical) EmergencyRedBright else TacticalCyan,
            modifier = Modifier.size(18.dp)
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          Text(item.detail, fontSize = 10.sp, color = TacticalOnSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (hasNumber) {
          Icon(
            imageVector = Icons.Default.Call,
            contentDescription = "Dial ${item.title}",
            tint = EmergencyRed,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

// ============================================================================
// EMERGENCY KIT — dedicated detail screen from the existing module data
// ============================================================================

/** Icon mapping for kit entries from the existing emergency_kit module. */
private fun kitIconFor(kitTitle: String): ImageVector = when {
  kitTitle.contains("Water", ignoreCase = true) -> Icons.Default.WaterDrop
  kitTitle.contains("food", ignoreCase = true) -> Icons.Default.Restaurant
  kitTitle.contains("First-aid", ignoreCase = true) -> Icons.Default.MedicalServices
  kitTitle.contains("Torch", ignoreCase = true) -> Icons.Default.FlashlightOn
  kitTitle.contains("Whistle", ignoreCase = true) -> Icons.Default.NotificationsActive
  kitTitle.contains("Documents", ignoreCase = true) -> Icons.Default.Description
  kitTitle.contains("Radio", ignoreCase = true) -> Icons.Default.NotificationsActive
  kitTitle.contains("Cash", ignoreCase = true) -> Icons.Default.AttachMoney
  else -> Icons.Default.Backpack
}

@Composable
private fun KitDetailScreen(onBack: () -> Unit) {
  val kitModule = DisasterInstructions.commonModules.firstOrNull { it.id == "emergency_kit" }
    ?: return

  Column(modifier = Modifier.fillMaxWidth()) {
    DetailHeader(
      icon = Icons.Default.Backpack,
      iconTint = NeonEmerald,
      title = "Emergency Kit",
      subtitle = "72-hour self-reliance pack",
      onBack = onBack
    )
    kitModule.items.forEach { item ->
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 4.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(ObsidianContainerLow)
          .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
          .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(NeonEmeraldContainer.copy(alpha = 0.2f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(kitIconFor(item.title), contentDescription = null, tint = NeonEmerald, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TacticalOnSurface)
          Text(item.detail, fontSize = 10.sp, color = TacticalOnSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

// ============================================================================
// EMERGENCY QUICK TRIGGER — permanently pinned above the bottom navigation
// on EVERY Instructions route. Light + SOS Siren stay fully functional.
// ============================================================================

@Composable
private fun EmergencyQuickTrigger(
  isFlashlightOn: Boolean,
  isSirenOn: Boolean,
  onToggleFlashlight: () -> Unit,
  onToggleSiren: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(EmergencyRed)
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon(
        imageVector = Icons.Default.Emergency,
        contentDescription = null,
        tint = Color.White,
        modifier = Modifier.size(18.dp)
      )
      Text(
        text = "EMERGENCY QUICK TRIGGER",
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = Color.White,
        letterSpacing = 0.6.sp
      )
    }
    Text(
      text = "Get help instantly, anytime",
      fontSize = 10.sp,
      color = Color.White.copy(alpha = 0.85f)
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      // LIGHT — large touch target, functional hardware torch toggle.
      Row(
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(10.dp))
          .background(if (isFlashlightOn) Color.White else Color.White.copy(alpha = 0.18f))
          .clickable { onToggleFlashlight() }
          .padding(horizontal = 12.dp, vertical = 10.dp)
          .testTag("emergency_flashlight_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.FlashlightOn,
          contentDescription = "Toggle flashlight",
          tint = if (isFlashlightOn) EmergencyRed else Color.White,
          modifier = Modifier.size(18.dp)
        )
        Text(
          text = if (isFlashlightOn) "LIGHT ON" else "LIGHT",
          fontSize = 12.sp,
          fontWeight = FontWeight.Black,
          color = if (isFlashlightOn) EmergencyRed else Color.White,
          letterSpacing = 0.4.sp
        )
      }
      // SOS SIREN — large touch target, functional siren toggle.
      Row(
        modifier = Modifier
          .weight(1f)
          .clip(RoundedCornerShape(10.dp))
          .background(if (isSirenOn) Color.Yellow else Color.White.copy(alpha = 0.18f))
          .clickable { onToggleSiren() }
          .padding(horizontal = 12.dp, vertical = 10.dp)
          .testTag("emergency_siren_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.VolumeUp,
          contentDescription = "Toggle SOS siren",
          tint = if (isSirenOn) Color.Black else Color.White,
          modifier = Modifier.size(18.dp)
        )
        Text(
          text = if (isSirenOn) "SIREN ON" else "SOS SIREN",
          fontSize = 12.sp,
          fontWeight = FontWeight.Black,
          color = if (isSirenOn) Color.Black else Color.White,
          letterSpacing = 0.4.sp
        )
      }
    }
  }
}
