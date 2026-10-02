package com.pokemmocompanion.app.tools

import com.pokemmocompanion.app.calc.Stat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * IV breeding plans from PokeMMO Hub's breeding simulator (assets/pokemmo/breeding.json). A plan is a tournament:
 * the bottom row is the 1×31 parents to catch or buy, every pair breeds into the row above, the top is the result.
 * Each Pokémon in the tree is the set of perfect IVs it carries; index 0 means the nature (Everstone) and 1..n are
 * the chosen stats in order.
 */
class Breeding(private val patterns: Map<String, Map<Int, Map<Int, List<List<Int>>>>>) {
  /** [rows] from the parents (index 0) up to the result; each Pokémon as its perfect stats (null = nature). */
  data class Plan(val rows: List<List<List<Stat?>>>, val withNature: Boolean) {
    /** 1×31 parents needed per stat (null = Pokémon with the right nature). */
    val parents: Map<Stat?, Int>
      get() = rows.first().groupingBy { it.single() }.eachCount()

    /** Breeds to do: every Pokémon above the bottom row is one breed. */
    val breeds: Int
      get() = rows.drop(1).sumOf { it.size }

    /** Held items used up: each breed takes a brace (or Everstone) on both parents. */
    val heldItems: Int
      get() = breeds * 2
  }

  /** Plan for [stats] (2-5 different stats, in priority order), optionally also passing a nature. */
  fun plan(stats: List<Stat>, withNature: Boolean): Plan? {
    val key = if (withNature) "nature" else "random"
    val rows = patterns[key]?.get(stats.size) ?: return null
    return Plan(
      rows.toSortedMap().values.map { row -> row.map { mon -> mon.map { if (it == 0) null else stats[it - 1] } } },
      withNature,
    )
  }

  companion object {
    /** Hub's own cost estimates (Pokéyen) for braces etc., used when no GTL prices are loaded. */
    val HUB_COST_ESTIMATE = mapOf(true to mapOf(2 to 75_000, 3 to 170_000, 4 to 355_000, 5 to 715_000), false to mapOf(2 to 20_000, 3 to 65_000, 4 to 155_000, 5 to 340_000))

    fun parse(text: String): Breeding {
      val root = Json.parseToJsonElement(text).jsonObject
      val patterns =
        root.mapValues { (_, byCount) ->
          byCount.jsonObject.entries.associate { (ivKey, rows) ->
            ivKey.removePrefix("iv").toInt() to
              rows.jsonObject.entries.associate { (row, mons) ->
                row.toInt() to mons.jsonArray.map { mon -> mon.jsonArray.map { it.jsonPrimitive.int } }
              }
          }
        }
      return Breeding(patterns)
    }
  }
}
