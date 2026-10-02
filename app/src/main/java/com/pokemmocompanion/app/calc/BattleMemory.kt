package com.pokemmocompanion.app.calc

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a battle has revealed about one opposing Pokémon, and what its stats can still be.
 *
 * Stats are narrowed by checking every possible value (any IVs 0-31, EVs 0-252, any nature) against what was seen:
 * - your hit took X% of its HP bar: only (HP, Def/SpD) pairs that turn your known damage roll into X% stay;
 * - its hit took N HP from you (exact, from your HP box): only Atk/SpA values that roll N against your stats stay;
 * - it moved before/after you in a turn (no priority, nobody paralyzed): its Speed is above/below yours.
 * When a hit fits no value at all, something unmodeled is boosting it (item, ability, weather, screens): that stat
 * starts over and a note says so.
 */
class OpponentMemory(val species: SpeciesData, val level: Int) {
  val moves = linkedSetOf<String>()
  var ability: String? = null
    private set
  var item: String? = null
    private set
  val notes = mutableListOf<String>()
  /** What each of your moves actually did to it, as shown ("38%", "≥37% KO"). */
  val measured = linkedMapOf<String, MutableList<String>>()
  var hits = 0
    private set

  private val hpValues = Inference.values(species, Stat.HP, level)
  private val defense = mapOf(Stat.DEF to Grid(hpValues, Inference.values(species, Stat.DEF, level)), Stat.SPD to Grid(hpValues, Inference.values(species, Stat.SPD, level)))
  private val offense = mapOf(Stat.ATK to Candidates(Inference.values(species, Stat.ATK, level)), Stat.SPA to Candidates(Inference.values(species, Stat.SPA, level)))
  private val speed = Candidates(Inference.values(species, Stat.SPE, level))

  fun revealMove(name: String) {
    moves += name
  }

  fun revealAbility(name: String) {
    if (ability == null) ability = name
  }

  fun revealItem(name: String) {
    if (item == null) item = name
  }

  /** Inferred range of [stat], or null while nothing narrowed it. */
  fun range(stat: Stat): IntRange? =
    when (stat) {
      Stat.HP -> defense.values.filter { it.observed }.mapNotNull { it.hpRange() }.reduceOrNull { a, b -> maxOf(a.first, b.first)..minOf(a.last, b.last) }
      Stat.DEF, Stat.SPD -> defense.getValue(stat).let { if (it.observed) it.statRange() else null }
      Stat.ATK, Stat.SPA -> offense.getValue(stat).let { if (it.observed) it.range() else null }
      Stat.SPE -> if (speed.observed) speed.range() else null
    }

  /**
   * Your move hit it: its HP bar went from [before] to [after] (fractions). [you] is your battler with its stages;
   * [itsStages] the opponent's stages. A KO (after = 0) only says the hit could do at least what was left.
   */
  fun onHitTaken(
    you: Battler,
    move: MoveData,
    crit: Boolean,
    before: Double,
    after: Double,
    itsStages: Map<Stat, Int>,
    field: Field = Field.NONE,
  ) {
    if (!move.isDamaging || before <= after) return
    val stat = if (move.category == MoveCategory.PHYSICAL) Stat.DEF else Stat.SPD
    val ko = after <= 0.005
    val drop = before - after
    measured.getOrPut(move.name) { mutableListOf() } += if (ko) "≥${pct(before)} KO" else pct(drop) + if (crit) " crit" else ""
    val ok =
      defense.getValue(stat).filter { hp, d ->
        val foe = Inference.battler(species, level, hp, mapOf(stat to d), itsStages)
        val r = Damage.range(you, foe, move, crit = crit, field = field) ?: return@filter true
        val lo = r.min.toDouble() / hp
        val hi = r.max.toDouble() / hp
        if (ko) hi >= before - TOLERANCE else drop in (lo - TOLERANCE)..(hi + TOLERANCE)
      }
    hits++
    if (!ok) notes += "${move.name} did ${pct(drop)}: more or less than any ${stat.short} allows (item, ability, weather or screens?)"
  }

  /** Its move hit you for [damage] HP (exact). [you] is your battler with its HP before the hit. */
  fun onHitDealt(you: Battler, move: MoveData, crit: Boolean, damage: Int, ko: Boolean, itsStages: Map<Stat, Int>, field: Field = Field.NONE) {
    if (!move.isDamaging || damage <= 0) return
    val stat = if (move.category == MoveCategory.PHYSICAL) Stat.ATK else Stat.SPA
    val ok =
      offense.getValue(stat).filter { v ->
        val foe = Inference.battler(species, level, 1, mapOf(stat to v), itsStages, ability)
        val r = Damage.range(foe, you, move, crit = crit, field = field) ?: return@filter true
        if (ko) r.max >= damage - 1 else damage in (r.min - 1)..(r.max + 1)
      }
    hits++
    if (!ok) notes += "${move.name} did $damage: more or less than any ${stat.short} allows (boosting item or ability?)"
  }

  /** It moved first ([itFirst]) or second against your effective speed [yourSpe]. */
  fun onTurnOrder(itFirst: Boolean, yourSpe: Int, itsStage: Int) {
    val ok = speed.filter { v -> Stages.apply(v, itsStage).let { if (itFirst) it >= yourSpe else it <= yourSpe } }
    if (!ok) notes += "Turn order doesn't fit any Speed (Quick Claw, Choice Scarf or Speed changes?)"
  }

  fun snapshot(seenBefore: SeenSet? = null) =
    Revealed(
      species.name, level, moves.toList(), ability, item, Stat.entries.mapNotNull { s -> range(s)?.let { s to it } }.toMap(), notes.toList(), hits,
      seenBefore, measured.mapValues { it.value.toList() },
    )

  /** Worst case for the player within what's known: highest possible value of each stat. */
  fun worstCase(base: Map<Stat, Int>): Map<Stat, Int> = base.mapValues { (stat, v) -> range(stat)?.last ?: v }

  private class Candidates(private val values: List<Int>) {
    private var allowed = BooleanArray(values.size) { true }
    var observed = false

    /** Keeps values passing [test]; returns false (and resets) when none would. */
    fun filter(test: (Int) -> Boolean): Boolean {
      val next = BooleanArray(values.size) { allowed[it] && test(values[it]) }
      observed = true
      return if (next.any { it }) {
        allowed = next
        true
      } else {
        allowed = BooleanArray(values.size) { true }
        false
      }
    }

    fun range(): IntRange? {
      val kept = values.filterIndexed { i, _ -> allowed[i] }
      return if (kept.isEmpty()) null else kept.min()..kept.max()
    }
  }

  /** Allowed (HP, stat) pairs. */
  private class Grid(private val hp: List<Int>, private val stat: List<Int>) {
    private var allowed = BooleanArray(hp.size * stat.size) { true }
    var observed = false

    fun filter(test: (Int, Int) -> Boolean): Boolean {
      val next = BooleanArray(allowed.size) { i -> allowed[i] && test(hp[i / stat.size], stat[i % stat.size]) }
      observed = true
      return if (next.any { it }) {
        allowed = next
        true
      } else {
        allowed = BooleanArray(allowed.size) { true }
        false
      }
    }

    fun hpRange(): IntRange? = project { it / stat.size }?.let { (a, b) -> hp[a]..hp[b] }

    fun statRange(): IntRange? = project { it % stat.size }?.let { (a, b) -> stat[a]..stat[b] }

    /** Smallest and largest index (in the sorted value list) that still has an allowed pair. */
    private fun project(index: (Int) -> Int): Pair<Int, Int>? {
      var lo = Int.MAX_VALUE
      var hi = -1
      for (i in allowed.indices) if (allowed[i]) {
        val k = index(i)
        if (k < lo) lo = k
        if (k > hi) hi = k
      }
      return if (hi < 0) null else lo to hi
    }
  }

  companion object {
    /** HP bar reading tolerance (fraction of the bar). */
    const val TOLERANCE = 0.02

    private fun pct(f: Double) = "${(f * 100).toInt()}%"
  }
}

/** What the UI shows about an opponent: revealed moves/ability/item, narrowed stat ranges and notes. */
data class Revealed(
  val species: String,
  val level: Int,
  val moves: List<String>,
  val ability: String?,
  val item: String?,
  val ranges: Map<Stat, IntRange>,
  val notes: List<String>,
  val hits: Int,
  /** The same species and level met in an earlier trainer battle. */
  val seenBefore: SeenSet? = null,
  /** Your moves' actual damage to it this battle, by move name. */
  val measured: Map<String, List<String>> = emptyMap(),
) {
  /**
   * [p] narrowed by what's known, revealed moves first. Worst case per stat: for wild Pokémon (0 EVs) the generic
   * worst case, lowered by inference; for trainers and players (any EVs) the highest value the inference still
   * allows, which starts at 252 EVs.
   */
  fun narrow(p: OpponentProfile, data: GameData, wild: Boolean): OpponentProfile {
    val seen = (moves + (seenBefore?.moves ?: emptyList())).distinct().mapNotNull(data::move)
    val sp = data.species(species)
    return p.copy(
      maxStats =
        p.maxStats.mapValues { (s, v) ->
          val top = ranges[s]?.last ?: if (wild || sp == null) v else Inference.values(sp, s, level).last()
          if (wild) minOf(top, v) else top
        },
      possibleMoves = (seen + p.possibleMoves).distinctBy { it.id },
      speedRange = ranges[Stat.SPE],
      knownAbility = ability,
    )
  }
}

/** Moves, ability and item an opposing trainer's Pokémon showed before (same species and level). */
@Serializable data class SeenSet(val moves: List<String> = emptyList(), val ability: String? = null, val item: String? = null, val at: Long = 0)

/** "Seen before" sets for trainer rematches, saved as JSON. Keyed by "Species|level". */
class SeenLibrary(private val file: File) {
  private val json = Json { ignoreUnknownKeys = true }
  private var sets: Map<String, SeenSet> = runCatching { json.decodeFromString<Map<String, SeenSet>>(file.readText()) }.getOrDefault(emptyMap())

  fun get(species: String, level: Int): SeenSet? = sets["$species|$level"]

  fun remember(r: Revealed, now: Long) {
    if (r.moves.isEmpty() && r.ability == null && r.item == null) return
    val key = "${r.species}|${r.level}"
    val old = sets[key]
    sets = sets + (key to SeenSet((r.moves + (old?.moves ?: emptyList())).distinct().take(8), r.ability ?: old?.ability, r.item ?: old?.item, now))
    file.writeText(json.encodeToString(sets))
  }
}

/** Shared helpers for the inference. */
object Inference {
  /** Every value [stat] can have at [level]: IVs 0-31, EVs 0-252, nature -/neutral/+. Sorted, distinct. */
  fun values(s: SpeciesData, stat: Stat, level: Int): List<Int> {
    val out = sortedSetOf<Int>()
    val natures = if (stat == Stat.HP) listOf(Nature.HARDY) else listOf(Nature.HARDY) + Nature.entries.filter { it.up == stat || it.down == stat }.distinctBy { it.up == stat }
    for (n in natures) for (iv in 0..Stats.MAX_IV) for (ev in 0..252 step 4) out += Stats.value(stat, s.base(stat), iv, ev, level, n)
    return out.toList()
  }

  /** The opponent as a battler with only the stats under test set ([hp] and [stats]); the rest don't matter. */
  fun battler(s: SpeciesData, level: Int, hp: Int, stats: Map<Stat, Int>, stages: Map<Stat, Int>, ability: String? = null) =
    Battler(
      name = s.name,
      types = s.types,
      level = level,
      hp = hp,
      maxHp = hp,
      atk = stats[Stat.ATK] ?: 1,
      def = stats[Stat.DEF] ?: 1,
      spa = stats[Stat.SPA] ?: 1,
      spd = stats[Stat.SPD] ?: 1,
      spe = stats[Stat.SPE] ?: 1,
      ability = ability,
      stages = stages,
    )
}

/**
 * Picks revealed abilities and items out of battle messages about an opposing Pokémon, e.g.
 * "Gyarados's Intimidate cuts Charmander's attack!", "The foe's Snorlax's Leftovers restored a little HP!",
 * "Snorlax ate its Sitrus Berry!", "Dugtrio hung on using its Focus Sash!".
 */
object Reveal {
  fun ability(text: String, s: SpeciesData): String? {
    val t = text.lowercase()
    if (s.name.lowercase() !in t) return null
    return s.abilities.filter { it != "--" }.firstOrNull { a -> "'s ${a.lowercase()}" in t || "${s.name.lowercase()}'s ${a.lowercase()}" in t }
  }

  /** [items]: names to look for (held items; longest first so "Sitrus Berry" beats "Berry"). */
  fun item(text: String, s: SpeciesData, items: List<String>): String? {
    val t = text.lowercase()
    if (s.name.lowercase() !in t) return null
    return items.firstOrNull { i ->
      val n = i.lowercase()
      "'s $n" in t || "its $n" in t
    }
  }
}
