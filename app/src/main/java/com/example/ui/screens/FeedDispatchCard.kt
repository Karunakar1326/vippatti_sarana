package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DomainAdd
import androidx.compose.material.icons.filled.Flood
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.NightShelter
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WrongLocation
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.disaster.DispatchIconType
import com.example.data.disaster.DispatchTagType
import com.example.data.disaster.FeedDispatch
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonEmeraldContainer
import com.example.ui.theme.ObsidianBright
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.OnEmergencyRedContainer
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalCyanContainer
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant

@Composable
internal fun FeedDispatchCard(
  dispatch: FeedDispatch,
  onActionClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 5.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(ObsidianContainer)
        .border(
          1.dp,
          if (dispatch.tagType == DispatchTagType.ROAD_CLOSED) EmergencyRed.copy(alpha = 0.4f) else TacticalOutlineVariant.copy(alpha = 0.3f),
          RoundedCornerShape(14.dp)
        )
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Top header row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          // Flex so long agency lines ellipsize instead of pushing the
          // severity tag badge off the card.
          modifier = Modifier.weight(1f)
        ) {
          val iconBg = when (dispatch.iconType) {
            DispatchIconType.RAIN -> TacticalCyanContainer.copy(alpha = 0.2f)
            DispatchIconType.SHELTER -> NeonEmerald.copy(alpha = 0.2f)
            DispatchIconType.FLOOD -> EmergencyRedContainer.copy(alpha = 0.3f)
            DispatchIconType.LOGISTICS -> ObsidianBright
          }
          val iconTint = when (dispatch.iconType) {
            DispatchIconType.RAIN -> TacticalCyan
            DispatchIconType.SHELTER -> NeonEmerald
            DispatchIconType.FLOOD -> EmergencyRedBright
            DispatchIconType.LOGISTICS -> TacticalOnSurface
          }

          Box(
            modifier = Modifier
              .size(30.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(iconBg),
            contentAlignment = Alignment.Center
          ) {
            val icon = when (dispatch.iconType) {
              DispatchIconType.RAIN -> Icons.Default.Umbrella
              DispatchIconType.SHELTER -> Icons.Default.NightShelter
              DispatchIconType.FLOOD -> Icons.Default.Flood
              DispatchIconType.LOGISTICS -> Icons.Default.DomainAdd
            }
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
          }

          Column {
            Text(
              text = dispatch.agency,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = iconTint
            )
            Text(
              text = dispatch.issuedTime,
              fontSize = 11.sp,
              color = TacticalOnSurfaceVariant
            )
          }
        }

        // Tag badge
        val (tagBg, tagTextColor, tagBorder) = when (dispatch.tagType) {
          DispatchTagType.HIGH_ALERT -> Triple(EmergencyRedContainer.copy(alpha = 0.3f), OnEmergencyRedContainer, EmergencyRed.copy(alpha = 0.3f))
          DispatchTagType.SHELTER_READY -> Triple(NeonEmeraldContainer.copy(alpha = 0.25f), NeonEmerald, NeonEmerald.copy(alpha = 0.3f))
          DispatchTagType.ROAD_CLOSED -> Triple(EmergencyRed, Color.White, EmergencyRed)
          DispatchTagType.CAPACITY_INFO -> Triple(TacticalCyanContainer.copy(alpha = 0.2f), TacticalCyan, TacticalCyan.copy(alpha = 0.3f))
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(tagBg)
            .border(1.dp, tagBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
          Text(
            text = dispatch.tag,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = tagTextColor
          )
        }
      }

      // Title and description
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = dispatch.title,
          fontSize = 15.sp,
          fontWeight = FontWeight.SemiBold,
          color = TacticalOnSurface,
          lineHeight = 20.sp
        )
        Text(
          text = dispatch.description,
          fontSize = 13.sp,
          color = TacticalOnSurfaceVariant,
          lineHeight = 17.sp
        )
      }

      // Bottom footer: Location & Action Button
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp)
          .border(
            width = 1.dp,
            color = TacticalOutlineVariant.copy(alpha = 0.2f),
            shape = RoundedCornerShape(0.dp)
          )
          .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.weight(1f)
        ) {
          val locIcon = if (dispatch.tagType == DispatchTagType.ROAD_CLOSED) Icons.Default.WrongLocation else Icons.Default.LocationOn
          val locColor = if (dispatch.tagType == DispatchTagType.ROAD_CLOSED) EmergencyRed else NeonEmerald
          Icon(
            imageVector = locIcon,
            contentDescription = null,
            tint = locColor,
            modifier = Modifier.size(15.dp)
          )
          Text(
            text = dispatch.location,
            fontSize = 11.sp,
            color = TacticalOnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
          modifier = Modifier
            .clickable { onActionClick() }
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .testTag("dispatch_action_${dispatch.id}")
        ) {
          Text(
            text = dispatch.actionLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = NeonEmerald
          )
          val actionIcon = when (dispatch.actionLabel) {
            "Detour Map" -> Icons.Default.Map
            "Get Directions" -> Icons.Default.NearMe
            else -> Icons.AutoMirrored.Filled.ArrowForward
          }
          Icon(
            imageVector = actionIcon,
            contentDescription = null,
            tint = NeonEmerald,
            modifier = Modifier.size(14.dp)
          )
        }
      }
    }
  }
}
