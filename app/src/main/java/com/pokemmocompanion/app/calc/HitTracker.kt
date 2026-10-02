package com.pokemmocompanion.app.calc

import com.pokemmocompanion.app.detect.BattleEvent
import com.pokemmocompanion.app.detect.Side

/** A measured hit or turn order, ready for [OpponentMemory]. [event] is the move's index in the battle log. */
sealed interface Observation {
  /** Your move took the opponent's HP bar from [before] to [after] (fractions; 0 = KO). */
  data class YouHit(val event: Int, val before: Double, val after: Double) : Observation

  /** Its move took your HP from [before] to [after] (exact; [ko] = your Pokémon fainted, so only a lower bound). */
  data class ItHit(val event: Int, val before: Int, val after: Int, val ko: Boolean) : Observation

  /** In one turn, [first] moved before [second] (both move events). */
  data class Order(val first: Int, val second: Int, val itFirst: Boolean) : Observation
}

/**
 * Measures hits from the battle's HP readings: after a move message, wait for the target's HP to stop changing (the
 * opponent's bar is read every frame; your HP number every couple of seconds), then report before/after. A new move
 * or a timeout ends the wait. Turns start when the move menu shows; the first two moves of a turn from different
 * sides give the turn order.
 */
class HitTracker {
  private data class Pending(val side: Side, val event: Int, val beforeOpp: Double?, val beforeYou: Int?, var frames: Int = 0)

  private var pending: Pending? = null
  private var lastOpp: Double? = null
  private val oppHistory = ArrayDeque<Double>()
  private var lastYou: Int? = null
  private var youStable = 0
  private var turnFirst: Pair<Side, Int>? = null
  private var turnDone = false

  fun reset() {
    pending = null
    lastOpp = null
    oppHistory.clear()
    lastYou = null
    youStable = 0
    turnFirst = null
    turnDone = false
  }

  /** The move menu is up: a new turn starts. */
  fun onTurnStart() {
    turnFirst = null
    turnDone = false
  }

  /** A move message: ends the previous wait and starts measuring this one. */
  fun onMove(index: Int, e: BattleEvent): List<Observation> {
    val out = mutableListOf<Observation>()
    finish(force = true)?.let(out::add)
    if (e.side == Side.YOU || e.side == Side.FOE) {
      pending = Pending(e.side, index, lastOpp, lastYou)
      val first = turnFirst
      if (first == null) turnFirst = e.side to index
      else if (!turnDone && first.first != e.side) {
        out += Observation.Order(first.second, index, itFirst = first.first == Side.FOE)
        turnDone = true
      }
    }
    return out
  }

  /** A "<name> fainted!" message: [yours] = your Pokémon. Settles a waiting hit as a KO. */
  fun onFainted(yours: Boolean): Observation? {
    val p = pending ?: return null
    return when {
      yours && p.side == Side.FOE && p.beforeYou != null -> Observation.ItHit(p.event, p.beforeYou, 0, ko = true).also { pending = null }
      !yours && p.side == Side.YOU && p.beforeOpp != null -> Observation.YouHit(p.event, p.beforeOpp, 0.0).also { pending = null }
      else -> null
    }
  }

  /** Every sampled frame: the opponent's HP bar (null when not shown) and your HP from the box (when read). */
  fun onFrame(oppHp: Double?, yourHp: Int?): Observation? {
    if (oppHp != null) {
      oppHistory.addLast(oppHp)
      while (oppHistory.size > 3) oppHistory.removeFirst()
      lastOpp = oppHp
    }
    if (yourHp != null) {
      youStable = if (yourHp == lastYou) youStable + 1 else 0
      lastYou = yourHp
    }
    val p = pending ?: return null
    p.frames++
    return finish(force = p.frames > TIMEOUT_FRAMES)
  }

  private fun finish(force: Boolean): Observation? {
    val p = pending ?: return null
    val result =
      when (p.side) {
        Side.YOU -> {
          val before = p.beforeOpp
          val now = lastOpp
          val settled = oppHistory.size == 3 && oppHistory.max() - oppHistory.min() < 0.006
          if (before != null && now != null && before - now > 0.01 && (settled || force)) Observation.YouHit(p.event, before, now) else null
        }
        Side.FOE -> {
          val before = p.beforeYou
          val now = lastYou
          if (before != null && now != null && now < before && (youStable >= 1 || force)) Observation.ItHit(p.event, before, now, ko = false) else null
        }
        Side.OTHER -> null
      }
    if (result != null || force) pending = null
    return result
  }

  companion object {
    /** ~6 s at 2 frames per second. */
    const val TIMEOUT_FRAMES = 12
  }
}
