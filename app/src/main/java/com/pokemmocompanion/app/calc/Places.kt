package com.pokemmocompanion.app.calc

import java.text.Normalizer

/** A place in the Hub spawn data. */
data class Place(val region: String, val name: String)

/** A wild encounter used to work out where the player is. */
data class Sighting(val species: Int, val level: Int)

/**
 * Turns what the app saw into a place with spawn data: the pause menu's place name (exact, but route numbers repeat
 * between Kanto and Unova) and recent wild encounters (which places have all of those Pokémon at those levels).
 */
class Places(private val spawns: Map<Int, List<Spawn>>) {
  /** All places with wild Pokémon, keyed by their normalized name. */
  private val byKey: Map<String, List<Place>> =
    spawns.values.flatten().map { Place(it.region, it.place) }.distinct().groupBy { key(it.name) }

  /** Every place with wild Pokémon, by region then name. */
  fun all(): List<Place> = byKey.values.flatten().sortedWith(compareBy({ it.region }, { it.name }))

  /** Places matching a menu name; several when the name exists in more than one region. Sub-areas match too. */
  fun match(menuName: String): List<Place> {
    val k = key(menuName)
    if (k.isEmpty()) return emptyList()
    byKey[k]?.let { return it }
    // "Mt. Moon" in the menu vs "Mt. Moon (1F)" in the data: every sub-area of that place.
    return byKey.filterKeys { it.startsWith(k) && it.length - k.length <= 4 }.values.flatten()
  }

  /** Picks one of [candidates]: the only one, or the one in [preferRegion]. */
  fun resolve(candidates: List<Place>, preferRegion: String?): Place? =
    candidates.singleOrNull() ?: candidates.firstOrNull { it.region == preferRegion }

  /**
   * Places that have every one of [sightings] (species at a level within its range there), optionally only in
   * [region]. Recent sightings first matter most, so callers should pass only the last few.
   */
  fun fromSightings(sightings: List<Sighting>, region: String? = null): List<Place> {
    if (sightings.isEmpty()) return emptyList()
    val sets =
      sightings.map { s ->
        spawns[s.species].orEmpty()
          .filter { it.level(s.level) && (region == null || it.region == region) }
          .map { Place(it.region, it.place) }
          .toSet()
      }
    return sets.reduce { a, b -> a intersect b }.toList()
  }

  /** Everything that spawns at [place], optionally only in [season] ("Spr".."Win") at [time] ("Morning"/"Day"/"Night"). */
  fun spawnsAt(place: Place, season: String? = null, time: String? = null): List<Pair<Int, Spawn>> =
    spawns.flatMap { (id, list) ->
      list.filter { sp ->
        sp.region == place.region && sp.place == place.name &&
          (season == null || sp.seasons.isEmpty() || season in sp.seasons.split('/')) &&
          (time == null || sp.rate(time) != "--")
      }.map { id to it }
    }

  companion object {
    fun key(name: String): String =
      Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD).filter { it in 'a'..'z' || it in '0'..'9' }
  }
}

private fun Spawn.level(l: Int) = l in (minLevel - 1)..(maxLevel + 1)

/** This spawn's rate at a time of day ("Morning", "Day" or "Night"). */
fun Spawn.rate(time: String): String =
  when (time) {
    "Morning" -> morning
    "Night" -> night
    else -> day
  }
