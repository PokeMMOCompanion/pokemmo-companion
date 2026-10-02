package com.pokemmocompanion.app.party

import com.pokemmocompanion.app.detect.Frame

/** A cut-out icon: ARGB pixels (transparent background), row by row. */
class Icon(val width: Int, val height: Int, val pixels: IntArray)

/**
 * Cuts the party icon of the Pokémon being viewed out of the summary screen's bottom bar, on the user's own screen
 * at runtime (the app ships no game art).
 *
 * The viewed Pokémon's icon is drawn at full strength on a blue tile (the others are faded). The tile is a
 * left-to-right gradient, so each column's background is estimated from its own top and bottom edge pixels, and
 * pixels that differ clearly from it are the icon.
 */
object PartyIcon {
  private const val REF_W = 1920f
  private const val REF_H = 1080f
  private const val X0 = 690
  private const val TILE = 90
  private const val Y0 = 990
  private const val Y1 = 1080
  private const val EDGE = 5
  private const val MIN_DISTANCE = 50

  fun cutout(frame: Frame, slot: Int): Icon? {
    val sx = frame.width / REF_W
    val sy = frame.height / REF_H
    val left = ((X0 + TILE * slot) * sx).toInt()
    val top = (Y0 * sy).toInt()
    val w = (TILE * sx).toInt().coerceAtMost(frame.width - left)
    val h = ((Y1 - Y0) * sy).toInt().coerceAtMost(frame.height - top)
    if (w < 20 || h < 20) return null
    val px = IntArray(w * h) { i -> frame.pixel(left + i % w, top + i / w) }

    // Per-column background: median of the top and bottom edge pixels of that column.
    val bg =
      IntArray(w) { x ->
        val samples = (0 until EDGE).map { px[it * w + x] } + (h - EDGE until h).map { px[it * w + x] }
        val r = samples.map { (it shr 16) and 0xFF }.sorted()[samples.size / 2]
        val g = samples.map { (it shr 8) and 0xFF }.sorted()[samples.size / 2]
        val b = samples.map { it and 0xFF }.sorted()[samples.size / 2]
        (r shl 16) or (g shl 8) or b
      }
    val mask = BooleanArray(w * h) { i -> distance(px[i], bg[i % w]) > MIN_DISTANCE }
    // Rows filled almost edge to edge are the bar's border, not the icon.
    for (y in 0 until h) {
      if ((0 until w).count { mask[y * w + it] } > w * 8 / 10) for (x in 0 until w) mask[y * w + x] = false
    }
    val ys = (0 until h).filter { y -> (0 until w).any { mask[y * w + it] } }
    val xs = (0 until w).filter { x -> (0 until h).any { mask[it * w + x] } }
    if (ys.isEmpty() || xs.isEmpty()) return null
    val ow = xs.last() - xs.first() + 1
    val oh = ys.last() - ys.first() + 1
    // An empty slot or a failed cut-out: too few icon pixels, or the whole tile.
    if (mask.count { it } < 200 || ow > w * 95 / 100) return null
    val out =
      IntArray(ow * oh) { i ->
        val x = xs.first() + i % ow
        val y = ys.first() + i / ow
        if (mask[y * w + x]) px[y * w + x] or (0xFF shl 24) else 0
      }
    return Icon(ow, oh, out)
  }

  private fun distance(a: Int, b: Int): Int {
    val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
    val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
    val db = (a and 0xFF) - (b and 0xFF)
    return kotlin.math.sqrt((dr * dr + dg * dg + db * db).toDouble()).toInt()
  }
}
