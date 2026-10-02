package com.pokemmocompanion.app.quest

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-wide quest progress backed by [QuestStore]. */
object QuestRepository {
  private var store: QuestStore? = null
  private val _state = MutableStateFlow(QuestState())
  val state: StateFlow<QuestState> = _state.asStateFlow()

  @Synchronized
  fun init(context: Context) {
    if (store != null) return
    store = QuestStore(File(context.applicationContext.filesDir, "quests.json")).also { _state.value = it.state }
  }

  @Synchronized
  private fun change(block: QuestStore.() -> QuestState) {
    _state.value = store?.block() ?: return
  }

  fun toggle(id: String) = change { toggle(id) }

  fun setRegion(name: String) = change { setRegion(name) }

  fun addTask(text: String) = change { addTask(text) }

  fun toggleTask(id: Long) = change { toggleTask(id) }

  fun removeTask(id: Long) = change { removeTask(id) }

  fun clearDone() = change { clearDone() }
}
