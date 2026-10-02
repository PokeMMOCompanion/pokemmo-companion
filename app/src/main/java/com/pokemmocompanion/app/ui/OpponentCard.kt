package com.pokemmocompanion.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import com.pokemmocompanion.app.ui.dex.SpriteIcon
import com.pokemmocompanion.app.ui.dex.TypeChip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.pokemmocompanion.app.ui.dex.DexPanel as Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokemmocompanion.app.calc.GameData
import com.pokemmocompanion.app.calc.GameDataLoader
import com.pokemmocompanion.app.calc.MoveCategory
import com.pokemmocompanion.app.calc.OpponentProfile
import com.pokemmocompanion.app.calc.Stat
import com.pokemmocompanion.app.detect.WildMon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberGameData(): GameData? {
  val context = LocalContext.current.applicationContext
  val data by produceState<GameData?>(null) { value = withContext(Dispatchers.Default) { GameDataLoader.get(context) } }
  return data
}

/**
 * The opponent at the top of the Battle tab: species, level and types. Tap for more info (worst-case stats,
 * weaknesses, resistances, possible moves). Hordes show one row per species.
 */
@Composable
fun OpponentHeader(mons: List<WildMon>) {
  val data = rememberGameData() ?: return
  // One profile per species, at its highest level in the battle (worst case for a horde).
  val profiles =
    mons.groupBy { it.name }.mapNotNull { (name, group) ->
      OpponentProfile.build(data, name, group.maxOf { it.level })?.let { it to group.size }
    }
  val unknown = mons.map { it.name }.distinct().filter { data.species(it) == null }
  if (profiles.isEmpty() && unknown.isEmpty()) return
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      profiles.forEachIndexed { i, (p, count) ->
        if (i > 0) HorizontalDivider()
        OpponentEntry(p, count)
      }
      if (unknown.isNotEmpty()) Text("Reading the opponent…", style = MaterialTheme.typography.bodySmall)
    }
  }
}

@Composable
private fun OpponentEntry(p: OpponentProfile, count: Int) {
  var expanded by rememberSaveable(p.species.id) { mutableStateOf(false) }
  Row(
    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    SpriteIcon(p.species.id, 44.dp)
    Text(
      (if (count > 1) "$count× " else "") + "${p.species.name} Lv. ${p.level}",
      fontWeight = FontWeight.Bold,
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.weight(1f),
    )
    p.species.types.forEach { TypeChip(it) }
    Text(if (expanded) "▾" else "▸ info", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
  }
  if (!expanded) return
  Text("Worst case: 31 IVs, boosting nature", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
  Text(
    Stat.entries.joinToString("  ") { "${it.short} ${p.maxStats.getValue(it)}" },
    fontFamily = FontFamily.Monospace,
    style = MaterialTheme.typography.bodySmall,
  )
  if (p.weaknesses.isNotEmpty()) {
    Text(
      "Weak to: " + p.weaknesses.joinToString { (t, m) -> "${t.label} ×${multiplier(m)}" },
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.primary,
    )
  }
  if (p.resistances.isNotEmpty()) {
    Text(
      "Resists: " + p.resistances.joinToString { (t, m) -> "${t.label} ×${multiplier(m)}" },
      style = MaterialTheme.typography.bodySmall,
    )
  }
  if (p.possibleMoves.isNotEmpty()) {
    Text(
      "May know: " +
        p.possibleMoves.joinToString { m ->
          if (m.category == MoveCategory.STATUS) m.name else "${m.name} (${m.type.label} ${m.power})"
        },
      style = MaterialTheme.typography.bodySmall,
    )
  }
}

private fun multiplier(m: Double) =
  when (m) {
    0.25 -> "¼"
    0.5 -> "½"
    else -> if (m == m.toInt().toDouble()) m.toInt().toString() else m.toString()
  }
