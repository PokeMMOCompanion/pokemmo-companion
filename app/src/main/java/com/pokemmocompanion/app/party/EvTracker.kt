package com.pokemmocompanion.app.party

import com.pokemmocompanion.app.calc.SpeciesData
import com.pokemmocompanion.app.calc.Stat
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * EVs gained since each party member's summary was last read, plus the player's targets. [baseline] is the EV
 * spread the gains were counted on top of; when a fresh summary read differs, gains start over from the new values.
 * Maps are keyed by party slot (0-5); lists are in [Stat] order.
 */
@Serializable
data class EvState(
  val baseline: Map<Int, List<Int>> = emptyMap(),
  val gains: Map<Int, List<Int>> = emptyMap(),
  val targets: Map<Int, List<Int>> = emptyMap(),
)

/**
 * EV rules in PokeMMO: every defeated Pokémon gives its EV yield to each party member that battled it and to every
 * Exp. Share holder; a Macho Brace doubles it (Power items are breeding items there). Caps: 252 per stat, 510 total.
 */
object EvRules {
  const val STAT_CAP = 252
  const val TOTAL_CAP = 510

  fun holdsExpShare(m: PartyMember) = m.item?.replace(".", "")?.contains("Exp Share", ignoreCase = true) == true

  fun holdsMachoBrace(m: PartyMember) = m.item?.contains("Macho Brace", ignoreCase = true) == true

  /** EVs each receiving slot gets from [defeated]: slots that battled ([participants]) plus Exp. Share holders. */
  fun award(defeated: List<SpeciesData>, participants: Set<Int>, party: List<PartyMember?>): Map<Int, List<Int>> {
    val yieldSum = List(6) { i -> defeated.sumOf { it.evYield[i] } }
    if (yieldSum.all { it == 0 }) return emptyMap()
    return party.withIndex()
      .filter { (slot, m) -> m != null && (slot in participants || holdsExpShare(m)) }
      .associate { (slot, m) -> slot to yieldSum.map { it * if (holdsMachoBrace(m!!)) 2 else 1 } }
  }

  /** Adds [add] to [current] within the caps (per stat first, then the total, in stat order). */
  fun addCapped(current: List<Int>, add: List<Int>): List<Int> {
    val out = current.toMutableList()
    for (i in 0 until 6) {
      val room = minOf(STAT_CAP - out[i], TOTAL_CAP - out.sum()).coerceAtLeast(0)
      out[i] += minOf(add[i], room)
    }
    return out
  }
}

/** [EvState] saved as JSON (files/evs.json). */
class EvStore(private val file: File) {
  private val json = Json { ignoreUnknownKeys = true }

  var state: EvState = runCatching { json.decodeFromString<EvState>(file.readText()) }.getOrDefault(EvState())
    private set

  private fun save(s: EvState): EvState {
    state = s
    file.writeText(json.encodeToString(EvState.serializer(), s))
    return s
  }

  /** Current EVs of [slot]: the last summary read plus gains since, within the caps. */
  fun current(slot: Int, member: PartyMember): List<Int>? {
    val read = member.evs ?: return null
    val base = state.baseline[slot]
    val gains = if (base == read) state.gains[slot] ?: List(6) { 0 } else List(6) { 0 }
    return EvRules.addCapped(read, gains)
  }

  /**
   * Applies a battle's awards. Returns, per slot, the stats that just reached their target (for the alert).
   */
  fun apply(awards: Map<Int, List<Int>>, party: List<PartyMember?>): Map<Int, List<Stat>> {
    var s = state
    val reached = mutableMapOf<Int, List<Stat>>()
    for ((slot, add) in awards) {
      val m = party.getOrNull(slot) ?: continue
      val read = m.evs ?: continue
      val before = current(slot, m) ?: continue
      // A new summary read starts the count over from the game's own numbers.
      val oldGains = if (s.baseline[slot] == read) s.gains[slot] ?: List(6) { 0 } else List(6) { 0 }
      val after = EvRules.addCapped(before, add)
      val gains = oldGains.zip(after.zip(before)) { g, (a, b) -> g + (a - b) }
      s = s.copy(baseline = s.baseline + (slot to read), gains = s.gains + (slot to gains))
      val target = s.targets[slot]
      if (target != null) {
        val hit = Stat.entries.filter { st -> target[st.ordinal] > 0 && before[st.ordinal] < target[st.ordinal] && after[st.ordinal] >= target[st.ordinal] }
        if (hit.isNotEmpty()) reached[slot] = hit
      }
    }
    save(s)
    return reached
  }

  fun setTarget(slot: Int, stat: Stat, value: Int) {
    val t = (state.targets[slot] ?: List(6) { 0 }).toMutableList()
    t[stat.ordinal] = value.coerceIn(0, EvRules.STAT_CAP)
    save(state.copy(targets = state.targets + (slot to t)))
  }

  /** Forget gains for [slot] (e.g. after using EV-lowering berries). */
  fun resetGains(slot: Int) = save(state.copy(gains = state.gains - slot, baseline = state.baseline - slot))
}

/** What was defeated in a finished wild battle, from its Pokémon and the battle messages. */
object BattleOutcome {
  /**
   * Won (no run, no catch): everything in the battle was defeated. Otherwise only those with a "The wild X fainted!"
   * message (each message counts once per Pokémon of that name, up to how many were there).
   */
  fun defeated(mons: List<String>, messages: List<String>): List<String> {
    val lower = messages.map { it.lowercase() }
    val ran = lower.any { "got away" in it }
    val caught = lower.any { "gotcha" in it || "was caught" in it }
    if (!ran && !caught && lower.any { "fainted" in it && "wild" in it }) return mons
    val faints = lower.filter { "fainted" in it && "wild" in it }
    return mons.groupBy { it }.flatMap { (name, group) ->
      val n = faints.count { name.lowercase() in it }
      group.take(minOf(n, group.size))
    }
  }
}
