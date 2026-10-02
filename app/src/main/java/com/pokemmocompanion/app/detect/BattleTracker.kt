package com.pokemmocompanion.app.detect

/**
 * Turns noisy per-frame readings into a stable screen state.
 * The state only switches after [confirmFrames] matching readings in a row; null readings carry no information
 * and leave both the state and any pending switch untouched.
 */
class BattleTracker(private val confirmFrames: Int = 2) {
  var state: ScreenState = ScreenState.UNKNOWN
    private set

  private var pending: ScreenState? = null
  private var pendingCount = 0

  /** Feeds one reading. Returns the new state if it changed, otherwise null. */
  fun update(reading: ScreenState?): ScreenState? {
    if (reading == null) return null
    if (reading == state) {
      pending = null
      pendingCount = 0
      return null
    }
    if (reading == pending) pendingCount++ else {
      pending = reading
      pendingCount = 1
    }
    if (pendingCount < confirmFrames) return null
    state = reading
    pending = null
    pendingCount = 0
    return state
  }

  fun reset() {
    state = ScreenState.UNKNOWN
    pending = null
    pendingCount = 0
  }
}
