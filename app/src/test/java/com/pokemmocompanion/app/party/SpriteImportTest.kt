package com.pokemmocompanion.app.party

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpriteImportTest {
  @Test
  fun pokemonDbNames() {
    assertEquals("https://img.pokemondb.net/sprites/black-white/normal/pidgey.png", SpriteImport.url("Pidgey"))
    assertEquals("mr-mime", SpriteImport.slug("Mr. Mime"))
    assertEquals("mime-jr", SpriteImport.slug("Mime Jr."))
    assertEquals("farfetchd", SpriteImport.slug("Farfetch'd"))
    assertEquals("nidoran-f", SpriteImport.slug("Nidoran♀"))
    assertEquals("nidoran-m", SpriteImport.slug("Nidoran♂"))
    assertEquals("porygon-z", SpriteImport.slug("Porygon-Z"))
    assertEquals("ho-oh", SpriteImport.slug("Ho-Oh"))
  }

  @Test
  fun trimsTransparentBorder() {
    val w = 4
    val px = IntArray(16)
    px[1 * w + 1] = 0xFF000000.toInt()
    px[2 * w + 2] = 0xFF000000.toInt()
    assertArrayEquals(intArrayOf(1, 1, 3, 3), SpriteImport.trimBox(px, 4, 4))
    assertNull(SpriteImport.trimBox(IntArray(16) { 0xFF000000.toInt() }, 4, 4))
  }
}
