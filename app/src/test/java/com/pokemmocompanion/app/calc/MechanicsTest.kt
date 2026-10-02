package com.pokemmocompanion.app.calc

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MechanicsTest {
  private val data =
    GameData.parse(
      File("src/main/assets/pokemmo/species.json").readText(),
      File("src/main/assets/pokemmo/moves.json").readText(),
    )

  private fun statsOf(name: String, level: Int, ivs: List<Int>, evs: List<Int>, nature: Nature): List<Int> {
    val s = data.species(name)!!
    return Stat.entries.map { Stats.value(it, s.base(it), ivs[it.ordinal], evs[it.ordinal], level, nature) }
  }

  @Test
  fun snapshotLoads() {
    assertEquals(649, data.species.size)
    assertEquals("Genesect", data.species(649)?.name)
    assertEquals(listOf(PokeType.FIRE), data.species("Charmander")?.types)
    assertEquals(MoveCategory.SPECIAL, data.move("DragonBreath")?.category)
    assertEquals(data.move("ThunderShock"), data.move("Thunder Shock"))
    assertNotNull(data.species("Nidoran♀"))
  }

  /** Values read off real summary screens on the Thor. */
  @Test
  fun statFormulaMatchesSummaryScreens() {
    // Charmander Lv. 13, Hardy, all IVs 15, EVs 5/1/1/0/1/11 -> 35/20/18/22/19/24
    assertEquals(
      listOf(35, 20, 18, 22, 19, 24),
      statsOf("Charmander", 13, List(6) { 15 }, listOf(5, 1, 1, 0, 1, 11), Nature.HARDY),
    )
    // Rattata Lv. 3, Quirky, IVs 21/31/10/6/27/12 -> 15/9/7/6/7/9
    assertEquals(
      listOf(15, 9, 7, 6, 7, 9),
      statsOf("Rattata", 3, listOf(21, 31, 10, 6, 27, 12), List(6) { 0 }, Nature.QUIRKY),
    )
    // Pikachu Lv. 5, Timid (+Spe -Atk), IVs 0/18/23/31/13/3 -> 18/9/10/11/10/15
    assertEquals(
      listOf(18, 9, 10, 11, 10, 15),
      statsOf("Pikachu", 5, listOf(0, 18, 23, 31, 13, 3), List(6) { 0 }, Nature.TIMID),
    )
  }

  @Test
  fun typeChart() {
    assertEquals(2.0, TypeChart.effectiveness(PokeType.FIRE, listOf(PokeType.GRASS)), 0.0)
    assertEquals(4.0, TypeChart.effectiveness(PokeType.ICE, listOf(PokeType.GRASS, PokeType.FLYING)), 0.0)
    assertEquals(0.0, TypeChart.effectiveness(PokeType.ELECTRIC, listOf(PokeType.WATER, PokeType.GROUND)), 0.0)
    assertEquals(0.5, TypeChart.effectiveness(PokeType.DARK, listOf(PokeType.STEEL)), 0.0) // Gen 5
  }

  @Test
  fun opponentProfile() {
    val p = OpponentProfile.build(data, "Rattata", 3)!!
    assertEquals(PokeType.FIGHTING, p.weaknesses.single().first)
    assertEquals(PokeType.GHOST to 0.0, p.resistances.first())
    // 31 IVs, 0 EVs, boosting nature: Atk (2*56+31)*3/100+5 = 9, ×1.1 -> 9
    assertEquals(9, p.maxStats[Stat.ATK])
    assertEquals(listOf("Tackle", "Tail Whip"), p.possibleMoves.map { it.name })
    assertTrue(p.possibleMoves.first().isDamaging)
  }
}
