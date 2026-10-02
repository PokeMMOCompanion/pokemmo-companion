package com.pokemmocompanion.app.detect

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ScreenState { UNKNOWN, OVERWORLD, BATTLE }

data class FrameFeatures(
  /** How many of the overworld party-rail rings (right edge, up to 6) show an HP arc. */
  val railRings: Int,
  /** Fraction of the player's HP box dark bar that matches (0..1). */
  val playerBox: Float,
  /** Fraction of the opponent's HP box dark bar that matches (0..1). */
  val opponentBox: Float,
  /** Top of the player's HP box dark bar (1920×1080 coordinates), where [playerBox] matched best. */
  val playerBoxY: Int = 0,
  /** HP arc of each party ring (0..[PartyRail.POINTS]), top to bottom; meaningful on the overworld only. */
  val ringArcs: List<Int> = emptyList(),
  /** Opponent's HP bar fill (0..1) in single battles, null when its box isn't showing. */
  val opponentHp: Float? = null,
)

/**
 * Cheap per-frame checks that tell the overworld from a battle by sampling a few hundred pixels.
 *
 * Coordinates are measured on 1920×1080 frames from the AYN Thor top screen and scaled to the actual frame size.
 * - Overworld: PokeMMO shows the party as a column of HP rings on the right edge ([PartyRail]).
 * - Battle: the rings are gone and the HP boxes are on screen (player's on the right, opponent's top left).
 */
object BattleDetector {
  private const val REF_W = 1920f
  private const val REF_H = 1080f

  private const val BOX_MIN_HIT = 0.6f
  private const val RAIL_MIN_ARC = 8
  private const val PLAYER_BOX_Y_MIN = 470
  private const val PLAYER_BOX_Y_MAX = 740
  private const val PLAYER_BOX_Y_STEP = 4

  fun features(frame: Frame): FrameFeatures {
    val sx = frame.width / REF_W
    val sy = frame.height / REF_H

    // A ring counts when it shows an HP arc of any length/color (a hurt or partly fainted party still reads as the
    // overworld). Battle and summary screens never produce an arc of this length (≤4 of 72 on every sample).
    val arcs = PartyRail.arcs(frame)
    val rings = arcs.count { it >= RAIL_MIN_ARC }

    // The player's HP box: a dark strip holding "33 / 33" with the light, hatched HP bar right on top of it. The box
    // sits at y≈616, or higher (≈548) when stat-change badges like "-1 Def" show under it, so scan a range. Dark
    // alone isn't enough: at night the ground behind the box is dark gray too, so the light bar ends (left and right
    // of the green HP fill, light at any HP) must be there as well.
    val (player, playerY) =
      (PLAYER_BOX_Y_MIN..PLAYER_BOX_Y_MAX step PLAYER_BOX_Y_STEP)
        .map { y0 ->
          val dark = darkFraction(frame, x0 = 1500, x1 = 1900, xStep = 20, y0 = y0, y1 = y0 + 35, yStep = 7, sx, sy)
          val barAbove =
            (lightFraction(frame, 1450, 1525, y0 - 20, y0 - 4, sx, sy) + lightFraction(frame, 1840, 1915, y0 - 20, y0 - 4, sx, sy)) / 2
          minOf(dark, barAbove * 1.5f) to y0
        }
        .maxBy { it.first }
    // Dark bar under the opponent's HP bar.
    val opponent = darkFraction(frame, x0 = 10, x1 = 460, xStep = 10, y0 = 185, y1 = 200, yStep = 3, sx, sy)

    val oppHp = if (opponent >= BOX_MIN_HIT) opponentHpFill(frame, sx, sy) else null

    return FrameFeatures(
      railRings = rings,
      playerBox = player,
      opponentBox = opponent,
      playerBoxY = playerY,
      ringArcs = arcs,
      opponentHp = oppHp,
    )
  }

  /**
   * Single battles: the opponent's HP bar runs from x≈92 to x≈388 at y≈164; the fill (green/yellow/red) grows from
   * the left and the empty part is white.
   */
  private fun opponentHpFill(frame: Frame, sx: Float, sy: Float): Float {
    var filled = 0
    var total = 0
    for (x in 92 until 388 step 2) {
      total++
      val p = frame.pixelOrZero((x * sx).roundToInt(), (164 * sy).roundToInt())
      if (minOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF) < 200) filled++
    }
    return filled.toFloat() / total
  }

  /** OVERWORLD or BATTLE when the frame is clear, null when it can't tell (transitions, intros, menus). */
  fun classify(f: FrameFeatures): ScreenState? =
    when {
      f.railRings > 0 -> ScreenState.OVERWORLD
      f.playerBox >= BOX_MIN_HIT || f.opponentBox >= BOX_MIN_HIT -> ScreenState.BATTLE
      else -> null
    }

  /** Fraction of near-white pixels (all channels ≥ 200) in a rectangle, reference coordinates. */
  private fun lightFraction(frame: Frame, x0: Int, x1: Int, y0: Int, y1: Int, sx: Float, sy: Float): Float {
    var hit = 0
    var total = 0
    for (y in y0 until y1 step 4) for (x in x0 until x1 step 10) {
      total++
      val p = frame.pixelOrZero((x * sx).roundToInt(), (y * sy).roundToInt())
      if (minOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF) >= 200) hit++
    }
    return if (total == 0) 0f else hit.toFloat() / total
  }

  private fun darkFraction(
    frame: Frame,
    x0: Int,
    x1: Int,
    xStep: Int,
    y0: Int,
    y1: Int,
    yStep: Int,
    sx: Float,
    sy: Float,
  ): Float {
    var hit = 0
    var total = 0
    for (y in y0 until y1 step yStep) {
      for (x in x0 until x1 step xStep) {
        total++
        if (isUiDark(frame.pixelOrZero((x * sx).roundToInt(), (y * sy).roundToInt()))) hit++
      }
    }
    return hit.toFloat() / total
  }

  private fun Frame.pixelOrZero(x: Int, y: Int): Int =
    if (x in 0 until width && y in 0 until height) pixel(x, y) else 0

  /** Near-black, unsaturated gray used by the HP box bars. */
  internal fun isUiDark(argb: Int): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val max = maxOf(r, g, b)
    return max < 80 && max - minOf(r, g, b) < 25
  }
}
