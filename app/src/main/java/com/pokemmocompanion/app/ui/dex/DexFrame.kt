package com.pokemmocompanion.app.ui.dex

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.pokemmocompanion.app.ui.tourTarget

/** The four places on the Home screen (plus Home itself). */
enum class DexSection(val label: String, val icon: TileIcon, private val colorIndex: Int, val tileLabel: String = label) {
  PARTY("Party", TileIcon.PARTY, 0),
  BATTLE("Battle Assistant", TileIcon.BATTLE, 1, tileLabel = "Battle\nAssistant"),
  QUESTS("Quests", TileIcon.QUEST, 2),
  TOOLS("Tools", TileIcon.TOOLS, 3),
  /** The Poké Ball in the middle of Home. */
  POKEDEX("Pokédex", TileIcon.POKEDEX, -1);

  val color: Color
    get() = if (colorIndex >= 0) DexColors.palette.tiles[colorIndex] else Color(0xFFE3350D)
}

/**
 * The Pokédex shell: body in the theme color, lens (the capture button) with three status lights and a status label, and
 * the screen with a faint dot grid like the DS menus. [content] is the scrolling screen content.
 *
 * @param lights red/amber/green lights lit or not (in battle / reading party / capturing)
 */
@Composable
fun DexFrame(
  status: String,
  lights: Triple<Boolean, Boolean, Boolean>,
  capturing: Boolean,
  onCapture: () -> Unit,
  /** False for Home: it fills the screen exactly instead of scrolling. */
  scrollable: Boolean = true,
  /** Changing this (the open section) plays the content's entrance: a quick fade and rise. */
  contentKey: Any? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  // Ambient motion, read only while drawing (no recomposition): the dot grid drifts diagonally one step every 9 s.
  val drift = rememberInfiniteTransition(label = "drift").animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "dots")
  val entrance = remember { Animatable(1f) }
  val first = remember { booleanArrayOf(true) }
  LaunchedEffect(contentKey) {
    if (first[0]) {
      first[0] = false
      return@LaunchedEffect
    }
    entrance.snapTo(0f)
    entrance.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
  }
  Column(
    Modifier.fillMaxSize().background(DexColors.Shell).safeDrawingPadding().padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      CaptureLens(capturing, 52.dp, modifier = Modifier.tourTarget("lens"), onClick = onCapture)
      Light(lights.first, DexColors.LightRed)
      Light(lights.second, DexColors.LightAmber)
      Light(lights.third, DexColors.LightGreen)
      Spacer(Modifier.weight(1f))
      // While not capturing, the status itself is the start button.
      Text(
        status,
        modifier =
          if (capturing) Modifier
          else
            Modifier.clip(RoundedCornerShape(8.dp))
              .border(2.dp, DexColors.ShellText.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
              .clickable(onClick = onCapture)
              .padding(horizontal = 10.dp, vertical = 6.dp),
        style = smashText(DexColors.ShellText, DexColors.palette.tileOutline.copy(alpha = 0.6f), 15),
      )
    }
    val dots = DexColors.ScreenDots
    Column(
      Modifier.weight(1f)
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(DexColors.Screen)
        .drawBehind {
          val step = 12.dp.toPx()
          val shift = drift.value * step
          var y = step / 2 + shift - step
          while (y < size.height) {
            var x = step / 2 + shift - step
            while (x < size.width) {
              drawCircle(dots, 1.3.dp.toPx(), Offset(x, y))
              x += step
            }
            y += step
          }
        }
        .border(3.dp, DexColors.Bezel, RoundedCornerShape(10.dp))
        .graphicsLayer {
          val p = entrance.value
          alpha = 0.35f + 0.65f * p
          translationY = (1 - p) * 18.dp.toPx()
        }
        .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      content = content,
    )
  }
}

/** A status light; while on it glows softly (a slow halo pulse). */
@Composable
private fun Light(on: Boolean, color: Color) {
  val pulse = rememberInfiniteTransition(label = "light").animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "glow")
  Box(
    Modifier.size(11.dp)
      .drawBehind {
        if (on) drawCircle(color.copy(alpha = 0.18f + 0.22f * pulse.value), size.minDimension / 2 + 3.dp.toPx() * pulse.value)
      }
      .clip(CircleShape)
      .background(if (on) color else DexColors.LightOff)
  )
}
