package com.pokemmocompanion.app.quest

import com.pokemmocompanion.app.calc.GameData
import com.pokemmocompanion.app.calc.PokeType
import com.pokemmocompanion.app.party.PartyMember
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestsTest {
  @Test
  fun everyRegionHasEightGymsAndALeague() {
    assertEquals(listOf("Kanto", "Johto", "Hoenn", "Sinnoh", "Unova"), Story.REGIONS.map { it.name })
    for (r in Story.REGIONS) {
      assertEquals(r.name, 8, r.milestones.count { it.kind == MilestoneKind.GYM })
      assertEquals(r.name, 5, r.milestones.count { it.kind == MilestoneKind.LEAGUE })
      assertEquals(r.name, r.milestones.size, r.milestones.map { it.id }.toSet().size)
    }
  }

  @Test
  fun progressIsSavedAndNextSkipsHms() {
    val file = Files.createTempFile("quests", ".json").toFile()
    val store = QuestStore(file)
    assertEquals("Brock", store.state.next()!!.title)
    store.toggle("kanto-gym-1")
    store.toggle("kanto-hm-cut")
    store.addTask("  Catch Abra ")
    store.addTask("   ")

    val reloaded = QuestStore(file).state
    assertEquals("Misty", reloaded.next()!!.title)
    assertEquals(1, reloaded.badges())
    assertEquals(listOf("Catch Abra"), reloaded.tasks.map { it.text })

    val store2 = QuestStore(file)
    store2.toggleTask(reloaded.tasks.single().id)
    store2.clearDone()
    assertTrue(store2.state.tasks.isEmpty())
    Story.region("Kanto").milestones.forEach { if (it.id !in store2.state.checked) store2.toggle(it.id) }
    assertNull(store2.state.next())
  }

  @Test
  fun matchupAgainstMisty() {
    val data =
      GameData.parse(
        File("src/main/assets/pokemmo/species.json").readText(),
        File("src/main/assets/pokemmo/moves.json").readText(),
      )
    val party =
      listOf(
        PartyMember(slot = 0, dexId = 4, species = "Charmander", moves = listOf("Scratch", "Ember")),
        PartyMember(slot = 1, dexId = 25, species = "Pikachu", moves = listOf("Thunder Shock", "Quick Attack")),
      )
    val m = QuestMatchup.against(PokeType.WATER, party, data)
    assertEquals(listOf("Pikachu" to "ThunderShock"), m.strong.map { it.first to it.second.replace(" ", "") })
    assertEquals(listOf("Charmander"), m.weak)
  }
}
