package com.pokemmocompanion.app.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** OCR'd text reaching the screen is always snapped to something known. */
class KnownTextTest {
  @Test
  fun garbledFixedMessagesMatch() {
    // Real reads from the logs.
    assertEquals("It's super effective!", BattleMessages.match("It's super affect. i ye!")?.text)
    assertEquals("super effective", BattleMessages.match("It's Super affect. i ye!")?.outcome)
    assertEquals("It is raining.", BattleMessages.match("It. is rainiig.")?.text)
    assertEquals("Got away safely!", BattleMessages.match("Gotaway safe ly!")?.text)
  }

  @Test
  fun unknownReadsAreHidden() {
    val log = BattleLog()
    log.onText(listOf("i PeJEadde ysPPO PILM H."))
    assertFalse(log.events.single().known)
  }

  @Test
  fun closestKnownName() {
    val items = KnownNames(listOf("Leftovers", "Sitrus Berry", "Oran Berry", "Exp. Share", "Macho Brace"))
    assertEquals("Leftovers", items.match("Lefto vers"))
    assertEquals("Sitrus Berry", items.match("Sitrus Bery"))
    assertEquals("Exp. Share", items.match("ExpShare"))
    assertNull(items.match("Quick Claw"))
    val places = KnownNames(listOf("Route 2", "Viridian Forest", "Pewter City"))
    assertEquals("Viridian Forest", places.match("Viridiam Forest"))
    assertEquals("Pewter City", places.match("Pewter Citv"))
  }
}
