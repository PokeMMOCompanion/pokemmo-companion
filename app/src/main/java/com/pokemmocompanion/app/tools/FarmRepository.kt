package com.pokemmocompanion.app.tools

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The player's berry plots (files/farm.json) and their reminders. Reminders are Android alarms, so they fire with the
 * app closed and capture off; [FarmAlarmReceiver] turns them into a sound, a buzz and a notification.
 */
object FarmRepository {
  private val json = Json { ignoreUnknownKeys = true }
  private var file: File? = null
  private var app: Context? = null
  private val _plots = MutableStateFlow<List<Plot>>(emptyList())
  val plots: StateFlow<List<Plot>> = _plots.asStateFlow()

  var berries: List<Berry> = emptyList()
    private set

  @Synchronized
  fun init(context: Context) {
    if (file != null) return
    app = context.applicationContext
    berries = context.assets.open("pokemmo/berries.json").bufferedReader().use { Berry.parse(it.readText()) }
    file = File(context.filesDir, "farm.json").also { f ->
      _plots.value = runCatching { json.decodeFromString(ListSerializer(Plot.serializer()), f.readText()) }.getOrDefault(emptyList())
    }
  }

  fun berry(id: Int) = berries.firstOrNull { it.id == id }

  fun plant(berryId: Int, count: Int, note: String, at: Long = System.currentTimeMillis()) =
    change { it + Plot(id = (it.maxOfOrNull { p -> p.id } ?: 0) + 1, berryId = berryId, count = count, plantedAt = at, note = note.trim()) }

  fun water(plotId: Long, at: Long = System.currentTimeMillis()) = change { list -> list.map { if (it.id == plotId) it.copy(wateredAt = at) else it } }

  fun remove(plotId: Long) = change { list -> list.filterNot { it.id == plotId } }

  @Synchronized
  private fun change(block: (List<Plot>) -> List<Plot>) {
    val old = _plots.value
    val new = block(old)
    _plots.value = new
    file?.writeText(json.encodeToString(ListSerializer(Plot.serializer()), new))
    app?.let { FarmAlarms.reschedule(it, old, new) }
  }

  /** Re-arms every future reminder (after a reboot, or when the app starts). */
  fun rearm(context: Context) {
    init(context)
    FarmAlarms.reschedule(context.applicationContext, _plots.value, _plots.value)
  }
}

object FarmAlarms {
  const val EXTRA_PLOT = "plot"
  const val EXTRA_KIND = "kind"

  private fun code(plotId: Long, kind: ReminderKind) = (plotId * 3 + kind.ordinal).toInt()

  private fun intent(context: Context, plotId: Long, kind: ReminderKind): PendingIntent =
    PendingIntent.getBroadcast(
      context,
      code(plotId, kind),
      Intent(context, FarmAlarmReceiver::class.java).putExtra(EXTRA_PLOT, plotId).putExtra(EXTRA_KIND, kind.name),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

  /** Cancels the old plots' alarms and sets the new plots' future ones. Inexact but allowed while idle. */
  fun reschedule(context: Context, old: List<Plot>, new: List<Plot>) {
    val am = context.getSystemService(AlarmManager::class.java)
    for (p in old) for (k in ReminderKind.entries) am.cancel(intent(context, p.id, k))
    val now = System.currentTimeMillis()
    for (p in new) {
      val b = FarmRepository.berry(p.berryId) ?: continue
      for (r in BerryTimes.reminders(p, b)) {
        if (r.at > now) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, intent(context, r.plotId, r.kind))
      }
    }
  }
}

/** Re-arms berry reminders after the device restarts (alarms don't survive a reboot). */
class FarmBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_BOOT_COMPLETED) FarmRepository.rearm(context)
  }
}
