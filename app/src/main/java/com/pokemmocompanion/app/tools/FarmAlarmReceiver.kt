package com.pokemmocompanion.app.tools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pokemmocompanion.app.capture.Alerts

/** A berry reminder went off: sound, buzz and a notification saying which plot and what to do. */
class FarmAlarmReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    FarmRepository.init(context)
    val plotId = intent.getLongExtra(FarmAlarms.EXTRA_PLOT, -1)
    val kind = intent.getStringExtra(FarmAlarms.EXTRA_KIND)?.let { runCatching { ReminderKind.valueOf(it) }.getOrNull() } ?: return
    val plot = FarmRepository.plots.value.firstOrNull { it.id == plotId } ?: return
    val berry = FarmRepository.berry(plot.berryId) ?: return
    val what = "${plot.count}× ${berry.name}" + if (plot.note.isNotEmpty()) " (${plot.note})" else ""
    val (title, text) =
      when (kind) {
        ReminderKind.WATER -> "Water your berries" to "$what: soil dries out in about an hour."
        ReminderKind.HARVEST -> "Berries ready" to "$what can be harvested now."
        ReminderKind.WITHER -> "Harvest soon!" to "$what: berries fall off in about an hour."
      }
    Alerts.farm(context, (plotId * 3 + kind.ordinal).toInt(), title, text)
  }
}
