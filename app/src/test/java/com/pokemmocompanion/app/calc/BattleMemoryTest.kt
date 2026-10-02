package com.pokemmocompanion.app.calc

import com.pokemmocompanion.app.detect.BattleEvent
import com.pokemmocompanion.app.detect.Side
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleMemoryTest {
  private val data =
    GameData.parse(File("src/main/assets/pokemmo/species.json").readText(), File("src/main/assets/pokemmo/moves.json").readText())

  /** A known build: what the app would see. */
  private fun build(name: String, level: Int, nature: Nature, ivs: Int, evs: List<Int>) =
    Build(data.species(name)!!, level, nature, List(6) { ivs }, evs)

  @Test
  fun yourHitsNarrowItsDefense() {
    val you = build("Machamp", 50, Nature.ADAMANT, 31, listOf(0, 252, 0, 0, 0, 252)).battler()
    val truth = build("Snorlax", 50, Nature.CAREFUL, 20, listOf(252, 0, 100, 0, 156, 0))
    val foe = truth.battler()
    val mem = OpponentMemory(truth.species, 50)
    val all = Inference.values(truth.species, Stat.DEF, 50)
    val punch = data.move("Cross Chop")!!
    // Three hits seen as HP bar drops (each a different roll).
    var hp = foe.maxHp
    for (roll in listOf(88, 95, 100)) {
      val r = Damage.range(you, foe, punch)!!
      val dmg = r.min + (r.max - r.min) * (roll - 85) / 15
      val before = hp.toDouble() / foe.maxHp
      hp -= dmg
      mem.onHitTaken(you, punch, crit = false, before = before, after = hp.toDouble() / foe.maxHp, itsStages = emptyMap())
    }
    val def = mem.range(Stat.DEF)!!
    assertTrue("true Def ${foe.def} in $def", foe.def in def)
    assertTrue("narrowed: $def vs ${all.first()}..${all.last()}", def.last - def.first < all.last() - all.first())
    assertTrue(foe.maxHp in mem.range(Stat.HP)!!)
    assertEquals(3, mem.hits)
  }

  @Test
  fun itsHitsNarrowItsAttack() {
    val you = build("Blastoise", 50, Nature.BOLD, 31, listOf(252, 0, 252, 0, 0, 0)).battler()
    val truth = build("Gyarados", 50, Nature.ADAMANT, 31, listOf(0, 252, 0, 0, 0, 252))
    val mem = OpponentMemory(truth.species, 50)
    val waterfall = data.move("Waterfall")!!
    val r = Damage.range(truth.battler(), you, waterfall)!!
    mem.onHitDealt(you, waterfall, crit = false, damage = r.max, ko = false, itsStages = emptyMap())
    val atk = mem.range(Stat.ATK)!!
    assertTrue("true Atk ${truth.stat(Stat.ATK)} in $atk", truth.stat(Stat.ATK) in atk)
    // A max roll means it's near the top of the range.
    assertTrue(atk.first > Inference.values(truth.species, Stat.ATK, 50).first())
    // Worst case uses the inferred top, not the generic max.
    assertEquals(atk.last, mem.worstCase(mapOf(Stat.ATK to 999)).getValue(Stat.ATK))
  }

  @Test
  fun impossibleHitResetsAndExplains() {
    val you = build("Blastoise", 50, Nature.BOLD, 31, listOf(252, 0, 252, 0, 0, 0)).battler()
    val mem = OpponentMemory(data.species("Rattata")!!, 5)
    mem.onHitDealt(you, data.move("Tackle")!!, crit = false, damage = 150, ko = false, itsStages = emptyMap())
    // 150 HP from a Lv 5 Rattata's Tackle is impossible: Atk starts over (every value allowed) and a note explains.
    val all = Inference.values(data.species("Rattata")!!, Stat.ATK, 5)
    assertEquals(all.first()..all.last(), mem.range(Stat.ATK))
    assertEquals(1, mem.notes.size)
  }

  @Test
  fun turnOrderBoundsSpeed() {
    val mem = OpponentMemory(data.species("Gyarados")!!, 50)
    mem.onTurnOrder(itFirst = false, yourSpe = 100, itsStage = 0)
    assertTrue(mem.range(Stat.SPE)!!.last <= 100)
    mem.onTurnOrder(itFirst = true, yourSpe = 90, itsStage = 0)
    assertTrue(mem.range(Stat.SPE)!!.first >= 90)
  }

  @Test
  fun revealsAbilityAndItem() {
    val gyarados = data.species("Gyarados")!!
    assertEquals("Intimidate", Reveal.ability("Gyarados's Intimidate cuts Charmander's attack!", gyarados))
    val snorlax = data.species("Snorlax")!!
    val items = listOf("Sitrus Berry", "Leftovers", "Focus Sash")
    assertEquals("Leftovers", Reveal.item("The foe's Snorlax's Leftovers restored a little HP!", snorlax, items))
    assertEquals("Sitrus Berry", Reveal.item("Snorlax ate its Sitrus Berry!", snorlax, items))
    assertNull(Reveal.item("Charmander's Leftovers restored a little HP!", snorlax, items))
  }

  @Test
  fun hitTrackerMeasuresHitsAndOrder() {
    val t = HitTracker()
    t.onFrame(1.0, 39)
    t.onTurnStart()
    assertTrue(t.onMove(0, BattleEvent(Side.YOU, "Charmander", "Ember", text = "Charmander used Ember!")).isEmpty())
    // Bar drains, then settles at 0.6.
    assertNull(t.onFrame(0.8, null))
    assertNull(t.onFrame(0.6, null))
    assertNull(t.onFrame(0.6, null))
    assertEquals(Observation.YouHit(0, 1.0, 0.6), t.onFrame(0.6, null))
    // Its move second in the same turn: order known; your HP 39 -> 30.
    val order = t.onMove(1, BattleEvent(Side.FOE, "Oddish", "Absorb", text = "The wild Oddish used Absorb!"))
    assertEquals(listOf<Observation>(Observation.Order(0, 1, itFirst = false)), order)
    // Your HP number counts down (33 mid-animation), then reads 30 twice.
    assertNull(t.onFrame(0.6, 33))
    assertNull(t.onFrame(0.6, 30))
    assertEquals(Observation.ItHit(1, 39, 30, ko = false), t.onFrame(0.6, 30))
  }

  @Test
  fun narrowingWildVersusTrainer() {
    val p = OpponentProfile.build(data, "Gyarados", 50)!!
    val empty = Revealed("Gyarados", 50, listOf("Waterfall"), "Intimidate", null, emptyMap(), emptyList(), 0)
    // Wild: unchanged worst case (0 EVs); trainer: up to 252 EVs until narrowed.
    assertEquals(p.maxStats, empty.narrow(p, data, wild = true).maxStats)
    val trainer = empty.narrow(p, data, wild = false)
    assertTrue(trainer.maxStats.getValue(Stat.ATK) > p.maxStats.getValue(Stat.ATK))
    assertEquals("Waterfall", trainer.possibleMoves.first().name)
    assertEquals("Intimidate", trainer.knownAbility)
    // A narrowed range caps it.
    val narrowed = empty.copy(ranges = mapOf(Stat.ATK to 100..120)).narrow(p, data, wild = false)
    assertEquals(120, narrowed.maxStats.getValue(Stat.ATK))
  }
}
