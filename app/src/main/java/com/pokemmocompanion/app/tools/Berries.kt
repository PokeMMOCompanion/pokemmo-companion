package com.pokemmocompanion.app.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A berry from the PokeMMO Hub Berries Helper snapshot (assets/pokemmo/berries.json).
 *
 * @param flavors seeds needed per flavor (spicy, dry, sweet, bitter, sour): a Plain seed counts 1, a Very seed 2
 */
@Serializable
data class Berry(
  val id: Int,
  val name: String,
  val effect: String = "",
  val growHours: Int,
  val witherHours: Int,
  val harvestMin: Int,
  val harvestMax: Int,
  val flavors: Map<String, Int>,
  val giftType: String = "",
  val giftPower: Int = 0,
) {
  /** "Spicy 2 · Sweet 1 · Bitter 1". */
  val seeds: String
    get() = flavors.filterValues { it > 0 }.entries.joinToString(" · ") { (f, n) -> "${f.replaceFirstChar { it.uppercase() }} $n" }

  companion object {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<Berry> = json.decodeFromString(text)
  }
}

/** One planted patch: [count] plants of a berry, planted (and last watered) at the given times (epoch ms). */
@Serializable
data class Plot(
  val id: Long,
  val berryId: Int,
  val count: Int = 1,
  val plantedAt: Long,
  val wateredAt: Long? = null,
  val note: String = "",
)

enum class ReminderKind { WATER, HARVEST, WITHER }

data class Reminder(val plotId: Long, val kind: ReminderKind, val at: Long)

/**
 * Berry timing as PokeMMO Hub's berry tracker works it out:
 * - ready to harvest [Berry.growHours] after planting;
 * - the soil dries out 10 hours after watering (15 for the slow 42/44/67-hour berries), and a fresh plant counts as
 *   already 6 hours dry;
 * - ripe berries fall off [Berry.witherHours] after they're ready.
 */
object BerryTimes {
  const val HOUR = 3_600_000L
  private const val PLANTED_DRY_HOURS = 6

  fun dryHours(b: Berry) = if (b.growHours >= 42) 15 else 10

  fun readyAt(p: Plot, b: Berry) = p.plantedAt + b.growHours * HOUR

  fun witherAt(p: Plot, b: Berry) = readyAt(p, b) + b.witherHours * HOUR

  /** When the soil is completely dry. */
  fun dryAt(p: Plot, b: Berry) = (p.wateredAt ?: (p.plantedAt - PLANTED_DRY_HOURS * HOUR)) + dryHours(b) * HOUR

  /** Soil moisture 0..1 (1 = just watered). */
  fun moisture(p: Plot, b: Berry, now: Long): Double = ((dryAt(p, b) - now).toDouble() / (dryHours(b) * HOUR)).coerceIn(0.0, 1.0)

  /**
   * Reminders for a plot: water an hour before the soil dries out (while still growing), harvest when ready, and a
   * last call an hour before the berries wither.
   */
  fun reminders(p: Plot, b: Berry): List<Reminder> {
    val ready = readyAt(p, b)
    val water = dryAt(p, b) - HOUR
    return listOfNotNull(
      Reminder(p.id, ReminderKind.WATER, water).takeIf { water < ready },
      Reminder(p.id, ReminderKind.HARVEST, ready),
      Reminder(p.id, ReminderKind.WITHER, witherAt(p, b) - HOUR),
    )
  }
}
