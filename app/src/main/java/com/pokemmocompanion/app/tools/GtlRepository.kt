package com.pokemmocompanion.app.tools

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** GTL prices from PokeMMO Hub's price API, fetched when asked and cached on the device (files/gtl.json). */
data class GtlState(val prices: Map<Int, GtlPrice> = emptyMap(), val fetchedAt: Long? = null, val loading: Boolean = false, val error: String? = null)

object GtlRepository {
  private var cache: File? = null
  private val _state = MutableStateFlow(GtlState())
  val state: StateFlow<GtlState> = _state.asStateFlow()

  var items: List<ItemInfo> = emptyList()
    private set

  @Synchronized
  fun init(context: Context) {
    if (cache != null) return
    items = context.assets.open("pokemmo/items.json").bufferedReader().use { Gtl.parseItems(it.readText()) }
    cache = File(context.filesDir, "gtl.json").also { f ->
      if (f.exists()) runCatching { _state.value = GtlState(Gtl.parsePrices(f.readText()), f.lastModified()) }
    }
  }

  /** Downloads the current price list. */
  suspend fun refresh() {
    _state.value = _state.value.copy(loading = true, error = null)
    _state.value =
      try {
        val text = withContext(Dispatchers.IO) { get(Gtl.PRICES_URL) }
        val prices = Gtl.parsePrices(text)
        withContext(Dispatchers.IO) { cache?.writeText(text) }
        GtlState(prices, System.currentTimeMillis())
      } catch (e: Exception) {
        _state.value.copy(loading = false, error = "Couldn't load prices: ${e.message}")
      }
  }

  /** Daily lowest prices for one item. */
  suspend fun history(itemId: Int): List<PricePoint> = withContext(Dispatchers.IO) { Gtl.parseHistory(get(Gtl.historyUrl(itemId))) }

  private fun get(url: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    conn.connectTimeout = 15_000
    conn.readTimeout = 30_000
    conn.setRequestProperty("User-Agent", "PokeMMO-Companion (personal Android app)")
    try {
      if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
      return conn.inputStream.bufferedReader().use { it.readText() }
    } finally {
      conn.disconnect()
    }
  }

  /** Lowest current price of the first item whose name matches, e.g. "Power Weight". */
  fun priceOf(name: String): Long? {
    val item = items.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
    return _state.value.prices[item.id]?.price?.takeIf { it > 0 }
  }
}
