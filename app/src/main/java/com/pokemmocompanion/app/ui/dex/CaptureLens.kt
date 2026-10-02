package com.pokemmocompanion.app.ui.dex

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Capture button: the Pokédex's big glossy lens, top left on the shell. Theme lens color when off; teal while
 * capturing, with a ring pulsing outward.
 */
@Composable
fun CaptureLens(on: Boolean, diameter: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val outline = DexColors.palette.tileOutline
  val lens = if (on) Color(0xFF5DCAA5) else DexColors.Lens
  val lensDeep = if (on) Color(0xFF0F6E56) else lerp(DexColors.Lens, Color.Black, 0.35f)
  val hit = rememberSmashHit()
  val pulse = rememberInfiniteTransition(label = "pulse")
  val pulseT by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "pulseT")
  Box(
    modifier
      .size(diameter)
      .graphicsLayer {
        scaleX = hit.scale.value
        scaleY = hit.scale.value
      }
      .drawBehind {
        // Hard shadow
        drawCircle(outline, size.minDimension / 2, center + Offset(2.dp.toPx(), 3.dp.toPx()))
      }
      .clip(CircleShape)
      .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { hit.play(then = onClick) },
  ) {
    Canvas(Modifier.fillMaxSize()) {
      val r = size.minDimension / 2
      val c = center
      // Rim
      drawCircle(Color(0xFFE8E8EC), r, c)
      drawCircle(outline, r - 1.5.dp.toPx(), c, style = Stroke(3.dp.toPx()))
      val lensR = r * 0.69f
      // Pulse ring while capturing
      if (on) {
        drawCircle(lens.copy(alpha = 0.7f * (1 - pulseT)), lensR * (1 + 0.35f * pulseT), c, style = Stroke(4.dp.toPx()))
      }
      // Lens: outline, color, deeper center, glossy highlight and glint
      drawCircle(outline, lensR + 1.5.dp.toPx(), c)
      drawCircle(lens, lensR - 1.dp.toPx(), c)
      drawCircle(lensDeep, lensR * 0.7f, c)
      drawOval(
        Color.White.copy(alpha = 0.75f),
        topLeft = Offset(c.x - lensR * 0.62f, c.y - lensR * 0.62f),
        size = androidx.compose.ui.geometry.Size(lensR * 0.6f, lensR * 0.4f),
      )
      drawCircle(Color.White.copy(alpha = 0.45f), lensR * 0.1f, Offset(c.x + lensR * 0.35f, c.y + lensR * 0.4f))
      drawSmashHit(hit)
    }
  }
}
