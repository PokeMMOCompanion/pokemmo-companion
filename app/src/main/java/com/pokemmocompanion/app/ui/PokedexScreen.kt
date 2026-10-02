package com.pokemmocompanion.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pokemmocompanion.app.calc.GameData
import com.pokemmocompanion.app.calc.GameDataLoader
import com.pokemmocompanion.app.calc.MoveCategory
import com.pokemmocompanion.app.calc.SpeciesData
import com.pokemmocompanion.app.calc.Spawn
import com.pokemmocompanion.app.calc.Stat
import com.pokemmocompanion.app.party.DexProgress
import com.pokemmocompanion.app.ui.dex.DexBar
import com.pokemmocompanion.app.ui.dex.DexChip
import com.pokemmocompanion.app.ui.dex.DexColors
import com.pokemmocompanion.app.ui.dex.SpriteIcon
import com.pokemmocompanion.app.ui.dex.TypeChip
import com.pokemmocompanion.app.ui.dex.smashText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val STAT_NAMES = listOf("HP", "Attack", "Defense", "Sp. Atk", "Sp. Def", "Speed")

/**
 * The Pokédex: searchable list of all 649 Pokémon (filter by EV yield) or the EV horde finder, and an entry per
 * Pokémon with base stats, EV yield, abilities, catch/breeding facts, held items, evolutions, wild locations and
 * level-up moves. Data: the PokeMMO Hub snapshot (credited under More).
 */
@Composable
fun PokedexScreen(modifier: Modifier = Modifier) {
  val data = rememberGameData()
  val context = LocalContext.current.applicationContext
  val spawns by
    produceState<Map<Int, List<Spawn>>?>(null) { value = withContext(Dispatchers.Default) { GameDataLoader.spawns(context) } }
  var selected by rememberSaveable { mutableIntStateOf(0) }
  var query by rememberSaveable { mutableStateOf("") }
  var evStat by rememberSaveable { mutableIntStateOf(-1) }
  var mode by rememberSaveable { mutableIntStateOf(0) } // 0 Pokémon, 1 Here, 2 EV hordes
  var neededOnly by rememberSaveable { mutableStateOf(false) }
  val caught by DexProgress.caught.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  // The system keyboard only closes when told to: on Search/Enter, when the list scrolls, on a tap, and on leaving.
  val keyboard = LocalSoftwareKeyboardController.current
  val focus = LocalFocusManager.current
  val dismissKeyboard = {
    focus.clearFocus()
    keyboard?.hide()
  }
  DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
  LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) dismissKeyboard() }

  if (data == null) {
    Text("Loading Pokédex…", modifier = modifier)
    return
  }
  BackHandler(enabled = selected != 0) { selected = 0 }
  val entry = data.species(selected)
  if (entry != null) {
    DexEntry(entry, data, spawns?.get(entry.id), onOpen = { selected = it }, onBack = { selected = 0 }, modifier)
    return
  }

  val shown =
    remember(data, query, evStat, neededOnly, caught) {
      val q = query.trim().lowercase()
      data.species.filter { s ->
        (q.isEmpty() || s.name.lowercase().contains(q) || s.id.toString() == q.trimStart('#', '0')) &&
          (evStat < 0 || s.evYield[evStat] > 0) &&
          (!neededOnly || caught[s.id] == false)
      }
        .let { list -> if (evStat >= 0) list.sortedByDescending { it.evYield[evStat] } else list }
    }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Chip("Pokémon", mode == 0) { mode = 0 }
      Chip("Here", mode == 1) {
        dismissKeyboard()
        mode = 1
      }
      Chip("EV hordes", mode == 2) {
        dismissKeyboard()
        mode = 2
      }
    }
    if (mode == 1) {
      HereView(data, spawns, onOpen = { selected = it }, Modifier.weight(1f))
      return@Column
    }
    if (mode == 2) {
      HordeFinder(data, spawns, onOpen = { selected = it }, Modifier.weight(1f))
      return@Column
    }
    OutlinedTextField(
      value = query,
      onValueChange = { query = it },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      placeholder = { Text("Search name or number") },
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      keyboardActions = KeyboardActions(onSearch = { dismissKeyboard() }, onDone = { dismissKeyboard() }),
      trailingIcon = {
        if (query.isNotEmpty()) {
          Text(
            "✕",
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { query = "" }.padding(10.dp),
            color = DexColors.InkMuted,
            fontWeight = FontWeight.Bold,
          )
        }
      },
    )
    Row(
      Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("EV yield", style = MaterialTheme.typography.labelMedium, color = DexColors.InkMuted)
      Chip("All", evStat < 0) {
        dismissKeyboard()
        evStat = -1
      }
      Stat.entries.forEach { st ->
        Chip(st.short, evStat == st.ordinal) {
          dismissKeyboard()
          evStat = st.ordinal
        }
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        "${shown.size} Pokémon · ${caught.count { it.value }} caught · ${caught.count { !it.value }} needed (from battles)",
        style = MaterialTheme.typography.labelSmall,
        color = DexColors.InkMuted,
        modifier = Modifier.weight(1f),
      )
      Chip("Needed", neededOnly) { neededOnly = !neededOnly }
    }
    LazyColumn(Modifier.weight(1f), state = listState) {
      items(shown, key = { it.id }) { s ->
        ListRow(s, evStat, caught[s.id]) {
          dismissKeyboard()
          selected = s.id
        }
      }
    }
  }
}

/** Red ball = caught; "NEED" = met but not caught yet; nothing = never met (unknown). */
@Composable
fun CaughtMark(caught: Boolean?) {
  when (caught) {
    true -> androidx.compose.foundation.Canvas(Modifier.size(12.dp)) {
      drawCircle(Color(0xFFE3350D), size.minDimension / 2)
      drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * 0.45f), size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.1f))
    }
    false -> DexChip("NEED", DexColors.Warn, Color.Black)
    null -> {}
  }
}

@Composable
private fun ListRow(s: SpeciesData, evStat: Int, caught: Boolean?, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(dexNo(s.id), style = MaterialTheme.typography.labelMedium, color = DexColors.InkMuted, modifier = Modifier.width(40.dp))
    Box(Modifier.size(32.dp)) { SpriteIcon(s.id, 32.dp) }
    Text(s.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    CaughtMark(caught)
    s.types.forEach { TypeChip(it) }
    Text(
      evText(s, highlight = evStat),
      style = MaterialTheme.typography.labelSmall,
      color = DexColors.InkMuted,
      textAlign = TextAlign.End,
      modifier = Modifier.width(64.dp),
    )
  }
}

@Composable
private fun DexEntry(
  s: SpeciesData,
  data: GameData,
  spawns: List<Spawn>?,
  onOpen: (Int) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier,
) {
  Column(modifier.verticalScroll(rememberScrollState(), reverseScrolling = false), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
        "◀ List",
        modifier =
          Modifier.clip(RoundedCornerShape(6.dp))
            .background(DexColors.palette.tileOutline)
            .clickable(onClick = onBack)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = DexColors.palette.tileText,
        style = MaterialTheme.typography.labelLarge,
      )
      SpriteIcon(s.id, 48.dp)
      Column(Modifier.weight(1f)) {
        Text(dexNo(s.id), style = MaterialTheme.typography.labelMedium, color = DexColors.InkMuted)
        Text(s.name, style = smashText(DexColors.Ink, DexColors.Track, 24))
      }
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { s.types.forEach { TypeChip(it) } }
    }

    Section("Base stats") {
      s.stats.forEachIndexed { i, v ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(STAT_NAMES[i], style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
          Text("$v", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
          DexBar(v / 180f, statColor(v), Modifier.weight(1f))
        }
      }
      Text("Total ${s.stats.sum()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }

    Section("Training") {
      Fact("EV yield", evText(s, highlight = -1, long = true))
      Fact("Base exp", "${s.expYield}")
      Fact("Abilities", s.abilities.joinToString(" · "))
      Fact("Catch rate", "${s.catchRate}")
      Fact("Growth", s.growth)
      if (s.heldItems.isNotEmpty()) Fact("Wild items", s.heldItems.joinToString(" · "))
    }

    Section("Breeding") {
      Fact("Gender", if (s.female < 0) "Genderless" else "${fmt(100 - s.female)}% ♂ · ${fmt(s.female)}% ♀")
      Fact("Egg groups", s.eggGroups.joinToString(" · "))
      Fact("Size", "${fmt(s.heightDm / 10.0)} m · ${fmt(s.weightHg / 10.0)} kg")
    }

    val chain = evolutionChain(s, data)
    if (chain.isNotEmpty()) {
      Section("Evolution") {
        chain.forEach { (from, to, how) ->
          Row(
            Modifier.fillMaxWidth().clickable { onOpen(if (to.id == s.id) from.id else to.id) }.padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            EvoName(from, from.id == s.id)
            Text("→", color = DexColors.InkMuted)
            EvoName(to, to.id == s.id)
            Spacer(Modifier.weight(1f))
            Text(how, style = MaterialTheme.typography.labelSmall, color = DexColors.InkMuted)
          }
        }
      }
    }

    Section("Wild locations") {
      when {
        spawns == null -> Text("Not found in the wild.", style = MaterialTheme.typography.bodySmall, color = DexColors.InkMuted)
        else -> spawns.groupBy { it.region }.forEach { (region, list) -> RegionSpawns(region, list) }
      }
    }

    Section("Level-up moves") {
      s.levelMoves.forEach { lm ->
        val m = data.move(lm.move)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Lv ${lm.level}", style = MaterialTheme.typography.labelSmall, color = DexColors.InkMuted, modifier = Modifier.width(40.dp))
          Text(lm.move, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
          if (m != null) {
            TypeChip(m.type)
            Text(
              when (m.category) {
                MoveCategory.STATUS -> "Status"
                else -> "${m.category.name.take(4).lowercase().replaceFirstChar { it.uppercase() }} ${m.power}"
              },
              style = MaterialTheme.typography.labelSmall,
              color = DexColors.InkMuted,
              modifier = Modifier.width(64.dp),
              textAlign = TextAlign.End,
            )
          }
        }
      }
    }
  }
}

/** One region of spawns, collapsed to a header with a count until tapped. */
@Composable
private fun RegionSpawns(region: String, list: List<Spawn>) {
  var open by rememberSaveable(region) { mutableStateOf(false) }
  Text(
    "${if (open) "▼" else "▶"} $region · ${list.distinctBy { it.place }.size} places",
    modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 6.dp),
    style = MaterialTheme.typography.bodyMedium,
    fontWeight = FontWeight.Bold,
  )
  if (!open) return
  list.forEach { sp ->
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, bottom = 6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(sp.place, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (sp.horde > 0) DexChip("Horde ×${sp.horde}", DexColors.Warn, Color.Black)
        DexChip(sp.method, DexColors.Track, DexColors.Ink)
      }
      Text(
        listOfNotNull(sp.levels, sp.rarity, sp.seasons.ifEmpty { null }).joinToString("  ·  "),
        style = MaterialTheme.typography.labelSmall,
        color = DexColors.InkMuted,
      )
    }
  }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(title.uppercase(), style = MaterialTheme.typography.titleSmall)
    content()
  }
}

@Composable
private fun Fact(label: String, value: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = DexColors.InkMuted, modifier = Modifier.width(84.dp))
    Text(value.ifEmpty { "—" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
  }
}

@Composable
private fun EvoName(s: SpeciesData, current: Boolean) {
  Text(
    s.name,
    style = MaterialTheme.typography.bodySmall,
    fontWeight = if (current) FontWeight.Black else FontWeight.Normal,
    color = if (current) DexColors.Ink else DexColors.InkMuted,
  )
}

/** Every evolution step in this Pokémon's family, from the base form down: (from, to, condition). */
private fun evolutionChain(s: SpeciesData, data: GameData): List<Triple<SpeciesData, SpeciesData, String>> {
  var root = s
  while (root.evolvesFrom != 0) root = data.species(root.evolvesFrom) ?: break
  val out = mutableListOf<Triple<SpeciesData, SpeciesData, String>>()
  fun walk(from: SpeciesData) {
    for (e in from.evolutions) {
      val to = data.species(e.id) ?: continue
      out += Triple(from, to, e.how)
      walk(to)
    }
  }
  walk(root)
  return out
}

private fun dexNo(id: Int) = "#" + id.toString().padStart(3, '0')

/** "1 Spe" / "2 Atk, 1 Spe"; [long] spells out the stat names. */
private fun evText(s: SpeciesData, highlight: Int, long: Boolean = false): String =
  s.evYield
    .mapIndexedNotNull { i, n -> if (n > 0) i to n else null }
    .sortedByDescending { it.first == highlight }
    .joinToString(", ") { (i, n) -> "$n ${if (long) STAT_NAMES[i] else Stat.entries[i].short}" }

private fun statColor(v: Int) =
  when {
    v < 50 -> DexColors.Bad
    v < 80 -> DexColors.Warn
    v < 110 -> DexColors.Good
    else -> DexColors.Teal
  }

private fun fmt(x: Double) = if (x == x.toLong().toDouble()) x.toLong().toString() else "%.1f".format(x)
