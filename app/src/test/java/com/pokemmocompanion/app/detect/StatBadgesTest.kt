package com.pokemmocompanion.app.detect

import com.pokemmocompanion.app.calc.Stat
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class StatBadgesTest {
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

  private fun playerBadges(f: Frame) = StatBadges.find(f, 1400, BattleDetector.features(f).playerBoxY + 55, 1920, BattleDetector.features(f).playerBoxY + 130)

  private fun opponentBadges(f: Frame) = StatBadges.find(f, 490, 145, 1300, 212)

  @Test
  fun opponentRaisedBadges() {
    // Bellsprout: "+1 Atk" "+1 SpA" (green), nothing under the player's box.
    val f = frame("frame_20261001_093040_373.png")
    val opp = opponentBadges(f)
    assertEquals(2, opp.size)
    assertEquals(listOf(true, true), opp.map { it.raised })
    assertEquals(0, playerBadges(f).size)
  }

  @Test
  fun playerLoweredBadge() {
    // "-1 Atk" under Charmander's box (pink), none on the opponent.
    val f = frame("frame_20261001_093637_098.png")
    val mine = playerBadges(f)
    assertEquals(1, mine.size)
    assertEquals(false, mine.single().raised)
    assertEquals(0, opponentBadges(f).size)
    // "-1 Def" during the ball-throw sequence.
    assertEquals(listOf(false), playerBadges(frame("frame_20261001_064336_230.png")).map { it.raised })
  }

  @Test
  fun noBadgesInPlainBattle() {
    val f = frame("frame_20260930_212753_768.png")
    assertEquals(0, playerBadges(f).size)
    assertEquals(0, opponentBadges(f).size)
  }

  @Test
  fun parsesBadgeText() {
    assertEquals(Stat.ATK to 1, StatBadges.parse("+1 Atk", raised = true))
    assertEquals(Stat.SPA to 1, StatBadges.parse("+1 SpA", raised = true))
    assertEquals(Stat.DEF to -1, StatBadges.parse("-1 Def", raised = false))
    assertEquals(Stat.ATK to -2, StatBadges.parse("2 Atk", raised = false)) // sign from the tile color
    assertEquals(Stat.SPD to -1, StatBadges.parse("-l SpD", raised = false)) // "1" read as "l"
    assertEquals(Stat.SPE to 2, StatBadges.parse("+2 Spe", raised = true))
    assertNull(StatBadges.parse("-1 Acc", raised = false)) // accuracy doesn't change damage
    assertNull(StatBadges.parse("Atk", raised = true))
  }
}
