package com.pokemmocompanion.app.party

import android.content.Context
import com.pokemmocompanion.app.detect.EncounterParser
import com.pokemmocompanion.app.calc.GameDataLoader
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The stored party, shared by the capture service (writes) and the UI (reads). Saved in party.json. */
object PartyRepository {
  private var store: PartyStore? = null
  private val _party = MutableStateFlow(Party())
  val party: StateFlow<Party> = _party.asStateFlow()

  @Synchronized
  fun init(context: Context) {
    if (store != null) return
    val app = context.applicationContext
    store =
      PartyStore(
        File(app.filesDir, "party.json"),
        speciesName = { id -> GameDataLoader.get(app).species(id)?.name },
        abilitiesOf = { name -> GameDataLoader.get(app).species(name)?.abilities.orEmpty() },
      )
    _party.value = store!!.party
  }

  @Synchronized
  fun apply(slot: Int, page: SummaryPage): ApplyResult {
    val s = store ?: return ApplyResult.UNCHANGED
    return s.apply(slot, page).also { if (it == ApplyResult.STORED) _party.value = s.party }
  }

  @Synchronized
  fun set(m: PartyMember) {
    val s = store ?: return
    s.set(m)
    _party.value = s.party
  }

  @Synchronized
  fun remove(slot: Int) {
    val s = store ?: return
    s.remove(slot)
    _party.value = s.party
  }

  @Synchronized
  fun leveledUp(slot: Int, level: Int) {
    val s = store ?: return
    if (s.leveledUp(slot, level)) _party.value = s.party
  }

  @Synchronized
  fun learned(slot: Int, move: String) {
    val s = store ?: return
    if (s.learned(slot, move)) _party.value = s.party
  }

  /** The party member a battle message calls [name] (nickname or species, allowing small OCR slips). */
  fun byName(name: String): PartyMember? {
    val n = name.lowercase().filter { it.isLetterOrDigit() }
    return _party.value.members.filterNotNull().minByOrNull { m ->
      listOfNotNull(m.nickname, m.species).minOf { EncounterParser.editDistance(n, it.lowercase().filter(Char::isLetterOrDigit)) }
    }?.takeIf { m -> listOfNotNull(m.nickname, m.species).any { EncounterParser.editDistance(n, it.lowercase().filter(Char::isLetterOrDigit)) <= 2 } }
  }

  @Synchronized
  fun updateHp(slot: Int, hp: Int) {
    val s = store ?: return
    if (s.updateHp(slot, hp)) _party.value = s.party
  }

  @Synchronized
  fun clear() {
    store?.clear()
    _party.value = Party()
  }
}
