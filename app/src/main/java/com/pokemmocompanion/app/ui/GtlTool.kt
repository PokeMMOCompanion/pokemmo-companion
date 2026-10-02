package com.pokemmocompanion.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokemmocompanion.app.tools.Gtl
import com.pokemmocompanion.app.tools.GtlRepository
import com.pokemmocompanion.app.tools.ItemInfo
import com.pokemmocompanion.app.tools.PricePoint
import com.pokemmocompanion.app.ui.dex.DexColors
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

/**
 * GTL prices (PokeMMO Hub's price API): search items, see the lowest listing and how many are up, tap one for its
 * price history. Prices download when you tap Refresh (or open this the first time) and are kept on the device.
 */
@Composable
fun GtlTool(modifier: Modifier) {
  val state by GtlRepository.state.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()
  var query by rememberSaveable { mutableStateOf("") }
  var sortByPrice by rememberSaveable { mutableStateOf(false) }
  var open by rememberSaveable { mutableIntStateOf(0) }
  LaunchedEffect(Unit) { if (state.prices.isEmpty() && !state.loading) GtlRepository.refresh() }

  val shown =
    remember(state.prices, query, sortByPrice) {
      GtlRepository.items
        .filter { (state.prices[it.id]?.listings ?: 0) > 0 && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)) }
        .let { list ->
          if (sortByPrice) list.sortedByDescending { state.prices[it.id]?.price ?: 0 }
          else list.sortedByDescending { state.prices[it.id]?.quantity ?: 0 }
        }
        .take(300)
    }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        when {
          state.loading -> "Loading prices…"
          state.fetchedAt != null -> "Updated ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(state.fetchedAt!!))}"
          else -> "No prices yet"
        },
        style = MaterialTheme.typography.labelMedium,
        color = DexColors.InkMuted,
        modifier = Modifier.weight(1f),
      )
      Chip("↻ Refresh", false) { scope.launch { GtlRepository.refresh() } }
    }
    state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DexColors.Bad) }
    SearchBox(query, "Search items") { query = it }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Chip("Most listed", !sortByPrice) { sortByPrice = false }
      Chip("Highest price", sortByPrice) { sortByPrice = true }
    }
    LazyColumn(Modifier.weight(1f)) {
      items(shown, key = { it.id }) { item ->
        ItemRow(item, open == item.id) { open = if (open == item.id) 0 else item.id }
      }
    }
  }
}

@Composable
private fun ItemRow(item: ItemInfo, expanded: Boolean, onClick: () -> Unit) {
  val price = GtlRepository.state.value.prices[item.id]
  Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(item.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
      Text(
        price?.let { "$" + Gtl.money(it.price) } ?: "—",
        fontWeight = FontWeight.Bold,
        color = DexColors.Good,
        textAlign = TextAlign.End,
      )
    }
    Text(
      listOfNotNull(price?.let { "${Gtl.money(it.quantity.toLong())} up in ${it.listings} listings" }, price?.updatedDay?.ifEmpty { null }?.let { "seen $it" })
        .joinToString(" · "),
      style = MaterialTheme.typography.labelSmall,
      color = DexColors.InkMuted,
    )
    if (expanded) {
      if (item.desc.isNotEmpty()) Text(item.desc, style = MaterialTheme.typography.bodySmall)
      PriceHistory(item.id)
    }
  }
}

@Composable
private fun PriceHistory(itemId: Int) {
  val points by produceState<List<PricePoint>?>(null, itemId) { value = runCatching { GtlRepository.history(itemId) }.getOrDefault(emptyList()) }
  val pts = points
  when {
    pts == null -> Text("Loading history…", style = MaterialTheme.typography.labelSmall)
    pts.size < 2 -> Text("No price history.", style = MaterialTheme.typography.labelSmall)
    else -> {
      val recent = pts.takeLast(90)
      val lo = recent.minOf { it.y }
      val hi = recent.maxOf { it.y }
      val color = DexColors.You
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
          Text("$" + Gtl.money(hi), style = MaterialTheme.typography.labelSmall)
          Text("$" + Gtl.money(lo), style = MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.weight(1f).height(60.dp)) {
          val span = (hi - lo).coerceAtLeast(1)
          fun at(i: Int) = Offset(i * size.width / (recent.size - 1), size.height - (recent[i].y - lo) * size.height / span)
          for (i in 1 until recent.size) drawLine(color, at(i - 1), at(i), strokeWidth = 4f)
        }
      }
      Text("Lowest price per day, last ${recent.size} days", style = MaterialTheme.typography.labelSmall, color = DexColors.InkMuted)
    }
  }
}

