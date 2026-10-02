package com.pokemmocompanion.app.detect

import com.pokemmocompanion.app.calc.Stat

/** One stat-change badge on screen ("+1 Atk"), in 1920×1080 reference coordinates. */
data class Badge(val left: Int, val top: Int, val right: Int, val bottom: Int, val raised: Boolean)

/**
 * Stat-stage badges PokeMMO shows in battle: a pale green tile for a raised stat, pale pink for a lowered one,
 * with dark text like "+1 Atk", "-2 Def", "+1 SpA".
 * - Opponent: a row right of its name box (x≈505+, y≈152-204).
 * - Player: under its HP box (≈64-119 px below the top of the box's dark strip), centered near x≈1680.
 * Tiles are found by color, which also gives the sign; OCR only has to read the number and the stat.
 */
object StatBadges {
  private const val REF_W = 1920f
  private const val REF_H = 1080f

  fun isRaisedTile(argb: Int): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return g > 220 && r < 235 && g - r > 15 && g - b > 10
  }

  fun isLoweredTile(argb: Int): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    return r > 240 && g in 191..234 && r - g > 15
  }

  /** Badges in a reference-coordinate region, left to right. */
  fun find(frame: Frame, x0: Int, y0: Int, x1: Int, y1: Int): List<Badge> {
    val sx = frame.width / REF_W
    val sy = frame.height / REF_H
    fun px(x: Int, y: Int): Int {
      val fx = (x * sx).toInt()
      val fy = (y * sy).toInt()
      return if (fx in 0 until frame.width && fy in 0 until frame.height) frame.pixel(fx, fy) else 0
    }
    val badges = mutableListOf<Badge>()
    var start = -1
    var raised = 0
    var lowered = 0
    var top = Int.MAX_VALUE
    var bottom = Int.MIN_VALUE
    var gap = 0
    fun close(end: Int) {
      if (start >= 0 && end - start >= 40) badges += Badge(start, top, end, bottom, raised >= lowered)
      start = -1
      raised = 0
      lowered = 0
      top = Int.MAX_VALUE
      bottom = Int.MIN_VALUE
    }
    for (x in x0 until x1 step 2) {
      var hits = 0
      for (y in y0 until y1 step 2) {
        val p = px(x, y)
        val up = isRaisedTile(p)
        val down = !up && isLoweredTile(p)
        if (up || down) {
          hits++
          if (up) raised++ else lowered++
          top = minOf(top, y)
          bottom = maxOf(bottom, y)
        }
      }
      if (hits >= 4) {
        if (start < 0) start = x
        gap = 0
      } else if (start >= 0 && ++gap > 3) {
        close(x - 2 * gap)
        gap = 0
      }
    }
    close(x1)
    return badges
  }

  private val BADGE = Regex("""([1-6])\s*(Sp\.?\s*A(?:tk)?|Sp\.?\s*D(?:ef)?|Spe(?:ed)?|Atk|Att|Def|Acc|Eva)""", RegexOption.IGNORE_CASE)

  /**
   * Stat and signed stage from a badge's OCR text and tile color, or null if unreadable.
   * Accuracy/evasion badges return null (they don't change damage).
   */
  fun parse(text: String, raised: Boolean): Pair<Stat, Int>? {
    val m = BADGE.find(text.replace('l', '1').replace('I', '1')) ?: return null
    val amount = m.groupValues[1].toInt()
    val name = m.groupValues[2].lowercase().filter { it.isLetter() }
    val stat =
      when {
        name.startsWith("spa") -> Stat.SPA
        name.startsWith("spd") -> Stat.SPD
        name.startsWith("spe") -> Stat.SPE
        name.startsWith("at") -> Stat.ATK
        name.startsWith("def") -> Stat.DEF
        else -> return null // accuracy / evasion
      }
    return stat to if (raised) amount else -amount
  }
}
