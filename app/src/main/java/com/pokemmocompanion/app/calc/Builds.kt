package com.pokemmocompanion.app.calc

/**
 * A hypothetical Pokémon for the team builder: species, level, nature, IVs, EVs, ability, item and up to four moves.
 * [ivs]/[evs] are in [Stat] order.
 */
data class Build(
  val species: SpeciesData,
  val level: Int = 50,
  val nature: Nature = Nature.HARDY,
  val ivs: List<Int> = List(6) { Stats.MAX_IV },
  val evs: List<Int> = List(6) { 0 },
  val ability: String? = species.abilities.firstOrNull { it != "--" },
  val item: String? = null,
  val moves: List<MoveData> = emptyList(),
) {
  fun stat(s: Stat) = Stats.value(s, species.base(s), ivs[s.ordinal], evs[s.ordinal], level, nature)

  fun battler(stages: Map<Stat, Int> = emptyMap()): Battler {
    val hp = stat(Stat.HP)
    return Battler(
      name = species.name,
      types = species.types,
      level = level,
      hp = hp,
      maxHp = hp,
      atk = stat(Stat.ATK),
      def = stat(Stat.DEF),
      spa = stat(Stat.SPA),
      spd = stat(Stat.SPD),
      spe = stat(Stat.SPE),
      ability = ability,
      item = item,
      stages = stages,
    )
  }
}

/** One side's moves against the other in a hypothetical battle. */
data class Matchup(val attacker: Build, val defender: Build, val moves: List<Pair<MoveData, DamageRange?>>) {
  val defenderHp: Int
    get() = defender.stat(Stat.HP)
}

/**
 * Team builder logic: suggested builds from base stats and learnsets, and both directions of a hypothetical battle
 * using the same damage formula as the live battle calc. Suggestions are a stat-driven starting point (attacking
 * stat + Speed, best STAB and coverage moves it can learn), not competitive sets.
 */
object Builds {
  /** Moves with a big drawback (recharge, self-KO, two-turn, lock-in) or fixed damage the formula doesn't model. */
  private val AVOID =
    setOf(
      "Hyper Beam", "Giga Impact", "Blast Burn", "Frenzy Plant", "Hydro Cannon", "Rock Wrecker", "Roar of Time",
      "Explosion", "Selfdestruct", "Self-Destruct", "Focus Punch", "SolarBeam", "Solar Beam", "Sky Attack",
      "Skull Bash", "Razor Wind", "Dig", "Fly", "Dive", "Bounce", "Shadow Force", "Outrage", "Thrash", "Petal Dance",
      "Last Resort", "Dream Eater", "Synchronoise", "Belch", "Future Sight", "Doom Desire", "Fling", "Natural Gift",
      "Struggle", "Snore", "Sucker Punch", "Feint", "Fake Out", "Magnitude", "Present", "Spit Up", "Trump Card",
      "Wring Out", "Crush Grip", "Flail", "Reversal", "Eruption", "Water Spout", "Endeavor", "Final Gambit",
      "Hidden Power", "Return", "Frustration", "Low Kick", "Grass Knot", "Gyro Ball", "Electro Ball", "Heavy Slam",
      "Punishment", "Pursuit", "Payback", "Avalanche", "Revenge", "Counter", "Mirror Coat", "Metal Burst", "Bide",
      "Uproar", "Rollout", "Ice Ball", "Fury Cutter", "Echoed Voice", "Round", "Acrobatics", "Hex", "Venoshock",
      "Assurance", "Stored Power", "Smelling Salts", "SmellingSalt", "Wake-Up Slap", "Brine", "Facade",
    )

  fun physical(s: SpeciesData) = s.base(Stat.ATK) >= s.base(Stat.SPA)

  /**
   * Suggested build: nature boosting the better attacking stat (or Speed when it's fast), 252 EVs in the attacking
   * stat and Speed (HP instead of Speed for slow Pokémon), and the best STAB plus coverage moves it can learn.
   */
  fun suggest(s: SpeciesData, data: GameData, level: Int = 50): Build {
    val phys = physical(s)
    val atkStat = if (phys) Stat.ATK else Stat.SPA
    val unused = if (phys) Stat.SPA else Stat.ATK
    val fast = s.base(Stat.SPE) >= 70
    val nature = Nature.entries.first { it.up == (if (fast) Stat.SPE else atkStat) && it.down == unused }
    val evs = MutableList(6) { 0 }
    evs[atkStat.ordinal] = 252
    if (fast) {
      evs[Stat.SPE.ordinal] = 252
      evs[Stat.HP.ordinal] = 4
    } else {
      evs[Stat.HP.ordinal] = 252
      evs[if (s.base(Stat.DEF) >= s.base(Stat.SPD)) Stat.SPD.ordinal else Stat.DEF.ordinal] = 4
    }
    return Build(s, level, nature, evs = evs, moves = bestMoves(s, data, phys))
  }

  /** Up to four moves: the strongest STAB per type, then whatever adds the most new super-effective coverage. */
  fun bestMoves(s: SpeciesData, data: GameData, phys: Boolean = physical(s)): List<MoveData> {
    val category = if (phys) MoveCategory.PHYSICAL else MoveCategory.SPECIAL
    val pool =
      data.learnable(s).map { it.first }
        .filter { it.isDamaging && it.category == category && it.name !in AVOID && it.power in 40..150 }
        .distinctBy { it.id }
    fun score(m: MoveData) = m.power * minOf(m.accuracy, 100) / 100.0 * (if (m.type in s.types) 1.5 else 1.0)
    val picks = mutableListOf<MoveData>()
    for (t in s.types) pool.filter { it.type == t }.maxByOrNull(::score)?.let(picks::add)
    while (picks.size < 4) {
      val covered = picks.map { it.type }.toSet()
      fun hits(t: PokeType) = PokeType.entries.filter { TypeChart.single(t, it) > 1.0 }.toSet()
      val already = covered.flatMap(::hits).toSet()
      val next =
        pool.filter { it.type !in covered }
          .maxByOrNull { m -> (hits(m.type) - already).size * 1000 + score(m) } ?: break
      picks += next
    }
    return picks
  }

  /** [attacker]'s moves against [defender], strongest first. */
  fun matchup(attacker: Build, defender: Build): Matchup {
    val a = attacker.battler()
    val d = defender.battler()
    return Matchup(
      attacker,
      defender,
      attacker.moves.map { it to Damage.range(a, d, it) }.sortedByDescending { it.second?.max ?: -1 },
    )
  }

  /** Who moves first (ignoring priority): positive = [a] is faster, 0 = speed tie. */
  fun speedDiff(a: Build, b: Build) = a.stat(Stat.SPE) - b.stat(Stat.SPE)
}
