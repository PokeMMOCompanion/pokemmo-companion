package com.pokemmocompanion.app.detect

import org.junit.Assert.assertEquals
import org.junit.Test

class GameTextTest {
  @Test
  fun keepsEnglishAndGameSymbolsOnly() {
    assertEquals("Nidorano Lv. 4", GameText.clean("Nidoranở Lv. 4")) // seen on the Thor
    assertEquals("Pokedex:", GameText.clean("Pokédex:"))
    assertEquals("★Shiny Ursaring★ Lv. 58 ♀", GameText.clean("★Shiny Ursaring★ Lv. 58 ♀"))
    assertEquals("Nidoran♂ Lv. 5", GameText.clean("Nidoran♂ Lv. 5"))
    assertEquals("Pidgey Lv. 5", GameText.clean("Pidgey Lv. 5Ж")) // non-Latin letters dropped
  }
}
