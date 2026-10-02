package com.pokemmocompanion.app.party

import com.pokemmocompanion.app.detect.EncounterParser

/** The player's Pokémon currently in battle, as read from its HP box (bottom right). */
data class ActiveReading(val name: String, val level: Int?, val hp: Int?, val maxHp: Int?)

/**
 * Reads the player's HP box: "Charmander Lv. 13" above the bar and "29 / 35" in the dark strip below it.
 * Matches the name to the stored party by nickname (or species when there is no nickname).
 */
object ActivePokemon {
  private val NAME_LEVEL = Regex("""^(.*?)\s*[Ll][vVyY][.,:]?\s*(\d{1,3})""")

  fun parse(nameText: String, hpText: String): ActiveReading? {
    val m = NAME_LEVEL.find(nameText.trim())
    // A status icon (PSN, BRN, ...) or ball icon before the name can come out as a stray letter: "D Charmander".
    val rawName =
      (m?.groupValues?.get(1) ?: nameText).trim().split(Regex("\\s+")).dropWhile { it.length <= 1 }.joinToString(" ")
        .trim { !it.isLetterOrDigit() }
    if (rawName.length < 2) return null
    val level = m?.groupValues?.get(2)?.toIntOrNull()?.takeIf { it in 1..100 }
    val hp = SummaryParser.numbers(hpText)
    return ActiveReading(rawName, level, hp.getOrNull(0), hp.getOrNull(1)?.takeIf { it > 0 })
  }

  /** Party member matching the reading; when names tie (two of a species), the level decides. */
  fun match(party: List<PartyMember>, r: ActiveReading): PartyMember? {
    fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    val name = norm(r.name)
    val scored =
      party.mapNotNull { m ->
        val candidates = listOfNotNull(m.nickname, m.species).map(::norm)
        val dist = candidates.minOfOrNull { EncounterParser.editDistance(it, name) } ?: return@mapNotNull null
        val limit = maxOf(1, name.length / 4)
        if (dist > limit) null else Triple(m, dist, if (r.level != null && m.level == r.level) 0 else 1)
      }
    return scored.minWithOrNull(compareBy({ it.second }, { it.third }))?.first
  }
}
