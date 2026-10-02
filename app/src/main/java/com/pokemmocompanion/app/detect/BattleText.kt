package com.pokemmocompanion.app.detect

/**
 * Corrects OCR'd battle messages. PokeMMO's pixel font reads with letter-level slips ("Caterpie uged String 5hot.!",
 * "won t go any lOWer", "Cat.erpie", "Tou're"), but battle messages use a small, fixed vocabulary plus Pokémon names,
 * so each word is snapped to a known word when it's one letter off.
 */
object BattleText {
  /** Words battle messages are made of (lowercase, apostrophes included). */
  private val VOCAB =
    """
    the wild foe used fainted gained exp points defense attack speed special sp def atk accuracy evasiveness evasion
    rose sharply drastically fell harshly severely won't can't doesn't isn't didn't wasn't go any higher lower was
    hurt by poison poisoned badly burn burned paralyzed paralysis asleep fast woke up frozen solid thawed out
    confused confusion it's its super effective not very critical hit missed avoided appeared sent broke free oh no
    gotcha caught data added to pokedex transferred pc someone's horde move moves is but it failed affect you're in
    charge come back level grew learned protected itself flinched recoil damaged restored health little bit wore off
    times nothing happened couldn't escape got away safely threw ball almost had shook trainer wants battle defeated
    trapped seeded sapped leech seed snapped stat stats more anymore cannot fled from enemy opposing team your
    """.trim().split(Regex("\\s+")).toSet()

  private val CONTRACTION = Regex("""\b(won|can|doesn|isn|didn|wasn|couldn|it|you|someone)\s*'?\s+(t|s|re)\b""", RegexOption.IGNORE_CASE)

  fun clean(text: String): String {
    var t = text
    // Dots inside words ("Cat.erpie") are OCR specks.
    t = t.replace(Regex("""(?<=\p{L})\.(?=\p{L})"""), "")
    // Split contractions: "won t" / "won' t" → "won't".
    t = CONTRACTION.replace(t) { m -> "${m.groupValues[1]}'${m.groupValues[2]}" }
    // Words after "used" are a move name: leave them for the move-list match (else "Harder" could become Herdier).
    var afterUsed = false
    return t.split(' ').joinToString(" ") { w ->
      if (afterUsed) {
        w
      } else {
        fixWord(w).also { if (it.trimEnd('!', '.').equals("used", ignoreCase = true)) afterUsed = true }
      }
    }
  }

  /** One whitespace-separated word, keeping its leading/trailing punctuation. */
  private fun fixWord(raw: String): String {
    val start = raw.indexOfFirst { it.isLetterOrDigit() }
    if (start < 0) return raw
    val end = raw.indexOfLast { it.isLetterOrDigit() || it == '\'' } + 1
    val whole = raw.substring(start, end)
    // Possessive ("Metapod's"): correct the name and keep the "'s".
    if (whole.length > 3 && whole.endsWith("'s", ignoreCase = true) && whole.lowercase() !in VOCAB) {
      return raw.substring(0, start) + fixWord(whole.dropLast(2)) + whole.takeLast(2) + raw.substring(end)
    }
    val core = whole
    val lower = core.lowercase()
    val fixed =
      when {
        lower in VOCAB -> lower
        core.length >= 4 && core.all { it.isLetter() || it == '\'' } -> {
          Species.match(core)?.takeIf { core[0].isUpperCase() }
            ?: VOCAB.filter { kotlin.math.abs(it.length - lower.length) <= 1 && EncounterParser.editDistance(it, lower) <= 1 }
              .singleOrNull()
            ?: core
        }
        else -> core
      }
    // Keep a capital first letter where the original had one (sentence start, names).
    val cased = if (fixed != core && core[0].isUpperCase() && fixed[0].isLowerCase()) fixed.replaceFirstChar { it.uppercase() } else fixed
    return raw.substring(0, start) + cased + raw.substring(end)
  }
}
