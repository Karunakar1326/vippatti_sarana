package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DomainAdd
import androidx.compose.material.icons.filled.Flood
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.NightShelter
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WrongLocation
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.example.data.news.NewsPresentation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.DispatchIconType
import com.example.data.DispatchTagType
import com.example.data.FeedDispatch
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedBright
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonEmeraldContainer
import com.example.ui.theme.ObsidianBright
import com.example.ui.theme.ObsidianContainer
import com.example.ui.theme.ObsidianContainerHigh
import com.example.ui.theme.ObsidianContainerHighest
import com.example.ui.theme.ObsidianContainerLow
import com.example.ui.theme.ObsidianContainerLowest
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OnEmergencyRedContainer
import com.example.ui.theme.OnNeonEmerald
import com.example.ui.theme.OnNeonEmeraldContainer
import com.example.ui.theme.TacticalCyan
import com.example.ui.theme.TacticalCyanContainer
import com.example.ui.theme.TacticalOnSurface
import com.example.ui.theme.TacticalOnSurfaceVariant
import com.example.ui.theme.TacticalOutlineVariant
import com.example.viewmodel.ScreenTab
import com.example.viewmodel.VippattiUiState

@Composable
fun DispatchesScreen(
  uiState: VippattiUiState,
  onSync: () -> Unit,
  onToggleAudio: () -> Unit,
  onSelectCategory: (String) -> Unit,
  onNavigateToEvacRoute: () -> Unit,
  onNavigateTab: (ScreenTab) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  val syncRotation by animateFloatAsState(
    targetValue = if (uiState.isSyncing) 360f else 0f,
    animationSpec = tween(durationMillis = 800),
    label = "sync_rotation"
  )

  val infiniteTransition = rememberInfiniteTransition(label = "pulse_live")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 0.8f,
    targetValue = 1.3f,
    animationSpec = infiniteRepeatable(
      animation = tween(1000, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "live_ping"
  )

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(ObsidianSurface)
      .padding(bottom = 8.dp)
  ) {
    // 1. Header App Bar
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
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          // Flex so the title block wraps/shrinks safely and the refresh
          // button stays fully on screen at any width.
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(ObsidianContainerHigh),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Outlined.Shield,
              contentDescription = "Intelligence Shield",
              tint = NeonEmerald,
              modifier = Modifier.size(22.dp)
            )
          }
          Column {
            Text(
              text = "Disaster & Weather Intelligence",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = TacticalOnSurface,
              lineHeight = 18.sp
            )
            Text(
              text = "VIPPATTI SARANA • EMERGENCY OPS",
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              color = TacticalOnSurfaceVariant,
              letterSpacing = 0.8.sp
            )
          }
        }

        IconButton(
          onClick = onSync,
          modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ObsidianContainer)
            .testTag("refresh_feed_button")
        ) {
          Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = "Refresh Feed",
            tint = TacticalOnSurface,
            modifier = Modifier
              .size(20.dp)
              .rotate(syncRotation)
          )
        }
      }
    }

    // 2. Offline Cached Mode Notification Banner
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 8.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianContainerLow)
            .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
          ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(12.dp)) {
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .scale(pulseScale)
                  .background(NeonEmerald.copy(alpha = 0.4f), CircleShape)
              )
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .background(NeonEmerald, CircleShape)
              )
            }

            Column {
              Text(
                text = "OFFLINE CACHED MODE ACTIVE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = NeonEmerald,
                letterSpacing = 0.8.sp
              )
              Text(
                text = uiState.lastSyncTime,
                fontSize = 12.sp,
                color = TacticalOnSurfaceVariant,
                maxLines = 1
              )
              Text(
                text = "GNews free plan: articles appear up to 12h after publication • not official alerts",
                fontSize = 10.sp,
                color = TacticalOnSurfaceVariant
              )
            }
          }

          Button(
            onClick = onSync,
            colors = ButtonDefaults.buttonColors(
              containerColor = NeonEmeraldContainer,
              contentColor = OnNeonEmeraldContainer
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier
              .height(30.dp)
              .testTag("sync_banner_button")
          ) {
            Text(
              text = if (uiState.isSyncing) "Syncing..." else "Sync",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }

    // 3. Urgent Audio Bulletin Service Pill
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 4.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianContainerHigh)
            .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(10.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
          ) {
            Box(
              modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TacticalCyanContainer.copy(alpha = 0.25f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Campaign,
                contentDescription = null,
                tint = TacticalCyan,
                modifier = Modifier.size(20.dp)
              )
            }

            Column {
              Text(
                text = "Audio Bulletin Service",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TacticalOnSurface
              )
              Text(
                text = "Low-bandwidth speech playback during blackouts",
                fontSize = 11.sp,
                color = TacticalOnSurfaceVariant
              )
            }
          }

          Button(
            onClick = onToggleAudio,
            colors = ButtonDefaults.buttonColors(
              containerColor = if (uiState.isAudioPlaying) NeonEmerald else ObsidianBright,
              contentColor = if (uiState.isAudioPlaying) OnNeonEmerald else NeonEmerald
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier
              .height(32.dp)
              .testTag("audio_bulletin_button")
          ) {
            Icon(
              imageVector = if (uiState.isAudioPlaying) Icons.Default.PauseCircle else Icons.Default.VolumeUp,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (uiState.isAudioPlaying) "Playing ${uiState.audioPlaybackSeconds}s" else "Listen",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }
    }

    // 4. Horizontal Filter Chips
    item {
      val categories = listOf("All", "Severe Alerts", "Weather Radar", "Shelter Updates", "Government Bulletins")
      LazyRow(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)
      ) {
        items(categories) { cat ->
          val isSelected = uiState.selectedNewsCategory == cat
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(if (isSelected) NeonEmerald else ObsidianContainer)
              .border(
                1.dp,
                if (isSelected) NeonEmerald else TacticalOutlineVariant.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
              )
              .clickable { onSelectCategory(cat) }
              .padding(horizontal = 14.dp, vertical = 7.dp)
              .testTag("filter_chip_${cat.replace(" ", "_").lowercase()}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            if (cat == "Severe Alerts") {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .background(EmergencyRed, CircleShape)
              )
            }
            Text(
              text = cat,
              fontSize = 12.sp,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
              color = if (isSelected) OnNeonEmerald else TacticalOnSurface
            )
          }
        }
      }
    }

    // 5. Severe-Alert Hero Card — the top REAL GNews article (never fabricated)
    item {
      val hero = uiState.newsHero
      val now = System.currentTimeMillis()
      if (hero != null) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(ObsidianContainerLow)
              .border(2.dp, EmergencyRed.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
          ) {
            // Banner Image with Overlays
            // Width-proportional hero height (≈0.53 of card width — the original
            // 176dp on a 360dp phone) so it scales down on small phones and
            // grows sensibly on large ones, instead of a fixed 176dp.
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 0.53f)
                .background(ObsidianContainerHighest)
            ) {
              if (hero.imageUrl != null) {
                AsyncImage(
                  model = hero.imageUrl,
                  contentDescription = "Disaster news article image",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
              } else {
                // Honest placeholder: the article has no image — never fake one.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = null,
                    tint = TacticalCyan,
                    modifier = Modifier.size(42.dp)
                  )
                }
              }

              // Bottom gradient scrim
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .background(
                    Brush.verticalGradient(
                      colors = listOf(
                        Color.Transparent,
                        ObsidianContainerLow.copy(alpha = 0.5f),
                        ObsidianContainerLow
                      )
                    )
                  )
              )

              // Honest source badge — a news article is NOT an official alert.
              Row(
                modifier = Modifier
                  .align(Alignment.TopStart)
                  .padding(12.dp)
                  .clip(CircleShape)
                  .background(EmergencyRedContainer.copy(alpha = 0.9f))
                  .border(1.dp, EmergencyRed.copy(alpha = 0.4f), CircleShape)
                  .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Warning,
                  contentDescription = null,
                  tint = EmergencyRedBright,
                  modifier = Modifier.size(14.dp)
                )
                Text(
                  text = NewsPresentation.HERO_BADGE,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = OnEmergencyRedContainer,
                  letterSpacing = 0.6.sp
                )
              }

              // Real publication age
              Box(
                modifier = Modifier
                  .align(Alignment.TopEnd)
                  .padding(12.dp)
                  .clip(CircleShape)
                  .background(ObsidianContainerLowest.copy(alpha = 0.8f))
                  .padding(horizontal = 8.dp, vertical = 3.dp)
              ) {
                Text(
                  text = NewsPresentation.relativeAge(hero.publishedAtMillis, now),
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Medium,
                  color = TacticalOnSurfaceVariant
                )
              }
            }

            // Article Text Content
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(EmergencyRed)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text(
                    text = hero.category.displayTag.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                  )
                }
                Text(
                  text = "${hero.scope.label} • ${hero.sourceName}",
                  fontSize = 12.sp,
                  color = TacticalOnSurfaceVariant
                )
              }

              Text(
                text = hero.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TacticalOnSurface,
                lineHeight = 22.sp
              )

              Text(
                text = NewsPresentation.articleSummary(hero),
                fontSize = 13.sp,
                color = TacticalOnSurfaceVariant,
                lineHeight = 18.sp
              )

              // Action row — real publisher attribution (no fake verification badge)
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 4.dp)
                  .border(
                    width = 1.dp,
                    color = TacticalOutlineVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(0.dp)
                  )
                  .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp),
                  // Flex so long publisher names ellipsize instead of pushing
                  // the evacuation button off screen.
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = null,
                    tint = NeonEmerald,
                    modifier = Modifier.size(16.dp)
                  )
                  Text(
                    text = "Reported by ${hero.sourceName}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonEmerald,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }

                Button(
                  onClick = onNavigateToEvacRoute,
                  colors = ButtonDefaults.buttonColors(
                    containerColor = NeonEmeraldContainer,
                    contentColor = OnNeonEmeraldContainer
                  ),
                  shape = RoundedCornerShape(8.dp),
                  contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                  modifier = Modifier
                    .height(34.dp)
                    .testTag("evacuation_routes_hero_button")
                ) {
                  Text(
                    text = "Evacuation Routes",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Icon(
                    imageVector = Icons.Default.NearMe,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                  )
                }
              }

              // Open the real publisher story in the browser
              Button(
                onClick = {
                  context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(hero.url)))
                },
                colors = ButtonDefaults.buttonColors(
                  containerColor = ObsidianBright,
                  contentColor = NeonEmerald
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(34.dp)
                  .testTag("hero_read_full_story_button")
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Read Full Story at ${hero.sourceName}",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1
                )
              }
            }
          }
        }
      } else {
        // Honest empty / loading / error hero — never a fabricated alert
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(16.dp))
              .background(ObsidianContainerLow)
              .border(2.dp, TacticalOutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(
              imageVector = Icons.Default.Campaign,
              contentDescription = null,
              tint = TacticalCyan,
              modifier = Modifier.size(36.dp)
            )
            Text(
              text = when {
                uiState.isSyncing -> "Fetching live disaster news from GNews…"
                uiState.newsError != null -> uiState.newsError.userMessage
                else -> "No severe disaster news loaded yet — tap Sync to pull live articles from GNews (Idukki → Kerala → India)"
              },
              fontSize = 13.sp,
              color = TacticalOnSurfaceVariant,
              lineHeight = 18.sp,
              textAlign = TextAlign.Center
            )
            Button(
              onClick = onSync,
              enabled = !uiState.isSyncing,
              colors = ButtonDefaults.buttonColors(
                containerColor = NeonEmeraldContainer,
                contentColor = OnNeonEmeraldContainer
              ),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier
                .height(34.dp)
                .testTag("hero_sync_now_button")
            ) {
              Text(
                text = if (uiState.isSyncing) "Syncing…" else "Sync Now",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }
    }

    // 6. Live Feed Dispatches Section Title
    item {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .background(NeonEmerald, CircleShape)
          )
          Text(
            text = "LIVE FEED DISPATCHES",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = TacticalOnSurface,
            letterSpacing = 0.6.sp
          )
        }
        Text(
          text = if (uiState.isSyncing) "Syncing…" else "LIVE VIA GNEWS",
          fontSize = 11.sp,
          color = TacticalOnSurfaceVariant
        )
      }
    }

    // 7. Feed Cards — REAL GNews articles mapped to dispatch cards
    val visibleArticles = if (uiState.selectedNewsCategory == "All") {
      uiState.newsArticles
    } else {
      uiState.newsArticles.filter { article ->
        NewsPresentation.matchesCategory(article.category, uiState.selectedNewsCategory)
      }
    }
    val filteredDispatches = NewsPresentation.toFeedDispatches(visibleArticles, System.currentTimeMillis())

    if (filteredDispatches.isEmpty()) {
      item {
        // Honest empty state — no fabricated filler dispatches
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(ObsidianContainer)
              .border(1.dp, TacticalOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = when {
                uiState.isSyncing -> "Fetching live disaster news…"
                uiState.selectedNewsCategory != "All" ->
                  "No articles match \"${uiState.selectedNewsCategory}\" right now — switch to All or tap Sync."
                uiState.newsError != null -> uiState.newsError.userMessage
                uiState.newsEverLoaded -> "Live feed returned no new articles — try again later."
                else -> "No disaster news loaded yet — tap Sync to fetch live GNews articles."
              },
              fontSize = 13.sp,
              color = TacticalOnSurfaceVariant,
              lineHeight = 18.sp,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    }

    items(filteredDispatches) { dispatch ->
      FeedDispatchCard(
        dispatch = dispatch,
        onActionClick = {
          dispatch.url?.let { url ->
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
          } ?: onNavigateTab(ScreenTab.INSTRUCTIONS)
        }
      )
    }
  }
}

@Composable
fun FeedDispatchCard(
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
