package com.pokemmocompanion.app.detect

import java.text.Normalizer

/** Plain copies of ML Kit's OCR results, so parsing can be unit tested without Android. Coordinates are pixels. */
data class OcrBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
  val height: Int
    get() = bottom - top
}

data class OcrElement(val text: String, val box: OcrBox)

data class OcrLine(val text: String, val elements: List<OcrElement> = emptyList())

/**
 * PokeMMO's text is plain English plus a few symbols, but ML Kit's Latin model can return accented or foreign
 * letters (Nidoran's ♂ came back as "ở"). Everything OCR returns goes through here first: accents are removed
 * ("ở" → "o", "é" → "e") and anything else outside ASCII is dropped, except the symbols the game uses.
 */
object GameText {
  private const val SYMBOLS = "♀♂★☆"

  fun clean(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
      .filter { it.code in 0x20..0x7E || it in SYMBOLS }
}
