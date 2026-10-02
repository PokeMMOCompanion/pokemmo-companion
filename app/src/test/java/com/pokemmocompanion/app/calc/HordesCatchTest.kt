package com.pokemmocompanion.app.calc

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HordesCatchTest {
  private val data =
    GameData.parse(
      File("src/main/assets/pokemmo/species.json").readText(),
      File("src/main/assets/pokemmo/moves.json").readText(),
    )
  private val spawns = Spawns.parse(File("src/main/assets/pokemmo/locations.json").readText())

  @Test
  fun hordeSpotsRankedByEvs() {
    val speed = Hordes.best(Stat.SPE, data.species, spawns)
    assertTrue(speed.size > 20)
    assertTrue(speed.zipWithNext().all { (a, b) -> a.evs >= b.evs })
    // A 5-horde of pure 1-Speed Pokémon gives 5 Speed EVs per horde.
    assertTrue(speed.first().evs >= 5.0)
    val top = speed.first()
    assertEquals(1.0, top.mix.sumOf { it.second }, 1e-9)
    // Every spot listed gives at least some Speed.
    assertTrue(speed.all { it.purity > 0 })
    // Spots from the PokeMMO forum EV guide: 5 × 2-EV Pokémon = 10 EVs per horde.
    val spd = Hordes.best(Stat.SPD, data.species, spawns)
    assertTrue(spd.any { it.place == "Battle Frontier" && it.evs == 10.0 && it.whenBest == "All year · Any time" })
    val spa = Hordes.best(Stat.SPA, data.species, spawns)
    assertTrue(spa.any { it.place.startsWith("Bell Tower") && it.evs == 10.0 })
  }

  @Test
  fun filterBySeason() {
    val winter = Hordes.TIMES.map { "Win" to it }.toSet()
    val spots = Hordes.best(Stat.HP, data.species, spawns, winter)
    assertTrue(spots.all { s -> s.whenBest.startsWith("Win") })
    assertEquals("Spr", Hordes.seasonForMonth(1))
    assertEquals("Win", Hordes.seasonForMonth(12))
  }

  @Test
  fun seasonLabels() {
    val all = Hordes.SEASONS.flatMap { s -> Hordes.TIMES.map { s to it } }.toSet()
    assertEquals("All year · Any time", Hordes.describe(all))
    assertEquals("Spr/Aut · Day", Hordes.describe(setOf("Spr" to "Day", "Aut" to "Day")))
  }

  @Test
  fun catchMatchesHubFormula() {
    // Full HP, Poké Ball, no status: (3 - 2) / 3 · 45 / 255
    assertEquals(45.0 / 3 / 255, Catch.chance(45, 1.0, 1.0, 1.0), 1e-9)
    // Next to no HP left: 3 / 3 · 45 / 255, three times the full-HP chance
    assertEquals(45.0 / 255, Catch.chance(45, 0.0, 1.0, 1.0), 1e-9)
    // Ultra Ball, half HP, asleep: (3-1)/3 * 45 * 2 * 2 / 255
    assertEquals(2.0 / 3 * 45 * 2 * 2 / 255, Catch.chance(45, 0.5, 2.0, 2.0), 1e-9)
    assertEquals(0.5, Catch.chance(255, 1.0, 1.5, 1.0), 1e-9)
    assertEquals(1.0, Catch.chance(255, 0.1, 2.0, 1.0), 0.0) // capped
    assertEquals(1, Catch.throwsFor(1.0))
    assertEquals(22, Catch.throwsFor(0.1)) // 1 - 0.9^22 ≥ 0.9
  }

  @Test
  fun hubCatchRatesForLegendaries() {
    assertEquals(5, data.species("Articuno")!!.catchRate)
  }
}
