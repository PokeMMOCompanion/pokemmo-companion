package com.pokemmocompanion.app.party

import com.pokemmocompanion.app.detect.BattleDetector
import com.pokemmocompanion.app.detect.Frame
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class ActivePokemonTest {
  private val party =
    listOf(
      PartyMember(slot = 0, nickname = "Charmander", species = "Charmander", level = 13),
      PartyMember(slot = 1, nickname = "Hoothoot", species = "Hoothoot", level = 4),
      PartyMember(slot = 5, nickname = "Sparky", species = "Pikachu", level = 5),
    )

  @Test
  fun parsesHpBox() {
    assertEquals(ActiveReading("Charmander", 13, 29, 35), ActivePokemon.parse("Charmander Lv. 13", "29 / 35"))
    assertEquals(ActiveReading("Sparky", 5, 18, 18), ActivePokemon.parse("Sparky Lv, 5 8", "18/ 18"))
    assertNull(ActivePokemon.parse("", "29 / 35"))
    // Poison icon before the name, seen on the Thor.
    assertEquals("Charmander", ActivePokemon.parse("D Charmander Lv. 14", "21/37")?.name)
  }

  @Test
  fun matchesPartyByNicknameOrSpecies() {
    assertEquals(0, ActivePokemon.match(party, ActiveReading("Charmander", 13, 29, 35))?.slot)
    assertEquals(0, ActivePokemon.match(party, ActiveReading("Charrnander", 13, null, null))?.slot) // OCR slip
    assertEquals(5, ActivePokemon.match(party, ActiveReading("Sparky", 5, null, null))?.slot)
    assertEquals(0, ActivePokemon.match(party, ActiveReading("DCharmander", 13, null, null))?.slot) // icon glued on
    assertNull(ActivePokemon.match(party, ActiveReading("Rattata", 3, null, null)))
  }

  @Test
  fun levelBreaksTiesBetweenSameSpecies() {
    val two = party + PartyMember(slot = 3, nickname = "Charmander", species = "Charmander", level = 20)
    assertEquals(3, ActivePokemon.match(two, ActiveReading("Charmander", 20, null, null))?.slot)
    assertEquals(0, ActivePokemon.match(two, ActiveReading("Charmander", 13, null, null))?.slot)
  }

  /** The HP box is found at both heights it appears at (normal, and raised by stat-change badges). */
  @Test
  fun playerBoxPositionOnSamples() {
    fun frame(name: String): Frame {
      val f = File("../samples", name)
      assumeTrue(f.exists())
      val img = ImageIO.read(f)
      return object : Frame {
        override val width = img.width
        override val height = img.height

        override fun pixel(x: Int, y: Int) = img.getRGB(x, y)
      }
    }
    // Measured top of the dark strip: 616 normally, 548 when raised (scan step 10 px, so ±10).
    val normal = BattleDetector.features(frame("frame_20260930_212753_768.png")).playerBoxY
    val raised = BattleDetector.features(frame("frame_20261001_064336_230.png")).playerBoxY
    assert(normal in 606..626) { "normal $normal" }
    assert(raised in 538..558) { "raised $raised" }

    // Night battles: dark ground behind the box used to pull the match down to y≈700.
    val nightRaised = BattleDetector.features(frame("frame_20261001_085453_928.png"))
    val nightNormal = BattleDetector.features(frame("frame_20261001_085646_005.png"))
    assert(nightRaised.playerBoxY in 538..558) { "night raised ${nightRaised.playerBoxY}" }
    assert(nightNormal.playerBoxY in 606..626) { "night normal ${nightNormal.playerBoxY}" }
    // Move selection covers the box: it must not be "found" somewhere else.
    assert(BattleDetector.features(frame("frame_20261001_085655_066.png")).playerBox < 0.6f)
  }
}
