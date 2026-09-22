package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * First onboarding page (welcome) for Vippatti Sarana.
 *
 * Follows the supplied reference design: soft sky background with pine
 * silhouettes, the disaster-management emblem as hero, three feature rows,
 * a 5-dot position indicator, and a bottom action area over rolling hills.
 * The page is theme-independent (fixed light artwork) and fully scrollable,
 * so compact screens never clip content and the hills never cover actions.
 *
 * Wording states real capabilities only: hazard updates/advisories that the
 * app actually surfaces, preparedness guidance, and emergency resources.
 * No live-protection or auto-response claims are made anywhere here.
 */
@Composable
fun OnboardingWelcomeScreen(
  onGetStarted: () -> Unit,
  onSignIn: () -> Unit,
  onSkip: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.White)
  ) {
    OnboardingBackdrop(modifier = Modifier.fillMaxSize())
    // Scrollable hero content on top; pagination, actions and hills are pinned
    // below it, so compact screens scroll the content while the actions and
    // the decorative hills stay visible and never overlap each other.
    Column(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState())
      ) {
        // Top action.
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, end = 12.dp),
          horizontalArrangement = Arrangement.End
        ) {
          Text(
            text = "Skip",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnboardingTeal,
            modifier = Modifier
              .testTag("onboarding_skip")
              .clickable(onClick = onSkip)
              .padding(horizontal = 16.dp, vertical = 12.dp)
          )
        }

        // Hero emblem.
        Box(
          modifier = Modifier.fillMaxWidth(),
          contentAlignment = Alignment.Center
        ) {
          Image(
            painter = painterResource(id = R.drawable.onboarding_hero_logo),
            contentDescription = "Vippatti Sarana emblem",
            contentScale = ContentScale.Fit,
            modifier = Modifier
              .size(190.dp)
              .shadow(8.dp, CircleShape)
              .clip(CircleShape)
              .background(Color.White)
              .testTag("onboarding_hero_logo")
          )
        }

        // Headings.
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 24.dp, end = 24.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "SAFER PEOPLE. STRONGER COMMUNITIES.",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = OnboardingTeal,
            textAlign = TextAlign.Center
          )
          Text(
            text = "Vippatti Sarana",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = OnboardingNavy,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
          )
          Text(
            text = "Disaster preparedness for a safer, more resilient India.",
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = OnboardingSlate,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = 8.dp)
          )
        }

      // Feature list. Bottom breathing room keeps the last row clear of the
      // pinned footer seam on compact screens.
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 16.dp, start = 28.dp, end = 28.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
          OnboardingFeatureRow(
            icon = Icons.Filled.VerifiedUser,
            title = "Reliable information",
            description = "Explore available hazard updates, advisories, and safety resources."
          )
          OnboardingFeatureRow(
            icon = Icons.Filled.Group,
            title = "Be prepared",
            description = "Learn essential safety practices, plan ahead, and access emergency resources."
          )
          OnboardingFeatureRow(
            icon = Icons.Filled.Eco,
            title = "A safer tomorrow",
            description = "Build preparedness and support informed decisions for individuals and communities."
          )
        }
      }

      // Pagination + actions (pinned above the hills, always reachable).
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 28.dp, end = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        OnboardingPagerIndicator(
          pageCount = 5,
          currentPage = 0,
          modifier = Modifier.padding(bottom = 20.dp)
        )
        androidx.compose.material3.Button(
          onClick = onGetStarted,
          colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = OnboardingTeal,
            contentColor = Color.White
          ),
          shape = RoundedCornerShape(28.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("onboarding_get_started")
        ) {
          Text(
            text = "Get Started",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
          )
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.padding(start = 8.dp).size(18.dp)
          )
        }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Already have an account? ",
            fontSize = 13.sp,
            color = OnboardingBodyGray
          )
          Text(
            text = "Sign In",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnboardingTeal,
            modifier = Modifier
              .testTag("onboarding_sign_in")
              .clickable(onClick = onSignIn)
              .padding(vertical = 12.dp)
          )
        }
      }

      // Decorative hills, always below the actions, never overlapping them.
      OnboardingHills(
        modifier = Modifier
          .fillMaxWidth()
          .height(56.dp)
      )
    }
  }
}

@Composable
internal fun OnboardingFeatureRow(
  icon: ImageVector,
  title: String,
  description: String
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(14.dp),
    verticalAlignment = Alignment.Top
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
        .background(OnboardingIconBg)
        .border(1.dp, OnboardingIconBorder, CircleShape)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = OnboardingTeal,
        modifier = Modifier.size(20.dp)
      )
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = OnboardingTitleBlack,
        lineHeight = 18.sp
      )
      Text(
        text = description,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        color = OnboardingBodyGray,
        lineHeight = 16.sp,
        modifier = Modifier.padding(top = 2.dp)
      )
    }
  }
}

@Composable
internal fun OnboardingPagerIndicator(
  pageCount: Int,
  currentPage: Int,
  modifier: Modifier = Modifier
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier
      .testTag("onboarding_pager")
      .semantics { contentDescription = "Step ${currentPage + 1} of $pageCount" }
  ) {
    repeat(pageCount) { index ->
      val active = index == currentPage
      Box(
        modifier = Modifier
          .then(
            if (active) {
              // Elongated active pill per the reference design system.
              Modifier.size(width = 20.dp, height = 8.dp)
            } else {
              Modifier.size(8.dp)
            }
          )
          .clip(CircleShape)
          .background(if (active) OnboardingTeal else OnboardingDotInactive)
      )
    }
  }
}

/** Sky glow, pine silhouettes, and bottom rolling hills (all decorative). */
@Composable
internal fun OnboardingBackdrop(modifier: Modifier = Modifier) {
  androidx.compose.foundation.Canvas(modifier = modifier) {
    // Soft mint atmosphere around the hero band.
    drawCircle(
      brush = Brush.radialGradient(
        0.0f to OnboardingGlow,
        0.55f to OnboardingGlowSoft,
        1.0f to Color.Transparent,
        center = center.copy(y = size.height * 0.22f),
        radius = size.width * 0.75f
      ),
      radius = size.width * 0.75f,
      center = center.copy(y = size.height * 0.22f)
    )
    // Pine silhouettes left and right of the hero.
    val pine = OnboardingPine
    val baseY = size.height * 0.30f
    val peakH = size.height * 0.09f
    val left = Path().apply {
      val w = size.width
      moveTo(0f, baseY)
      lineTo(w * 0.03f, baseY - peakH * 0.55f)
      lineTo(w * 0.055f, baseY - peakH * 0.2f)
      lineTo(w * 0.085f, baseY - peakH * 0.8f)
      lineTo(w * 0.11f, baseY - peakH * 0.35f)
      lineTo(w * 0.135f, baseY - peakH)
      lineTo(w * 0.165f, baseY)
      close()
    }
    drawPath(left, pine)
    val right = Path().apply {
      val w = size.width
      moveTo(w, baseY)
      lineTo(w * 0.97f, baseY - peakH * 0.55f)
      lineTo(w * 0.945f, baseY - peakH * 0.2f)
      lineTo(w * 0.915f, baseY - peakH * 0.8f)
      lineTo(w * 0.89f, baseY - peakH * 0.35f)
      lineTo(w * 0.865f, baseY - peakH)
      lineTo(w * 0.835f, baseY)
      close()
    }
    drawPath(right, pine)
  }
}

/** Rolling hills pinned to the bottom edge (drawn in-flow, below actions). */
@Composable
internal fun OnboardingHills(modifier: Modifier = Modifier) {
  androidx.compose.foundation.Canvas(modifier = modifier) {
    val w = size.width
    val h = size.height
    val rear = Path().apply {
      moveTo(0f, h * 0.75f)
      quadraticTo(w * 0.13f, h * 0.3f, w * 0.26f, h * 0.58f)
      quadraticTo(w * 0.39f, h * 0.82f, w * 0.53f, h * 0.37f)
      quadraticTo(w * 0.66f, h * 0.1f, w * 0.79f, h * 0.63f)
      quadraticTo(w * 0.9f, h * 0.85f, w, h * 0.47f)
      lineTo(w, h)
      lineTo(0f, h)
      close()
    }
    drawPath(rear, OnboardingHillRear)
    val front = Path().apply {
      moveTo(0f, h * 0.83f)
      cubicTo(w * 0.08f, h * 0.67f, w * 0.16f, h * 0.87f, w * 0.28f, h * 0.63f)
      cubicTo(w * 0.36f, h * 0.47f, w * 0.42f, h * 0.75f, w * 0.55f, h * 0.53f)
      cubicTo(w * 0.66f, h * 0.37f, w * 0.72f, h * 0.67f, w * 0.84f, h * 0.47f)
      cubicTo(w * 0.92f, h * 0.33f, w * 0.96f, h * 0.55f, w, h * 0.4f)
      lineTo(w, h)
      lineTo(0f, h)
      close()
    }
    drawPath(front, OnboardingHillFront)
  }
}

// Reference-faithful fixed palette for the light onboarding artwork.
internal val OnboardingTeal = Color(0xFF0A5C53)
internal val OnboardingNavy = Color(0xFF0F2438)
internal val OnboardingSlate = Color(0xFF4B5563)
internal val OnboardingTitleBlack = Color(0xFF111827)
internal val OnboardingBodyGray = Color(0xFF6B7280)
internal val OnboardingIconBg = Color(0xFFF0FDFA)
internal val OnboardingIconBorder = Color(0xFFCCFBF1)
internal val OnboardingDotInactive = Color(0xFFCBD5E1)
internal val OnboardingGlow = Color(0x140AB8A6)
internal val OnboardingGlowSoft = Color(0xCCF0FDF9)
internal val OnboardingPine = Color(0x33115E59)
internal val OnboardingHillRear = Color(0x590D5248)
internal val OnboardingHillFront = Color(0xB3083E37)
