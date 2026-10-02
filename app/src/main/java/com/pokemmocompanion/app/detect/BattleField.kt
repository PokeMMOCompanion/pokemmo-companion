package com.pokemmocompanion.app.detect

import com.pokemmocompanion.app.calc.FieldState
import com.pokemmocompanion.app.calc.Weather

/**
 * Follows the battle's conditions from its messages: weather starting/continuing/ending, and Reflect / Light Screen
 * going up or wearing off on each side. Loose matching, since OCR garbles words ("It. is rainiig.").
 */
object BattleField {
  fun apply(state: FieldState, text: String): FieldState {
    val t = text.lowercase()
    var s = state
    when {
      "rain" in t && ("stopped" in t || "stop" in t) -> if (s.weather == Weather.RAIN) s = s.copy(weather = null)
      "rain" in t -> s = s.copy(weather = Weather.RAIN)
      "sunlight" in t && "faded" in t -> if (s.weather == Weather.SUN) s = s.copy(weather = null)
      "sunlight" in t || "sunny" in t -> s = s.copy(weather = Weather.SUN)
      "sandstorm" in t && "subsided" in t -> if (s.weather == Weather.SAND) s = s.copy(weather = null)
      "sandstorm" in t -> s = s.copy(weather = Weather.SAND)
      "hail" in t && "stopped" in t -> if (s.weather == Weather.HAIL) s = s.copy(weather = null)
      "hail" in t -> s = s.copy(weather = Weather.HAIL)
    }
    for (screen in listOf("Reflect", "Light Screen")) {
      val name = screen.lowercase()
      if (name !in t) continue
      // "...the foe's team" / "...the opposing team" is the opponent's side; "your team" is yours.
      val theirs = "foe" in t || "opposing" in t || "enemy" in t
      when {
        "wore off" in t -> s = if (theirs) s.copy(itsScreens = s.itsScreens - screen) else s.copy(yourScreens = s.yourScreens - screen)
        "raised" in t -> s = if (theirs) s.copy(itsScreens = s.itsScreens + screen) else s.copy(yourScreens = s.yourScreens + screen)
      }
    }
    return s
  }
}

/** Party changes announced in battle: "Charmander grew to level 16!", "Charmander learned Ember!". */
object PartyMessages {
  private val LEVEL = Regex("""^(.+?)\s+grew\s+to\s+(?:level|lv\.?)\s*(\d{1,3})""", RegexOption.IGNORE_CASE)
  private val LEARNED = Regex("""^(.+?)\s+learned\s+(.+?)[!.]*$""", RegexOption.IGNORE_CASE)

  fun levelUp(text: String): Pair<String, Int>? =
    LEVEL.find(text.trim())?.let { m -> m.groupValues[2].toIntOrNull()?.takeIf { it in 2..100 }?.let { m.groupValues[1].trim() to it } }

  fun learned(text: String): Pair<String, String>? {
    val t = text.trim()
    if ("did not" in t.lowercase() || "forgot" in t.lowercase()) return null
    return LEARNED.find(t)?.let { m -> m.groupValues[1].trim() to m.groupValues[2].trim() }
  }
}
