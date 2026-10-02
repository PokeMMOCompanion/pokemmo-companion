package com.pokemmocompanion.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokemmocompanion.app.tools.Berry
import com.pokemmocompanion.app.tools.BerryTimes
import com.pokemmocompanion.app.tools.FarmRepository
import com.pokemmocompanion.app.tools.Plot
import com.pokemmocompanion.app.ui.dex.DexBar
import com.pokemmocompanion.app.ui.dex.DexColors
import kotlinx.coroutines.delay

/**
 * Berries: "My farm" lists the planted plots with live countdowns (reminders fire even with the app closed), and
 * "All berries" lists every berry's seeds, grow time and yield, with a Plant button.
 */
@Composable
fun BerriesTool(modifier: Modifier) {
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var planting by rememberSaveable { mutableStateOf<Int?>(null) }
  val plots by FarmRepository.plots.collectAsStateWithLifecycle()
  val now by produceState(System.currentTimeMillis()) {
    while (true) {
      value = System.currentTimeMillis()
      delay(30_000)
    }
  }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Chip("My farm (${plots.size})", tab == 0) { tab = 0 }
      Chip("All berries", tab == 1) { tab = 1 }
    }
    planting?.let { id ->
      FarmRepository.berry(id)?.let { b ->
        PlantForm(b, onDone = {
          planting = null
          tab = 0
        })
      }
    }
    if (tab == 0) {
      if (plots.isEmpty()) {
        Text(
          "Nothing planted. Open All berries and tap Plant after you plant in the game. Reminders buzz before the soil " +
            "dries, when berries are ready, and before they wither.",
          style = MaterialTheme.typography.bodySmall,
          color = DexColors.InkMuted,
        )
      }
      LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(plots.sortedBy { p -> FarmRepository.berry(p.berryId)?.let { BerryTimes.readyAt(p, it) } ?: 0 }, key = { it.id }) { p ->
          FarmRepository.berry(p.berryId)?.let { PlotCard(p, it, now) }
        }
      }
    } else {
      var query by rememberSaveable { mutableStateOf("") }
      SearchBox(query, "Search berries") { query = it }
      val shown = remember(query) { FarmRepository.berries.filter { it.name.contains(query.trim(), ignoreCase = true) } }
      LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(shown, key = { it.id }) { b -> BerryCard(b) { planting = b.id } }
      }
    }
  }
}

@Composable
private fun PlotCard(p: Plot, b: Berry, now: Long) {
  val ready = BerryTimes.readyAt(p, b)
  val wither = BerryTimes.witherAt(p, b)
  val moisture = BerryTimes.moisture(p, b, now)
  val small = MaterialTheme.typography.bodySmall
  Column(
    Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .border(1.5.dp, DexColors.palette.tileOutline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("${p.count}× ${b.name}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
      if (p.note.isNotEmpty()) Text(p.note, style = small, color = DexColors.InkMuted)
    }
    Text(
      when {
        now >= wither -> "Withered (berries fell off)."
        now >= ready -> "Ready! Harvest within ${duration(wither - now)}."
        else -> "Ready in ${duration(ready - now)} · yields ${b.harvestMin}–${b.harvestMax} each"
      },
      style = small,
      color = if (now in ready until wither) DexColors.Good else if (now >= wither) DexColors.Bad else DexColors.Ink,
      fontWeight = if (now in ready until wither) FontWeight.Bold else null,
    )
    if (now < ready) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Soil", style = small, color = DexColors.InkMuted)
        DexBar(moisture.toFloat(), if (moisture > 0.3) DexColors.You else DexColors.Bad, Modifier.weight(1f))
        Text(if (moisture <= 0.0) "Dry!" else "dry in ${duration(BerryTimes.dryAt(p, b) - now)}", style = small)
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      if (now < ready) Chip("💧 Watered", false) { FarmRepository.water(p.id) }
      Chip(if (now >= ready) "Harvested" else "Remove", false) { FarmRepository.remove(p.id) }
    }
  }
}

@Composable
private fun BerryCard(b: Berry, onPlant: () -> Unit) {
  val small = MaterialTheme.typography.bodySmall
  Column(
    Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .border(1.5.dp, DexColors.palette.tileOutline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(b.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
      Chip("Plant", false, onPlant)
    }
    Text("Seeds: ${b.seeds}  (Plain = 1, Very = 2)", style = small)
    Text("Grows in ${b.growHours}h · ${b.harvestMin}–${b.harvestMax} berries · withers ${b.witherHours}h after", style = small)
    if (b.effect.isNotEmpty()) Text(b.effect, style = small, color = DexColors.InkMuted)
  }
}

@Composable
private fun PlantForm(b: Berry, onDone: () -> Unit) {
  var count by rememberSaveable { mutableIntStateOf(1) }
  var note by rememberSaveable { mutableStateOf("") }
  var minutesAgo by rememberSaveable { mutableLongStateOf(0) }
  Column(
    Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .border(2.dp, DexColors.palette.tileOutline, RoundedCornerShape(8.dp))
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text("Plant ${b.name}", fontWeight = FontWeight.Bold)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("How many", style = MaterialTheme.typography.labelMedium, color = DexColors.InkMuted)
      Chip("−", false) { count = (count - 1).coerceAtLeast(1) }
      Text("$count", fontWeight = FontWeight.Bold)
      Chip("+", false) { count = (count + 1).coerceAtMost(99) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("Planted", style = MaterialTheme.typography.labelMedium, color = DexColors.InkMuted)
      listOf(0L to "now", 30L to "30m ago", 60L to "1h ago", 120L to "2h ago").forEach { (m, label) ->
        Chip(label, minutesAgo == m) { minutesAgo = m }
      }
    }
    OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Where? e.g. Route 120 patch") })
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Chip("Plant", true) {
        FarmRepository.plant(b.id, count, note, System.currentTimeMillis() - minutesAgo * 60_000)
        onDone()
      }
      Text("Cancel", modifier = Modifier.clickable(onClick = onDone).padding(6.dp), color = DexColors.InkMuted)
    }
  }
}
