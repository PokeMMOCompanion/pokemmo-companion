package com.pokemmocompanion.app.detect

/**
 * PokeMMO shows a small red Poké Ball after a wild Pokémon's name (after the level and gender symbol) when that
 * species is already registered as caught. No ball means it's still needed for the Pokédex.
 *
 * The check counts strongly red pixels in a strip that starts at the level number and runs about 6 text heights
 * to the right. Blue (♂) and pink (♀) gender symbols and the white text don't count as red.
 * On sample frames, caught boxes score ~0.19-0.26 (red pixels / height²) and uncaught ones 0.0.
 */
object CaughtIcon {
  private const val SEARCH_WIDTH_HEIGHTS = 6f
  private const val MIN_RED_PER_HEIGHT_SQ = 0.05f

  fun score(frame: Frame, level: OcrBox): Float {
    val h = level.height.coerceAtLeast(1)
    val x0 = level.left.coerceIn(0, frame.width)
    val x1 = (level.right + SEARCH_WIDTH_HEIGHTS * h).toInt().coerceIn(0, frame.width)
    val y0 = (level.top - 0.15f * h).toInt().coerceIn(0, frame.height)
    val y1 = (level.bottom + 0.15f * h).toInt().coerceIn(0, frame.height)
    var red = 0
    for (y in y0 until y1) for (x in x0 until x1) if (isBallRed(frame.pixel(x, y))) red++
    return red.toFloat() / (h * h)
  }

  fun isCaught(frame: Frame, level: OcrBox): Boolean = score(frame, level) >= MIN_RED_PER_HEIGHT_SQ

  private fun isBallRed(argb: Int): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return r >= 170 && g <= 90 && b <= 100
  }
}
