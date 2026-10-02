package com.pokemmocompanion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pokemmocompanion.app.calc.Nature
import com.pokemmocompanion.app.calc.Stat
import com.pokemmocompanion.app.calc.Stats
import com.pokemmocompanion.app.party.PartyMember
import com.pokemmocompanion.app.party.PartyRepository
import com.pokemmocompanion.app.ui.dex.DexColors
import com.pokemmocompanion.app.ui.dex.SpriteIcon
import com.pokemmocompanion.app.ui.dex.TypeChip
import com.pokemmocompanion.app.ui.dex.smashText

private enum class EditorPage { FORM, SPECIES, MOVE }

/**
 * Manual entry for a party slot, for when the summary screens won't read: species, nickname, level, nature, ability,
 * item, HP, stats, IVs, EVs and moves. "Calculate stats" fills the stats from level, nature, IVs and EVs. Saving
 * replaces the slot (a later Read party can still update it).
 */
@Composable
fun MemberEditor(slot: Int, existing: PartyMember?, onClose: () -> Unit) {
  Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Column(Modifier.fillMaxSize().background(DexColors.Screen).padding(12.dp)) { EditorBody(slot, existing, onClose) }
  }
}

@Composable
private fun EditorBody(slot: Int, existing: PartyMember?, onClose: () -> Unit) {
  val data = rememberGameData() ?: return
  var page by remember { mutableStateOf(EditorPage.FORM) }
  var moveSlot by remember { mutableStateOf(0) }
  var species by remember { mutableStateOf((existing?.dexId ?: 0).let(data::species) ?: existing?.species?.let(data::species)) }
  var nickname by remember { mutableStateOf(existing?.nickname.orEmpty()) }
  var level by remember { mutableStateOf(existing?.level?.toString().orEmpty()) }
  var nature by remember { mutableStateOf(existing?.nature?.let(Nature::parse)) }
  var ability by remember { mutableStateOf(existing?.ability) }
  var item by remember { mutableStateOf(existing?.item.orEmpty()) }
  var hp by remember { mutableStateOf(existing?.currentHp?.toString().orEmpty()) }
  val stats = remember { six(existing?.stats) }
  val ivs = remember { six(existing?.ivs) }
  val evs = remember { six(existing?.evs) }
  val moves = remember { mutableStateListOf<String>().apply { addAll(existing?.moves.orEmpty()) } }
  var error by remember { mutableStateOf<String?>(null) }

  when (page) {
    EditorPage.SPECIES ->
      return SpeciesPicker(Modifier.fillMaxSize(), data) { s ->
        if (s != null && s.id != species?.id) {
          species = s
          ability = s.abilities.firstOrNull { it != "--" }
          moves.clear()
        }
        page = EditorPage.FORM
      }
    EditorPage.MOVE -> {
      // Only reachable once a species is picked.
      val s = species ?: return
      return MovePicker(Modifier.fillMaxSize(), data, s) { m ->
        if (m != null && moves.none { it == m.name }) {
          if (moveSlot < moves.size) moves[moveSlot] = m.name else moves += m.name
        }
        page = EditorPage.FORM
      }
    }
    EditorPage.FORM -> {}
  }

  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("SLOT ${slot + 1} · ENTER BY HAND", style = smashText(DexColors.Ink, DexColors.Track, 18), modifier = Modifier.weight(1f))
      Text("✕", modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClose).padding(10.dp), fontWeight = FontWeight.Bold)
    }

    // Species
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      SpriteIcon(species?.id, 40.dp)
      Text(species?.name ?: "Pick a Pokémon", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
      species?.types?.forEach { TypeChip(it) }
      Chip(if (species == null) "Pick" else "Change", species == null) { page = EditorPage.SPECIES }
    }
    val s = species ?: return@Column

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(nickname, { nickname = it }, Modifier.weight(1f), singleLine = true, label = { Text("Nickname") })
      NumberField("Level", level, Modifier.width(90.dp)) { level = it }
    }

    Label("Nature")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Nature.entries.sortedBy { it.up?.ordinal ?: -1 }.forEach { n ->
        val name = n.name.lowercase().replaceFirstChar { it.uppercase() }
        Chip(if (n.up == null) name else "$name +${n.up.short}−${n.down!!.short}", nature == n) { nature = n }
      }
    }

    Label("Ability")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      s.abilities.filter { it != "--" }.distinct().forEach { a -> Chip(a, ability == a) { ability = a } }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(item, { item = it }, Modifier.weight(1f), singleLine = true, label = { Text("Held item") })
      NumberField("HP now", hp, Modifier.width(100.dp)) { hp = it }
    }

    SixFields("Stats (as shown in game)", stats)
    SixFields("IVs (0-31)", ivs)
    SixFields("EVs (0-252)", evs)
    Chip("Calculate stats from level, nature, IVs and EVs", false) {
      val lv = level.toIntOrNull()
      if (lv == null || lv !in 1..100) {
        error = "Enter a level first."
      } else {
        Stat.entries.forEach { st ->
          val iv = ivs[st.ordinal].toIntOrNull() ?: Stats.MAX_IV
          val ev = evs[st.ordinal].toIntOrNull() ?: 0
          stats[st.ordinal] = Stats.value(st, s.base(st), iv.coerceIn(0, 31), ev.coerceIn(0, 252), lv, nature ?: Nature.HARDY).toString()
        }
        error = null
      }
    }

    Label("Moves (tap to change)")
    (0 until 4).forEach { i ->
      val m = moves.getOrNull(i)
      Row(
        Modifier.fillMaxWidth().clickable {
          moveSlot = i
          page = EditorPage.MOVE
        }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (m == null) {
          Text("+ add move", style = MaterialTheme.typography.bodySmall, color = DexColors.InkMuted)
        } else {
          Text(m, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
          data.move(m)?.let { TypeChip(it.type) }
          Text("✕", modifier = Modifier.clickable { moves.removeAt(i) }.padding(6.dp), color = DexColors.InkMuted)
        }
      }
    }

    error?.let { Text(it, color = DexColors.Bad, style = MaterialTheme.typography.bodySmall) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Chip("Save", true) {
        val lv = level.toIntOrNull()?.takeIf { it in 1..100 }
        val st = stats.toInts()
        when {
          lv == null -> error = "Level must be 1-100."
          st == null -> error = "Enter all six stats (or use Calculate stats)."
          else -> {
            PartyRepository.set(
              PartyMember(
                slot = slot,
                nickname = nickname.trim().ifEmpty { null },
                level = lv,
                dexId = s.id,
                species = s.name,
                nature = nature?.name?.lowercase()?.replaceFirstChar { it.uppercase() },
                currentHp = hp.toIntOrNull()?.coerceIn(0, st[0]) ?: st[0],
                stats = st,
                evs = evs.toInts(),
                ivs = ivs.toInts(),
                moves = moves.toList(),
                ability = ability,
                item = item.trim().ifEmpty { null },
              )
            )
            onClose()
          }
        }
      }
      Text("Cancel", modifier = Modifier.clickable(onClick = onClose).padding(6.dp), color = DexColors.InkMuted)
    }
  }
}

private fun six(v: List<Int>?): SnapshotStateList<String> = mutableStateListOf<String>().apply { addAll(v?.map { it.toString() } ?: List(6) { "" }) }

/** All six as numbers, or null if any is missing. */
private fun List<String>.toInts(): List<Int>? = map { it.trim().toIntOrNull() ?: return null }

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelMedium, color = DexColors.InkMuted)

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
  OutlinedTextField(
    value,
    { v -> onChange(v.filter(Char::isDigit).take(3)) },
    modifier,
    singleLine = true,
    label = { Text(label) },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
  )
}

/** Six number fields in two rows of three (HP Atk Def / SpA SpD Spe). */
@Composable
private fun SixFields(title: String, values: SnapshotStateList<String>) {
  Label(title)
  for (row in listOf(Stat.entries.take(3), Stat.entries.drop(3))) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      row.forEach { st -> NumberField(st.short, values[st.ordinal], Modifier.weight(1f)) { values[st.ordinal] = it } }
    }
  }
}

