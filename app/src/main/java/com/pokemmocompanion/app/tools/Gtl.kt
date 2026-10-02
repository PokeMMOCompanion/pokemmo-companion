package com.pokemmocompanion.app.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** An item as PokeMMO Hub's Items page lists it (assets/pokemmo/items.json); [id] is the GTL price API's item id. */
@Serializable
data class ItemInfo(val id: Int, val hubId: Int = 0, val name: String, val desc: String = "", val category: Int = 0)

/**
 * Current GTL price from PokeMMO Hub's price API (apis.fiereu.de/pokemmoprices/v1/items): the lowest listing,
 * how many listings and how many items are up.
 */
@Serializable
data class GtlPrice(
  val item_id: Int,
  val tradable: Boolean = true,
  val price: Long = 0,
  val listings: Int = 0,
  val quantity: Int = 0,
  /** [year, month, day, hour, minute, ...] */
  val last_updated: List<Long> = emptyList(),
) {
  /** "2026-01-27" or "" when unknown. */
  val updatedDay: String
    get() = if (last_updated.size >= 3) "%04d-%02d-%02d".format(last_updated[0], last_updated[1], last_updated[2]) else ""
}

/** One point of a price history: [x] epoch seconds, [y] lowest price. */
@Serializable data class PricePoint(val x: Long, val y: Long)

object Gtl {
  const val PRICES_URL = "https://apis.fiereu.de/pokemmoprices/v1/items"

  fun historyUrl(itemId: Int) = "https://apis.fiereu.de/pokemmoprices/v1/graph/items/$itemId/min"

  // Some Hub items have no category (null): fall back to the default.
  private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
  }

  fun parseItems(text: String): List<ItemInfo> = json.decodeFromString(text)

  fun parsePrices(text: String): Map<Int, GtlPrice> = json.decodeFromString<List<GtlPrice>>(text).associateBy { it.item_id }

  /** Daily lows: the API can give several points per day; keep the lowest of each day, oldest first. */
  fun parseHistory(text: String): List<PricePoint> =
    json.decodeFromString<List<PricePoint>>(text).groupBy { it.x / 86_400 }.map { (_, pts) -> pts.minBy { it.y } }.sortedBy { it.x }

  /** "1,234,567". */
  fun money(v: Long) = "%,d".format(v)
}
