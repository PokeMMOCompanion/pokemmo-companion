package com.pokemmocompanion.app.calc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One wild spawn from the PokeMMO Hub snapshot (assets/pokemmo/locations.json). Rarities are the Hub's strings:
 * "10%", "--" (not at that time), "Special", "Lure".
 *
 * @param horde 0 = single encounter, 3 or 5 = horde size
 * @param seasons "" = all year, else e.g. "Spr/Sum"
 */
data class Spawn(
  val region: String,
  val place: String,
  val method: String,
  val minLevel: Int,
  val maxLevel: Int,
  val horde: Int,
  val morning: String,
  val day: String,
  val night: String,
  val seasons: String,
) {
  val levels: String
    get() = if (minLevel == maxLevel) "Lv $minLevel" else "Lv $minLevel–$maxLevel"

  /** "10%" when the same at every time of day, else "M 10% · D 10% · N --". */
  val rarity: String
    get() = if (morning == day && day == night) morning else "M $morning · D $day · N $night"
}

object Spawns {
  /** Dex number → spawns, sorted by region and place. */
  fun parse(json: String): Map<Int, List<Spawn>> =
    Json.parseToJsonElement(json).jsonObject.mapKeys { it.key.toInt() }.mapValues { (_, list) ->
      list.jsonArray.map { row(it.jsonArray) }
    }

  private fun row(a: JsonArray): Spawn {
    fun str(i: Int) = a[i].jsonPrimitive.content
    fun num(i: Int) = a[i].jsonPrimitive.int
    return Spawn(str(0), str(1), str(2), num(3), num(4), num(5), str(6), str(7), str(8), str(9))
  }
}
