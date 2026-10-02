package com.pokemmocompanion.app.party

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Which species the player has caught, learned from battles: the Pokédex ball next to an opponent's name (latest
 * reading wins) and "Gotcha!" messages. Species never met are unknown. Saved as files/dex.json (dex number → caught).
 */
class DexStore(private val file: File) {
  private val json = Json { ignoreUnknownKeys = true }

  var caught: Map<Int, Boolean> = runCatching { json.decodeFromString<Map<Int, Boolean>>(file.readText()) }.getOrDefault(emptyMap())
    private set

  /** Records a reading; a "caught" never goes back to "needed" (a misread ball must not undo a catch). */
  fun record(dexId: Int, isCaught: Boolean): Boolean {
    val old = caught[dexId]
    if (old == true || old == isCaught) return false
    caught = caught + (dexId to isCaught)
    file.writeText(json.encodeToString(caught))
    return true
  }
}

object DexProgress {
  private var store: DexStore? = null
  private val _caught = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
  /** Dex number → caught (true) or still needed (false); missing = never met. */
  val caught: StateFlow<Map<Int, Boolean>> = _caught.asStateFlow()

  @Synchronized
  fun init(context: Context) {
    if (store != null) return
    store = DexStore(File(context.applicationContext.filesDir, "dex.json")).also { _caught.value = it.caught }
  }

  @Synchronized
  fun record(dexId: Int, isCaught: Boolean) {
    val s = store ?: return
    if (s.record(dexId, isCaught)) _caught.value = s.caught
  }
}
