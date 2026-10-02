package com.pokemmocompanion.app.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.pokemmocompanion.app.MainActivity

/**
 * Player-facing alerts. Each kind has its own vibration pattern so it can be told apart by feel alone:
 * - Shiny: three long, full-strength buzzes.
 * - Pokédex: two short taps.
 *
 * Vibration is driven directly (as an alarm, so touch-feedback settings don't mute it) rather than through the
 * notification channel, and so is sound (the app's own [Sounds]). Notifications are silent, low importance: a
 * status-bar entry, never a heads-up popup that could cover the game on the top screen.
 */
object Alerts {
  private const val SHINY_CHANNEL = "shiny_silent"
  private const val POKEDEX_CHANNEL = "pokedex_silent"
  // Older channels played the system sound or vibrated; a channel's sound can't be changed once created.
  private val OLD_CHANNELS = listOf("pokedex", "shiny_alert", "pokedex_needed")
  private const val SHINY_ID = 101
  private const val FARM_CHANNEL = "farm_silent"
  private const val EV_CHANNEL = "ev_silent"
  private const val POKEDEX_ID = 100

  private val SHINY_PATTERN = longArrayOf(0, 700, 250, 700, 250, 700)
  private val POKEDEX_PATTERN = longArrayOf(0, 120, 120, 120)
  private val FARM_PATTERN = longArrayOf(0, 60, 90, 60, 90, 60)
  private val GOAL_PATTERN = longArrayOf(0, 300, 120, 120)

  fun shiny(context: Context, names: List<String>) {
    vibrate(context, SHINY_PATTERN)
    Sounds.shiny(context)
    notify(context, SHINY_CHANNEL, "Shiny alert", SHINY_ID, "★ SHINY ★", "Shiny ${names.joinToString()}!")
  }

  fun pokedexNeeded(context: Context, names: List<String>) {
    vibrate(context, POKEDEX_PATTERN)
    Sounds.pokedex(context)
    notify(
      context,
      POKEDEX_CHANNEL,
      "Pokédex: not caught yet",
      POKEDEX_ID,
      "Needed for Pokédex",
      "${names.joinToString()} — not caught yet",
    )
  }

  /** Berry reminder ([id] keeps one notification per plot and kind). */
  fun farm(context: Context, id: Int, title: String, text: String) {
    vibrate(context, FARM_PATTERN)
    Sounds.farm(context)
    notify(context, FARM_CHANNEL, "Berry reminders", 2000 + id, title, text)
  }

  /** A party member reached an EV target. */
  fun evGoal(context: Context, id: Int, text: String) {
    vibrate(context, GOAL_PATTERN)
    Sounds.goal(context)
    notify(context, EV_CHANNEL, "EV targets", 3000 + id, "EV target reached", text)
  }

  /** Sample of each alert's vibration and sound so the player can learn them. */
  fun test(context: Context, shiny: Boolean) {
    vibrate(context, if (shiny) SHINY_PATTERN else POKEDEX_PATTERN)
    if (shiny) Sounds.shiny(context) else Sounds.pokedex(context)
  }

  private fun vibrate(context: Context, pattern: LongArray) {
    val vibrator =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
      } else {
        @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
      }
    if (!vibrator.hasVibrator()) return
    // Full strength on every "on" segment, off in between.
    val amplitudes = IntArray(pattern.size) { i -> if (i % 2 == 1) 255 else 0 }
    val effect =
      if (vibrator.hasAmplitudeControl()) VibrationEffect.createWaveform(pattern, amplitudes, -1)
      else VibrationEffect.createWaveform(pattern, -1)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
    } else {
      vibrator.vibrate(effect)
    }
  }

  private fun notify(context: Context, channel: String, channelName: String, id: Int, title: String, text: String) {
    val nm = context.getSystemService(NotificationManager::class.java)
    OLD_CHANNELS.forEach(nm::deleteNotificationChannel)
    nm.createNotificationChannel(
      NotificationChannel(channel, channelName, NotificationManager.IMPORTANCE_LOW).apply {
        enableVibration(false) // vibration and sound are ours, see vibrate() and Sounds
        setSound(null, null)
      }
    )
    val open =
      PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    nm.notify(
      id,
      NotificationCompat.Builder(context, channel)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(open)
        .setAutoCancel(true)
        .build(),
    )
  }
}
