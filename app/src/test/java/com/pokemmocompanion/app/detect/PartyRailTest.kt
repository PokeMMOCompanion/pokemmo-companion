package com.pokemmocompanion.app.detect

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PartyRailTest {
  private fun frame(name: String): Frame {
    val f = File("../samples", name)
    assumeTrue("Missing sample $f", f.exists())
    val img = ImageIO.read(f)
    return object : Frame {
      override val width = img.width
      override val height = img.height

      override fun pixel(x: Int, y: Int) = img.getRGB(x, y)
    }
  }

  /** Full party at full HP, including rings over bushes and trees (the background is as green as the ring). */
  @Test
  fun fullHpRingsReadFull() {
    for (f in listOf("frame_20260930_212609_713.png", "frame_20260930_212716_697.png", "frame_20260930_212800_081.png")) {
      assertTrue(f, PartyRail.arcs(frame(f)).all { it >= 66 })
    }
  }

  /** Metapod and Rattata fainted, Hoothoot and Pikachu low (amber), Spearow full, Charmander most of its HP. */
  @Test
  fun hurtAndFaintedParty() {
    val hp = PartyRail.arcs(frame("frame_20261001_103927_279.png")).map { PartyRail.hpFraction(it) }
    assertEquals(0f, hp[0], 0f) // Metapod fainted
    assertEquals(0.32f, hp[1], 0.06f) // Hoothoot
    assertEquals(0f, hp[2], 0f) // Rattata fainted
    assertEquals(1f, hp[3], 0.02f) // Spearow
    assertEquals(0.78f, hp[4], 0.08f) // Charmander
    assertEquals(0.44f, hp[5], 0.06f) // Pikachu
    // Still the overworld, even with fainted and amber rings.
    assertEquals(ScreenState.OVERWORLD, BattleDetector.classify(BattleDetector.features(frame("frame_20261001_103927_279.png"))))
  }

  @Test
  fun noRingsInBattle() {
    for (f in listOf("frame_20260930_212753_768.png", "frame_20261001_085453_928.png", "frame_20261001_064330_230.png")) {
      assertTrue(f, PartyRail.arcs(frame(f)).all { it <= 4 })
    }
  }

  @Test
  fun opponentHpBar() {
    assertEquals(1f, BattleDetector.features(frame("frame_20260930_212753_768.png")).opponentHp!!, 0.05f) // full
    assertEquals(0.5f, BattleDetector.features(frame("frame_20261001_085453_928.png")).opponentHp!!, 0.07f) // half, yellow
    assertEquals(0.2f, BattleDetector.features(frame("frame_20261001_093040_373.png")).opponentHp!!, 0.07f) // red
    assertNull(BattleDetector.features(frame("frame_20260930_212609_713.png")).opponentHp) // overworld
  }
}
