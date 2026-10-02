package com.pokemmocompanion.app.capture

import com.pokemmocompanion.app.detect.BattleResult
import com.pokemmocompanion.app.calc.FieldState
import com.pokemmocompanion.app.calc.Place
import com.pokemmocompanion.app.calc.Revealed
import com.pokemmocompanion.app.calc.Stat
import com.pokemmocompanion.app.detect.BattleEvent
import com.pokemmocompanion.app.detect.EncounterSummary
import com.pokemmocompanion.app.detect.FrameFeatures
import com.pokemmocompanion.app.detect.ScreenState
import com.pokemmocompanion.app.party.ActiveReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CaptureState(
  val running: Boolean = false,
  /** Display that MediaProjection mirrors. Android always mirrors the default display. */
  val sourceDisplayId: Int? = null,
  val frameWidth: Int = 0,
  val frameHeight: Int = 0,
  /** Size of the captured content, if Android reports it (API 34+, e.g. single-app capture). */
  val contentWidth: Int? = null,
  val contentHeight: Int? = null,
  val framesReceived: Long = 0,
  val framesPerSecond: Float = 0f,
  val screenState: ScreenState = ScreenState.UNKNOWN,
  val features: FrameFeatures? = null,
  val battlesSeen: Int = 0,
  val encounters: EncounterSummary = EncounterSummary(),
  /** Raw OCR lines from the last name read, for tuning. */
  val lastOcrText: String? = null,
  /** Raw OCR lines from the last intro text read, for tuning. */
  val lastIntroText: String? = null,
  /** Most recent decided battle, wild or trainer. */
  val lastBattle: BattleResult? = null,
  /** Opponents of the battle in progress (null until their names are read, and outside battles). */
  val currentBattle: BattleResult? = null,
  /** Single battle: the opponent's HP box is gone (it fainted); the battle is just winding down. */
  val opponentDown: Boolean = false,
  /** Opponent's HP bar fill (0..1) in single battles. */
  val opponentHp: Float? = null,
  val trainerBattles: Int = 0,
  /** The player's Pokémon in battle, read from its HP box (null outside battles or until read). */
  val active: ActiveReading? = null,
  /** Party slot [active] was matched to. */
  val activeSlot: Int? = null,
  /** Raw OCR of the player's HP box ("name | hp"), for when it can't be parsed. */
  val activeOcr: String? = null,
  /** Stat stages from the badges under the player's HP box / next to the opponent's name box. */
  val yourStages: Map<Stat, Int> = emptyMap(),
  val oppStages: Map<Stat, Int> = emptyMap(),
  /** Move history of the current (or last) battle, from the battle text box. */
  val battleLog: List<BattleEvent> = emptyList(),
  /** Set by the UI: read PokeMMO summary screens into the stored party. */
  val partyReading: Boolean = false,
  /** Last summary page read, e.g. "Slot 1 · STATS", for feedback while reading. */
  val partyStatus: String? = null,
  /** Weather and screens in the current battle (statuses come from the battle log). */
  val field: FieldState = FieldState.NONE,
  /** Battle memory per opposing Pokémon ("Species|level"): revealed moves/ability/item and narrowed stats. */
  val revealed: Map<String, Revealed> = emptyMap(),
  /** Where the player is: from the pause menu header, or worked out from recent wild encounters. */
  val here: Here? = null,
  val error: String? = null,
)

/**
 * The player's location. [place] is set when it's certain enough (menu name in one region, or one place fits the
 * encounters); otherwise [candidates] lists the options. [menuName] is the raw menu text (towns without wild Pokémon
 * have no [place]). [season]/[timeOfDay]/[clock] come from the menu's game clock.
 */
data class Here(
  val place: Place?,
  val candidates: List<Place> = emptyList(),
  val menuName: String? = null,
  val season: String? = null,
  val timeOfDay: String? = null,
  val clock: String? = null,
  val fromMenu: Boolean,
  val at: Long,
  /** Picked by hand in the Here view. */
  val manual: Boolean = false,
)

/** Shared state between [CaptureService] and the UI. */
object CaptureRepository {
  private val _state = MutableStateFlow(CaptureState())
  val state: StateFlow<CaptureState> = _state.asStateFlow()

  fun update(transform: (CaptureState) -> CaptureState) = _state.update(transform)
}
