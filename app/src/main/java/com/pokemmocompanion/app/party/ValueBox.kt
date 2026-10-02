package com.pokemmocompanion.app.party

import com.pokemmocompanion.app.detect.Frame

/** One character in a value box, in frame pixels. */
data class Glyph(val left: Int, val right: Int, val top: Int, val bottom: Int) {
  val width: Int
    get() = right - left

  val height: Int
    get() = bottom - top

  /** "1" is the only narrow digit in PokeMMO's font: width ≤ 0.52 × height, all other digits ≥ 0.62. */
  val isNarrow: Boolean
    get() = width <= NARROW_RATIO * height

  companion object {
    const val NARROW_RATIO = 0.57f
  }
}

/**
 * Pixel-level help for the numeric summary boxes (Pokédex number, stats, EVs, IVs), where ML Kit can miss a lone
 * small digit (often "1") in an otherwise empty white box.
 *
 * Text is dark gray on white, except maxed IVs (green) and zero IVs (red).
 */
object ValueBox {
  private const val REF_W = 1920f
  private const val REF_H = 1080f
  private const val INSET_X = 8
  private const val INSET_Y = 6

  fun isText(argb: Int): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    return max < 150 || (max - min > 90 && min < 120)
  }

  /** Characters left to right. Icons (much taller than the digits, like the Pokédex box's ball) are dropped. */
  fun glyphs(frame: Frame, box: FieldBox): List<Glyph> {
    val sx = frame.width / REF_W
    val sy = frame.height / REF_H
    val x0 = ((box.left + INSET_X) * sx).toInt().coerceIn(0, frame.width)
    val x1 = ((box.right - INSET_X) * sx).toInt().coerceIn(x0, frame.width)
    val y0 = ((box.top + INSET_Y) * sy).toInt().coerceIn(0, frame.height)
    val y1 = ((box.bottom - INSET_Y) * sy).toInt().coerceIn(y0, frame.height)
    val minHeight = (8 * sy).toInt().coerceAtLeast(3)

    val runs = mutableListOf<Glyph>()
    var start = -1
    var top = Int.MAX_VALUE
    var bottom = Int.MIN_VALUE
    for (x in x0..x1) {
      var colTop = Int.MAX_VALUE
      var colBottom = Int.MIN_VALUE
      if (x < x1) {
        for (y in y0 until y1) {
          if (isText(frame.pixel(x, y))) {
            if (y < colTop) colTop = y
            colBottom = y + 1
          }
        }
      }
      val hasText = colBottom > colTop
      if (hasText) {
        if (start < 0) start = x
        top = minOf(top, colTop)
        bottom = maxOf(bottom, colBottom)
      } else if (start >= 0) {
        if (bottom - top >= minHeight) runs += Glyph(start, x, top, bottom)
        start = -1
        top = Int.MAX_VALUE
        bottom = Int.MIN_VALUE
      }
    }
    if (runs.isEmpty()) return runs
    val typical = runs.map { it.height }.sorted()[runs.size / 2]
    return runs.filter { it.height in (typical * 6 / 10)..(typical * 13 / 10) }
  }

  /**
   * Fallback when OCR's number doesn't fit the box: if every character is narrow, the value is all ones.
   * Returns null when that can't be decided.
   */
  fun onesFallback(glyphs: List<Glyph>): String? =
    if (glyphs.isNotEmpty() && glyphs.all { it.isNarrow }) "1".repeat(glyphs.size) else null

  /**
   * Checks OCR's reading of a single-number box against the characters actually in it. If the digit count
   * doesn't match (usually a "1" that OCR skipped or turned into nothing) and every character is a narrow "1",
   * the value is all ones. Otherwise OCR's text is kept.
   */
  fun reconcile(ocrText: String, glyphs: List<Glyph>, frame: Frame? = null): String {
    val digits = SummaryParser.digitRun(ocrText)
    if (digits != null && digits.length == glyphs.size) return ocrText
    onesFallback(glyphs)?.let { return it }
    if (frame != null && glyphs.isNotEmpty()) {
      val shapes = glyphs.map { classify(frame, it) }
      if (shapes.all { it != null }) return shapes.joinToString("")
    }
    return ocrText
  }

  /**
   * Reads a digit from its shape alone, for the digits that have a clear signature in PokeMMO's font
   * (hole positions as a fraction of the glyph height, measured on the Thor):
   * 1 narrow, no hole · 0 one tall hole (≈0.19-0.76) · 6 one low hole (≈0.52-0.76) · 9 one high hole (≈0.19-0.43)
   * · 4 one small mid hole (≈0.33-0.52) · 8 two holes. 2, 3, 5 and 7 have no hole and return null (OCR only).
   */
  fun classify(frame: Frame, g: Glyph): Char? {
    if (g.isNarrow) return '1'
    val holes = holes(frame, g)
    return when (holes.size) {
      2 -> '8'
      1 -> {
        val (top, bottom) = holes[0]
        when {
          bottom - top >= 0.45f -> '0'
          top >= 0.45f -> '6'
          top < 0.3f && bottom <= 0.5f -> '9'
          top >= 0.25f && bottom <= 0.6f -> '4'
          else -> null
        }
      }
      else -> null
    }
  }

  /** Enclosed background regions inside a glyph, as (top, bottom) fractions of its height. */
  internal fun holes(frame: Frame, g: Glyph): List<Pair<Float, Float>> {
    // Text mask with a 1-pixel empty border, so the outside is one connected background region.
    val w = g.width + 2
    val h = g.height + 2
    val ink = BooleanArray(w * h)
    for (y in 0 until g.height) for (x in 0 until g.width) ink[(y + 1) * w + x + 1] = isText(frame.pixel(g.left + x, g.top + y))
    val seen = BooleanArray(w * h)
    val queue = ArrayDeque<Int>()
    fun fill(start: Int): IntArray { // returns [minY, maxY, size]
      var minY = Int.MAX_VALUE
      var maxY = Int.MIN_VALUE
      var size = 0
      queue.addLast(start)
      seen[start] = true
      while (queue.isNotEmpty()) {
        val i = queue.removeFirst()
        val y = i / w
        val x = i % w
        minY = minOf(minY, y)
        maxY = maxOf(maxY, y)
        size++
        fun visit(n: Int) {
          if (!seen[n] && !ink[n]) {
            seen[n] = true
            queue.addLast(n)
          }
        }
        if (x > 0) visit(i - 1)
        if (x < w - 1) visit(i + 1)
        if (y > 0) visit(i - w)
        if (y < h - 1) visit(i + w)
      }
      return intArrayOf(minY, maxY, size)
    }
    fill(0) // outside
    val holes = mutableListOf<Pair<Float, Float>>()
    for (i in 0 until w * h) {
      if (seen[i] || ink[i]) continue
      val (minY, maxY, size) = fill(i).toList()
      if (size >= 3) holes += ((minY - 1).toFloat() / g.height) to ((maxY - 1).toFloat() / g.height)
    }
    return holes
  }

  /**
   * Several boxes' characters as one black-on-white image: each box becomes a row, enlarged [scale]×, with a blank
   * row's worth of space between rows. ML Kit reads a column of numbers far more reliably than a lone digit.
   */
  class Stack(val width: Int, val height: Int, val pixels: IntArray, private val margin: Int, private val pitch: Int, val rows: Int) {
    /** Which row a line of OCR text belongs to, from its vertical center in this image. */
    fun rowAt(centerY: Int): Int? = ((centerY - margin) / pitch).takeIf { centerY >= margin && it in 0 until rows }
  }

  fun stack(frame: Frame, rows: List<List<Glyph>>, scale: Int, margin: Int = 40): Stack? {
    if (rows.all { it.isEmpty() }) return null
    val pad = 4
    // Crop rectangle per row (null = empty box).
    val rects =
      rows.map { g ->
        if (g.isEmpty()) null
        else
          intArrayOf(
            (g.first().left - pad).coerceAtLeast(0),
            (g.minOf { it.top } - pad).coerceAtLeast(0),
            (g.last().right + pad).coerceAtMost(frame.width),
            (g.maxOf { it.bottom } + pad).coerceAtMost(frame.height),
          )
      }
    val rowH = rects.filterNotNull().maxOf { it[3] - it[1] } * scale
    val rowW = rects.filterNotNull().maxOf { it[2] - it[0] } * scale
    val pitch = rowH * 2
    val w = rowW + 2 * margin
    val h = pitch * rows.size + 2 * margin
    val out = IntArray(w * h) { WHITE }
    rects.forEachIndexed { i, r ->
      if (r == null) return@forEachIndexed
      val (left, top, right, bottom) = r.toList()
      for (y in top until bottom) for (x in left until right) {
        if (!isText(frame.pixel(x, y))) continue
        val ox = margin + (x - left) * scale
        val oy = margin + i * pitch + (rowH / 2 - (bottom - top) * scale / 2) + (y - top) * scale
        for (dy in 0 until scale) for (dx in 0 until scale) out[(oy + dy) * w + ox + dx] = BLACK
      }
    }
    return Stack(w, h, out, margin, pitch, rows.size)
  }

  private const val WHITE = -0x1 // 0xFFFFFFFF
  private const val BLACK = -0x1000000 // 0xFF000000
}
