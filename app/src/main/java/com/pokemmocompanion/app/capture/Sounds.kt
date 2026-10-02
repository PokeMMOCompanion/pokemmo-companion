package com.pokemmocompanion.app.capture

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The app's own sounds, synthesized at startup (no audio files, no game audio):
 * - Click: a short rising "pop" for button presses (media volume).
 * - Shiny: a sparkling arpeggio, played twice (alarm volume, so it's loud even if media is down).
 * - Pokédex: a rising two-note "ding-ding" (alarm volume).
 * - Farm: a soft falling "drip-drop" for berry reminders (alarm volume).
 * - Goal: a quick rising three-note fanfare when an EV target is reached (alarm volume).
 */
object Sounds {
  private const val RATE = 44100

  private var click: Clip? = null
  private var shiny: Clip? = null
  private var pokedex: Clip? = null
  private var farm: Clip? = null
  private var goal: Clip? = null

  fun click(context: Context) = clip(context) { click }.play()

  fun shiny(context: Context) = clip(context) { shiny }.play()

  fun pokedex(context: Context) = clip(context) { pokedex }.play()

  fun farm(context: Context) = clip(context) { farm }.play()

  fun goal(context: Context) = clip(context) { goal }.play()

  @Synchronized
  private fun clip(context: Context, pick: () -> Clip?): Clip {
    if (click == null) {
      click = Clip(pop(), AudioAttributes.USAGE_GAME)
      shiny = Clip(sparkle(), AudioAttributes.USAGE_ALARM)
      pokedex = Clip(dingDing(), AudioAttributes.USAGE_ALARM)
      farm = Clip(dripDrop(), AudioAttributes.USAGE_ALARM)
      goal = Clip(fanfare(), AudioAttributes.USAGE_ALARM)
    }
    return pick()!!
  }

  /** Quick upward pitch sweep with a fast decay, plus a tiny high tick on top. */
  private fun pop(): ShortArray {
    val out = FloatArray(ms(70))
    var phase = 0.0
    for (i in out.indices) {
      val t = i.toDouble() / RATE
      val f = 700 + 1100 * min(1.0, t / 0.04)
      phase += 2 * PI * f / RATE
      out[i] += (sin(phase) * exp(-t * 55)).toFloat()
    }
    tone(out, 2400.0, start = 0.0, dur = 0.025, decay = 120.0, gain = 0.35)
    return pcm(out)
  }

  /** Fast rising major arpeggio of bell tones with a high shimmer, twice. */
  private fun sparkle(): ShortArray {
    val out = FloatArray(ms(1500))
    val notes = listOf(76, 79, 84, 88, 91, 96) // E5 G5 C6 E6 G6 C7
    for (rep in 0..1) {
      val base = rep * 0.7
      notes.forEachIndexed { i, n -> bell(out, midi(n), base + i * 0.06, 0.45, 7.0, 0.5) }
      // Shimmer: two high notes trading quickly
      for (k in 0 until 6) bell(out, midi(if (k % 2 == 0) 100 else 103), base + 0.38 + k * 0.035, 0.12, 25.0, 0.18)
    }
    return pcm(out)
  }

  /** Two rising bell notes (G5, D6). */
  private fun dingDing(): ShortArray {
    val out = FloatArray(ms(650))
    bell(out, midi(79), 0.0, 0.35, 9.0, 0.7)
    bell(out, midi(86), 0.16, 0.45, 7.0, 0.7)
    return pcm(out)
  }

  /** Two soft falling notes (C6, G5), like water drops. */
  private fun dripDrop(): ShortArray {
    val out = FloatArray(ms(520))
    tone(out, midi(84), 0.0, 0.22, 14.0, 0.7)
    tone(out, midi(79), 0.2, 0.3, 11.0, 0.7)
    return pcm(out)
  }

  /** Quick rising major triad with the top note held (C5, E5, G5, C6). */
  private fun fanfare(): ShortArray {
    val out = FloatArray(ms(900))
    listOf(72, 76, 79).forEachIndexed { i, n -> bell(out, midi(n), i * 0.09, 0.2, 10.0, 0.55) }
    bell(out, midi(84), 0.27, 0.6, 4.0, 0.7)
    return pcm(out)
  }

  private fun bell(out: FloatArray, f: Double, start: Double, dur: Double, decay: Double, gain: Double) {
    tone(out, f, start, dur, decay, gain)
    tone(out, f * 2, start, dur, decay * 1.8, gain * 0.35)
    tone(out, f * 3, start, dur, decay * 2.6, gain * 0.12)
  }

  private fun tone(out: FloatArray, f: Double, start: Double, dur: Double, decay: Double, gain: Double) {
    val s0 = (start * RATE).toInt()
    val n = min((dur * RATE).toInt(), out.size - s0)
    val attack = RATE * 0.003
    for (i in 0 until n) {
      val t = i.toDouble() / RATE
      val env = min(1.0, i / attack) * exp(-t * decay)
      out[s0 + i] += (gain * env * sin(2 * PI * f * t)).toFloat()
    }
  }

  private fun midi(n: Int) = 440.0 * 2.0.pow((n - 69) / 12.0)

  private fun ms(ms: Int) = RATE * ms / 1000

  private fun pcm(x: FloatArray): ShortArray {
    val peak = x.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6f)
    return ShortArray(x.size) { (x[it] / peak * 0.85f * Short.MAX_VALUE).toInt().toShort() }
  }

  /** One preloaded static AudioTrack; replaying restarts it from the beginning. */
  private class Clip(data: ShortArray, usage: Int) {
    private val track =
      AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        )
        .setTransferMode(AudioTrack.MODE_STATIC)
        .setBufferSizeInBytes(data.size * 2)
        .build()
        .apply { write(data, 0, data.size) }

    @Synchronized
    fun play() {
      try {
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
        track.reloadStaticData()
        track.play()
      } catch (_: IllegalStateException) {
        // Sound is a nice-to-have; never let it break an alert or a tap.
      }
    }
  }
}
