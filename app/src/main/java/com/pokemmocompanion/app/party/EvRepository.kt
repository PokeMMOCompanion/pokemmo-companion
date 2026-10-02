package com.pokemmocompanion.app.party

import android.content.Context
import com.pokemmocompanion.app.calc.Stat
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-wide EV tracking backed by [EvStore]. */
object EvRepository {
  private var store: EvStore? = null
  private val _state = MutableStateFlow(EvState())
  val state: StateFlow<EvState> = _state.asStateFlow()

  @Synchronized
  fun init(context: Context) {
    if (store != null) return
    store = EvStore(File(context.applicationContext.filesDir, "evs.json")).also { _state.value = it.state }
  }

  /** Current EVs (last summary read + tracked gains), or null if the member's EVs were never read. */
  fun current(slot: Int, member: PartyMember): List<Int>? = store?.current(slot, member)

  @Synchronized
  fun apply(awards: Map<Int, List<Int>>, party: List<PartyMember?>): Map<Int, List<Stat>> {
    val s = store ?: return emptyMap()
    return s.apply(awards, party).also { _state.value = s.state }
  }

  @Synchronized
  fun setTarget(slot: Int, stat: Stat, value: Int) {
    val s = store ?: return
    s.setTarget(slot, stat, value)
    _state.value = s.state
  }

  @Synchronized
  fun resetGains(slot: Int) {
    val s = store ?: return
    s.resetGains(slot)
    _state.value = s.state
  }
}
