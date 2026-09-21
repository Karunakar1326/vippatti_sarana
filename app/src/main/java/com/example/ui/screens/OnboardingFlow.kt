package com.example.ui.screens

import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Stable page model for the five-step onboarding flow. Page bodies are
 * heterogeneous (each mirrors its reference screen), but navigation,
 * pagination, and completion all run off this single list — never off
 * duplicated per-page logic.
 */
internal enum class OnboardingStep {
  WELCOME,
  RISK,
  PREPARE,
  ALERTS,
  READY
}

/**
 * Completion-flag storage. The flag is written only when the user skips,
 * signs in, or finishes page 5 — never merely for changing pages. Uses the
 * same preferences file/key the app already used, so existing installs keep
 * their completed state.
 */
class OnboardingCompletion(private val prefs: SharedPreferences) {

  fun isCompleted(): Boolean = prefs.getBoolean(KEY_COMPLETED, false)

  fun setCompleted() {
    prefs.edit().putBoolean(KEY_COMPLETED, true).apply()
  }

  companion object {
    const val PREFS_NAME = "vippatti_onboarding"
    private const val KEY_COMPLETED = "completed"
  }
}

/**
 * Five-page onboarding flow. Page 1 reuses [OnboardingWelcomeScreen]
 * verbatim; pages 2–5 share one scaffold (top bar, scrollable body, pager,
 * primary action, hills). System back moves to the previous page and is
 * consumed on page 1 so it can neither close the app nor bypass onboarding.
 *
 * Copy follows the reference screens, adapted where the reference overclaims:
 * feeds are "available" (never guaranteed live), shelters are "nearby
 * information" (the registry is demo data), and no page promises delivery,
 * response, or protection.
 */
@Composable
fun OnboardingFlowScreen(
  onFinish: () -> Unit,
  modifier: Modifier = Modifier
) {
  val steps = OnboardingStep.entries
  var page by rememberSaveable { mutableIntStateOf(0) }
  BackHandler(enabled = true) {
    if (page > 0) page--
  }
  if (page == 0) {
    OnboardingWelcomeScreen(
      onGetStarted = { page = 1 },
      onSignIn = onFinish,
      onSkip = onFinish,
      modifier = modifier
    )
  } else {
    OnboardingFlowPage(
      step = steps[page],
      onBack = { page-- },
      onPrimary = { if (page == steps.lastIndex) onFinish() else page++ },
      onSkip = onFinish,
      modifier = modifier
    )
  }
}

@Composable
private fun OnboardingFlowPage(
  step: OnboardingStep,
  onBack: () -> Unit,
  onPrimary: () -> Unit,
  onSkip: () -> Unit,
  modifier: Modifier = Modifier
) {
  val last = step == OnboardingStep.entries.last()
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
  ) {
    OnboardingBackdrop(modifier = Modifier.fillMaxSize())
    Column(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
      ) {
        OnboardingFlowTopBar(
          step = step,
          showBack = !last,
          onBack = onBack,
          skipLabel = if (last) "Done" else "Skip",
          onSkip = onSkip
        )
        when (step) {
          OnboardingStep.WELCOME -> Unit // Rendered by OnboardingWelcomeScreen.
          OnboardingStep.RISK -> RiskPageBody()
          OnboardingStep.PREPARE -> PreparePageBody()
          OnboardingStep.ALERTS -> AlertsPageBody()
          OnboardingStep.READY -> ReadyPageBody()
        }
      }
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 28.dp, end = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        OnboardingPagerIndicator(
          pageCount = OnboardingStep.entries.size,
          currentPage = step.ordinal,
          modifier = Modifier.padding(bottom = 20.dp)
        )
        OnboardingPrimaryButton(
          label = if (last) "Get Started" else "Continue",
          onClick = onPrimary,
          modifier = Modifier.testTag("onboarding_primary")
        )
        if (last) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.Shield,
              contentDescription = null,
              tint = OnboardingBodyGray,
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = "Vippatti Sarana • Safer People. Stronger Communities.",
              fontSize = 11.sp,
              color = OnboardingBodyGray,
              textAlign = TextAlign.Center
            )
          }
        } else {
          // Keeps footer height stable so the primary button never jumps
          // between pages.
          Box(modifier = Modifier.height(26.dp))
        }
      }
      OnboardingHills(
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp)
      )
    }
  }
}

@Composable
private fun OnboardingFlowTopBar(
  step: OnboardingStep,
  showBack: Boolean,
  onBack: () -> Unit,
  skipLabel: String,
  onSkip: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 12.dp, start = 8.dp, end = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (showBack) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Go back",
        tint = OnboardingTitleBlack,
        modifier = Modifier
          .testTag("onboarding_back")
          .clickable(onClick = onBack)
          .padding(12.dp)
          .size(24.dp)
      )
    } else {
      // Placeholder keeps the pill centered, mirroring the reference layout.
      Box(modifier = Modifier.size(48.dp))
    }
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .clip(RoundedCornerShape(16.dp))
        .background(Color(0xFFF1F5F9))
        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
        .padding(horizontal = 14.dp, vertical = 4.dp)
        .testTag("onboarding_step_pill")
    ) {
      Text(
        text = "Step ${step.ordinal + 1} of ${OnboardingStep.entries.size}",
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = OnboardingSlate
      )
    }
    Text(
      text = skipLabel,
      fontSize = 14.sp,
      fontWeight = FontWeight.SemiBold,
      color = OnboardingTeal,
      modifier = Modifier
        .testTag("onboarding_skip")
        .clickable(onClick = onSkip)
        .padding(horizontal = 16.dp, vertical = 12.dp)
    )
  }
}

@Composable
private fun OnboardingPrimaryButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  androidx.compose.material3.Button(
    onClick = onClick,
    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
      containerColor = OnboardingTeal,
      contentColor = Color.White
    ),
    shape = RoundedCornerShape(28.dp),
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
  ) {
    Text(
      text = label,
      fontSize = 15.sp,
      fontWeight = FontWeight.SemiBold
    )
    Icon(
      imageVector = Icons.AutoMirrored.Filled.ArrowForward,
      contentDescription = null,
      modifier = Modifier.padding(start = 8.dp).size(18.dp)
    )
  }
}

// ---------------------------------------------------------------------------
// Page 2 — risk awareness.
// ---------------------------------------------------------------------------

@Composable
private fun RiskPageBody() {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 24.dp, end = 24.dp)
  ) {
    RiskMapHero(modifier = Modifier.fillMaxWidth().height(180.dp))
    Text(
      text = "Understand risk in your area",
      fontSize = 23.sp,
      fontWeight = FontWeight.ExtraBold,
      color = OnboardingNavy,
      lineHeight = 28.sp,
      modifier = Modifier.padding(top = 12.dp)
    )
    Text(
      text = "Explore available hazard updates, seasonal watches, and nearby " +
        "shelter information across India.",
      fontSize = 14.sp,
      color = OnboardingSlate,
      lineHeight = 20.sp,
      modifier = Modifier.padding(top = 8.dp)
    )
    Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 16.dp, bottom = 8.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(FlowNoticeBg)
        .border(1.dp, FlowNoticeBorder, RoundedCornerShape(16.dp))
        .padding(14.dp)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .background(Color.White)
          .border(1.dp, Color(0xFFCFFAFE), CircleShape)
      ) {
        Icon(
          imageVector = Icons.Filled.Lightbulb,
          contentDescription = null,
          tint = FlowInfoCyan,
          modifier = Modifier.size(16.dp)
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Connected sources, clearly labeled",
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          color = OnboardingTitleBlack
        )
        Text(
          text = "Earthquake, fire, weather-alert and news feeds report their " +
            "own status as Live, Recent, or Cached — so you always know how " +
            "fresh the picture is.",
          fontSize = 12.sp,
          color = OnboardingSlate,
          lineHeight = 16.sp,
          modifier = Modifier.padding(top = 4.dp)
        )
      }
    }
  }
}

/** Stylized coverage card: contours, radar rings, and a location pin. */
@Composable
private fun RiskMapHero(modifier: Modifier = Modifier) {
  androidx.compose.foundation.Canvas(modifier = modifier.clip(RoundedCornerShape(16.dp))) {
    drawRect(FlowHeroCardBg)
    val w = size.width
    val h = size.height
    // Contour curves.
    val contour = FlowContour
    drawPath(
      Path().apply {
        moveTo(-10f, h * 0.3f)
        quadraticTo(w * 0.3f, h * 0.05f, w * 0.55f, h * 0.35f)
        quadraticTo(w * 0.75f, h * 0.6f, w + 10f, h * 0.25f)
      },
      contour, style = Stroke(width = 3f)
    )
    drawPath(
      Path().apply {
        moveTo(-10f, h * 0.6f)
        quadraticTo(w * 0.35f, h * 0.5f, w * 0.6f, h * 0.75f)
        quadraticTo(w * 0.8f, h * 0.95f, w + 10f, h * 0.65f)
      },
      contour, style = Stroke(width = 3f)
    )
    drawPath(
      Path().apply {
        moveTo(-10f, h * 0.88f)
        quadraticTo(w * 0.4f, h * 0.78f, w * 0.7f, h * 0.98f)
        quadraticTo(w * 0.85f, h * 1.05f, w + 10f, h * 0.9f)
      },
      contour, style = Stroke(width = 3f)
    )
    // Radar rings around the pin.
    drawCircle(FlowRadarOuter, radius = h * 0.30f, center = center)
    drawCircle(FlowRadarInner, radius = h * 0.19f, center = center)
    // Location pin: teal disc, white ring, teal core.
    drawCircle(OnboardingTeal, radius = h * 0.10f, center = center)
    drawCircle(Color.White, radius = h * 0.055f, center = center)
    drawCircle(OnboardingTeal, radius = h * 0.022f, center = center)
  }
}

// ---------------------------------------------------------------------------
// Page 3 — preparedness.
// ---------------------------------------------------------------------------

@Composable
private fun PreparePageBody() {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 24.dp, end = 24.dp)
  ) {
    KitHero(modifier = Modifier.fillMaxWidth().height(170.dp))
    Text(
      text = "Prepare and respond with confidence",
      fontSize = 23.sp,
      fontWeight = FontWeight.ExtraBold,
      color = OnboardingNavy,
      lineHeight = 28.sp,
      modifier = Modifier.padding(top = 12.dp)
    )
    Text(
      text = "Get ready with essential resources and quick actions when it matters.",
      fontSize = 14.sp,
      color = OnboardingSlate,
      lineHeight = 20.sp,
      modifier = Modifier.padding(top = 6.dp)
    )
    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
    ) {
      PrepareCard(
        icon = Icons.Filled.MedicalServices,
        iconBg = FlowKitBlueBg,
        iconTint = FlowKitBlue,
        title = "72-Hour Emergency Kit",
        description = "Water, food, medicines, documents and more."
      )
      PrepareCard(
        icon = Icons.Filled.Call,
        iconBg = FlowKitBlueBg,
        iconTint = FlowKitBlue,
        title = "Emergency Helplines",
        description = "Quick access to 112, 108, 101 and other helplines."
      )
      PrepareCard(
        icon = Icons.Filled.MenuBook,
        iconBg = FlowGuideGreenBg,
        iconTint = FlowGuideGreen,
        title = "Survival Guide",
        description = "Offline guides, safety tips and step-by-step instructions."
      )
    }
  }
}

@Composable
private fun PrepareCard(
  icon: ImageVector,
  iconBg: Color,
  iconTint: Color,
  title: String,
  description: String
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(Color.White)
      .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(16.dp))
      .padding(12.dp)
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(44.dp)
        .clip(CircleShape)
        .background(iconBg)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(20.dp)
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = OnboardingNavy
      )
      Text(
        text = description,
        fontSize = 12.sp,
        color = OnboardingBodyGray,
        modifier = Modifier.padding(top = 2.dp)
      )
    }
  }
}

/** Simplified preparedness illustration: checklist, first-aid box, bottle. */
@Composable
private fun KitHero(modifier: Modifier = Modifier) {
  androidx.compose.foundation.Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    // Mint glow.
    drawOval(
      color = FlowGlow,
      topLeft = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.1f),
      size = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.8f)
    )
    val uw = w / 320f // Reference SVG units.
    // Checklist clipboard (right).
    drawRoundRect(
      color = Color.White,
      topLeft = androidx.compose.ui.geometry.Offset(200f * uw, 28f * uw),
      size = androidx.compose.ui.geometry.Size(94f * uw, 124f * uw),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f * uw)
    )
    repeat(3) { i ->
      val cy = (60f + i * 15f) * uw
      drawCircle(Color(0xFF10B981), radius = 5.5f * uw,
        center = androidx.compose.ui.geometry.Offset(180f * uw, cy))
      drawLine(
        color = Color.White,
        start = androidx.compose.ui.geometry.Offset(177.5f * uw, cy),
        end = androidx.compose.ui.geometry.Offset(179.2f * uw, cy + 1.7f * uw),
        strokeWidth = 1.5f * uw
      )
      drawLine(
        color = Color.White,
        start = androidx.compose.ui.geometry.Offset(179.2f * uw, cy + 1.7f * uw),
        end = androidx.compose.ui.geometry.Offset(182.8f * uw, cy - 1.7f * uw),
        strokeWidth = 1.5f * uw
      )
      drawRoundRect(
        color = Color(0xFFF1F5F9),
        topLeft = androidx.compose.ui.geometry.Offset(190f * uw, (cy - 2.5f * uw)),
        size = androidx.compose.ui.geometry.Size(48f * uw, 4.5f * uw),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * uw)
      )
    }
    // First-aid box (front center).
    drawRoundRect(
      color = Color(0xFFEF4444),
      topLeft = androidx.compose.ui.geometry.Offset(120f * uw, 106f * uw),
      size = androidx.compose.ui.geometry.Size(66f * uw, 46f * uw),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(9f * uw)
    )
    drawRoundRect(
      color = Color.White,
      topLeft = androidx.compose.ui.geometry.Offset(148f * uw, 122f * uw),
      size = androidx.compose.ui.geometry.Size(10f * uw, 24f * uw),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * uw)
    )
    drawRoundRect(
      color = Color.White,
      topLeft = androidx.compose.ui.geometry.Offset(141f * uw, 129f * uw),
      size = androidx.compose.ui.geometry.Size(24f * uw, 10f * uw),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * uw)
    )
    // Water bottle (left).
    drawRoundRect(
      color = Color(0xFF38BDF8),
      topLeft = androidx.compose.ui.geometry.Offset(66f * uw, 86f * uw),
      size = androidx.compose.ui.geometry.Size(18f * uw, 52f * uw),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(7f * uw)
    )
    drawRoundRect(
      color = Color(0xFF0284C7),
      topLeft = androidx.compose.ui.geometry.Offset(69f * uw, 80f * uw),
      size = androidx.compose.ui.geometry.Size(12f * uw, 7f * uw),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * uw)
    )
  }
}

// ---------------------------------------------------------------------------
// Page 4 — updates and offline resources.
// ---------------------------------------------------------------------------

@Composable
private fun AlertsPageBody() {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 24.dp, end = 24.dp)
  ) {
    PhoneHero(modifier = Modifier.fillMaxWidth().height(190.dp))
    Text(
      text = "Stay alert when seconds count",
      fontSize = 22.sp,
      fontWeight = FontWeight.Bold,
      color = OnboardingNavy,
      lineHeight = 27.sp,
      modifier = Modifier.padding(top = 12.dp)
    )
    Text(
      text = "Get important disaster updates and keep essential safety " +
        "resources available when you need them.",
      fontSize = 12.sp,
      color = OnboardingSlate,
      lineHeight = 17.sp,
      modifier = Modifier.padding(top = 6.dp)
    )
    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
    ) {
      AlertsInfoCard(
        icon = Icons.Filled.Notifications,
        iconBg = Color(0xFFD6F0EA),
        iconTint = OnboardingTeal,
        title = "Disaster Alert Updates",
        badge = "Stay informed",
        badgeBg = FlowBadgeGreenBg,
        badgeText = FlowBadgeGreenText,
        badgeBorder = Color(0xFFA7F3D0),
        description = "Available disaster warnings, regional updates, and " +
          "important safety information.",
        highlighted = true
      )
      AlertsInfoCard(
        icon = Icons.Filled.CloudOff,
        iconBg = Color(0xFFF0F9FF),
        iconTint = Color(0xFF0369A1),
        title = "Offline Safety Resources",
        badge = "Ready when offline",
        badgeBg = Color(0xFFF1F5F9),
        badgeText = Color(0xFF475569),
        badgeBorder = Color(0xFFE2E8F0),
        description = "Saved safety instructions, emergency contacts, and " +
          "available offline resources without internet.",
        highlighted = false
      )
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(20.dp)
          .clip(CircleShape)
          .background(Color(0xFFF1F5F9))
      ) {
        Icon(
          imageVector = Icons.Filled.FlashOn,
          contentDescription = null,
          tint = OnboardingSlate,
          modifier = Modifier.size(14.dp)
        )
      }
      Text(
        text = "Battery-friendly • Designed to help you prepare",
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = OnboardingSlate
      )
    }
  }
}

@Composable
private fun AlertsInfoCard(
  icon: ImageVector,
  iconBg: Color,
  iconTint: Color,
  title: String,
  badge: String,
  badgeBg: Color,
  badgeText: Color,
  badgeBorder: Color,
  description: String,
  highlighted: Boolean
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(if (highlighted) FlowMintCardBg else Color.White)
      .border(
        1.dp,
        if (highlighted) FlowMintCardBorder else Color(0xFFE2E8F0),
        RoundedCornerShape(16.dp)
      )
      .padding(14.dp)
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(iconBg)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(20.dp)
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = title,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          color = OnboardingNavy
        )
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(badgeBg)
            .border(1.dp, badgeBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
          Text(
            text = badge,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = badgeText
          )
        }
      }
      Text(
        text = description,
        fontSize = 11.sp,
        color = OnboardingSlate,
        lineHeight = 15.sp,
        modifier = Modifier.padding(top = 4.dp)
      )
    }
  }
}

/** Tilted phone mockup with a contour map, pin, and control card. */
@Composable
private fun PhoneHero(modifier: Modifier = Modifier) {
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Box(
      modifier = Modifier
        .fillMaxSize(0.72f)
        .graphicsLayer { rotationZ = 8f }
        .clip(RoundedCornerShape(28.dp))
        .background(Color.White)
        .border(2.dp, Color(0xFFE2E8F0), RoundedCornerShape(28.dp))
        .padding(8.dp)
    ) {
      androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))) {
        drawRect(FlowPhoneScreenBg)
        val w = size.width
        val h = size.height
        val contour = FlowContour
        drawPath(
          Path().apply {
            moveTo(-10f, h * 0.2f)
            quadraticTo(w * 0.4f, h * 0.05f, w * 0.7f, h * 0.3f)
            quadraticTo(w * 0.9f, h * 0.5f, w + 10f, h * 0.25f)
          },
          contour, style = Stroke(width = 3f)
        )
        drawPath(
          Path().apply {
            moveTo(-10f, h * 0.6f)
            quadraticTo(w * 0.4f, h * 0.5f, w * 0.65f, h * 0.75f)
            quadraticTo(w * 0.85f, h * 0.95f, w + 10f, h * 0.7f)
          },
          contour, style = Stroke(width = 3f)
        )
        drawCircle(FlowRadarOuter, radius = h * 0.26f, center = center)
        drawCircle(FlowRadarInner, radius = h * 0.17f, center = center)
        drawCircle(OnboardingTeal, radius = h * 0.09f, center = center)
        drawCircle(Color.White, radius = h * 0.05f, center = center)
        drawCircle(OnboardingTeal, radius = h * 0.02f, center = center)
      }
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .clip(RoundedCornerShape(16.dp))
        .background(Color.White)
        .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(16.dp))
        .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .size(28.dp)
          .clip(CircleShape)
          .background(FlowGuideGreenBg)
          .border(1.dp, Color(0xFFA7F3D0), CircleShape)
      ) {
        Icon(
          imageVector = Icons.Filled.Shield,
          contentDescription = null,
          tint = FlowGuideGreen,
          modifier = Modifier.size(16.dp)
        )
      }
      Column {
        Text(
          text = "You're in control",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = OnboardingNavy
        )
        Text(
          text = "Change anytime",
          fontSize = 10.sp,
          fontWeight = FontWeight.Medium,
          color = OnboardingSlate
        )
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Page 5 — ready.
// ---------------------------------------------------------------------------

@Composable
private fun ReadyPageBody() {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 24.dp, end = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    SunriseHero(modifier = Modifier.fillMaxWidth().height(170.dp))
    Text(
      text = "Setup Complete",
      fontSize = 12.sp,
      fontWeight = FontWeight.SemiBold,
      color = OnboardingTeal,
      modifier = Modifier.padding(top = 12.dp)
    )
    Text(
      text = "You're ready to explore.",
      fontSize = 26.sp,
      fontWeight = FontWeight.Bold,
      color = OnboardingTitleBlack,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(top = 4.dp)
    )
    Text(
      text = "Setup is complete. Explore available maps, alerts, guides, and " +
        "safety tools to stay informed and prepared.",
      fontSize = 14.sp,
      color = OnboardingSlate,
      textAlign = TextAlign.Center,
      lineHeight = 20.sp,
      modifier = Modifier.padding(top = 8.dp)
    )
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 14.dp, bottom = 8.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(Color.White)
        .border(1.dp, Color(0xFFF1F5F9), RoundedCornerShape(16.dp))
        .padding(16.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 12.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.TaskAlt,
          contentDescription = null,
          tint = OnboardingTeal,
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = "Your setup summary",
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          color = OnboardingTitleBlack
        )
      }
      ReadySummaryRow(
        icon = Icons.Filled.LocationOn,
        title = "Location preference",
        subtitle = "Approximate area (district-level)"
      )
      ReadySummaryRow(
        icon = Icons.Filled.MenuBook,
        title = "Guides and resources",
        subtitle = "Ready to explore"
      )
      ReadySummaryRow(
        icon = Icons.Filled.Tune,
        title = "Preferences",
        subtitle = "Stored only on this device"
      )
    }
  }
}

@Composable
private fun ReadySummaryRow(
  icon: ImageVector,
  title: String,
  subtitle: String
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 6.dp)
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(Color(0xFFEAEDFF))
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = OnboardingTeal,
        modifier = Modifier.size(18.dp)
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = OnboardingTitleBlack
      )
      Text(
        text = subtitle,
        fontSize = 12.sp,
        color = OnboardingBodyGray
      )
    }
    Icon(
      imageVector = Icons.Filled.CheckCircle,
      contentDescription = null,
      tint = OnboardingTeal,
      modifier = Modifier.size(18.dp)
    )
  }
}

/** Sunrise card: pale sky, sun, layered hills, shield with check. */
@Composable
private fun SunriseHero(modifier: Modifier = Modifier) {
  androidx.compose.foundation.Canvas(modifier = modifier.clip(RoundedCornerShape(16.dp))) {
    val w = size.width
    val h = size.height
    drawRect(
      brush = androidx.compose.ui.graphics.Brush.verticalGradient(
        0f to Color.White,
        0.55f to Color(0xFFF0FDFA),
        1f to Color(0xFFD7EFE9)
      )
    )
    // Sun.
    drawCircle(Color(0xFFFFE3A3), radius = h * 0.22f, center = center.copy(y = h * 0.42f))
    drawCircle(Color(0xFFFFD98A), radius = h * 0.15f, center = center.copy(y = h * 0.42f))
    // Hills.
    drawPath(
      Path().apply {
        moveTo(0f, h * 0.72f)
        quadraticTo(w * 0.25f, h * 0.5f, w * 0.5f, h * 0.7f)
        quadraticTo(w * 0.75f, h * 0.9f, w, h * 0.62f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
      },
      Color(0xFF6FBFA8)
    )
    drawPath(
      Path().apply {
        moveTo(0f, h * 0.85f)
        quadraticTo(w * 0.3f, h * 0.7f, w * 0.55f, h * 0.88f)
        quadraticTo(w * 0.8f, h * 1.02f, w, h * 0.8f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
      },
      Color(0xFF0A5C53)
    )
    // Shield with check.
    val cx = w / 2f
    val cy = h * 0.40f
    val s = h * 0.16f
    drawPath(
      Path().apply {
        moveTo(cx, cy - s)
        lineTo(cx + s * 0.85f, cy - s * 0.55f)
        lineTo(cx + s * 0.85f, cy + s * 0.2f)
        quadraticTo(cx + s * 0.85f, cy + s * 0.8f, cx, cy + s * 1.1f)
        quadraticTo(cx - s * 0.85f, cy + s * 0.8f, cx - s * 0.85f, cy + s * 0.2f)
        lineTo(cx - s * 0.85f, cy - s * 0.55f)
        close()
      },
      OnboardingNavy
    )
    val c = center.copy(y = cy + s * 0.1f)
    val r = s * 0.38f
    drawLine(
      color = Color.White,
      start = c.copy(x = c.x - r, y = c.y),
      end = c.copy(x = c.x - r * 0.25f, y = c.y + r * 0.7f),
      strokeWidth = s * 0.14f
    )
    drawLine(
      color = Color.White,
      start = c.copy(x = c.x - r * 0.25f, y = c.y + r * 0.7f),
      end = c.copy(x = c.x + r, y = c.y - r * 0.6f),
      strokeWidth = s * 0.14f
    )
  }
}

// Flow-local tints drawn from the reference screens.
private val FlowNoticeBg = Color(0xFFEAF7FA)
private val FlowNoticeBorder = Color(0xFFD5EEF3)
private val FlowInfoCyan = Color(0xFF0E7490)
private val FlowHeroCardBg = Color(0xFFEFFAF6)
private val FlowContour = Color(0xFFA7F3D0)
private val FlowRadarOuter = Color(0x40A7F3D0)
private val FlowRadarInner = Color(0x595CF3D0)
private val FlowGlow = Color(0x99CCFBF1)
private val FlowKitBlueBg = Color(0xFFE0F2FE)
private val FlowKitBlue = Color(0xFF0284C7)
private val FlowGuideGreenBg = Color(0xFFD1FAE5)
private val FlowGuideGreen = Color(0xFF059669)
private val FlowMintCardBg = Color(0xFFF0FAF8)
private val FlowMintCardBorder = Color(0xFF8AD1C6)
private val FlowBadgeGreenBg = Color(0xFFE6F7EF)
private val FlowBadgeGreenText = Color(0xFF1F8A53)
private val FlowPhoneScreenBg = Color(0xFFF4F9F8)
