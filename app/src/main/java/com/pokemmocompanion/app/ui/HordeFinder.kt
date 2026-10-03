package com.pokemmocompanion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokemmocompanion.app.calc.GameData
import com.pokemmocompanion.app.calc.HordeSpot
import com.pokemmocompanion.app.calc.Hordes
import com.pokemmocompanion.app.calc.Spawn
import com.pokemmocompanion.app.calc.Stat
import com.pokemmocompanion.app.quest.Story
import com.pokemmocompanion.app.ui.dex.DexChip
import com.pokemmocompanion.app.ui.dex.DexColors
import com.pokemmocompanion.app.ui.dex.SpriteIcon
import java.util.Calendar

private val SEASON_NAMES = mapOf("Spr" to "Spring", "Sum" to "Summer", "Aut" to "Autumn", "Win" to "Winter")

/**
 * EV horde finder: pick a stat and see the horde spots that give the most of it per horde, filtered by season, time
 * of day and region. Tapping a Pokémon in a spot opens its Pokédex entry.
 */
@Composable
fun HordeFinder(data: GameData, spawns: Map<Int, List<Spawn>>?, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
  val thisSeason = remember { Hordes.seasonForMonth(Calendar.getInstance().get(Calendar.MONTH) + 1) }
  var stat by rememberSaveable { mutableIntStateOf(Stat.SPE.ordinal) }
  var season by rememberSaveable { mutableStateOf(thisSeason) } // "" = any season
  var time by rememberSaveable { mutableStateOf("") } // "" = any time
  var region by rememberSaveable { mutableStateOf("") } // "" = all regions

  if (spawns == null) {
    Text("Loading spawns…", modifier = modifier)
    return
  }
  val spots =
    remember(data, spawns, stat, season, time, region) {
      val allowed =
        Hordes.ALL_TIMES.filter { (s, t) -> (season.isEmpty() || s == season) && (time.isEmpty() || t == time) }.toSet()
      Hordes.best(Stat.entries[stat], data.species, spawns, allowed).filter { region.isEmpty() || it.region == region }.take(60)
    }

  Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    ChipRow("Stat") {
      Stat.entries.forEach { st -> Chip(st.short, stat == st.ordinal) { stat = st.ordinal } }
    }
    ChipRow("Season") {
      Chip("Any", season.isEmpty()) { season = "" }
      Hordes.SEASONS.forEach { s ->
        Chip(if (s == thisSeason) "$s (now)" else s, season == s) { season = s }
      }
    }
    ChipRow("Time") {
      Chip("Any", time.isEmpty()) { time = "" }
      Hordes.TIMES.forEach { t -> Chip(t, time == t) { time = t } }
    }
    ChipRow("Region") {
      Chip("All", region.isEmpty()) { region = "" }
      Story.REGIONS.forEach { r -> Chip(r.name, region == r.name) { region = r.name } }
    }
    Text(
      if (spots.isEmpty()) "No hordes give ${Stat.entries[stat].short} with these filters."
      else "Best ${Stat.entries[stat].short} hordes${if (season.isNotEmpty()) " in ${SEASON_NAMES[season]}" else ""}. Macho Brace doubles EVs.",
      style = MaterialTheme.typography.labelSmall,
      color = DexColors.InkMuted,
    )
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      items(spots, key = { listOf(it.region, it.place, it.method, it.size).joinToString("|") }) { spot ->
        SpotCard(spot, onOpen)
      }
    }
  }
}

@Composable
private fun SpotCard(spot: HordeSpot, onOpen: (Int) -> Unit) {
  Column(
    Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .border(1.5.dp, DexColors.palette.tileOutline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        "${fmtEv(spot.evs)} EVs",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Black,
        color = DexColors.Good,
      )
      Column(Modifier.weight(1f)) {
        Text(spot.place, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(spot.region, style = MaterialTheme.typography.labelSmall, color = DexColors.InkMuted)
      }
      DexChip("×${spot.size}", DexColors.WarnFill, Color.Black)
      DexChip(spot.method, DexColors.Track, DexColors.Ink)
    }
    Text(
      spot.whenBest + if (spot.purity < 0.999) " · ${(spot.purity * 100).toInt()}% of hordes give it" else "",
      style = MaterialTheme.typography.labelSmall,
      color = DexColors.InkMuted,
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      spot.mix.forEach { (s, share) ->
        Row(
          Modifier.clip(RoundedCornerShape(6.dp)).clickable { onOpen(s.id) }.padding(2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Box(Modifier.size(28.dp)) { SpriteIcon(s.id, 28.dp) }
          Text(
            "${s.name}${if (share < 0.999) " ${(share * 100).toInt()}%" else ""}",
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    }
  }
}

@Composable
private fun ChipRow(label: String, content: @Composable () -> Unit) {
  Row(
    Modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = DexColors.InkMuted, modifier = Modifier.padding(end = 2.dp))
    content()
  }
}

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
  val outline = DexColors.palette.tileOutline
  Text(
    text,
    modifier =
      Modifier.clip(RoundedCornerShape(6.dp))
        .background(if (on) outline else DexColors.Track)
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 5.dp),
    color = if (on) DexColors.palette.tileText else DexColors.Ink,
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold,
  )
}

private fun fmtEv(x: Double) = if (x == x.toLong().toDouble()) x.toLong().toString() else "%.1f".format(x)
