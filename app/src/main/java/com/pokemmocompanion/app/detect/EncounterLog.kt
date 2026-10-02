package com.pokemmocompanion.app.detect

import java.io.File

data class Encounter(val timeMillis: Long, val mons: List<WildMon>)

data class EncounterSummary(
  val battles: Int = 0,
  val pokemon: Int = 0,
  val shinies: Int = 0,
  val bySpecies: List<Pair<String, Int>> = emptyList(),
  val last: Encounter? = null,
)

/**
 * Append-only encounter log stored as CSV (one row per Pokémon: time,battle,name,level,shiny,caught).
 * Rows from the same battle share the battle number, so hordes stay grouped.
 */
class EncounterLog(private val file: File) {
  private val encounters = mutableListOf<Encounter>()

  init {
    if (file.exists()) load()
  }

  fun record(timeMillis: Long, mons: List<WildMon>): EncounterSummary {
    val battle = encounters.size + 1
    file.parentFile?.mkdirs()
    file.appendText(
      mons.joinToString("") { "$timeMillis,$battle,${it.name.replace(",", " ")},${it.level},${it.shiny}\n" }
    )
    encounters += Encounter(timeMillis, mons)
    return summary()
  }

  fun summary(): EncounterSummary {
    val all = encounters.flatMap { it.mons }
    return EncounterSummary(
      battles = encounters.size,
      pokemon = all.size,
      shinies = all.count { it.shiny },
      bySpecies = all.groupingBy { it.name }.eachCount().toList().sortedByDescending { it.second },
      last = encounters.lastOrNull(),
    )
  }

  private fun load() {
    file.readLines()
      .mapNotNull { line ->
        val p = line.split(",")
        if (p.size < 5) return@mapNotNull null
        val time = p[0].toLongOrNull() ?: return@mapNotNull null
        val battle = p[1].toIntOrNull() ?: return@mapNotNull null
        val level = p[3].toIntOrNull() ?: return@mapNotNull null
        // The caught column was added later; older rows have 5 columns.
        val caught = p.getOrNull(5)?.toBooleanStrictOrNull()
        // Re-snap stored names so rows logged before species matching (e.g. "Nidorang") merge with the real name.
        val name = Species.match(p[2]) ?: p[2]
        Triple(battle, time, WildMon(name, level, p[4].toBoolean(), caught))
      }
      .groupBy { it.first }
      .toSortedMap()
      .values
      .forEach { rows -> encounters += Encounter(rows.first().second, rows.map { it.third }) }
  }
}
