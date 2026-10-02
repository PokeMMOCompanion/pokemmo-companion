package com.pokemmocompanion.app.detect

/**
 * The battle text box (bottom of the screen) as clean black-on-white pixels for OCR.
 *
 * Battle messages are pure white pixel-font text on a semi-transparent dark band with the battle scene showing
 * through, which OCR misreads ("won' t" → "wori t"). Keeping only near-white pixels (all channels ≥ 200) as
 * black on a white background removes the scene; on sample frames the result is clean even over sprites.
 */
object TextBand {
  private const val WHITE = -0x1 // 0xFFFFFFFF
  private const val BLACK = -0x1000000 // 0xFF000000

  /**
   * @param pixels ARGB pixels of the band, row by row ([width] × [height])
   * @return the binarized band with a white [margin] around it: width, height, pixels
   */
  fun binarize(pixels: IntArray, width: Int, height: Int, margin: Int = 16): Triple<Int, Int, IntArray> {
    val w = width + 2 * margin
    val h = height + 2 * margin
    val out = IntArray(w * h) { WHITE }
    for (y in 0 until height) {
      val row = y * width
      val outRow = (y + margin) * w + margin
      for (x in 0 until width) {
        val p = pixels[row + x]
        val min = minOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
        if (min >= 200) out[outRow + x] = BLACK
      }
    }
    return Triple(w, h, out)
  }
}
