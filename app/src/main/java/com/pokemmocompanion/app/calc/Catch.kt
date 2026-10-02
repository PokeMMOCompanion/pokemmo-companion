package com.pokemmocompanion.app.calc

/**
 * PokeMMO catch chances, as PokeMMO Hub's catch calculator (pokemmohub.com/tools/catch-calculator) works them out:
 *
 *   x = (3·maxHP − 2·HP) · catchRate · ball / (3·maxHP) · status
 *   chance = x / 255, capped at 100%
 *
 * (Hub writes the last step as ((65536 / ⁴√(255/x)) / 65536)⁴, which is the same thing.) Ball and status
 * multipliers are Hub's; balls that only work in some situations carry the condition.
 */
enum class Ball(val label: String, val rate: Double, val condition: String? = null) {
  POKE("Poké Ball", 1.0),
  GREAT("Great Ball", 1.5),
  ULTRA("Ultra Ball", 2.0),
  HEAL("Heal Ball", 1.25),
  NET("Net Ball", 3.5, "Water or Bug"),
  NEST("Nest Ball", 4.0, "low level"),
  DUSK("Dusk Ball", 2.5, "night or cave"),
  QUICK("Quick Ball", 5.0, "first turn"),
  TIMER("Timer Ball", 4.0, "long battles"),
  REPEAT("Repeat Ball", 2.5, "already caught"),
  LUXURY("Luxury Ball", 2.0),
  DREAM("Dream Ball", 4.0, "asleep"),
}

/** Status multipliers per Hub; other statuses don't help. */
enum class CatchStatus(val label: String, val rate: Double) {
  NONE("No status", 1.0),
  SLEEP("Sleep", 2.0),
  FREEZE("Freeze", 2.0),
  PARALYSIS("Paralysis", 1.5),
  ;

  companion object {
    /** From the battle log's status codes ("SLP", "FRZ", "PAR", ...). */
    fun fromCode(code: String?): CatchStatus =
      when (code) {
        "SLP" -> SLEEP
        "FRZ" -> FREEZE
        "PAR" -> PARALYSIS
        else -> NONE
      }
  }
}

object Catch {
  /** Chance 0..1 to catch with one throw. [hpFraction] is current/max HP (0..1]. */
  fun chance(catchRate: Int, hpFraction: Double, ball: Double, status: Double): Double {
    val hp = hpFraction.coerceIn(0.0, 1.0)
    val x = (3 - 2 * hp) * catchRate * ball / 3 * status
    return (x / 255).coerceIn(0.0, 1.0)
  }

  /** Throws needed for a [confidence] chance of having caught it (e.g. 0.9 → "90% sure by N balls"). */
  fun throwsFor(chance: Double, confidence: Double = 0.9): Int =
    when {
      chance >= 1.0 -> 1
      chance <= 0.0 -> Int.MAX_VALUE
      else -> kotlin.math.ceil(kotlin.math.ln(1 - confidence) / kotlin.math.ln(1 - chance)).toInt()
    }

  /** Whether a ball's bonus applies to what we know about this Pokémon; null = can't tell (shown with its condition). */
  fun applies(ball: Ball, types: List<PokeType>, caught: Boolean?, asleep: Boolean): Boolean? =
    when (ball) {
      Ball.NET -> PokeType.WATER in types || PokeType.BUG in types
      Ball.REPEAT -> caught
      Ball.DREAM -> asleep
      Ball.NEST, Ball.DUSK, Ball.QUICK, Ball.TIMER -> null
      else -> true
    }
}
