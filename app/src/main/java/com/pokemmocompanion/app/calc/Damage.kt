package com.pokemmocompanion.app.calc

import kotlin.math.ceil

/** One side of a damage calculation. Stats are final in-game values (no stat stages yet). */
data class Battler(
  val name: String,
  val types: List<PokeType>,
  val level: Int,
  val hp: Int,
  val maxHp: Int,
  val atk: Int,
  val def: Int,
  val spa: Int,
  val spd: Int,
  val spe: Int,
  val ability: String? = null,
  val item: String? = null,
  /** Stat stages from in-battle badges (-6..+6), e.g. ATK to -1 after Growl. */
  val stages: Map<Stat, Int> = emptyMap(),
) {
  /** Speed after stat stages. */
  val effectiveSpe: Int
    get() = Stages.apply(spe, stages[Stat.SPE] ?: 0)
}

object Stages {
  /** Stage multiplier for Atk/Def/SpA/SpD/Spe: (2+n)/2 when raised, 2/(2-n) when lowered. */
  fun apply(stat: Int, stage: Int): Int {
    val s = stage.coerceIn(-6, 6)
    return if (s >= 0) stat * (2 + s) / 2 else stat * 2 / (2 - s)
  }
}

data class DamageRange(val min: Int, val max: Int) {
  fun percentOf(hp: Int) = PercentRange(min * 100.0 / hp, max * 100.0 / hp)
}

data class PercentRange(val min: Double, val max: Double)

enum class Weather(val label: String) { RAIN("Rain"), SUN("Harsh sun"), SAND("Sandstorm"), HAIL("Hail") }

/**
 * Battle conditions for one hit: [weather]; Reflect / Light Screen on the defender's side; whether the attacker is
 * burned.
 */
data class Field(val weather: Weather? = null, val reflect: Boolean = false, val lightScreen: Boolean = false, val burned: Boolean = false) {
  companion object {
    val NONE = Field()
  }
}

/**
 * Gen 5 damage formula with PokeMMO's critical hit (1.5×, 2.25× with Sniper).
 * Order: base → spread (0.75) → weather → crit → random (85-100%) → STAB → type → burn → screens/items, with
 * flooring at each step. Stat stages are applied (crits ignore the attacker's drops and the defender's boosts).
 * Field ([Field]): rain/sun boost Water/Fire ×1.5 and weaken the other ×0.5, a sandstorm raises Rock types' Sp. Def
 * ×1.5, burn halves physical damage (not with Guts), Reflect/Light Screen halve damage except on critical hits.
 * Not modeled yet: multi-hit moves, fixed-damage moves.
 */
object Damage {
  private val TYPE_ITEMS =
    mapOf(
      "Silk Scarf" to PokeType.NORMAL, "Charcoal" to PokeType.FIRE, "Mystic Water" to PokeType.WATER,
      "Magnet" to PokeType.ELECTRIC, "Miracle Seed" to PokeType.GRASS, "Never-Melt Ice" to PokeType.ICE,
      "NeverMeltIce" to PokeType.ICE, "Black Belt" to PokeType.FIGHTING, "Poison Barb" to PokeType.POISON,
      "Soft Sand" to PokeType.GROUND, "Sharp Beak" to PokeType.FLYING, "TwistedSpoon" to PokeType.PSYCHIC,
      "Twisted Spoon" to PokeType.PSYCHIC, "Silver Powder" to PokeType.BUG, "SilverPowder" to PokeType.BUG,
      "Hard Stone" to PokeType.ROCK, "Spell Tag" to PokeType.GHOST, "Dragon Fang" to PokeType.DRAGON,
      "BlackGlasses" to PokeType.DARK, "Black Glasses" to PokeType.DARK, "Metal Coat" to PokeType.STEEL,
    )
  private val PUNCHES =
    setOf(
      "Mach Punch", "Bullet Punch", "Comet Punch", "Dizzy Punch", "Drain Punch", "DynamicPunch", "Dynamic Punch",
      "Fire Punch", "Focus Punch", "Hammer Arm", "Ice Punch", "Mega Punch", "Meteor Mash", "Shadow Punch",
      "Sky Uppercut", "ThunderPunch", "Thunder Punch",
    )
  private val RECOIL =
    setOf(
      "Double-Edge", "Take Down", "Brave Bird", "Flare Blitz", "Head Smash", "Wood Hammer", "Volt Tackle",
      "Submission", "Jump Kick", "Hi Jump Kick", "High Jump Kick", "Wild Charge", "Head Charge",
    )
  private val PINCH = mapOf("Blaze" to PokeType.FIRE, "Torrent" to PokeType.WATER, "Overgrow" to PokeType.GRASS, "Swarm" to PokeType.BUG)

  /** Defender abilities that make a type deal no damage. */
  private val IMMUNITIES =
    mapOf(
      "Levitate" to PokeType.GROUND, "Flash Fire" to PokeType.FIRE, "Volt Absorb" to PokeType.ELECTRIC,
      "Lightningrod" to PokeType.ELECTRIC, "Lightning Rod" to PokeType.ELECTRIC, "Motor Drive" to PokeType.ELECTRIC,
      "Water Absorb" to PokeType.WATER, "Storm Drain" to PokeType.WATER, "Dry Skin" to PokeType.WATER,
      "Sap Sipper" to PokeType.GRASS,
    )

  /** Type effectiveness including immunity abilities (Levitate, Flash Fire, Wonder Guard, ...). */
  fun effectiveness(move: MoveData, defender: Battler): Double {
    if (IMMUNITIES[defender.ability] == move.type) return 0.0
    val eff = TypeChart.effectiveness(move.type, defender.types)
    if (defender.ability == "Wonder Guard" && eff <= 1.0) return 0.0
    return eff
  }

  /** Damage range for one hit, or null for status moves. [spread] = move hits several foes (hordes). */
  fun range(
    attacker: Battler,
    defender: Battler,
    move: MoveData,
    crit: Boolean = false,
    spread: Boolean = false,
    field: Field = Field.NONE,
  ): DamageRange? {
    if (!move.isDamaging) return null
    // Shell Armor / Battle Armor: no critical hits.
    @Suppress("NAME_SHADOWING") val crit = crit && defender.ability != "Shell Armor" && defender.ability != "Battle Armor"
    val eff = effectiveness(move, defender)
    if (eff == 0.0) return DamageRange(0, 0)

    var power = move.power
    if (attacker.ability == "Technician" && power <= 60) power = power * 3 / 2
    if (PINCH[attacker.ability] == move.type && attacker.hp * 3 <= attacker.maxHp) power = power * 3 / 2
    if (TYPE_ITEMS[attacker.item] == move.type) power = power * 6 / 5
    if (attacker.ability == "Iron Fist" && move.name in PUNCHES) power = power * 6 / 5
    if (attacker.ability == "Reckless" && move.name in RECOIL) power = power * 6 / 5
    if (attacker.ability == "Sand Force" && field.weather == Weather.SAND && move.type in setOf(PokeType.ROCK, PokeType.GROUND, PokeType.STEEL)) {
      power = power * 13 / 10
    }
    if (defender.ability == "Heatproof" && move.type == PokeType.FIRE) power /= 2
    if (defender.ability == "Dry Skin" && move.type == PokeType.FIRE) power = power * 5 / 4

    val physical = move.category == MoveCategory.PHYSICAL
    // Stat stages; a critical hit ignores the attacker's lowered attack and the defender's raised defense.
    val atkStage = attacker.stages[if (physical) Stat.ATK else Stat.SPA] ?: 0
    val defStage = defender.stages[if (physical) Stat.DEF else Stat.SPD] ?: 0
    var a = Stages.apply(if (physical) attacker.atk else attacker.spa, if (crit) maxOf(atkStage, 0) else atkStage)
    if (physical && (attacker.ability == "Huge Power" || attacker.ability == "Pure Power")) a *= 2
    if (physical && attacker.ability == "Hustle") a = a * 3 / 2
    if (physical && attacker.ability == "Guts" && field.burned) a = a * 3 / 2
    if (!physical && attacker.ability == "Solar Power" && field.weather == Weather.SUN) a = a * 3 / 2
    if (physical && attacker.item == "Choice Band") a = a * 3 / 2
    if (!physical && attacker.item == "Choice Specs") a = a * 3 / 2
    if (defender.ability == "Thick Fat" && (move.type == PokeType.FIRE || move.type == PokeType.ICE)) a /= 2
    var d =
      Stages.apply(if (physical) defender.def else defender.spd, if (crit) minOf(defStage, 0) else defStage)
        .coerceAtLeast(1)
    if (!physical && field.weather == Weather.SAND && PokeType.ROCK in defender.types) d = d * 3 / 2

    var base = (2 * attacker.level / 5 + 2) * power * a / d / 50 + 2
    if (spread) base = base * 3 / 4
    when {
      field.weather == Weather.RAIN && move.type == PokeType.WATER -> base = base * 3 / 2
      field.weather == Weather.RAIN && move.type == PokeType.FIRE -> base /= 2
      field.weather == Weather.SUN && move.type == PokeType.FIRE -> base = base * 3 / 2
      field.weather == Weather.SUN && move.type == PokeType.WATER -> base /= 2
    }
    if (crit) base = if (attacker.ability == "Sniper") base * 9 / 4 else base * 3 / 2

    fun finish(roll: Int): Int {
      var dmg = base * roll / 100
      if (move.type in attacker.types) dmg = if (attacker.ability == "Adaptability") dmg * 2 else dmg * 3 / 2
      dmg = (dmg * eff).toInt()
      if (attacker.ability == "Tinted Lens" && eff < 1.0) dmg *= 2
      if ((defender.ability == "Filter" || defender.ability == "Solid Rock") && eff > 1.0) dmg = dmg * 3 / 4
      if (defender.ability == "Multiscale" && defender.hp >= defender.maxHp) dmg /= 2
      if (physical && field.burned && attacker.ability != "Guts") dmg /= 2
      if (!crit && ((physical && field.reflect) || (!physical && field.lightScreen))) dmg /= 2
      if (attacker.item == "Life Orb") dmg = dmg * 13 / 10
      if (attacker.item == "Expert Belt" && eff > 1.0) dmg = dmg * 6 / 5
      return dmg.coerceAtLeast(1)
    }
    return DamageRange(finish(85), finish(100))
  }

  /** "Guaranteed OHKO", "Possible 2HKO", ... from a damage range against [hp]. */
  fun koLabel(r: DamageRange, hp: Int): String {
    if (r.max == 0) return "No effect"
    val surest = ceil(hp.toDouble() / r.min).toInt()
    val fastest = ceil(hp.toDouble() / r.max).toInt()
    fun name(n: Int) = if (n == 1) "OHKO" else "${n}HKO"
    return when {
      surest == fastest -> "Guaranteed ${name(surest)}"
      else -> "Possible ${name(fastest)}"
    }
  }
}
