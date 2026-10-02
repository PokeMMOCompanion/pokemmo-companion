package com.pokemmocompanion.app.party

import com.pokemmocompanion.app.detect.Frame

/**
 * PokeMMO's summary screen marks the current selection with a blue tile in two rows (1920×1080 coordinates):
 * - Top: the page tabs, 9 tiles ~89 px wide every 97.5 px from x=525, y≈0-89.
 * - Bottom: the party, 6 tiles ~90 px wide every 90 px from x=690, y≈990-1080 (measured with a full party).
 * Pixels are sampled near each tile's edges, clear of the icon drawn on it.
 */
private object BlueTiles {
  private const val REF_W = 1920f
  private const val REF_H = 1080f
  private const val MIN_HIT = 0.75f
  private const val MAX_OTHER = 0.25f

  /** Index of the one highlighted tile, or null if none (or more than one) is. */
  fun highlighted(frame: Frame, count: Int, x0: Float, step: Float, dxs: IntArray, ys: IntArray): Int? {
    val sx = frame.width / REF_W
    val sy = frame.height / REF_H
    val scores =
      (0 until count).map { i ->
        var hit = 0
        for (dx in dxs) for (y in ys) {
          val px = ((x0 + step * i + dx) * sx).toInt().coerceIn(0, frame.width - 1)
          val py = (y * sy).toInt().coerceIn(0, frame.height - 1)
          if (isTileBlue(frame.pixel(px, py))) hit++
        }
        hit.toFloat() / (dxs.size * ys.size)
      }
    val best = scores.indices.maxBy { scores[it] }
    return best.takeIf { scores[best] >= MIN_HIT && scores.filterIndexed { i, _ -> i != best }.all { it < MAX_OTHER } }
  }

  private fun isTileBlue(argb: Int): Boolean {
    val r = (argb shr 16) and 0xFF
    val b = argb and 0xFF
    return b >= 200 && b - r >= 100
  }
}

/** Which party member the summary screen shows (0 = leftmost = first in the party). */
object PartySlot {
  fun highlighted(frame: Frame): Int? =
    BlueTiles.highlighted(frame, 6, 690f, 90f, intArrayOf(6, 14, 76, 84), intArrayOf(1000, 1015, 1060, 1072))
}

/** Which summary page is open. Tabs left to right: info, stats, EVs, IVs, moves, location, contest, ribbons, particles. */
object SummaryTabs {
  private val PAGES =
    listOf(
      SummaryPageKind.INFO,
      SummaryPageKind.STATS,
      SummaryPageKind.EVS,
      SummaryPageKind.IVS,
      SummaryPageKind.MOVES,
    )

  fun highlighted(frame: Frame): SummaryPageKind? {
    val tab = BlueTiles.highlighted(frame, 9, 525f, 97.5f, intArrayOf(5, 12, 77, 84), intArrayOf(6, 15, 75, 84))
      ?: return null
    return PAGES.getOrElse(tab) { SummaryPageKind.OTHER }
  }
}
