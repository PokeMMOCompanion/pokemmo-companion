package com.pokemmocompanion.app.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BattleTrackerTest {
  private val O = ScreenState.OVERWORLD
  private val B = ScreenState.BATTLE

  @Test
  fun singleOddReadingDoesNotSwitch() {
    val t = BattleTracker(confirmFrames = 2)
    t.update(O)
    t.update(O)
    assertEquals(O, t.state)
    assertNull(t.update(B))
    t.update(O) // interrupts the pending switch
    assertNull(t.update(B))
    assertEquals(O, t.state)
  }

  @Test
  fun unknownReadingsKeepPendingSwitch() {
    val t = BattleTracker(confirmFrames = 2)
    t.update(O)
    t.update(O)
    t.update(B)
    t.update(null)
    assertEquals(B, t.update(B))
  }
}
