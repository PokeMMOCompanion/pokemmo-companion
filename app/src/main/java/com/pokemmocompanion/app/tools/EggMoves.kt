package com.pokemmocompanion.app.tools

import com.pokemmocompanion.app.calc.GameData
import com.pokemmocompanion.app.calc.SpeciesData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Egg move parent chains from PokeMMO Hub's Egg Moves Calculator (assets/pokemmo/egg_moves.json). Hub lists them per
 * base-form species and move, each chain from the direct father down to the Pokémon that learns the move itself.
 */
class EggMoves(private val chains: Map<Int, Map<Int, List<List<Pair<Int, Int>>>>>) {
  /** One breeding step: [father] (knowing the move, learned [how]) breeds with [mother] to pass it on. */
  data class Step(val father: SpeciesData, val how: String, val mother: SpeciesData)

  /** The species egg moves are listed under: the first form of its evolution family. */
  fun baseOf(s: SpeciesData, data: GameData): SpeciesData {
    var b = s
    while (b.evolvesFrom != 0) b = data.species(b.evolvesFrom) ?: break
    return b
  }

  /** Egg move ids with at least one known chain for [s]'s family. */
  fun moves(s: SpeciesData, data: GameData): List<Int> = chains[baseOf(s, data).id]?.keys?.sorted().orEmpty()

  /** Every way to get [moveId] onto [s], each as steps in breeding order (do the first one first); shortest first. */
  fun routes(s: SpeciesData, moveId: Int, data: GameData): List<List<Step>> {
    val target = baseOf(s, data)
    return chains[target.id]?.get(moveId).orEmpty()
      .mapNotNull { chain ->
        val mons = chain.map { (id, how) -> (data.species(id) ?: return@mapNotNull null) to how }
        // chain[0] fathers the target, chain[i+1] fathers chain[i]; breed from the far end.
        mons.indices.reversed().map { i ->
          Step(mons[i].first, describe(mons[i].second), if (i == 0) target else mons[i - 1].first)
        }
      }
      .sortedBy { it.size }
  }

  companion object {
    /** Hub's learn codes: real levels up to 100, then special values. */
    fun describe(code: Int): String =
      when (code) {
        101 -> "special/event move"
        105 -> "egg move + Light Ball"
        106 -> "on evolution / Move Relearner"
        107 -> "as its pre-evolution"
        108 -> "as an egg move (breed it first)"
        else -> "Lv $code"
      }

    fun parse(text: String): EggMoves {
      val root = Json.parseToJsonElement(text).jsonObject
      return EggMoves(
        root.entries.associate { (target, moves) ->
          target.toInt() to
            moves.jsonObject.entries.associate { (move, list) ->
              move.toInt() to
                list.jsonArray.map { chain ->
                  chain.jsonArray.map { step -> step.jsonArray.let { it[0].jsonPrimitive.int to it[1].jsonPrimitive.int } }
                }
            }
        }
      )
    }
  }
}
