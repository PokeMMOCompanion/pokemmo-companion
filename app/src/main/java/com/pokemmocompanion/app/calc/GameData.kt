package com.pokemmocompanion.app.calc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * PokeMMO battle data for Gen 1-5, from a one-time PokeMMO Hub snapshot (tools/hub_snapshot.py,
 * app/src/main/assets/pokemmo). Plain Kotlin: built from JSON strings so it can be unit tested on the JVM.
 */
@Serializable data class LevelMove(val level: Int, val move: String)

/** [how]: readable condition such as "Lv 16" or "Fire Stone". */
@Serializable data class Evolution(val id: Int, val how: String)

@Serializable
data class SpeciesData(
  val id: Int,
  val name: String,
  val types: List<PokeType>,
  /** Base stats in [Stat] order: HP, Atk, Def, SpA, SpD, Spe. */
  val stats: List<Int>,
  val abilities: List<String>,
  val levelMoves: List<LevelMove>,
  val obtainable: Boolean,
  /** EVs given when defeated, in [Stat] order. */
  val evYield: List<Int> = List(6) { 0 },
  val expYield: Int = 0,
  val catchRate: Int = 0,
  /** Percent female; -1 = genderless. */
  val female: Double = 50.0,
  val eggGroups: List<String> = emptyList(),
  val growth: String = "",
  val heightDm: Int = 0,
  val weightHg: Int = 0,
  val heldItems: List<String> = emptyList(),
  /** Dex number this evolves from; 0 = none. */
  val evolvesFrom: Int = 0,
  val evolutions: List<Evolution> = emptyList(),
  /** Move ids learnable by TM/HM, from tutors, and as egg moves. */
  val tmMoves: List<Int> = emptyList(),
  val tutorMoves: List<Int> = emptyList(),
  val eggMoves: List<Int> = emptyList(),
) {
  fun base(stat: Stat) = stats[stat.ordinal]
}

enum class MoveCategory { PHYSICAL, SPECIAL, STATUS }

@Serializable
data class MoveData(
  val id: Int,
  val name: String,
  val type: PokeType,
  val category: MoveCategory,
  val power: Int,
  /** 101 means the move never misses. */
  val accuracy: Int,
  val pp: Int,
  val priority: Int,
  /** PokeMMO target type; 4 and 5 hit several foes (matters in hordes). */
  val target: Int,
) {
  val isDamaging: Boolean
    get() = category != MoveCategory.STATUS && power > 0

  val hitsMultipleFoes: Boolean
    get() = target == 4 || target == 5
}

class GameData(val species: List<SpeciesData>, val moves: List<MoveData>) {
  private val speciesByName = species.associateBy { key(it.name) }
  private val speciesById = species.associateBy { it.id }
  private val movesByName = moves.associateBy { key(it.name) }
  private val movesById = moves.associateBy { it.id }

  fun species(name: String): SpeciesData? = speciesByName[key(name)]

  fun species(dexId: Int): SpeciesData? = speciesById[dexId]

  /** Move names are compared without spaces/punctuation, so "Thunder Shock" finds "ThunderShock". */
  fun move(name: String): MoveData? = movesByName[key(name)]

  fun move(id: Int): MoveData? = movesById[id]

  /** Every move [s] can learn (level-up, TM/HM, tutor, egg), with how. */
  fun learnable(s: SpeciesData): List<Pair<MoveData, String>> {
    val out = LinkedHashMap<Int, Pair<MoveData, String>>()
    s.levelMoves.forEach { lm -> move(lm.move)?.let { out.putIfAbsent(it.id, it to "Lv ${lm.level}") } }
    s.tmMoves.forEach { id -> move(id)?.let { out.putIfAbsent(id, it to "TM") } }
    s.tutorMoves.forEach { id -> move(id)?.let { out.putIfAbsent(id, it to "Tutor") } }
    s.eggMoves.forEach { id -> move(id)?.let { out.putIfAbsent(id, it to "Egg") } }
    return out.values.toList()
  }

  /** Move for OCR text: exact match, else the single closest name within a small edit distance. */
  fun matchMove(text: String): MoveData? {
    // No move name has digits: OCR's digit look-alikes are letters here ("51ash" → "slash").
    val k = key(digitsAsLetters(text))
    if (k.length < 3) return null
    movesByName[k]?.let { return it }
    val limit = if (k.length <= 6) 1 else 2
    val close = movesByName.entries.map { it to editDistance(k, it.key) }.filter { it.second <= limit }
    val best = close.minOfOrNull { it.second } ?: return null
    return close.filter { it.second == best }.singleOrNull()?.first?.value
  }

  companion object {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(speciesJson: String, movesJson: String) =
      GameData(json.decodeFromString(speciesJson), json.decodeFromString(movesJson))

    private fun editDistance(a: String, b: String): Int {
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

    /** OCR reads some letters as digits: 5/S, 1/l, 0/O, 8/B, 4/A, 6/G, 7/T, 2/Z, 3/E. */
    fun digitsAsLetters(text: String) =
      text.map {
        when (it) {
          '5' -> 's'
          '1' -> 'l'
          '0' -> 'o'
          '8' -> 'b'
          '4' -> 'a'
          '6' -> 'g'
          '7' -> 't'
          '2' -> 'z'
          '3' -> 'e'
          else -> it
        }
      }.joinToString("")

    internal fun key(name: String) = name.lowercase().filter { it.isLetterOrDigit() || it == '♀' || it == '♂' }
  }
}
