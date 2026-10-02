package com.pokemmocompanion.app.detect

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncounterTrackingTest {
  @get:Rule val tmp = TemporaryFolder()

  private val pidgey = WildMon("Pidgey", 4, false)
  private val nido = WildMon("Nidorino", 24, false)
  private val B = ScreenState.BATTLE
  private val O = ScreenState.OVERWORLD

  @Test
  fun wildIntroThenHordeNames() {
    val id = BattleIdentifier()
    id.onFrame(railVisible = true, changed = null)
    assertFalse(id.needsIntroRead)

    id.onFrame(railVisible = false, changed = null) // battle transition, rail gone
    assertTrue(id.needsIntroRead)
    id.onIntroRead(listOf("A horde of wild Nidorino appeared!"))
    assertFalse(id.needsIntroRead)

    id.onFrame(railVisible = false, changed = B)
    assertNull(id.onNameRead(emptyList())) // boxes not up yet
    assertNull(id.onNameRead(listOf(nido, nido, nido))) // horde partly read
    assertEquals(BattleResult(List(5) { nido }, wild = true), id.onNameRead(List(5) { nido }))
    assertFalse(id.needsNameRead)
    assertFalse(id.needsIntroRead) // no re-arming while still on the battle screen
  }

  @Test
  fun readWithRealSpeciesNameWins() {
    val id = BattleIdentifier()
    id.onFrame(railVisible = false, changed = null)
    id.onIntroRead(listOf("A wild Nidoran o' appeared!"))
    id.onFrame(railVisible = false, changed = B)
    id.onNameRead(listOf(WildMon("Nidoranx", 4, false))) // garbled first read
    assertEquals(listOf(WildMon("Nidoran♂", 4, false)), id.onNameRead(listOf(WildMon("Nidoran♂", 4, false)))?.mons)
  }

  @Test
  fun trainerBattleIsNotWild() {
    val id = BattleIdentifier()
    id.onFrame(railVisible = false, changed = null)
    id.onIntroRead(listOf("Youngster Joey would like to battle!"))
    id.onIntroRead(listOf("Youngster Joey sent out Rattata!"))
    id.onFrame(railVisible = false, changed = B)
    id.onNameRead(listOf(WildMon("Rattata", 5, false)))
    assertEquals(false, id.onNameRead(listOf(WildMon("Rattata", 5, false)))?.wild)
  }

  @Test
  fun menuThenBattleStartsFreshIntroWatch() {
    val id = BattleIdentifier(maxIntroReads = 2)
    id.onFrame(railVisible = false, changed = null) // a menu hides the rail
    repeat(2) { id.onIntroRead(listOf("BAG")) }
    assertFalse(id.needsIntroRead) // read budget spent
    id.onFrame(railVisible = true, changed = null) // menu closed

    id.onFrame(railVisible = false, changed = null) // now a real battle
    assertTrue(id.needsIntroRead)
    id.onIntroRead(listOf("A wild Pidgey appeared!"))
    id.onFrame(railVisible = false, changed = B)
    id.onNameRead(listOf(pidgey))
    assertEquals(BattleResult(listOf(pidgey), wild = true), id.onNameRead(listOf(pidgey)))
  }

  @Test
  fun battleEndingBeforeConfirmationFlushesResult() {
    val id = BattleIdentifier()
    id.onFrame(railVisible = false, changed = null)
    id.onIntroRead(listOf("A wild Pidgey appeared!"))
    id.onFrame(railVisible = false, changed = B)
    id.onNameRead(listOf(pidgey))
    assertEquals(BattleResult(listOf(pidgey), wild = true), id.onFrame(railVisible = true, changed = O))
  }

  @Test
  fun garbledIntroStillCountsAsWild() {
    // Night + rain: the intro OCR'd as garbage (log 2026-10-01 20:25), but nothing said "trainer".
    val id = BattleIdentifier()
    id.onFrame(railVisible = false, changed = null)
    repeat(4) { id.onIntroRead(listOf("i PeJEadde ysPPO PILM H")) }
    id.onFrame(railVisible = false, changed = B)
    val oddish = WildMon("Oddish", 4, false, caught = false)
    id.onNameRead(listOf(oddish))
    assertEquals(true, id.onNameRead(listOf(oddish))?.wild)
  }

  @Test
  fun midBattleStartCountsAsWild() {
    val id = BattleIdentifier()
    // Earlier wild battle leaves wildSeen set.
    id.onFrame(railVisible = false, changed = null)
    id.onIntroRead(listOf("A wild Pidgey appeared!"))
    id.onFrame(railVisible = false, changed = B)
    id.onFrame(railVisible = true, changed = O)
    // Rail visible right up to the confirmed battle, so the intro was never watched.
    id.onFrame(railVisible = true, changed = null)
    id.onFrame(railVisible = true, changed = B)
    id.onNameRead(listOf(pidgey))
    // Unknown intro: treated as wild so an alert is never missed.
    assertEquals(true, id.onNameRead(listOf(pidgey))?.wild)
  }

  @Test
  fun introTextToleratesOcrSlips() {
    assertTrue(IntroText.isWild(listOf("A wlld Pidgey appeared!")))
    assertTrue(IntroText.isWild(listOf("A wild Pidgey appeaned!")))
    assertTrue(IntroText.isWild(listOf("A wild horde appeared!")))
    assertFalse(IntroText.isWild(listOf("Rowan sent out Charmander!")))
    assertFalse(IntroText.isWild(listOf("Charmander gained 32 Exp. Points!")))
    assertTrue(IntroText.isTrainer(listOf("Youngster Joey would like to battle!")))
    assertTrue(IntroText.isTrainer(listOf("Lass Ann wants to battIe!")))
    assertTrue(IntroText.isTrainer(listOf("You are challenged by Bug Catcher Rick!")))
    assertFalse(IntroText.isTrainer(listOf("A wild Pidgey appeared!")))
    assertFalse(IntroText.isTrainer(listOf("FIGHT | POKEMON | BAG | RUN")))
  }

  @Test
  fun logPersistsAndGroupsHordes() {
    val file = File(tmp.root, "encounters.csv")
    EncounterLog(file).apply {
      record(1000, listOf(pidgey))
      record(2000, List(5) { nido } + WildMon("Nidorino", 23, true))
    }
    val s = EncounterLog(file).summary()
    assertEquals(2, s.battles)
    assertEquals(7, s.pokemon)
    assertEquals(1, s.shinies)
    assertEquals(listOf("Nidorino" to 6, "Pidgey" to 1), s.bySpecies)
    assertEquals(2000L, s.last?.timeMillis)
    assertEquals(6, s.last?.mons?.size)
  }
}
