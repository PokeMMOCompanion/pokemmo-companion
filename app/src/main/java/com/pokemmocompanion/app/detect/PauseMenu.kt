package com.pokemmocompanion.app.detect

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the pause menu's header says (right-hand panel, top): "Route 2 Ch. 5", the money, then
 * "Wednesday, 02:33, Summer". It's the only place the game names the player's location.
 */
data class MenuInfo(val place: String, val channel: Int?, val hour: Int?, val minute: Int?, val season: String?) {
  /** Hub's time-of-day buckets: Morning 4-10, Day 10-20, Night 20-4 (game clock). */
  val timeOfDay: String?
    get() =
      hour?.let {
        when (it) {
          in 4..9 -> "Morning"
          in 10..19 -> "Day"
          else -> "Night"
        }
      }
}

object PauseMenu {
  private const val REF_W = 1920f
  private const val REF_H = 1080f
  /** The panel's flat background. */
  private const val PANEL = 0x2B343D
  private val SAMPLES = listOf(1060 to 980, 1300 to 1000, 1600 to 1040, 1900 to 960, 1045 to 600, 1500 to 200, 1060 to 300, 1700 to 180)

  /** Header text area (reference coordinates): place, money and the date line. */
  const val HEADER_LEFT = 1090
  const val HEADER_TOP = 8
  const val HEADER_RIGHT = 1745
  const val HEADER_BOTTOM = 165

  /** True when the right-hand menu panel is open: its flat slate background shows at nearly all sample points. */
  fun isOpen(frame: Frame): Boolean {
    val sx = frame.width / REF_W
    val sy = frame.height / REF_H
    val hits =
      SAMPLES.count { (x, y) ->
        val p = frame.pixel((x * sx).roundToInt().coerceIn(0, frame.width - 1), (y * sy).roundToInt().coerceIn(0, frame.height - 1))
        abs(((p shr 16) and 0xFF) - ((PANEL shr 16) and 0xFF)) <= 6 &&
          abs(((p shr 8) and 0xFF) - ((PANEL shr 8) and 0xFF)) <= 6 &&
          abs((p and 0xFF) - (PANEL and 0xFF)) <= 6
      }
    return hits >= SAMPLES.size - 1
  }

  private val CHANNEL = Regex("""\s+Ch\s*[.,]?\s*(\d+)\s*$""", RegexOption.IGNORE_CASE)
  private val CLOCK = Regex("""(\d{1,2})\s*[:.]\s*(\d{2})""")
  private val SEASONS = listOf("Spring", "Summer", "Autumn", "Winter")

  /** Reads the header lines (top to bottom). Null when no line looks like a place name. */
  fun parse(lines: List<String>): MenuInfo? {
    val clean = lines.map { GameText.clean(it).trim() }.filter { it.isNotEmpty() }
    // The place is the line with the channel; failing that (channel misread), the first line that isn't money or the
    // date, but only when the date line with its season is there to show this really is the menu header.
    val hasDate = clean.any { l -> CLOCK.containsMatchIn(l) && SEASONS.any { l.contains(it, ignoreCase = true) } }
    val placeLine =
      clean.firstOrNull { CHANNEL.containsMatchIn(it) }
        ?: clean.firstOrNull { hasDate && !it.contains('$') && !CLOCK.containsMatchIn(it) && it.any(Char::isLetter) }
        ?: return null
    val channel = CHANNEL.find(placeLine)?.groupValues?.get(1)?.toIntOrNull()
    val place = CHANNEL.replace(placeLine, "").trim().trimStart { !it.isLetterOrDigit() }
    if (place.length < 3) return null
    val dateLine = clean.firstOrNull { CLOCK.containsMatchIn(it) && it !== placeLine }
    val clock = dateLine?.let { CLOCK.find(it) }
    val season = dateLine?.let { d -> SEASONS.firstOrNull { s -> d.contains(s, ignoreCase = true) } }
    return MenuInfo(
      place = place,
      channel = channel,
      hour = clock?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..23 },
      minute = clock?.groupValues?.get(2)?.toIntOrNull()?.takeIf { it in 0..59 },
      season = season,
    )
  }
}
