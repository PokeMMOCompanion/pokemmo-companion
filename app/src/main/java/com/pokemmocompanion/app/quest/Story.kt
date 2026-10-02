package com.pokemmocompanion.app.quest

import com.pokemmocompanion.app.calc.PokeType
import com.pokemmocompanion.app.calc.PokeType.BUG
import com.pokemmocompanion.app.calc.PokeType.DARK
import com.pokemmocompanion.app.calc.PokeType.DRAGON
import com.pokemmocompanion.app.calc.PokeType.ELECTRIC
import com.pokemmocompanion.app.calc.PokeType.FIGHTING
import com.pokemmocompanion.app.calc.PokeType.FIRE
import com.pokemmocompanion.app.calc.PokeType.FLYING
import com.pokemmocompanion.app.calc.PokeType.GHOST
import com.pokemmocompanion.app.calc.PokeType.GRASS
import com.pokemmocompanion.app.calc.PokeType.GROUND
import com.pokemmocompanion.app.calc.PokeType.ICE
import com.pokemmocompanion.app.calc.PokeType.NORMAL
import com.pokemmocompanion.app.calc.PokeType.POISON
import com.pokemmocompanion.app.calc.PokeType.PSYCHIC
import com.pokemmocompanion.app.calc.PokeType.ROCK
import com.pokemmocompanion.app.calc.PokeType.STEEL
import com.pokemmocompanion.app.calc.PokeType.WATER

enum class MilestoneKind(val heading: String) {
  GYM("Gyms"),
  LEAGUE("Pokémon League"),
  HM("HMs"),
}

/**
 * One step of a region's story: a gym, an Elite Four member or Champion, or an HM.
 *
 * @param id stable key for saved progress, e.g. "kanto-gym-2"
 * @param type the gym/Elite Four specialty, null for HMs and mixed teams
 */
data class Milestone(
  val id: String,
  val kind: MilestoneKind,
  val title: String,
  val detail: String,
  val type: PokeType? = null,
)

data class Region(val name: String, val milestones: List<Milestone>)

/**
 * Story checklist per region: the 8 gyms in order, the Pokémon League and the HMs. Lineups are the ones from the
 * games PokeMMO's regions are based on (FireRed/LeafGreen, HeartGold/SoulSilver, Emerald, Platinum, Black/White).
 */
object Story {
  private class Builder(val key: String) {
    val list = mutableListOf<Milestone>()

    fun gym(leader: String, city: String, type: PokeType?, badge: String) {
      list += Milestone("$key-gym-${list.count { it.kind == MilestoneKind.GYM } + 1}", MilestoneKind.GYM, leader, "$city · $badge Badge", type)
    }

    fun league(name: String, role: String, type: PokeType?) {
      list += Milestone("$key-league-${list.count { it.kind == MilestoneKind.LEAGUE } + 1}", MilestoneKind.LEAGUE, name, role, type)
    }

    fun hms(vararg names: String) {
      for (n in names) list += Milestone("$key-hm-${n.lowercase().replace(' ', '-')}", MilestoneKind.HM, n, "HM")
    }
  }

  private fun region(name: String, build: Builder.() -> Unit) = Region(name, Builder(name.lowercase()).apply(build).list)

  val REGIONS: List<Region> =
    listOf(
      region("Kanto") {
        gym("Brock", "Pewter City", ROCK, "Boulder")
        gym("Misty", "Cerulean City", WATER, "Cascade")
        gym("Lt. Surge", "Vermilion City", ELECTRIC, "Thunder")
        gym("Erika", "Celadon City", GRASS, "Rainbow")
        gym("Koga", "Fuchsia City", POISON, "Soul")
        gym("Sabrina", "Saffron City", PSYCHIC, "Marsh")
        gym("Blaine", "Cinnabar Island", FIRE, "Volcano")
        gym("Giovanni", "Viridian City", GROUND, "Earth")
        league("Lorelei", "Elite Four", ICE)
        league("Bruno", "Elite Four", FIGHTING)
        league("Agatha", "Elite Four", GHOST)
        league("Lance", "Elite Four", DRAGON)
        league("Rival", "Champion", null)
        hms("Cut", "Fly", "Surf", "Strength", "Flash", "Rock Smash", "Waterfall")
      },
      region("Johto") {
        gym("Falkner", "Violet City", FLYING, "Zephyr")
        gym("Bugsy", "Azalea Town", BUG, "Hive")
        gym("Whitney", "Goldenrod City", NORMAL, "Plain")
        gym("Morty", "Ecruteak City", GHOST, "Fog")
        gym("Chuck", "Cianwood City", FIGHTING, "Storm")
        gym("Jasmine", "Olivine City", STEEL, "Mineral")
        gym("Pryce", "Mahogany Town", ICE, "Glacier")
        gym("Clair", "Blackthorn City", DRAGON, "Rising")
        league("Will", "Elite Four", PSYCHIC)
        league("Koga", "Elite Four", POISON)
        league("Bruno", "Elite Four", FIGHTING)
        league("Karen", "Elite Four", DARK)
        league("Lance", "Champion", DRAGON)
        hms("Cut", "Fly", "Surf", "Strength", "Whirlpool", "Waterfall")
      },
      region("Hoenn") {
        gym("Roxanne", "Rustboro City", ROCK, "Stone")
        gym("Brawly", "Dewford Town", FIGHTING, "Knuckle")
        gym("Wattson", "Mauville City", ELECTRIC, "Dynamo")
        gym("Flannery", "Lavaridge Town", FIRE, "Heat")
        gym("Norman", "Petalburg City", NORMAL, "Balance")
        gym("Winona", "Fortree City", FLYING, "Feather")
        gym("Tate & Liza", "Mossdeep City", PSYCHIC, "Mind")
        gym("Juan", "Sootopolis City", WATER, "Rain")
        league("Sidney", "Elite Four", DARK)
        league("Phoebe", "Elite Four", GHOST)
        league("Glacia", "Elite Four", ICE)
        league("Drake", "Elite Four", DRAGON)
        league("Wallace", "Champion", WATER)
        hms("Cut", "Flash", "Rock Smash", "Strength", "Surf", "Fly", "Dive", "Waterfall")
      },
      region("Sinnoh") {
        gym("Roark", "Oreburgh City", ROCK, "Coal")
        gym("Gardenia", "Eterna City", GRASS, "Forest")
        gym("Fantina", "Hearthome City", GHOST, "Relic")
        gym("Maylene", "Veilstone City", FIGHTING, "Cobble")
        gym("Crasher Wake", "Pastoria City", WATER, "Fen")
        gym("Byron", "Canalave City", STEEL, "Mine")
        gym("Candice", "Snowpoint City", ICE, "Icicle")
        gym("Volkner", "Sunyshore City", ELECTRIC, "Beacon")
        league("Aaron", "Elite Four", BUG)
        league("Bertha", "Elite Four", GROUND)
        league("Flint", "Elite Four", FIRE)
        league("Lucian", "Elite Four", PSYCHIC)
        league("Cynthia", "Champion", null)
        hms("Cut", "Fly", "Surf", "Strength", "Defog", "Rock Smash", "Waterfall", "Rock Climb")
      },
      region("Unova") {
        gym("Cilan / Chili / Cress", "Striaton City", null, "Trio")
        gym("Lenora", "Nacrene City", NORMAL, "Basic")
        gym("Burgh", "Castelia City", BUG, "Insect")
        gym("Elesa", "Nimbasa City", ELECTRIC, "Bolt")
        gym("Clay", "Driftveil City", GROUND, "Quake")
        gym("Skyla", "Mistralton City", FLYING, "Jet")
        gym("Brycen", "Icirrus City", ICE, "Freeze")
        gym("Drayden / Iris", "Opelucid City", DRAGON, "Legend")
        league("Shauntal", "Elite Four", GHOST)
        league("Marshal", "Elite Four", FIGHTING)
        league("Grimsley", "Elite Four", DARK)
        league("Caitlin", "Elite Four", PSYCHIC)
        league("Alder", "Champion", null)
        hms("Cut", "Fly", "Surf", "Strength", "Waterfall", "Dive")
      },
    )

  fun region(name: String): Region = REGIONS.firstOrNull { it.name == name } ?: REGIONS.first()
}
