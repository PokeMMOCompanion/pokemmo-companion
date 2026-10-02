package com.pokemmocompanion.app.calc

import com.pokemmocompanion.app.detect.BattleContext
import com.pokemmocompanion.app.detect.EncounterParser
import com.pokemmocompanion.app.detect.Species
import com.pokemmocompanion.app.detect.WildMon
import com.pokemmocompanion.app.party.PartyMember

/**
 * Fills battle-message slots from what the app knows about the battle: the player's party (nicknames, species and
 * their four moves) and the current opponents (species and the level-up moves they can have). These short lists
 * allow much looser matching than the full species/move lists, so "Thurder laWe" from Pikachu still resolves to
 * Thunder Wave. Falls back to the full lists.
 */
class BattleKnowledge(
  private val data: GameData,
  private val party: List<PartyMember>,
  private val opponents: List<WildMon>,
) : BattleContext {
  private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() || it == '♀' || it == '♂' }

  /** Closest candidate within ~40% of the letters, or null. */
  private fun closest(raw: String, candidates: Collection<String>): String? {
    val r = norm(raw)
    if (r.length < 2) return null
    return candidates
      .map { it to EncounterParser.editDistance(r, norm(it)) }
      .filter { (c, d) -> d <= maxOf(1, maxOf(r.length, norm(c).length) * 2 / 5) }
      .minByOrNull { it.second }
      ?.first
  }

  private val partyNames = party.flatMap { listOfNotNull(it.nickname, it.species) }.distinct()
  private val opponentNames = opponents.map { it.name }.distinct()

  override fun pokemon(raw: String): String? = closest(raw, partyNames + opponentNames) ?: Species.match(raw)

  override fun move(actor: String?, raw: String): String? {
    val known =
      if (actor == null) {
        emptyList()
      } else {
        party.firstOrNull { it.nickname == actor || it.species == actor }?.moves
          ?: opponents.firstOrNull { it.name == actor }?.let { mon ->
            data.species(mon.name)?.let { WildMoves.possible(it, mon.level) }
          }
          ?: emptyList()
      }
    val letters = GameData.digitsAsLetters(raw)
    return closest(letters, known) ?: data.matchMove(letters)?.name
  }
}
