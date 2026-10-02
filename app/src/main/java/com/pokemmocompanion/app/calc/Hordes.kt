package com.pokemmocompanion.app.calc

/**
 * Horde finder for EV training: for one stat, the horde spots that give the most EVs per horde.
 *
 * A spot is (region, place, method, horde size). Which Pokémon show up changes with season and time of day, so each of
 * the 12 season × time combinations is worked out on its own: the horde rates are turned into shares of all hordes
 * there, and EVs per horde = size × Σ share × that Pokémon's EV yield for the stat.
 */
data class HordeSpot(
  val region: String,
  val place: String,
  val method: String,
  val size: Int,
  /** EVs of the stat per horde, at the best season/time. */
  val evs: Double,
  /** Share of hordes there that give the stat at all (0..1), at the best season/time. */
  val purity: Double,
  /** When it's that good: e.g. "All year · Day" or "Spr/Sum · Morning/Day". */
  val whenBest: String,
  /** (species, share 0..1) at the best season/time, most common first. */
  val mix: List<Pair<SpeciesData, Double>>,
  /** How many of the season × time combinations it's that good in (12 = always). */
  val availability: Int,
)

object Hordes {
  val SEASONS = listOf("Spr", "Sum", "Aut", "Win")
  val TIMES = listOf("Morning", "Day", "Night")

  /** All season × time combinations. */
  val ALL_TIMES: Set<Pair<String, String>> = SEASONS.flatMap { s -> TIMES.map { s to it } }.toSet()

  /** PokeMMO's season changes every real month: Spring, Summer, Autumn, Winter, then round again from January. */
  fun seasonForMonth(month: Int): String = SEASONS[(month - 1) % 4]

  /**
   * Spots for [stat], best first. [allowed] limits the season/time combinations considered (e.g. only this season);
   * ties go to spots that are good more often.
   */
  fun best(
    stat: Stat,
    species: List<SpeciesData>,
    spawns: Map<Int, List<Spawn>>,
    allowed: Set<Pair<String, String>> = ALL_TIMES,
  ): List<HordeSpot> {
    val byId = species.associateBy { it.id }
    // spot -> (season, time) -> species -> rate %
    val spots = HashMap<List<Any>, HashMap<Pair<String, String>, HashMap<SpeciesData, Double>>>()
    for ((id, list) in spawns) {
      val s = byId[id] ?: continue
      for (sp in list) {
        if (sp.horde == 0) continue
        val key = listOf(sp.region, sp.place, sp.method, sp.horde)
        val seasons = if (sp.seasons.isEmpty()) SEASONS else sp.seasons.split('/')
        for (season in seasons) {
          for ((t, rate) in TIMES.zip(listOf(sp.morning, sp.day, sp.night))) {
            if ((season to t) !in allowed) continue
            val pct = percent(rate) ?: continue
            spots.getOrPut(key) { HashMap() }.getOrPut(season to t) { HashMap() }.merge(s, pct, Double::plus)
          }
        }
      }
    }
    return spots.mapNotNull { (key, combos) ->
      val scored =
        combos.mapValues { (_, mix) ->
          val total = mix.values.sum()
          val shares = mix.mapValues { it.value / total }
          val evs = key[3] as Int * shares.entries.sumOf { (s, share) -> share * s.evYield[stat.ordinal] }
          val purity = shares.entries.filter { it.key.evYield[stat.ordinal] > 0 }.sumOf { it.value }
          Triple(evs, purity, shares)
        }
      val top = scored.values.maxOf { it.first }
      if (top <= 0.0) return@mapNotNull null
      val bestCombos = scored.filter { it.value.first >= top - 1e-9 }
      val first = bestCombos.values.maxBy { it.second }
      HordeSpot(
        region = key[0] as String,
        place = key[1] as String,
        method = key[2] as String,
        size = key[3] as Int,
        evs = top,
        purity = first.second,
        whenBest = describe(bestCombos.keys),
        mix = first.third.entries.sortedByDescending { it.value }.map { it.key to it.value },
        availability = bestCombos.size,
      )
    }
      .sortedWith(
        compareByDescending<HordeSpot> { it.evs }
          .thenByDescending { it.purity }
          .thenByDescending { it.availability }
          .thenBy { it.region }
          .thenBy { it.place }
      )
  }

  /** "10%" → 10.0; "--", "Special", "Lure" → null. */
  private fun percent(rate: String): Double? = rate.removeSuffix("%").toDoubleOrNull()?.takeIf { it > 0 }

  /** Season/time combos as a short label, e.g. "All year · Day" or "Spr/Aut · Any time". */
  internal fun describe(combos: Set<Pair<String, String>>): String {
    // Group seasons by the set of times they share.
    val bySeason = SEASONS.associateWith { s -> TIMES.filter { (s to it) in combos } }.filterValues { it.isNotEmpty() }
    return bySeason.entries
      .groupBy({ it.value }, { it.key })
      .entries
      .joinToString("; ") { (times, seasons) ->
        val s = if (seasons.size == 4) "All year" else seasons.joinToString("/")
        val t = if (times.size == 3) "Any time" else times.joinToString("/")
        "$s · $t"
      }
  }
}
