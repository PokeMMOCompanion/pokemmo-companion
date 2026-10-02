package com.pokemmocompanion.app.ui.dex

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class TileIcon { PARTY, BATTLE, QUEST, TOOLS, POKEDEX }

/** Bold italic label with a hard outline-style shadow (Smash menu lettering). */
fun smashText(color: Color, outline: Color, sizeSp: Int) =
  TextStyle(
    color = color,
    fontSize = sizeSp.sp,
    fontWeight = FontWeight.Black,
    fontStyle = FontStyle.Italic,
    shadow = Shadow(outline, Offset(3f, 3f), 0f),
  )

/** Simple icons drawn with shapes (no icon library, no game art). */
fun DrawScope.drawTileIcon(icon: TileIcon, topLeft: Offset, size: Float, color: Color) {
  val u = size / 10f
  fun p(x: Float, y: Float) = Offset(topLeft.x + x * u, topLeft.y + y * u)
  when (icon) {
    TileIcon.PARTY -> {
      // Three figures: heads and shoulders
      for ((cx, r) in listOf(2.6f to 1.3f, 7.4f to 1.3f, 5f to 1.7f)) {
        val big = r > 1.5f
        drawCircle(color, r * u, p(cx, if (big) 3.2f else 3.8f))
        drawArc(
          color, 180f, 180f, true,
          topLeft = p(cx - (if (big) 2.8f else 2.2f), if (big) 5.6f else 6f),
          size = Size((if (big) 5.6f else 4.4f) * u, (if (big) 5.6f else 4.4f) * u),
        )
      }
    }
    TileIcon.BATTLE -> {
      // Crossed swords: blades, crossguards, pommels
      val stroke = 1.1f * u
      drawLine(color, p(1.5f, 1.5f), p(8f, 8f), stroke, StrokeCap.Round)
      drawLine(color, p(8.5f, 1.5f), p(2f, 8f), stroke, StrokeCap.Round)
      drawLine(color, p(5.6f, 8.6f), p(8.6f, 5.6f), stroke, StrokeCap.Round)
      drawLine(color, p(1.4f, 5.6f), p(4.4f, 8.6f), stroke, StrokeCap.Round)
      drawCircle(color, 0.8f * u, p(8.8f, 8.8f))
      drawCircle(color, 0.8f * u, p(1.2f, 8.8f))
    }
    TileIcon.QUEST -> {
      // Flag on a pole
      drawLine(color, p(2.2f, 0.8f), p(2.2f, 9.6f), 1.1f * u, StrokeCap.Round)
      val flag =
        Path().apply {
          moveTo(p(2.2f, 1f).x, p(2.2f, 1f).y)
          lineTo(p(9f, 3.2f).x, p(9f, 3.2f).y)
          lineTo(p(2.2f, 5.6f).x, p(2.2f, 5.6f).y)
          close()
        }
      drawPath(flag, color)
    }
    TileIcon.TOOLS -> {
      // Wrench: handle and an open ring at the head
      drawLine(color, p(2f, 8f), p(6.2f, 3.8f), 1.5f * u, StrokeCap.Round)
      drawCircle(color, 2.2f * u, p(7f, 3f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.3f * u))
      drawCircle(color, 0.9f * u, p(2f, 8f))
    }
    TileIcon.POKEDEX -> {
      // Ball outline, band and center button
      drawCircle(color, 4.3f * u, p(5f, 5f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.1f * u))
      drawLine(color, p(0.8f, 5f), p(9.2f, 5f), 1.1f * u)
      drawCircle(color, 1.5f * u, p(5f, 5f))
    }
  }
}

/** Small circular icon badge (used in section headers). */
@Composable
fun TileBadge(icon: TileIcon, color: Color) {
  Box(Modifier.size(30.dp).clip(RoundedCornerShape(50)).background(color).border(2.dp, DexColors.palette.tileOutline, RoundedCornerShape(50))) {
    Canvas(Modifier.fillMaxSize().padding(6.dp)) { drawTileIcon(icon, Offset.Zero, size.minDimension, Color.White) }
  }
}

/** Section title: Smash lettering, with the "◀ Home" button. */
@Composable
fun SectionHeader(title: String, icon: TileIcon, color: Color, onHome: () -> Unit) {
  androidx.compose.foundation.layout.Row(
    Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
  ) {
    Text(
      "◀ Home",
      modifier =
        Modifier.clip(RoundedCornerShape(6.dp))
          .background(DexColors.palette.tileOutline)
          .clickable(onClick = onHome)
          .padding(horizontal = 10.dp, vertical = 6.dp),
      style = TextStyle(color = DexColors.palette.tileText, fontSize = 13.sp, fontWeight = FontWeight.Bold),
    )
    TileBadge(icon, color)
    Text(title, style = smashText(color, DexColors.palette.tileOutline, 22))
  }
}

