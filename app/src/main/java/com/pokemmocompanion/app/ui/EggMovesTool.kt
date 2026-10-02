package com.pokemmocompanion.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokemmocompanion.app.calc.GameData
import com.pokemmocompanion.app.calc.SpeciesData
import com.pokemmocompanion.app.tools.EggMoves
import com.pokemmocompanion.app.ui.dex.DexColors
import com.pokemmocompanion.app.ui.dex.SpriteIcon
import com.pokemmocompanion.app.ui.dex.TypeChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Egg moves (PokeMMO Hub's chains): pick a Pokémon, then one of its egg moves, and see every way to breed it on, as
 * numbered steps: a father that knows the move breeds with a mother of the next species.
 */
@Composable
fun EggMovesTool(modifier: Modifier) {
  val data = rememberGameData()
  val context = LocalContext.current.applicationContext
  val egg by produceState<EggMoves?>(null) {
    value = withContext(Dispatchers.IO) { context.assets.open("pokemmo/egg_moves.json").bufferedReader().use { EggMoves.parse(it.readText()) } }
  }
  var speciesId by rememberSaveable { mutableIntStateOf(0) }
  var moveId by rememberSaveable { mutableIntStateOf(0) }
  if (data == null || egg == null) {
    Text("Loading…", modifier = modifier)
    return
  }
  val species = data.species(speciesId)
  if (species == null) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SearchBox(query, "Which Pokémon?") { query = it }
      val shown =
        remember(query) {
          data.species.filter { s -> egg!!.moves(s, data).isNotEmpty() && (query.isBlank() || s.name.contains(query.trim(), ignoreCase = true)) }
        }
      LazyColumn(Modifier.weight(1f)) {
        items(shown, key = { it.id }) { s -> SpeciesRow(s) { speciesId = s.id } }
      }
    }
    return
  }
  val moves = egg!!.moves(species, data).mapNotNull(data::move).sortedBy { it.name }
  Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      SpriteIcon(species.id, 40.dp)
      Text(species.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
      Chip("Change", false) {
        speciesId = 0
        moveId = 0
      }
    }
    Text("Egg moves", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      moves.forEach { m -> Chip(m.name, moveId == m.id) { moveId = m.id } }
    }
    val move = data.move(moveId)
    if (move != null) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(move.name, fontWeight = FontWeight.Bold)
        TypeChip(move.type)
        Text(if (move.isDamaging) "${move.category.name.lowercase()} ${move.power}" else "status", style = MaterialTheme.typography.labelSmall)
      }
      val routes = egg!!.routes(species, moveId, data)
      Text("${routes.size} way${if (routes.size == 1) "" else "s"} — fewest steps first", style = MaterialTheme.typography.labelSmall, color = DexColors.InkMuted)
      routes.take(12).forEach { route -> RouteCard(route, data) }
    }
  }
}

@Composable
private fun SpeciesRow(s: SpeciesData, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(Modifier.size(32.dp)) { SpriteIcon(s.id, 32.dp) }
    Text(s.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    Text(s.eggGroups.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = DexColors.InkMuted)
  }
}

@Composable
private fun RouteCard(route: List<EggMoves.Step>, data: GameData) {
  Column(
    Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(8.dp))
      .border(1.5.dp, DexColors.palette.tileOutline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    route.forEachIndexed { i, step ->
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${i + 1}.", style = MaterialTheme.typography.labelMedium, color = DexColors.InkMuted)
        SpriteIcon(step.father.id, 24.dp)
        Column(Modifier.weight(1f)) {
          Text("♂ ${step.father.name} × ♀ ${step.mother.name}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
          Text(
            if (i == 0) "${step.father.name} learns it ${step.how}" else "use the ${step.father.name} from step $i",
            style = MaterialTheme.typography.labelSmall,
            color = DexColors.InkMuted,
          )
        }
      }
    }
  }
}
