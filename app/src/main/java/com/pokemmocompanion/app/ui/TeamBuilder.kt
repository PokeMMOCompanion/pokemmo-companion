package com.pokemmocompanion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokemmocompanion.app.calc.Build
import com.pokemmocompanion.app.calc.Builds
import com.pokemmocompanion.app.calc.Damage
import com.pokemmocompanion.app.calc.GameData
import com.pokemmocompanion.app.calc.MoveData
import com.pokemmocompanion.app.calc.Nature
import com.pokemmocompanion.app.calc.Stat
import com.pokemmocompanion.app.calc.Stats
import com.pokemmocompanion.app.party.PartyMember
import com.pokemmocompanion.app.party.PartyRepository
import com.pokemmocompanion.app.ui.dex.DexColors
import com.pokemmocompanion.app.ui.dex.SpriteIcon
import com.pokemmocompanion.app.ui.dex.TypeChip
import com.pokemmocompanion.app.ui.dex.smashText

/** The Team builder button on Battle Assistant; opens the builder full screen. */
@Composable
fun TeamBuilderButton() {
  var open by rememberSaveable { mutableStateOf(false) }
  Text(
    "⚔  Team builder · test matchups",
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .border(2.dp, DexColors.palette.tileOutline, RoundedCornerShape(8.dp))
        .clickable { open = true }
        .padding(vertical = 10.dp),
    style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.Black,
    textAlign = TextAlign.Center,
  )
  if (open) {
    Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
      Column(
        Modifier.fillMaxSize()
          .background(DexColors.Screen)
          .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("TEAM BUILDER", style = smashText(DexColors.Ink, DexColors.Track, 20), modifier = Modifier.weight(1f))
          Text("✕", modifier = Modifier.clip(RoundedCornerShape(50)).clickable { open = false }.padding(10.dp), fontWeight = FontWeight.Bold)
        }
        TeamBuilder(Modifier.weight(1f))
      }
    }
  }
}

/** A party member as a build (stored stats come from its species, nature, IVs and EVs). */
fun PartyMember.toBuild(data: GameData): Build? {
  val s = (dexId?.let(data::species) ?: species?.let(data::species)) ?: return null
  return Build(
    species = s,
    level = level ?: 50,
    nature = nature?.let(Nature::parse) ?: Nature.HARDY,
    ivs = ivs ?: List(6) { Stats.MAX_IV },
    evs = evs ?: List(6) { 0 },
    ability = ability ?: s.abilities.firstOrNull { it != "--" },
    item = item?.takeUnless { it.equals("None", ignoreCase = true) },
    moves = moves.mapNotNull(data::move),
  )
}

private enum class Picking { NONE, YOU, FOE, YOUR_MOVE, FOE_MOVE }

@Composable
private fun TeamBuilder(modifier: Modifier) {
  val data = rememberGameData() ?: return
  val party by PartyRepository.party.collectAsStateWithLifecycle()
  val members = party.members.filterNotNull()
  var you by remember { mutableStateOf(members.firstNotNullOfOrNull { it.toBuild(data) } ?: Builds.suggest(data.species("Pikachu")!!, data)) }
  var foe by remember { mutableStateOf(Builds.suggest(data.species("Gyarados")!!, data)) }
  var picking by remember { mutableStateOf(Picking.NONE) }
  var moveSlot by remember { mutableStateOf(0) }

  when (picking) {
    Picking.YOU, Picking.FOE ->
      return SpeciesPicker(modifier, data) { s ->
        if (s != null) {
          val level = if (picking == Picking.YOU) you.level else foe.level
          val b = Builds.suggest(s, data, level)
          if (picking == Picking.YOU) you = b else foe = b
        }
        picking = Picking.NONE
      }
    Picking.YOUR_MOVE, Picking.FOE_MOVE -> {
      val who = if (picking == Picking.YOUR_MOVE) you else foe
      return MovePicker(modifier, data, who.species) { m ->
        if (m != null) {
          val moves = who.moves.toMutableList()
          if (moveSlot < moves.size) moves[moveSlot] = m else moves += m
          val nb = who.copy(moves = moves.distinctBy { it.id }.take(4))
          if (picking == Picking.YOUR_MOVE) you = nb else foe = nb
        }
        picking = Picking.NONE
      }
    }
    Picking.NONE -> {}
  }

  Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    if (members.isNotEmpty()) {
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Your party", style = MaterialTheme.typography.labelMedium, color = DexColors.InkMuted)
        members.forEach { m -> m.toBuild(data)?.let { b -> Chip(b.species.name, you.species.id == b.species.id) { you = b } } }
      }
    }
    BuildCard("YOU", you, onChange = { you = it }, onSpecies = { picking = Picking.YOU }, onMove = { slot ->
      moveSlot = slot
      picking = Picking.YOUR_MOVE
    }, data = data)
    SpeedLine(you, foe)
    MatchupCard("Your moves", you, foe)
    MatchupCard("Its moves", foe, you)
    BuildCard("OPPONENT", foe, onChange = { foe = it }, onSpecies = { picking = Picking.FOE }, onMove = { slot ->
      moveSlot = slot
      picking = Picking.FOE_MOVE
    }, data = data, wildOption = true)
  }
}

@Composable
private fun BuildCard(
  title: String,
  b: Build,
  onChange: (Build) -> Unit,
  onSpecies: () -> Unit,
  onMove: (Int) -> Unit,
  data: GameData,
  wildOption: Boolean = false,
) {
  var editEvs by remember { mutableStateOf(false) }
  val small = MaterialTheme.typography.bodySmall
  Column(
    Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .border(2.dp, DexColors.palette.tileOutline.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
      .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      SpriteIcon(b.species.id, 40.dp)
      Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = DexColors.InkMuted)
        Text(b.species.name, fontWeight = FontWeight.Bold)
      }
      b.species.types.forEach { TypeChip(it) }
      Chip("Change", false, onSpecies)
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      Text("Lv", style = MaterialTheme.typography.labelMedium, color = DexColors.InkMuted)
      listOf(5, 10, 20, 30, 50, 75, 100).forEach { l -> Chip("$l", b.level == l) { onChange(b.copy(level = l)) } }
      Chip("−", false) { onChange(b.copy(level = (b.level - 1).coerceAtLeast(1))) }
      Text("${b.level}", fontWeight = FontWeight.Bold)
      Chip("+", false) { onChange(b.copy(level = (b.level + 1).coerceAtMost(100))) }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      Text("Build", style = MaterialTheme.typography.labelMedium, color = DexColors.InkMuted)
      Chip("Suggested", false) { onChange(Builds.suggest(b.species, data, b.level)) }
      if (wildOption) {
        Chip("Wild (worst case)", false) {
          onChange(Build(b.species, b.level, Nature.HARDY, moves = b.moves).let { w ->
            // Worst case for the player: nature boosting its better attack.
            w.copy(nature = Nature.entries.first { it.up == (if (Builds.physical(b.species)) Stat.ATK else Stat.SPA) && it.down == Stat.SPE })
          })
        }
      }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      Text("Nature", style = MaterialTheme.typography.labelMedium, color = DexColors.InkMuted)
      Nature.entries.filter { it.up != null }.sortedBy { it.up!!.ordinal }.forEach { n ->
        Chip("${n.name.lowercase().replaceFirstChar { it.uppercase() }} +${n.up!!.short}−${n.down!!.short}", b.nature == n) { onChange(b.copy(nature = n)) }
      }
    }
    // Stats and EVs
    Column(Modifier.clickable { editEvs = !editEvs }) {
      Text(
        Stat.entries.joinToString("  ") { "${it.short} ${b.stat(it)}" },
        style = small,
        fontWeight = FontWeight.Bold,
      )
      Text(
        "EVs " + Stat.entries.filter { b.evs[it.ordinal] > 0 }.joinToString(" / ") { "${b.evs[it.ordinal]} ${it.short}" }.ifEmpty { "none" } +
          (b.ability?.let { " · $it" } ?: "") + (b.item?.let { " · $it" } ?: "") + if (editEvs) "" else "  (tap to edit EVs)",
        style = MaterialTheme.typography.labelSmall,
        color = DexColors.InkMuted,
      )
    }
    if (editEvs) {
      Stat.entries.forEach { st ->
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(st.short, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(30.dp))
          listOf(0, 4, 84, 128, 172, 252).forEach { v ->
            Chip("$v", b.evs[st.ordinal] == v) {
              val evs = b.evs.toMutableList().also { it[st.ordinal] = v }
              if (evs.sum() <= 510) onChange(b.copy(evs = evs))
            }
          }
        }
      }
      if (b.species.abilities.count { it != "--" } > 1) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
          Text("Ability", style = MaterialTheme.typography.labelMedium, color = DexColors.InkMuted)
          b.species.abilities.filter { it != "--" }.distinct().forEach { a -> Chip(a, b.ability == a) { onChange(b.copy(ability = a)) } }
        }
      }
    }
    // Moves
    Text("Moves (tap to change)", style = MaterialTheme.typography.labelSmall, color = DexColors.InkMuted)
    (0 until 4).forEach { i ->
      val m = b.moves.getOrNull(i)
      Row(
        Modifier.fillMaxWidth().clickable { onMove(i) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (m == null) {
          Text("+ add move", style = small, color = DexColors.InkMuted)
        } else {
          Text(m.name, style = small, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
          TypeChip(m.type)
          Text(if (m.isDamaging) "${m.power}" else "—", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
        }
      }
    }
  }
}

@Composable
private fun SpeedLine(you: Build, foe: Build) {
  val d = Builds.speedDiff(you, foe)
  Text(
    when {
      d > 0 -> "▶ You move first (Spe ${you.stat(Stat.SPE)} vs ${foe.stat(Stat.SPE)})"
      d < 0 -> "◀ It moves first (Spe ${foe.stat(Stat.SPE)} vs ${you.stat(Stat.SPE)})"
      else -> "Speed tie (${you.stat(Stat.SPE)}) — 50/50"
    },
    style = MaterialTheme.typography.titleSmall,
    color = if (d > 0) DexColors.Good else if (d < 0) DexColors.Bad else DexColors.Warn,
    fontWeight = FontWeight.Black,
  )
}

@Composable
private fun MatchupCard(title: String, attacker: Build, defender: Build) {
  val m = Builds.matchup(attacker, defender)
  val hp = m.defenderHp
  Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Text(title.uppercase() + " vs ${defender.species.name} ($hp HP)", style = MaterialTheme.typography.titleSmall)
    if (m.moves.isEmpty()) Text("No moves set.", style = MaterialTheme.typography.bodySmall, color = DexColors.InkMuted)
    m.moves.forEach { (move, r) ->
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(move.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (r == null) {
          Text("status", style = MaterialTheme.typography.labelSmall, color = DexColors.InkMuted)
        } else {
          val p = r.percentOf(hp)
          Text(
            "${p.min.toInt()}–${p.max.toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.End,
          )
          Text(
            Damage.koLabel(r, hp).replace("Guaranteed", "").replace("Possible", "maybe").trim(),
            style = MaterialTheme.typography.labelSmall,
            color = if (r.min >= hp) DexColors.Good else DexColors.InkMuted,
            modifier = Modifier.width(76.dp),
            textAlign = TextAlign.End,
          )
        }
      }
    }
  }
}

@Composable
internal fun SpeciesPicker(modifier: Modifier, data: GameData, onPick: (com.pokemmocompanion.app.calc.SpeciesData?) -> Unit) {
  var query by rememberSaveable { mutableStateOf("") }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    BackHeader("◀ Back", "Pick a Pokémon") { onPick(null) }
    SearchBox(query, "Search") { query = it }
    val shown = remember(query) { data.species.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) } }
    LazyColumn(Modifier.weight(1f)) {
      items(shown, key = { it.id }) { s ->
        Row(
          Modifier.fillMaxWidth().clickable { onPick(s) }.padding(vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Box(Modifier.size(32.dp)) { SpriteIcon(s.id, 32.dp) }
          Text(s.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
          s.types.forEach { TypeChip(it) }
        }
      }
    }
  }
}

@Composable
internal fun MovePicker(modifier: Modifier, data: GameData, species: com.pokemmocompanion.app.calc.SpeciesData, onPick: (MoveData?) -> Unit) {
  var query by rememberSaveable { mutableStateOf("") }
  val all = remember(species.id) { data.learnable(species).sortedByDescending { it.first.power } }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    BackHeader("◀ Back", "${species.name}'s moves") { onPick(null) }
    SearchBox(query, "Search moves") { query = it }
    val shown = all.filter { query.isBlank() || it.first.name.contains(query.trim(), ignoreCase = true) }
    LazyColumn(Modifier.weight(1f).heightIn(min = 100.dp)) {
      items(shown, key = { it.first.id }) { (m, how) ->
        Row(
          Modifier.fillMaxWidth().clickable { onPick(m) }.padding(vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(m.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
          TypeChip(m.type)
          Text(
            if (m.isDamaging) "${m.category.name.take(4).lowercase()} ${m.power}" else "status",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.End,
          )
          Text(how, style = MaterialTheme.typography.labelSmall, color = DexColors.InkMuted, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
        }
      }
    }
  }
}
