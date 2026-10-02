package com.pokemmocompanion.app.ui

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokemmocompanion.app.calc.GameData
import com.pokemmocompanion.app.calc.Hordes
import com.pokemmocompanion.app.calc.Places
import com.pokemmocompanion.app.calc.Spawn
import com.pokemmocompanion.app.calc.rate
import com.pokemmocompanion.app.capture.CaptureRepository
import com.pokemmocompanion.app.party.DexProgress
import com.pokemmocompanion.app.capture.Here
import com.pokemmocompanion.app.ui.dex.DexChip
import com.pokemmocompanion.app.ui.dex.DexColors
import com.pokemmocompanion.app.ui.dex.SpriteIcon
import com.pokemmocompanion.app.ui.dex.smashText
import java.util.Calendar

private val SEASON_SHORT = mapOf("Spring" to "Spr", "Summer" to "Sum", "Autumn" to "Aut", "Winter" to "Win")

/**
 * "Here": the player's place (read from the pause menu header, or worked out from recent wild encounters) and what
 * can be found there right now, for the game's season and time of day.
 */
@Composable
fun HereView(data: GameData, spawns: Map<Int, List<Spawn>>?, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
  val state by CaptureRepository.state.collectAsStateWithLifecycle()
  val caught by DexProgress.caught.collectAsStateWithLifecycle()
  val here = state.here
  var chosen by rememberSaveable { mutableStateOf<String?>(null) } // "region|place" picked from candidates
  var showAll by rememberSaveable { mutableStateOf(false) }
  var picking by rememberSaveable { mutableStateOf(false) }
  if (spawns == null) {
    Text("Loading spawns…", modifier = modifier)
    return
  }
  val places = remember(spawns) { Places(spawns) }
  if (picking) {
    PlacePicker(places, modifier) { p ->
      if (p != null) {
        CaptureRepository.update {
          it.copy(here = Here(p, season = here?.season, timeOfDay = here?.timeOfDay, clock = here?.clock, fromMenu = false, at = System.currentTimeMillis(), manual = true))
        }
      }
      picking = false
    }
    return
  }
  val place = here?.place ?: here?.candidates?.firstOrNull { "${it.region}|${it.name}" == chosen }
  val season = here?.season?.let(SEASON_SHORT::get) ?: Hordes.seasonForMonth(Calendar.getInstance().get(Calendar.MONTH) + 1)
  val time = here?.timeOfDay

  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Column(
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .border(2.dp, DexColors.palette.tileOutline, RoundedCornerShape(10.dp))
        .padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      when {
        here == null -> {
          Text("Where are you?", style = smashText(DexColors.Ink, DexColors.Track, 18))
          Text(
            "With capture on, open the game's menu once: the app reads the place and the game clock from it. " +
              "Wild battles also give it away after a fight or two.",
            style = MaterialTheme.typography.bodySmall,
            color = DexColors.InkMuted,
          )
        }
        else -> {
          Text(
            place?.name ?: here.menuName ?: "Somewhere with…",
            style = smashText(DexColors.Ink, DexColors.Track, 20),
          )
          Text(
            listOfNotNull(
                place?.region,
                when {
                  here.manual -> "picked by you"
                  here.fromMenu -> "from the menu"
                  else -> "probably (from encounters)"
                },
                here.clock?.let { "game time $it" },
                here.season,
                time,
              )
              .joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = DexColors.InkMuted,
          )
          if (place == null && here.candidates.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              here.candidates.forEach { c ->
                Chip("${c.name} (${c.region})", false) { chosen = "${c.region}|${c.name}" }
              }
            }
          } else if (place == null) {
            Text("No wild Pokémon listed for this place.", style = MaterialTheme.typography.bodySmall, color = DexColors.InkMuted)
          }
        }
      }
    }
    Chip("📍 Pick place", false) { picking = true }
    if (place == null) return@Column

    val list =
      remember(place, season, time, showAll) {
        places.spawnsAt(place, if (showAll) null else season, if (showAll) null else time)
          .sortedWith(compareBy<Pair<Int, Spawn>> { it.second.method }.thenByDescending { pct(rateNow(it.second, time)) })
      }
    val needed = list.map { it.first }.distinct().count { caught[it] == false }
    if (needed > 0) {
      Text("$needed still needed for your Pokédex here", style = MaterialTheme.typography.labelMedium, color = DexColors.Warn, fontWeight = FontWeight.Bold)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Chip("Now (${season}${time?.let { " · $it" } ?: ""})", !showAll) { showAll = false }
      Chip("Any season/time", showAll) { showAll = true }
    }
    LazyColumn(Modifier.weight(1f)) {
      list.groupBy { it.second.method }.forEach { (method, rows) ->
        item(key = "h$method") { Text(method.uppercase(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp)) }
        items(rows, key = { (id, sp) -> "$method|$id|${sp.minLevel}|${sp.horde}|${sp.seasons}|${sp.morning}${sp.day}${sp.night}" }) { (id, sp) ->
          SpawnRow(data, id, sp, time, showAll, caught[id]) { onOpen(id) }
        }
      }
    }
  }
}

/** Pick a place by hand: region, then search the places that have wild Pokémon. */
@Composable
private fun PlacePicker(places: Places, modifier: Modifier, onPick: (com.pokemmocompanion.app.calc.Place?) -> Unit) {
  var region by rememberSaveable { mutableStateOf("Kanto") }
  var query by rememberSaveable { mutableStateOf("") }
  val all = remember(places) { places.all() }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    BackHeader("◀ Back", "Where are you?") { onPick(null) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      all.map { it.region }.distinct().forEach { r -> Chip(r, region == r) { region = r } }
    }
    SearchBox(query, "Search places") { query = it }
    val shown = all.filter { it.region == region && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)) }
    LazyColumn(Modifier.weight(1f)) {
      items(shown, key = { "${it.region}|${it.name}" }) { p ->
        Text(p.name, modifier = Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 10.dp), fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun SpawnRow(data: GameData, id: Int, sp: Spawn, time: String?, showAll: Boolean, caught: Boolean?, onClick: () -> Unit) {
  val s = data.species(id) ?: return
  Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(Modifier.size(32.dp)) { SpriteIcon(id, 32.dp) }
    Column(Modifier.weight(1f)) {
      Text(s.name, fontWeight = FontWeight.Bold)
      Text(
        listOfNotNull(sp.levels, if (showAll) sp.seasons.ifEmpty { null } else null).joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = DexColors.InkMuted,
      )
    }
    CaughtMark(caught)
    if (sp.horde > 0) DexChip("Horde ×${sp.horde}", DexColors.Track, DexColors.Ink)
    Text(if (showAll || time == null) sp.rarity else rateNow(sp, time), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
  }
}

private fun rateNow(sp: Spawn, time: String?) = if (time == null) sp.rarity else sp.rate(time)

private fun pct(r: String) = r.removeSuffix("%").toDoubleOrNull() ?: 0.0

