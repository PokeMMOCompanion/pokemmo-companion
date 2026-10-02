package com.pokemmocompanion.app.detect

enum class Gender { FEMALE, MALE }

/**
 * The gender symbol PokeMMO draws after an opponent's level: pink ♀ or blue ♂ (Nidoran's box: ~300 pink pixels
 * for ♀, ~260-320 blue for ♂, none of the other color). Used to tell Nidoran♀ from Nidoran♂ when OCR drops the
 * symbol that's part of the name.
 */
object GenderIcon {
  private const val SEARCH_WIDTH_HEIGHTS = 3f
  private const val MIN_PIXELS_PER_HEIGHT_SQ = 0.08f

  fun detect(frame: Frame, level: OcrBox): Gender? {
    val h = level.height.coerceAtLeast(1)
    val x0 = level.right.coerceIn(0, frame.width)
    val x1 = (level.right + SEARCH_WIDTH_HEIGHTS * h).toInt().coerceIn(0, frame.width)
    val y0 = (level.top - 0.2f * h).toInt().coerceIn(0, frame.height)
    val y1 = (level.bottom + 0.2f * h).toInt().coerceIn(0, frame.height)
    var pink = 0
    var blue = 0
    for (y in y0 until y1) for (x in x0 until x1) {
      val p = frame.pixel(x, y)
      val r = (p shr 16) and 0xFF
      val g = (p shr 8) and 0xFF
      val b = p and 0xFF
      if (r > 180 && b > 120 && g < 130 && r - g > 80) pink++
      if (b > 180 && r < 120 && b - r > 90 && g > 90) blue++
    }
    val min = MIN_PIXELS_PER_HEIGHT_SQ * h * h
    return when {
      pink >= min && pink > 2 * blue -> Gender.FEMALE
      blue >= min && blue > 2 * pink -> Gender.MALE
      else -> null
    }
  }

  /**
   * Species name with the gender symbol taken from the icon where it is part of the name (Nidoran). The icon wins
   * over whatever OCR made of the symbol ("Nidoran", "Nidorang", "Nidoranở", ...).
   */
  fun resolveName(name: String, gender: Gender?): String =
    if (gender != null && name.startsWith("Nidoran", ignoreCase = true) && name.length <= "Nidoran".length + 2) {
      if (gender == Gender.FEMALE) "Nidoran♀" else "Nidoran♂"
    } else {
      name
    }
}
