package com.pokemmocompanion.app.calc

import com.pokemmocompanion.app.party.PartyMember
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DamageTest {
  private val data =
    GameData.parse(
      File("src/main/assets/pokemmo/species.json").readText(),
      File("src/main/assets/pokemmo/moves.json").readText(),
    )

  /** A Charmander as read from the summary screens. */
  private val charmander =
    PartyMember(
      slot = 0, nickname = "Charmander", level = 13, dexId = 4, species = "Charmander", nature = "Hardy",
      currentHp = 35, stats = listOf(35, 20, 18, 22, 19, 24), moves = listOf("DragonBreath", "Metal Claw", "Tackle", "Ember"),
      ability = "Blaze", item = "Super Potion",
    )

  private fun opp(name: String, level: Int) = OpponentProfile.build(data, name, level)!!

  private fun move(name: String) = data.move(name)!!

  @Test
  fun emberVsRattataMatchesHandCalc() {
    val you = BattleAdvisor.battler(data, charmander)!!
    val rattata = opp("Rattata", 3)
    // Worst-case Rattata Lv. 3: HP 15, SpD (2*35+31)*3/100+5 = 8 (×1.1 = 8).
    assertEquals(15, rattata.maxStats[Stat.HP])
    assertEquals(8, rattata.maxStats[Stat.SPD])
    // base = ((2*13/5+2) * 40 * 22 / 8 / 50) + 2 = 17; ×0.85 = 14, STAB 21 | ×1.00 = 17, STAB 25
    val r = Damage.range(you, BattleAdvisor.opponentBattler(rattata, null), move("Ember"))!!
    assertEquals(DamageRange(21, 25), r)
    assertEquals("Guaranteed OHKO", Damage.koLabel(r, 15))
  }

  @Test
  fun critIsOneAndAHalfAndSniperMore() {
    val you = BattleAdvisor.battler(data, charmander)!!
    val foe = BattleAdvisor.opponentBattler(opp("Snorlax", 20), null)
    val normal = Damage.range(you, foe, move("Tackle"))!!
    val crit = Damage.range(you, foe, move("Tackle"), crit = true)!!
    val sniper = Damage.range(you.copy(ability = "Sniper"), foe, move("Tackle"), crit = true)!!
    assertTrue("crit ${crit.max} vs ${normal.max}", crit.max in (normal.max * 3 / 2 - 2)..(normal.max * 3 / 2 + 2))
    assertTrue("sniper ${sniper.max} vs ${normal.max}", sniper.max in (normal.max * 9 / 4 - 3)..(normal.max * 9 / 4 + 3))
  }

  @Test
  fun abilitiesAndImmunities() {
    val you = BattleAdvisor.battler(data, charmander)!!
    val bronzor = opp("Bronzor", 20)
    // Mud-Slap vs Bronzor: Levitate (one of its abilities) would make it immune: a possibility, not the headline.
    val mud = BattleAdvisor.moveCalc(data, you, bronzor, move("Mud-Slap"), horde = false)!!
    assertTrue(mud.percent.max > 0.0)
    assertTrue(mud.abilityNotes.toString(), "if Levitate: no effect" in mud.abilityNotes)
    // Once it's revealed, it's the headline.
    val known = BattleAdvisor.moveCalc(data, you, bronzor.copy(knownAbility = "Levitate"), move("Mud-Slap"), horde = false)!!
    assertEquals("No effect", known.label)
    assertTrue(known.abilityNotes.isEmpty())
    // Thick Fat halves Ember vs Snorlax.
    val snorlax = BattleAdvisor.opponentBattler(opp("Snorlax", 20), null)
    val plain = Damage.range(you, snorlax, move("Ember"))!!
    val fat = Damage.range(you, snorlax.copy(ability = "Thick Fat"), move("Ember"))!!
    assertTrue("${fat.max} vs ${plain.max}", fat.max <= plain.max / 2 + 1)
    // Status moves have no damage.
    assertNull(Damage.range(you, snorlax, move("Growl")))
  }

  @Test
  fun hordeSpreadPenalty() {
    val geodude = opp("Geodude", 20)
    val you = BattleAdvisor.battler(data, charmander)!!.copy(atk = 60, level = 30)
    val single = BattleAdvisor.moveCalc(data, you, geodude, move("Earthquake"), horde = false)!!
    val horde = BattleAdvisor.moveCalc(data, you, geodude, move("Earthquake"), horde = true)!!
    assertTrue(horde.spread)
    assertTrue("${horde.percent.max} vs ${single.percent.max}", horde.percent.max < single.percent.max * 0.8)
    // Single-target moves aren't penalized in hordes.
    assertEquals(
      BattleAdvisor.moveCalc(data, you, geodude, move("Tackle"), horde = false)!!.percent,
      BattleAdvisor.moveCalc(data, you, geodude, move("Tackle"), horde = true)!!.percent,
    )
  }

  @Test
  fun koLabels() {
    assertEquals("Guaranteed OHKO", Damage.koLabel(DamageRange(104, 121), 100))
    assertEquals("Possible OHKO", Damage.koLabel(DamageRange(90, 110), 100))
    assertEquals("Guaranteed 2HKO", Damage.koLabel(DamageRange(50, 60), 100))
    assertEquals("Possible 2HKO", Damage.koLabel(DamageRange(45, 55), 100))
    assertEquals("No effect", Damage.koLabel(DamageRange(0, 0), 100))
  }

  @Test
  fun adviceRanksMovesAndSuggestsSwitch() {
    val rattata = opp("Rattata", 3)
    val a = BattleAdvisor.advise(data, charmander, 35, rattata, horde = false, party = listOf(charmander))!!
    // Ember (40 ×1.5 STAB) and DragonBreath (60, no STAB) both do 21-25: tied at the top, ahead of Tackle/Metal Claw.
    assertEquals(setOf("Ember", "DragonBreath"), a.yourMoves.take(2).map { it.move.name }.toSet())
    assertEquals(a.yourMoves[0].percent, a.yourMoves[1].percent)
    assertEquals(MoveOrder.YOU_FIRST, a.speed.order)
    assertNull(a.switchTo) // a Lv. 3 Rattata can't KO a Lv. 13 Charmander
    assertTrue(a.threats.none { it.canKo })

    // Low on HP against something that can KO: a healthy party member that survives is suggested.
    val geodude = opp("Geodude", 20)
    val squirtle =
      PartyMember(
        slot = 1, nickname = "Squirtle", level = 20, dexId = 7, species = "Squirtle", nature = "Modest",
        currentHp = 55, stats = listOf(55, 30, 40, 35, 40, 30), moves = listOf("Water Gun"), ability = "Torrent",
      )
    val low = BattleAdvisor.advise(data, charmander, 5, geodude, horde = false, party = listOf(charmander, squirtle))!!
    assertTrue(low.threats.first().canKo)
    assertNotNull(low.switchTo)
    assertEquals("Squirtle", low.switchTo!!.member.species)
  }

  @Test
  fun statStages() {
    assertEquals(150, Stages.apply(100, 1))
    assertEquals(200, Stages.apply(100, 2))
    assertEquals(66, Stages.apply(100, -1))
    assertEquals(50, Stages.apply(100, -2))
    assertEquals(400, Stages.apply(100, 6))

    val you = BattleAdvisor.battler(data, charmander)!!.copy(level = 30, atk = 60)
    val foe = BattleAdvisor.opponentBattler(opp("Snorlax", 30), null)
    val plain = Damage.range(you, foe, move("Tackle"))!!
    val growled = Damage.range(you.copy(stages = mapOf(Stat.ATK to -1)), foe, move("Tackle"))!!
    val boosted = Damage.range(you, foe.copy(stages = mapOf(Stat.DEF to 2)), move("Tackle"))!!
    assertTrue("${growled.max} < ${plain.max}", growled.max < plain.max)
    assertTrue("${boosted.max} < ${plain.max}", boosted.max < plain.max)
    // Crits ignore the attacker's drop and the defender's boost...
    assertEquals(
      Damage.range(you, foe, move("Tackle"), crit = true),
      Damage.range(you.copy(stages = mapOf(Stat.ATK to -1)), foe.copy(stages = mapOf(Stat.DEF to 2)), move("Tackle"), crit = true),
    )
    // ...but not the attacker's boost.
    val swords = Damage.range(you.copy(stages = mapOf(Stat.ATK to 2)), foe, move("Tackle"), crit = true)!!
    assertTrue(swords.max > Damage.range(you, foe, move("Tackle"), crit = true)!!.max)
  }

  @Test
  fun speedStagesChangeMoveOrder() {
    val rattata = opp("Rattata", 3)
    val fast = BattleAdvisor.advise(data, charmander, 35, rattata, horde = false, party = listOf(charmander))!!
    assertEquals(MoveOrder.YOU_FIRST, fast.speed.order)
    val slowed = BattleAdvisor.advise(data, charmander, 35, rattata, horde = false, party = listOf(charmander), yourStages = mapOf(Stat.SPE to -6))!!
    assertTrue(slowed.speed.order != MoveOrder.YOU_FIRST)
  }

  /** A Metapod at +6 Def, 18/24 HP, vs a Lv. 5 Caterpie (seen on the Thor: Tackle did 1 damage). */
  @Test
  fun threatsArePercentOfTotalHpAndKoUsesCurrentHp() {
    val metapod =
      PartyMember(
        slot = 4, nickname = "Metapod", level = 7, dexId = 11, species = "Metapod", nature = "Hardy",
        stats = listOf(24, 8, 15, 10, 9, 10), moves = listOf("Harden"), ability = "Shed Skin",
      )
    val caterpie = opp("Caterpie", 5)
    val hardened = BattleAdvisor.battler(data, metapod, currentHp = 18, stages = mapOf(Stat.DEF to 6))!!
    val t = BattleAdvisor.threat(hardened, caterpie, move("Tackle"))!!
    assertEquals(2, t.maxDamage)
    assertEquals(2 * 100.0 / 24, t.maxPercent, 0.01) // 8% of total HP, not 11% of the 18 left
    assertTrue(!t.canKo)
    val nearlyOut = BattleAdvisor.battler(data, metapod, currentHp = 2, stages = mapOf(Stat.DEF to 6))!!
    assertTrue(BattleAdvisor.threat(nearlyOut, caterpie, move("Tackle"))!!.canKo)
  }

  @Test
  fun moreAbilities() {
    val you = BattleAdvisor.battler(data, charmander)!!
    // Sturdy: a hit that could OHKO gets the note.
    val onix = opp("Onix", 3)
    val ember = BattleAdvisor.moveCalc(data, you, onix, move("Ember"), horde = false)!!
    if (ember.damage.max >= ember.targetHp) assertTrue(ember.abilityNotes.any { it.startsWith("if Sturdy") })
    // Filter / Solid Rock soften super-effective hits; Heatproof halves fire; Tinted Lens doubles resisted hits.
    val target = BattleAdvisor.opponentBattler(opp("Bronzong", 30), null)
    val fire = move("Ember")
    val plain = Damage.range(you, target, fire)!!
    assertTrue(Damage.range(you, target.copy(ability = "Heatproof"), fire)!!.max < plain.max)
    assertTrue(Damage.range(you, target.copy(ability = "Solid Rock"), fire)!!.max < plain.max)
    val rattata = BattleAdvisor.opponentBattler(opp("Rattata", 20), null)
    val tinted = BattleAdvisor.opponentBattler(opp("Butterfree", 20), "Tinted Lens")
    val absorb = move("Absorb")
    assertTrue(Damage.range(tinted, BattleAdvisor.opponentBattler(opp("Charizard", 20), null), absorb)!!.max >
      Damage.range(tinted.copy(ability = null), BattleAdvisor.opponentBattler(opp("Charizard", 20), null), absorb)!!.max)
    // Shell Armor: no crits.
    val shell = rattata.copy(ability = "Shell Armor")
    assertEquals(Damage.range(you, shell, move("Scratch"))!!, Damage.range(you, shell, move("Scratch"), crit = true)!!)
  }
}
