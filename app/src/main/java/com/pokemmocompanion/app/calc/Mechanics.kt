package com.pokemmocompanion.app.calc

import com.pokemmocompanion.app.calc.PokeType.*

/** Stat order matches the data files: HP, Atk, Def, SpA, SpD, Spe. */
enum class Stat(val short: String) { HP("HP"), ATK("Atk"), DEF("Def"), SPA("SpA"), SPD("SpD"), SPE("Spe") }

/** PokeMMO uses the Gen 5 type list: no Fairy. */
enum class PokeType {
  NORMAL, FIRE, WATER, ELECTRIC, GRASS, ICE, FIGHTING, POISON, GROUND,
  FLYING, PSYCHIC, BUG, ROCK, GHOST, DRAGON, DARK, STEEL;

  val label: String
    get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

enum class Nature(val up: Stat?, val down: Stat?) {
  HARDY(null, null), DOCILE(null, null), SERIOUS(null, null), BASHFUL(null, null), QUIRKY(null, null),
  LONELY(Stat.ATK, Stat.DEF), BRAVE(Stat.ATK, Stat.SPE), ADAMANT(Stat.ATK, Stat.SPA), NAUGHTY(Stat.ATK, Stat.SPD),
  BOLD(Stat.DEF, Stat.ATK), RELAXED(Stat.DEF, Stat.SPE), IMPISH(Stat.DEF, Stat.SPA), LAX(Stat.DEF, Stat.SPD),
  TIMID(Stat.SPE, Stat.ATK), HASTY(Stat.SPE, Stat.DEF), JOLLY(Stat.SPE, Stat.SPA), NAIVE(Stat.SPE, Stat.SPD),
  MODEST(Stat.SPA, Stat.ATK), MILD(Stat.SPA, Stat.DEF), QUIET(Stat.SPA, Stat.SPE), RASH(Stat.SPA, Stat.SPD),
  CALM(Stat.SPD, Stat.ATK), GENTLE(Stat.SPD, Stat.DEF), SASSY(Stat.SPD, Stat.SPE), CAREFUL(Stat.SPD, Stat.SPA);

  /** Multiplier in tenths (11 = ×1.1, 9 = ×0.9), applied with integer math like the games. */
  fun tenths(stat: Stat) =
    when (stat) {
      up -> 11
      down -> 9
      else -> 10
    }

  companion object {
    fun parse(text: String): Nature? = entries.firstOrNull { it.name.equals(text.trim(), ignoreCase = true) }
  }
}

object Stats {
  const val MAX_IV = 31

  /** Gen 3+ stat formula (matches PokeMMO's summary screen). */
  fun value(stat: Stat, base: Int, iv: Int, ev: Int, level: Int, nature: Nature = Nature.HARDY): Int {
    val core = (2 * base + iv + ev / 4) * level / 100
    return if (stat == Stat.HP) {
      if (base == 1) 1 else core + level + 10 // Shedinja always has 1 HP
    } else {
      (core + 5) * nature.tenths(stat) / 10
    }
  }

  /** Worst case for the player: 31 IVs, given EVs (0 for wild), and a nature that boosts [stat]. */
  fun max(species: SpeciesData, stat: Stat, level: Int, ev: Int = 0): Int {
    val boosting = Nature.entries.firstOrNull { it.up == stat } ?: Nature.HARDY
    return value(stat, species.base(stat), Stats.MAX_IV, ev, level, boosting)
  }
}

/** Gen 2-5 type chart (Steel resists Ghost and Dark). */
object TypeChart {
  private val SUPER = mutableMapOf<PokeType, Set<PokeType>>()
  private val RESIST = mutableMapOf<PokeType, Set<PokeType>>()
  private val IMMUNE = mutableMapOf<PokeType, Set<PokeType>>()

  private fun row(atk: PokeType, double: Set<PokeType>, half: Set<PokeType>, zero: Set<PokeType> = emptySet()) {
    SUPER[atk] = double
    RESIST[atk] = half
    IMMUNE[atk] = zero
  }

  init {
    row(NORMAL, setOf(), setOf(ROCK, STEEL), setOf(GHOST))
    row(FIRE, setOf(GRASS, ICE, BUG, STEEL), setOf(FIRE, WATER, ROCK, DRAGON))
    row(WATER, setOf(FIRE, GROUND, ROCK), setOf(WATER, GRASS, DRAGON))
    row(ELECTRIC, setOf(WATER, FLYING), setOf(ELECTRIC, GRASS, DRAGON), setOf(GROUND))
    row(GRASS, setOf(WATER, GROUND, ROCK), setOf(FIRE, GRASS, POISON, FLYING, BUG, DRAGON, STEEL))
    row(ICE, setOf(GRASS, GROUND, FLYING, DRAGON), setOf(FIRE, WATER, ICE, STEEL))
    row(FIGHTING, setOf(NORMAL, ICE, ROCK, DARK, STEEL), setOf(POISON, FLYING, PSYCHIC, BUG), setOf(GHOST))
    row(POISON, setOf(GRASS), setOf(POISON, GROUND, ROCK, GHOST), setOf(STEEL))
    row(GROUND, setOf(FIRE, ELECTRIC, POISON, ROCK, STEEL), setOf(GRASS, BUG), setOf(FLYING))
    row(FLYING, setOf(GRASS, FIGHTING, BUG), setOf(ELECTRIC, ROCK, STEEL))
    row(PSYCHIC, setOf(FIGHTING, POISON), setOf(PSYCHIC, STEEL), setOf(DARK))
    row(BUG, setOf(GRASS, PSYCHIC, DARK), setOf(FIRE, FIGHTING, POISON, FLYING, GHOST, STEEL))
    row(ROCK, setOf(FIRE, ICE, FLYING, BUG), setOf(FIGHTING, GROUND, STEEL))
    row(GHOST, setOf(PSYCHIC, GHOST), setOf(DARK, STEEL), setOf(NORMAL))
    row(DRAGON, setOf(DRAGON), setOf(STEEL))
    row(DARK, setOf(PSYCHIC, GHOST), setOf(FIGHTING, DARK, STEEL))
    row(STEEL, setOf(ICE, ROCK), setOf(FIRE, WATER, ELECTRIC, STEEL))
  }

  fun single(attack: PokeType, defend: PokeType): Double =
    when (defend) {
      in IMMUNE.getValue(attack) -> 0.0
      in SUPER.getValue(attack) -> 2.0
      in RESIST.getValue(attack) -> 0.5
      else -> 1.0
    }

  fun effectiveness(attack: PokeType, defender: List<PokeType>): Double =
    defender.fold(1.0) { acc, t -> acc * single(attack, t) }
}

object WildMoves {
  /**
   * Every level-up move the species knows by [level]. PokeMMO's exact wild moveset rule isn't confirmed (a caught
   * Lv. 5 Pikachu didn't match the main-series "last four moves" rule), so plan against all of them.
   */
  fun possible(species: SpeciesData, level: Int): List<String> =
    species.levelMoves.filter { it.level <= level }.map { it.move }.distinct()
}
