package com.pokemmocompanion.app.party

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PartyEditTest {
  private fun store() = PartyStore(Files.createTempFile("party", ".json").toFile(), speciesName = { if (it == 5) "Charmeleon" else null })

  @Test
  fun manualEntryLevelUpsAndNewMoves() {
    val s = store()
    s.set(PartyMember(slot = 0, level = 16, dexId = 5, species = "Charmeleon", stats = listOf(47, 30, 28, 35, 31, 35), moves = listOf("Ember", "Scratch")))
    assertEquals(16, s.party.members[0]!!.level)

    // Battle messages: level up flags stats; a third move fits; a fifth doesn't and flags moves.
    s.leveledUp(0, 17)
    s.learned(0, "Metal Claw")
    assertEquals(17, s.party.members[0]!!.level)
    assertEquals(listOf("Ember", "Scratch", "Metal Claw"), s.party.members[0]!!.moves)
    assertEquals(setOf("stats"), s.party.members[0]!!.outdated)
    s.learned(0, "Smokescreen")
    s.learned(0, "Dragon Rage")
    assertEquals(setOf("stats", "moves"), s.party.members[0]!!.outdated)

    // Entering it by hand again clears the flags; Remove empties the slot.
    s.set(s.party.members[0]!!.copy(level = 17))
    assertEquals(emptySet<String>(), s.party.members[0]!!.outdated)
    s.remove(0)
    assertNull(s.party.members[0])
  }

  @Test
  fun speciesNameIsNotANickname() {
    assertNull(PartyMember(slot = 0, species = "Nidoran♂", nickname = "Nidoran").realNickname)
    assertNull(PartyMember(slot = 0, species = "Mr. Mime", nickname = "Mr Mime").realNickname)
    assertEquals("Nidoran♀", PartyMember(slot = 0, species = "Nidoran♀", nickname = "Nidoran").shownName)
    assertEquals("Sparky", PartyMember(slot = 0, species = "Pikachu", nickname = "Sparky").shownName)
  }
}
