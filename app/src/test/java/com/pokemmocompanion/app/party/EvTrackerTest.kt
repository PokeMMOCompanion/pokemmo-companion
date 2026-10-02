package com.pokemmocompanion.app.party

import com.pokemmocompanion.app.calc.GameData
import com.pokemmocompanion.app.calc.Stat
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvTrackerTest {
  private val data =
    GameData.parse(File("src/main/assets/pokemmo/species.json").readText(), File("src/main/assets/pokemmo/moves.json").readText())
  private val zero = List(6) { 0 }

  @Test
  fun awardsToFightersAndExpShare() {
    val party =
      listOf(
        PartyMember(slot = 0, evs = zero),
        PartyMember(slot = 1, evs = zero, item = "Exp. Share"),
        PartyMember(slot = 2, evs = zero, item = "Macho Brace"),
        PartyMember(slot = 3, evs = zero),
      )
    val tentacruel = data.species("Tentacruel")!! // 2 Sp. Def
    val a = EvRules.award(List(5) { tentacruel }, participants = setOf(0, 2), party = party)
    assertEquals(setOf(0, 1, 2), a.keys)
    assertEquals(10, a.getValue(0)[Stat.SPD.ordinal])
    assertEquals(10, a.getValue(1)[Stat.SPD.ordinal])
    assertEquals(20, a.getValue(2)[Stat.SPD.ordinal]) // Macho Brace
  }

  @Test
  fun capsAndTargets() {
    assertEquals(252, EvRules.addCapped(listOf(0, 0, 0, 0, 0, 250), listOf(0, 0, 0, 0, 0, 10))[5])
    assertEquals(510, EvRules.addCapped(listOf(252, 252, 0, 0, 0, 0), listOf(0, 0, 10, 0, 0, 0)).sum())

    val store = EvStore(Files.createTempFile("evs", ".json").toFile())
    val party = listOf(PartyMember(slot = 0, evs = listOf(0, 0, 0, 0, 0, 240)))
    store.setTarget(0, Stat.SPE, 252)
    assertTrue(store.apply(mapOf(0 to listOf(0, 0, 0, 0, 0, 5)), party).isEmpty())
    assertEquals(245, store.current(0, party[0])!![5])
    assertEquals(mapOf(0 to listOf(Stat.SPE)), store.apply(mapOf(0 to listOf(0, 0, 0, 0, 0, 10)), party))
    assertEquals(252, store.current(0, party[0])!![5])
    // A fresh summary read (different EVs) starts over from the game's numbers.
    val reread = listOf(PartyMember(slot = 0, evs = listOf(0, 0, 0, 0, 0, 100)))
    assertEquals(100, store.current(0, reread[0])!![5])
  }

  @Test
  fun defeatedFromMessages() {
    val horde = List(5) { "Tentacruel" }
    assertEquals(horde, BattleOutcome.defeated(horde, listOf("A horde of wild Tentacruel appeared!", "The wild Tentacruel fainted!")))
    assertEquals(
      listOf("Tentacruel", "Tentacruel"),
      BattleOutcome.defeated(horde, listOf("The wild Tentacruel fainted!", "The wild Tentacruel fainted!", "Got away safely!")),
    )
    assertEquals(emptyList<String>(), BattleOutcome.defeated(listOf("Oddish"), listOf("Gotcha! Oddish was caught!")))
    assertEquals(emptyList<String>(), BattleOutcome.defeated(listOf("Hoothoot"), listOf("Got away safely!")))
  }
}
