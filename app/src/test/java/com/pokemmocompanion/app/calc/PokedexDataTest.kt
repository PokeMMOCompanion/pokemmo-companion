package com.pokemmocompanion.app.calc

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PokedexDataTest {
  private val data =
    GameData.parse(
      File("src/main/assets/pokemmo/species.json").readText(),
      File("src/main/assets/pokemmo/moves.json").readText(),
    )
  private val spawns = Spawns.parse(File("src/main/assets/pokemmo/locations.json").readText())

  @Test
  fun evYieldsAndFacts() {
    val pidgey = data.species("Pidgey")!!
    assertEquals(listOf(0, 0, 0, 0, 0, 1), pidgey.evYield)
    assertEquals(255, pidgey.catchRate)
    assertEquals(50.0, pidgey.female, 0.0)
    assertEquals(-1.0, data.species("Magnemite")!!.female, 0.0)
  }

  @Test
  fun evolutionsBothWays() {
    val bulbasaur = data.species("Bulbasaur")!!
    assertEquals(listOf(Evolution(2, "Lv 16")), bulbasaur.evolutions)
    assertEquals(1, data.species("Ivysaur")!!.evolvesFrom)
    assertTrue(data.species("Eevee")!!.evolutions.any { it.id == 136 && it.how == "Fire Stone" })
  }

  @Test
  fun spawnsMergeSeasons() {
    val pidgey = spawns.getValue(16)
    // Same rates in Spring, Summer and Winter, different in Autumn
    assertTrue(pidgey.any { it.place == "Berry Forest" && it.seasons == "Spr/Sum/Win" && it.rarity == "M 5% · D 5% · N --" })
    assertTrue(pidgey.any { it.region == "Kanto" && it.place == "Bond Bridge" && it.seasons == "" })
    // Legendaries have no wild spawns; starters only show up with a lure.
    assertTrue(spawns[150].isNullOrEmpty())
    assertTrue(spawns.getValue(1).single().day == "Lure")
  }
}
