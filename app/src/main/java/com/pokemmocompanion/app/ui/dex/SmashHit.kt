package com.pokemmocompanion.app.ui.dex

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import com.pokemmocompanion.app.capture.Sounds
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Smash Bros.-style tap feedback: a punch (squash, pop, settle), a white flash, and speed lines bursting out from the
 * center, with the app's click sound. [play] runs it and then calls `then`, so the screen changes once the hit has landed (~250 ms).
 */
class SmashHit(private val scope: CoroutineScope, private val context: Context) {
  val scale = Animatable(1f)
  val flash = Animatable(0f)
  val burst = Animatable(1f)
  private var busy = false

  fun play(then: () -> Unit) {
    if (busy) return
    busy = true
    Sounds.click(context)
    scope.launch {
      launch {
        flash.snapTo(0.65f)
        flash.animateTo(0f, tween(260))
      }
      launch {
        burst.snapTo(0f)
        burst.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
      }
      scale.animateTo(0.9f, tween(55))
      scale.animateTo(1.06f, tween(90))
      scale.animateTo(1f, tween(90))
      busy = false
      then()
    }
  }
}

@Composable
fun rememberSmashHit(): SmashHit {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current.applicationContext
  return remember { SmashHit(scope, context) }
}

/** Flash and speed lines for [hit]; draw this last, over the button. */
fun DrawScope.drawSmashHit(hit: SmashHit) {
  val f = hit.flash.value
  if (f > 0f) drawRect(Color.White.copy(alpha = f))
  val b = hit.burst.value
  if (b < 1f) {
    val alpha = 1f - b
    val maxR = size.maxDimension * 0.75f
    for (i in 0 until 12) {
      val a = Math.toRadians(i * 30.0 + 15)
      val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
      val start = center + dir * (maxR * (0.15f + 0.6f * b))
      val end = center + dir * (maxR * (0.35f + 0.65f * b))
      drawLine(Color.White.copy(alpha = alpha), start, end, strokeWidth = (if (i % 2 == 0) 6f else 3f), cap = StrokeCap.Round)
    }
  }
}
