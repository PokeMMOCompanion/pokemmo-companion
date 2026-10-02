package com.pokemmocompanion.app.ui

import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokemmocompanion.app.party.SpriteImporter
import com.pokemmocompanion.app.ui.dex.DexColors
import com.pokemmocompanion.app.ui.dex.DexThemes
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Steps of the first-run tour. */
enum class TourStep { CAPTURE, SPRITES, THEME, TIPS, PARTY }

/** The first-run tour: shown once (until finished or skipped), and again from Settings. */
object Onboarding {
  private const val PREFS = "onboarding"
  private const val SEEN = "seen"
  private val _step = MutableStateFlow<TourStep?>(null)
  val step: StateFlow<TourStep?> = _step.asStateFlow()

  /** Where tour targets are on screen (root coordinates), by name: "lens", "tile_PARTY". */
  val targets = mutableStateMapOf<String, Rect>()

  fun startIfFirstRun(context: Context) {
    if (!context.getSharedPreferences(PREFS, 0).getBoolean(SEEN, false)) _step.value = TourStep.CAPTURE
  }

  fun restart() {
    _step.value = TourStep.CAPTURE
  }

  fun go(step: TourStep) {
    _step.value = step
  }

  fun finish(context: Context) {
    context.getSharedPreferences(PREFS, 0).edit().putBoolean(SEEN, true).apply()
    _step.value = null
  }
}

/** Marks a composable as a tour target. */
fun Modifier.tourTarget(name: String) = onGloballyPositioned { Onboarding.targets[name] = it.boundsInRoot() }

/**
 * The tour overlay: dims the screen, spotlights the step's target with a bouncing finger, and explains in a bubble.
 * Tapping inside the spotlight does what tapping the control would.
 */
@Composable
fun TourOverlay(capturing: Boolean, onStartCapture: () -> Unit, onOpenParty: () -> Unit, onTheme: (Int) -> Unit) {
  val context = LocalContext.current.applicationContext
  val step by Onboarding.step.collectAsStateWithLifecycle()
  val current = step ?: return
  // Capture already on (or turned on through the prompt): move on.
  LaunchedEffect(current, capturing) { if (current == TourStep.CAPTURE && capturing) Onboarding.go(TourStep.SPRITES) }

  val targetName =
    when (current) {
      TourStep.CAPTURE -> "lens"
      TourStep.PARTY -> "tile_PARTY"
      else -> null
    }
  val target = targetName?.let { Onboarding.targets[it] }
  val density = LocalDensity.current
  val pad = with(density) { 8.dp.toPx() }
  val hole = target?.let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) }
  val bounce = rememberInfiniteTransition(label = "finger").animateFloat(0f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "bob")

  BoxWithConstraints(
    Modifier.fillMaxSize()
      .pointerInput(current, hole) {
        detectTapGestures { p ->
          if (hole != null && hole.contains(p)) {
            when (current) {
              TourStep.CAPTURE -> onStartCapture()
              TourStep.PARTY -> {
                onOpenParty()
                Onboarding.finish(context)
              }
              else -> {}
            }
          }
        }
      }
  ) {
    // Dim with a spotlight hole and a ring around it.
    Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
      drawRect(Color.Black.copy(alpha = 0.62f))
      if (hole != null) {
        val r = CornerRadius(14.dp.toPx())
        drawRoundRect(Color.Black, topLeft = hole.topLeft, size = hole.size, cornerRadius = r, blendMode = BlendMode.Clear)
        val grow = 4.dp.toPx() * bounce.value
        drawRoundRect(
          Color.White.copy(alpha = 0.9f),
          topLeft = Offset(hole.left - grow, hole.top - grow),
          size = androidx.compose.ui.geometry.Size(hole.width + 2 * grow, hole.height + 2 * grow),
          cornerRadius = r,
          style = Stroke(3.dp.toPx()),
        )
      }
    }
    // Finger pointing up at the spotlight, bobbing.
    if (hole != null) {
      val fingerSize = with(density) { 44.dp.toPx() }
      val x = (hole.center.x - fingerSize / 2).roundToInt()
      val y = (hole.bottom + with(density) { (6 + 10 * bounce.value).dp.toPx() }).roundToInt()
      Text("👆", fontSize = 40.sp, modifier = Modifier.offset { IntOffset(x, y) })
    }
    Bubble(current, capturing, onTheme, onOpenParty, context, Modifier.align(Alignment.BottomCenter).padding(16.dp))
  }
}

@Composable
private fun Bubble(step: TourStep, capturing: Boolean, onTheme: (Int) -> Unit, onOpenParty: () -> Unit, context: Context, modifier: Modifier) {
  val scope = rememberCoroutineScope()
  var download by remember { mutableStateOf<String?>(null) }
  val outline = DexColors.palette.tileOutline
  Column(
    modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(DexColors.Screen)
      .border(3.dp, outline, RoundedCornerShape(14.dp))
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    val (title, body) =
      when (step) {
        TourStep.CAPTURE ->
          "Welcome!" to
            "Tap the lens to start watching the game screen. Android will ask to allow screen capture: choose the " +
              "entire screen. Nothing is saved or uploaded."
        TourStep.SPRITES ->
          "Pokémon sprites" to "Download pictures of all 649 Pokémon for the Pokédex, party and battles (one time, needs internet)."
        TourStep.THEME -> "Pick a look" to "Choose a theme. You can change it any time in Tools → Settings."
        TourStep.TIPS ->
          "Where things are" to
            "Battle Assistant opens by itself in battles. The Poké Ball is the Pokédex (Here shows what spawns where " +
              "you are: just open the game's menu). Tools has berries, breeding, egg moves, GTL prices and settings."
        TourStep.PARTY ->
          "Last step: read your party" to
            "The battle tips need your Pokémon. After this, Party opens: tap Read party, then in PokeMMO open your " +
              "first Pokémon's Summary and page through its tabs (Stats, EVs, IVs, Moves), about a second each. Do " +
              "the same for each Pokémon, then tap Done reading. If something won't read, tap a Pokémon and use Edit."
      }
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = DexColors.Ink)
    Text(body, style = MaterialTheme.typography.bodyMedium, color = DexColors.Ink)
    if (step == TourStep.THEME) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DexThemes.ALL.forEachIndexed { i, t -> Chip(t.name, DexColors.palette == t) { onTheme(i) } }
      }
    }
    download?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DexColors.InkMuted) }
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (step != TourStep.PARTY) {
        TextButton(onClick = { Onboarding.finish(context) }) { Text("Skip tour") }
      }
      Box(Modifier.weight(1f))
      when (step) {
        TourStep.CAPTURE -> if (capturing) Button(onClick = { Onboarding.go(TourStep.SPRITES) }) { Text("Next") }
        TourStep.SPRITES -> {
          TextButton(onClick = { Onboarding.go(TourStep.THEME) }) { Text(if (download == null) "Later" else "Next") }
          if (download == null) {
            Button(
              onClick = {
                download = "Downloading…"
                scope.launch {
                  val (got, failed) = SpriteImporter.downloadAll { n -> download = "Downloading… $n / 649" }
                  download = if (failed == 0) "Done: $got sprites." else "$got downloaded, $failed failed (retry in Tools → Settings)."
                }
              }
            ) {
              Text("Download now")
            }
          }
        }
        TourStep.THEME -> Button(onClick = { Onboarding.go(TourStep.TIPS) }) { Text("Next") }
        TourStep.TIPS -> Button(onClick = { Onboarding.go(TourStep.PARTY) }) { Text("Next") }
        TourStep.PARTY -> {
          TextButton(onClick = { Onboarding.finish(context) }) { Text("Later") }
          Button(
            onClick = {
              onOpenParty()
              Onboarding.finish(context)
            }
          ) {
            Text("Okay, let's go")
          }
        }
      }
    }
  }
}

