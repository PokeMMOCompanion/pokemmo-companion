package com.pokemmocompanion.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokemmocompanion.app.calc.Stat
import com.pokemmocompanion.app.capture.CaptureState
import com.pokemmocompanion.app.ui.dex.DexColors
import com.pokemmocompanion.app.ui.dex.DexPanel as Card
import com.pokemmocompanion.app.ui.dex.TypeChip

/**
 * Battle memory for the current opponent: moves it used, ability and item it showed, and its stats narrowed down
 * from the damage it took and dealt (and turn order). Trainer Pokémon met before show their earlier moves too.
 */
@Composable
fun RevealedCard(state: CaptureState) {
  val data = rememberGameData() ?: return
  val mon = state.currentBattle?.mons?.singleOrNull() ?: return
  val species = data.species(mon.name) ?: return
  val r = state.revealed["${species.name}|${mon.level}"]
  val seen = r?.seenBefore
  if (r == null || (r.moves.isEmpty() && r.ability == null && r.item == null && r.ranges.isEmpty() && seen == null)) return
  val small = MaterialTheme.typography.bodySmall
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("BATTLE MEMORY · ${species.name} Lv ${mon.level}", style = MaterialTheme.typography.titleSmall)
      if (r.moves.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          r.moves.forEach { name ->
            val m = data.move(name)
            Text(name, style = small, fontWeight = FontWeight.Bold)
            m?.let { TypeChip(it.type) }
          }
        }
      }
      val unseen = seen?.moves?.filter { it !in r.moves }.orEmpty()
      if (unseen.isNotEmpty()) Text("Seen before: ${unseen.joinToString()}", style = small, color = DexColors.InkMuted)
      val ability = r.ability ?: seen?.ability
      val item = r.item ?: seen?.item
      if (ability != null || item != null) {
        Text(listOfNotNull(ability?.let { "Ability: $it" }, item?.let { "Item: $it" }).joinToString(" · "), style = small)
      }
      if (r.ranges.isNotEmpty()) {
        Text(
          Stat.entries.joinToString("  ") { s ->
            val range = r.ranges[s]
            "${s.short} " + when {
              range == null -> "?"
              range.first == range.last -> "${range.first}"
              else -> "${range.first}–${range.last}"
            }
          },
          style = small,
          fontWeight = FontWeight.Bold,
        )
        Text(
          "From ${r.hits} hit${if (r.hits == 1) "" else "s"} and turn order; the calcs above use the top of each range.",
          style = MaterialTheme.typography.labelSmall,
          color = DexColors.InkMuted,
        )
      }
      r.notes.takeLast(2).forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = DexColors.Warn) }
    }
  }
}
