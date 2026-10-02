package com.pokemmocompanion.app.detect

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The overworld party rail: one ring per party member down the right edge (centers x=1845, y=253+150·k on
 * 1920×1080). Each ring is an HP gauge: a semi-transparent arc that ends at the bottom (6 o'clock) and grows with HP,
 * green when healthy, amber/red when low, and missing (a greyed-out disc) when the Pokémon has fainted.
 *
 * The arc is semi-transparent, so its color depends on what's behind it. A point on the ring counts as arc when either
 * - it's clearly greener (or more amber) than the background just inside and outside the ring at that angle, or
 * - it has the ring's own blue-green (needed over bushes and trees, where the background is as green as the ring).
 */
object PartyRail {
  const val POINTS = 72
  private const val REF_W = 1920f
  private const val REF_H = 1080f
  private const val CX = 1845f
  private const val CY0 = 253f
  private const val DY = 150f
  private const val R = 58f
  private const val R_IN = 45f
  private const val R_OUT = 71f
  /** Where arcs end: 6 o'clock is point 36 when point 0 is 12 o'clock and points go clockwise. */
  private val ARC_END = 34..38
  /** Arcs this short (or none) mean the Pokémon has fainted. */
  const val FAINTED_MAX = 2

  /** Arc length (0..[POINTS]) of each of the six rings, top to bottom. */
  fun arcs(frame: Frame): List<Int> = (0 until 6).map { arc(frame, it) }

  /** HP fraction 0..1 from an arc length; 0 when fainted. */
  fun hpFraction(arc: Int): Float = if (arc <= FAINTED_MAX) 0f else arc.toFloat() / POINTS

  private fun arc(frame: Frame, k: Int): Int {
    val sx = frame.width / REF_W
    val sy = frame.height / REF_H
    val cy = CY0 + DY * k
    fun px(r: Float, th: Double): Int {
      val x = ((CX + r * cos(th)) * sx).roundToInt().coerceIn(0, frame.width - 1)
      val y = ((cy + r * sin(th)) * sy).roundToInt().coerceIn(0, frame.height - 1)
      return frame.pixel(x, y)
    }
    val hit =
      BooleanArray(POINTS) { t ->
        val th = 2 * PI * t / POINTS - PI / 2 // start at 12 o'clock, clockwise
        val band = px(R, th)
        val inner = px(R_IN, th)
        val outer = px(R_OUT, th)
        greenness(band) - maxOf(greenness(inner), greenness(outer)) >= 25 ||
          amberness(band) - maxOf(amberness(inner), amberness(outer)) >= 40 ||
          isRingGreen(band)
      }
    // Longest run (allowing single-point gaps) ending at the bottom and going back counter-clockwise.
    var best = 0
    for (end in ARC_END) {
      var n = 0
      var gaps = 0
      var t = end
      while (n + gaps < POINTS) {
        if (hit[((t % POINTS) + POINTS) % POINTS]) {
          n++
          gaps = 0
        } else if (++gaps > 1) {
          break
        }
        t--
      }
      best = maxOf(best, n)
    }
    return best.coerceAtMost(POINTS)
  }

  private fun channels(p: Int) = Triple((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)

  private fun greenness(p: Int): Int {
    val (r, g, b) = channels(p)
    return g - (r + b) / 2
  }

  private fun amberness(p: Int): Int {
    val (r, g, b) = channels(p)
    return (r + g) / 2 - b
  }

  /** The ring's own blue-green, about rgb(35-80, 150-185, 78-115). */
  private fun isRingGreen(p: Int): Boolean {
    val (r, g, b) = channels(p)
    return r <= 90 && g >= 140 && g - r >= 85 && b in 70..125 && g - b >= 45
  }
}
