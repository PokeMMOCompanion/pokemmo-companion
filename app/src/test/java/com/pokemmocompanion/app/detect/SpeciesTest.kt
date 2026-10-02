package com.pokemmocompanion.app.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeciesTest {
  @Test
  fun listCoversGen1To5() {
    assertEquals(649, SPECIES_NAMES.size)
    assertEquals("Bulbasaur", SPECIES_NAMES.first())
    assertEquals("Genesect", SPECIES_NAMES.last())
  }

  @Test
  fun exactAndPunctuation() {
    assertEquals("Pidgey", Species.match("Pidgey"))
    assertEquals("Mr. Mime", Species.match("Mr Mime"))
    assertEquals("Farfetch'd", Species.match("Farfetchd"))
    assertEquals("Porygon2", Species.match("Porygon2"))
    assertEquals("Ho-Oh", Species.match("Ho Oh"))
  }

  @Test
  fun nidoranGenderFromOcrLookalikes() {
    assertEquals("Nidoran♀", Species.match("Nidoran♀"))
    assertEquals("Nidoran♀", Species.match("Nidorang"))
    assertEquals("Nidoran♂", Species.match("Nidorand"))
    assertEquals("Nidoran♂", Species.match("Nidoranở")) // seen on the Thor: ♂ OCR'd as an accented o
    assertNull(Species.match("Nidoran")) // symbol lost entirely: can't tell which
    assertEquals("Nidorino", Species.match("Nidorino"))
    assertEquals("Nidorina", Species.match("Nidorlna"))
  }

  @Test
  fun smallMisreadsAreFixed() {
    assertEquals("Pidgey", Species.match("Pidgev"))
    assertEquals("Electabuzz", Species.match("Electabuz"))
    assertEquals("Amoonguss", Species.match("Arnoonguss"))
  }

  @Test
  fun garbageIsRejected() {
    assertNull(Species.match("Youngster"))
    assertNull(Species.match("HP"))
  }

  @Test
  fun parserUsesSpeciesNames() {
    assertEquals(listOf(WildMon("Nidoran♀", 5, false)), EncounterParser.parse(listOf("Nidorang Lv. 5")))
    assertEquals(listOf(WildMon("Nidoran♂", 5, true)), EncounterParser.parse(listOf("★Shiny Nidoran♂★ Lv. 5")))
    assertEquals(listOf(WildMon("Porygon2", 30, false)), EncounterParser.parse(listOf("Porygon2 Lv. 30")))
    assertEquals(listOf(WildMon("Shinx", 8, false)), EncounterParser.parse(listOf("Shinx Lv. 8")))
  }
}
