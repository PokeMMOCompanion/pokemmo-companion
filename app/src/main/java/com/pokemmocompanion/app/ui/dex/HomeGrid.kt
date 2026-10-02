package com.pokemmocompanion.app.ui.dex

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokemmocompanion.app.ui.tourTarget

/** One Home tile: label, status tag, color, icon and what tapping it does. */
data class HomeTile(val label: String, val status: String, val color: Color, val icon: TileIcon, val onClick: () -> Unit)

private enum class Corner(val left: Boolean, val top: Boolean) {
  TOP_LEFT(true, true),
  TOP_RIGHT(false, true),
  BOTTOM_LEFT(true, false),
  BOTTOM_RIGHT(false, false),
}

/** Rounded rectangle with a circle cut out of it; the circle's center is given relative to the tile. */
private class CutoutShape(private val center: (Size) -> Offset, private val radiusPx: Float, private val cornerPx: Float) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    val tile = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(cornerPx))) }
    val hole = Path().apply { addOval(Rect(center(size), radiusPx)) }
    return Outline.Generic(Path.combine(PathOperation.Difference, tile, hole))
  }
}

/**
 * The Home screen: four tiles in the corners with a circular cut-out at their inner corners, and the Pokédex
 * button (a Poké Ball) exactly in the middle. Tiles fill four equal quarters and their hard shadows are drawn outside the layout,
 * so the button and the cut-outs share the same center.
 */
@Composable
fun HomeGrid(tiles: List<HomeTile>, onCenter: () -> Unit, modifier: Modifier = Modifier) {
  val gap = 14.dp
  val button = 120.dp
  val ring = 9.dp
  // Fills the whole screen: each tile takes a quarter (minus the gap), so the tiles reach the four corners.
  // One slow shared clock (6 s) drives the tiles' idle motion, each tile a quarter-cycle apart.
  val clock = rememberInfiniteTransition(label = "idle").animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "clock")
  // A slower one (20 s) for the light sheen: each tile once per cycle, so one sweep somewhere every ~5 s.
  val sheen = rememberInfiniteTransition(label = "sheen").animateFloat(0f, 1f, infiniteRepeatable(tween(20_000, easing = LinearEasing)), label = "sheen")
  // Tile backgrounds (Smash Ultimate menu style): stripes scroll one step every 4 s.
  val scroll = rememberInfiniteTransition(label = "stripes").animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "scroll")
  BoxWithConstraints(modifier.fillMaxSize()) {
    val tileWidth = (maxWidth - gap) / 2
    val tileHeight = (maxHeight - gap) / 2
    Corner.entries.forEachIndexed { i, corner ->
      val t = tiles[i]
      CornerTile(
        t,
        corner,
        Modifier.size(tileWidth, tileHeight)
          .offset(x = if (corner.left) 0.dp else tileWidth + gap, y = if (corner.top) 0.dp else tileHeight + gap)
          .tourTarget("tile_" + t.icon.name),
        gap,
        button / 2 + ring,
        clock,
        sheen,
        scroll,
        phase = i * 0.25f,
      )
    }
    PokeballButton(button, Modifier.align(Alignment.Center), onCenter)
  }
}

@Composable
private fun CornerTile(t: HomeTile, corner: Corner, modifier: Modifier, gap: Dp, cutRadius: Dp, clock: State<Float>, sheen: State<Float>, scroll: State<Float>, phase: Float) {
  val outline = DexColors.palette.tileOutline
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val density = androidx.compose.ui.platform.LocalDensity.current
  val shape =
    remember(corner, density) {
      with(density) {
        val half = gap.toPx() / 2
        CutoutShape(
          center = { s ->
            Offset(if (corner.left) s.width + half else -half, if (corner.top) s.height + half else -half)
          },
          radiusPx = cutRadius.toPx(),
          cornerPx = 10.dp.toPx(),
        )
      }
    }
  val drop = 5.dp
  val hit = rememberSmashHit()
  Box(
    modifier
      .graphicsLayer {
        scaleX = hit.scale.value
        scaleY = hit.scale.value
        // Idle bob: a couple of dp up and down.
        translationY = sin(2 * PI * (clock.value + phase)).toFloat() * 2.dp.toPx()
      }
      // Hard shadow, drawn outside the layout so it doesn't shift the grid.
      .drawBehind {
        val o = shape.createOutline(size, layoutDirection, this)
        translate(drop.toPx(), drop.toPx()) {
          drawPath((o as Outline.Generic).path, outline)
        }
      }
      .offset(x = if (pressed) 4.dp else 0.dp, y = if (pressed) 4.dp else 0.dp)
      .clip(shape)
      .background(t.color)
      .border(3.dp, outline, shape)
      .clickable(interactionSource = interaction, indication = null) { hit.play(then = t.onClick) }
  ) {
    Canvas(Modifier.fillMaxSize()) {
      // Moving background, Smash Ultimate menu style: a light-to-dark wash, thin diagonal stripes drifting across,
      // and a soft glow breathing behind the icon (each tile a little out of step with the others).
      drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent, Color.Black.copy(alpha = 0.14f))))
      val spacing = 22.dp.toPx()
      val slant = size.height * 0.55f
      val shift = ((scroll.value + phase) % 1f) * spacing
      var x = -slant - spacing + shift
      while (x < size.width + spacing) {
        drawLine(Color.White.copy(alpha = 0.09f), Offset(x + slant, 0f), Offset(x, size.height), strokeWidth = spacing * 0.32f)
        x += spacing
      }
      val s = size.height * 0.8f
      val left = if (corner.left) -s * 0.12f else size.width - s * 0.88f
      val top = if (corner.top) -s * 0.1f else size.height - s * 0.9f
      val breathe = (sin(2 * PI * (clock.value + phase)).toFloat() + 1f) / 2f
      drawCircle(
        Brush.radialGradient(
          listOf(Color.White.copy(alpha = 0.10f + 0.10f * breathe), Color.Transparent),
          center = Offset(left + s / 2, top + s / 2),
          radius = s * (0.55f + 0.08f * breathe),
        ),
        radius = s * (0.55f + 0.08f * breathe),
        center = Offset(left + s / 2, top + s / 2),
      )
      // Big faded icon in the outer corner
      drawTileIcon(t.icon, Offset(left, top), s, outline.copy(alpha = 0.25f))
    }
    // Label on the outer side, at the edge facing the middle row.
    Column(
      Modifier.align(
          when (corner) {
            Corner.TOP_LEFT -> Alignment.BottomStart
            Corner.TOP_RIGHT -> Alignment.BottomEnd
            Corner.BOTTOM_LEFT -> Alignment.TopStart
            Corner.BOTTOM_RIGHT -> Alignment.TopEnd
          }
        )
        .padding(horizontal = 14.dp, vertical = 12.dp),
      horizontalAlignment = if (corner.left) Alignment.Start else Alignment.End,
    ) {
      Text(
        t.label,
        style = smashText(DexColors.palette.tileText, outline, 24).copy(lineHeight = 25.sp),
        textAlign = if (corner.left) TextAlign.Start else TextAlign.End,
      )
      Text(
        t.status,
        modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(outline).padding(horizontal = 7.dp, vertical = 1.dp),
        style = TextStyle(color = DexColors.palette.tileText, fontSize = 11.sp, fontWeight = FontWeight.Bold),
      )
    }
    Canvas(Modifier.fillMaxSize()) {
      // A faint light sheen sweeps across now and then (about a second, once per sheen cycle per tile).
      val u = ((sheen.value - phase) % 1f + 1f) % 1f
      if (u < SHEEN_SHARE) {
        val x = -size.width * 0.3f + (u / SHEEN_SHARE) * size.width * 1.6f
        drawLine(Color.White.copy(alpha = 0.15f), Offset(x + size.height * 0.4f, 0f), Offset(x, size.height), strokeWidth = size.width * 0.09f)
      }
      drawSmashHit(hit)
    }
  }
}

/** The Pokédex button: a Poké Ball drawn from plain shapes, with a hard shadow and the Smash hit on tap. */
@Composable
private fun PokeballButton(diameter: Dp, modifier: Modifier, onClick: () -> Unit) {
  val outline = DexColors.palette.tileOutline
  val hit = rememberSmashHit()
  // Every few seconds it wobbles like a ball with something inside.
  val wobble =
    rememberInfiniteTransition(label = "ball").animateFloat(
      0f,
      0f,
      infiniteRepeatable(
        keyframes {
          durationMillis = 5200
          0f at 3900
          -9f at 4100
          8f at 4350
          -4f at 4600
          2f at 4800
          0f at 5000
        }
      ),
      label = "wobble",
    )
  Box(
    modifier
      .size(diameter)
      .graphicsLayer {
        scaleX = hit.scale.value
        scaleY = hit.scale.value
        rotationZ = wobble.value
        transformOrigin = TransformOrigin(0.5f, 0.9f)
      }
      .drawBehind { drawCircle(outline, size.minDimension / 2, center + Offset(4.dp.toPx(), 5.dp.toPx())) }
      .clip(CircleShape)
      .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { hit.play(then = onClick) },
  ) {
    Canvas(Modifier.fillMaxSize()) {
      val r = size.minDimension / 2
      val c = center
      val line = 7.dp.toPx()
      drawCircle(Color(0xFFF4F4F6), r, c)
      drawArc(Color(0xFFE3350D), 180f, 180f, useCenter = true, topLeft = Offset.Zero, size = size)
      // Gloss on the red half
      drawOval(
        Color.White.copy(alpha = 0.45f),
        topLeft = Offset(c.x - r * 0.62f, c.y - r * 0.78f),
        size = Size(r * 0.55f, r * 0.3f),
      )
      drawRect(outline, topLeft = Offset(0f, c.y - line / 2), size = Size(size.width, line))
      drawCircle(outline, r - 2.dp.toPx(), c, style = Stroke(4.dp.toPx()))
      // Center button
      drawCircle(outline, r * 0.3f, c)
      drawCircle(Color(0xFFF4F4F6), r * 0.3f - line * 0.7f, c)
      drawCircle(outline.copy(alpha = 0.25f), r * 0.12f, c, style = Stroke(2.dp.toPx()))
      drawSmashHit(hit)
    }
  }
}

/** Share of the 20 s sheen cycle one sweep takes (~1 s). */
private const val SHEEN_SHARE = 0.05f
