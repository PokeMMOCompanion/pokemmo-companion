package com.pokemmocompanion.app.tools

import com.pokemmocompanion.app.calc.Builds
import com.pokemmocompanion.app.calc.GameData
import com.pokemmocompanion.app.calc.Nature
import com.pokemmocompanion.app.calc.Places
import com.pokemmocompanion.app.calc.Sighting
import com.pokemmocompanion.app.calc.Spawns
import com.pokemmocompanion.app.calc.Stat
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsTest {
  private fun asset(name: String) = File("src/main/assets/pokemmo/$name").readText()

  private val data = GameData.parse(asset("species.json"), asset("moves.json"))

  @Test
  fun berryTimesFollowHub() {
    val berries = Berry.parse(asset("berries.json"))
    assertEquals(64, berries.size)
    val leppa = berries.single { it.name == "Leppa Berry" }
    assertEquals(20, leppa.growHours)
    assertEquals("Spicy 2 · Sweet 1 · Bitter 1", leppa.seeds)
    val h = BerryTimes.HOUR
    val plot = Plot(1, leppa.id, 4, plantedAt = 0)
    assertEquals(20 * h, BerryTimes.readyAt(plot, leppa))
    assertEquals(28 * h, BerryTimes.witherAt(plot, leppa))
    // Planting counts as 6 h dry: soil dry at 4 h, reminder an hour before.
    assertEquals(4 * h, BerryTimes.dryAt(plot, leppa))
    val r = BerryTimes.reminders(plot, leppa)
    assertEquals(listOf(ReminderKind.WATER to 3 * h, ReminderKind.HARVEST to 20 * h, ReminderKind.WITHER to 27 * h), r.map { it.kind to it.at })
    // Slow berries dry over 15 h.
    val lum = berries.single { it.name == "Lum Berry" }
    assertEquals(15 * h, BerryTimes.dryAt(plot.copy(wateredAt = 0), lum))
  }

  @Test
  fun breedingPlanCountsParents() {
    val b = Breeding.parse(asset("breeding.json"))
    val plan = b.plan(listOf(Stat.HP, Stat.ATK, Stat.SPE), withNature = true)!!
    assertEquals(listOf(8, 4, 2, 1), plan.rows.map { it.size })
    assertEquals(mapOf(null to 1, Stat.HP to 4, Stat.ATK to 2, Stat.SPE to 1), plan.parents)
    assertEquals(7, plan.breeds)
    assertEquals(listOf(listOf(null, Stat.HP, Stat.ATK, Stat.SPE)), plan.rows.last())
    val five = b.plan(listOf(Stat.HP, Stat.ATK, Stat.DEF, Stat.SPD, Stat.SPE), withNature = false)!!
    assertEquals(16, five.rows.first().size)
    assertEquals(15, five.breeds)
  }

  @Test
  fun eggMoveRoutesInBreedingOrder() {
    val egg = EggMoves.parse(asset("egg_moves.json"))
    val ivysaur = data.species("Ivysaur")!!
    assertEquals("Bulbasaur", egg.baseOf(ivysaur, data).name)
    val sludge = data.move("Sludge")!!.id
    assertTrue(sludge in egg.moves(ivysaur, data))
    val route = egg.routes(ivysaur, sludge, data).first { it.size == 3 }
    // Gulpin/Grimer/Koffing learns it, passes to Shellos, then Mudkip, then Bulbasaur.
    assertEquals(listOf("Shellos", "Mudkip", "Bulbasaur"), route.map { it.mother.name })
    assertEquals("Bulbasaur", route.last().mother.name)
    assertEquals("as an egg move (breed it first)", route.last().how)
  }

  @Test
  fun gtlParsing() {
    val items = Gtl.parseItems(asset("items.json"))
    assertEquals("Leppa Berry", items.single { it.id == 5154 }.name)
    val prices = Gtl.parsePrices("""[{"item_id":5154,"tradable":true,"price":740,"listings":10,"quantity":119951,"last_updated":[2026,10,1,20,0]}]""")
    assertEquals(740L, prices.getValue(5154).price)
    assertEquals("2026-10-01", prices.getValue(5154).updatedDay)
    val hist = Gtl.parseHistory("""[{"x":86400,"y":900},{"x":86400,"y":890},{"x":172800,"y":905}]""")
    assertEquals(listOf(890L, 905L), hist.map { it.y })
  }

  @Test
  fun suggestedBuilds() {
    val garchomp = Builds.suggest(data.species("Garchomp")!!, data)
    assertEquals(Nature.JOLLY, garchomp.nature)
    assertEquals(252, garchomp.evs[Stat.ATK.ordinal])
    assertEquals(4, garchomp.moves.size)
    assertTrue(garchomp.moves.any { it.name == "Earthquake" })
    val snorlax = Builds.suggest(data.species("Snorlax")!!, data)
    assertEquals(Nature.ADAMANT, snorlax.nature)
    assertEquals(252, snorlax.evs[Stat.HP.ordinal])
    val m = Builds.matchup(garchomp, Builds.suggest(data.species("Gyarados")!!, data))
    assertNotNull(m.moves.first().second)
  }

  @Test
  fun placesFromMenuAndSightings() {
    val places = Places(Spawns.parse(asset("locations.json")))
    val viridian = places.match("Viridian Forest")
    assertEquals(listOf("Kanto"), viridian.map { it.region }.distinct())
    // Route 2 exists in Kanto and Unova.
    val route2 = places.match("Route 2")
    assertTrue(route2.map { it.region }.containsAll(listOf("Kanto", "Unova")))
    assertEquals("Kanto", places.resolve(route2, "Kanto")!!.region)
    // Pidgey Lv 3 + Rattata Lv 3 in Kanto: Route 1 is among the places that have both.
    val guess = places.fromSightings(listOf(Sighting(16, 3), Sighting(19, 3)), "Kanto")
    assertTrue(guess.any { it.name == "Route 1" })
  }
}
