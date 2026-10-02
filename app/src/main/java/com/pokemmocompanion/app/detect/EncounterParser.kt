package com.pokemmocompanion.app.detect

/** [caught] is true when the Pokédex "caught" Poké Ball shows next to the name, null when it wasn't checked. */
data class WildMon(val name: String, val level: Int, val shiny: Boolean, val caught: Boolean? = null)

/** A parsed entry plus where its level number was on screen (null when OCR gave no word positions). */
data class ParsedMon(val mon: WildMon, val levelBox: OcrBox?)

/**
 * Pulls wild Pokémon out of OCR text from the opponent name boxes, e.g. "Pidgey Lv. 4" or "★Shiny Ursaring★ Lv. 58".
 * Horde boxes sit side by side, so one OCR line can hold several entries.
 */
object EncounterParser {
  // Up to 4 capitalized words (e.g. "Mr. Mime", "Porygon2", "Nidoran♀"), then "Lv." in its common OCR variants,
  // then the level.
  private val ENTRY =
    Regex("""([A-Z][\p{L}\d'.\-♀♂]*(?:\s+[A-Z][\p{L}\d'.\-♀♂]*){0,3})\s*[Ll][vVyY][.,:]?\s*(\d{1,3})""")

  /** PokeMMO prefixes shiny names with "Shiny" (and wraps them in stars). */
  private const val SHINY = "shiny"

  fun parse(lines: List<String>): List<WildMon> = parseLines(lines.map { OcrLine(it) }).map { it.mon }

  fun parseLines(lines: List<OcrLine>): List<ParsedMon> = lines.flatMap(::parseLine)

  private fun parseLine(line: OcrLine): List<ParsedMon> {
    // Stars and other decorations become spaces; same length, so offsets still match elements.
    // ♀/♂ stay because they're part of "Nidoran♀"/"Nidoran♂".
    val text = line.text.map { if (it.isLetterOrDigit() || it.isWhitespace() || it in "'.-,:♀♂") it else ' ' }.joinToString("")
    val offsets = elementOffsets(line)
    return ENTRY.findAll(text).mapNotNull { m ->
      // Stray one-character words are usually a misread gender or Poké Ball icon from the box before.
      val words = m.groupValues[1].split(Regex("\\s+")).dropWhile { it.trimEnd('.').length <= 1 }
      if (words.isEmpty()) return@mapNotNull null
      val shiny = words.size > 1 && editDistance(words.first().lowercase(), SHINY) <= 1
      val ocrName = (if (shiny) words.drop(1) else words).joinToString(" ")
      val level = parseLevel(m.groupValues[2]) ?: return@mapNotNull null
      if (ocrName.length < 3) return@mapNotNull null
      // Snap to a real species name; keep the raw text if nothing is close (so it still shows up for tuning).
      val name = Species.match(ocrName) ?: ocrName
      val levelEnd = m.groups[2]!!.range.last
      val box = offsets.firstOrNull { (range, _) -> levelEnd in range }?.second?.box
      ParsedMon(WildMon(name, level, shiny), box)
    }.toList()
  }

  /** Character range of each OCR word within the line text. */
  private fun elementOffsets(line: OcrLine): List<Pair<IntRange, OcrElement>> {
    var cursor = 0
    return line.elements.mapNotNull { el ->
      val start = line.text.indexOf(el.text, cursor)
      if (start < 0 || el.text.isEmpty()) return@mapNotNull null
      cursor = start + el.text.length
      (start until cursor) to el
    }
  }

  /** Levels are 1..100; a third digit above that is usually a misread gender symbol stuck to the number. */
  private fun parseLevel(digits: String): Int? {
    val n = digits.toInt()
    return when {
      n in 1..100 -> n
      digits.length == 3 -> digits.take(2).toInt().takeIf { it in 1..100 }
      else -> null
    }
  }

  internal fun editDistance(a: String, b: String): Int {
    val dp = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
      var prev = dp[0]
      dp[0] = i
      for (j in 1..b.length) {
        val cur = dp[j]
        dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
        prev = cur
      }
    }
    return dp[b.length]
  }
}
