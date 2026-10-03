package com.pokemmocompanion.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import com.pokemmocompanion.app.ui.dex.DexPanel as Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokemmocompanion.app.capture.CaptureRepository
import com.pokemmocompanion.app.calc.Stat
import com.pokemmocompanion.app.party.PartyMember
import com.pokemmocompanion.app.party.PartyRepository
import com.pokemmocompanion.app.ui.dex.DexBar
import com.pokemmocompanion.app.ui.dex.DexColors
import com.pokemmocompanion.app.ui.dex.SpriteIcon
import com.pokemmocompanion.app.ui.dex.hpColor

@Composable
fun PartyCard() {
  val capture by CaptureRepository.state.collectAsStateWithLifecycle()
  val party by PartyRepository.party.collectAsStateWithLifecycle()
  var editing by rememberSaveable { mutableStateOf<Int?>(null) }
  editing?.let { slot -> MemberEditor(slot, party.members.getOrNull(slot)) { editing = null } }

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("YOUR PARTY · TAP FOR DETAILS", style = MaterialTheme.typography.titleSmall)
      // HP updates itself from the overworld rings; everything else is a snapshot from the last Read party.
      Text(
        "Read party again after any change: new order, a level up, a new move, or a changed held item. " +
          "HP updates on its own.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (capture.partyReading) {
          Button(onClick = { CaptureRepository.update { it.copy(partyReading = false, partyStatus = null) } }) {
            Text("Done reading")
          }
        } else {
          Button(
            onClick = { CaptureRepository.update { it.copy(partyReading = true, partyStatus = null) } },
            enabled = capture.running,
          ) {
            Text("Read party")
          }
        }
        OutlinedButton(onClick = { PartyRepository.clear() }) { Text("Clear") }
      }
      when {
        !capture.running -> Text("Start capture first.", style = MaterialTheme.typography.bodySmall)
        capture.partyReading ->
          Text(
            "In PokeMMO, open each Pokémon's Summary. Start on the first tab (Pokédex/Name/Nature), then go " +
              "through Stats, EVs, IVs and Moves, about a second per tab.\n" + (capture.partyStatus?.let { "Last read: $it" } ?: "Waiting for a summary screen…"),
            style = MaterialTheme.typography.bodySmall,
            color = DexColors.You,
          )
      }
      val members = party.members.filterNotNull()
      if (members.isEmpty()) {
        Text("No party read yet.", style = MaterialTheme.typography.bodySmall)
      }
      members.forEachIndexed { i, m ->
        if (i > 0) HorizontalDivider()
        MemberEntry(m) { editing = m.slot }
      }
      // OCR fallback: type a Pokémon in by hand.
      party.members.indexOfFirst { it == null }.takeIf { it >= 0 }?.let { slot ->
        Chip("+ Add slot ${slot + 1} manually", false) { editing = slot }
      }
    }
  }
}

@Composable
private fun MemberEntry(m: PartyMember, onEdit: () -> Unit) {
  val name = m.species ?: m.nickname ?: "?"
  // A real nickname only: "Nidoran" next to Nidoran♂ is just the species as the game writes it.
  val nick = m.realNickname?.takeIf { m.species != null }?.let { " \"$it\"" } ?: ""
  var expanded by rememberSaveable(m.slot) { mutableStateOf(false) }
  val maxHp = m.stats?.get(0)
  val hp = m.currentHp
  // Compact row like the Pokédex mockup: name, level and HP, with an HP bar. Tap for the details.
  Row(
    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SpriteIcon(m.dexId, 40.dp)
    Text("${m.slot + 1} $name$nick", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 6.dp))
    Text(
      // A percentage: the overworld rings only give HP to within a point or two.
      listOfNotNull(
        m.level?.let { "Lv. $it" },
        if (hp != null && maxHp != null && maxHp > 0) (if (hp == 0) "Fainted" else "HP ${hp * 100 / maxHp}%") else null,
      ).joinToString(" · "),
      style = MaterialTheme.typography.bodySmall,
    )
  }
  if (hp != null && maxHp != null && maxHp > 0) DexBar(hp.toFloat() / maxHp, hpColor(hp.toFloat() / maxHp))
  if (m.outdated.isNotEmpty()) {
    Text(
      "↻ Re-read " + m.outdated.sorted().joinToString(" and ") { if (it == "stats") "Stats (leveled up)" else "Moves (learned a new one)" },
      style = MaterialTheme.typography.labelSmall,
      color = DexColors.Warn,
      fontWeight = FontWeight.Bold,
    )
  }
  if (!expanded) return
  m.nature?.let { Text("Nature: $it", style = MaterialTheme.typography.bodySmall) }
  val small = MaterialTheme.typography.bodySmall
  val mono = FontFamily.Monospace
  fun six(label: String, v: List<Int>?) =
    v?.let { label + Stat.entries.joinToString(" ") { s -> "${s.short} ${it[s.ordinal]}" } }

  six("Stats ", m.stats)?.let { Text(it, fontFamily = mono, style = small) }
  six("IVs   ", m.ivs)?.let { Text(it, fontFamily = mono, style = small) }
  EvPanel(m)
  if (m.moves.isNotEmpty() || m.ability != null) {
    Text(
      listOfNotNull(m.moves.takeIf { it.isNotEmpty() }?.joinToString(), m.ability?.let { "Ability: $it" }).joinToString(" · "),
      style = small,
    )
  }
  m.item?.let { Text("Item: $it", style = small) }
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Chip("✎ Edit", false, onEdit)
    Chip("Remove", false) { PartyRepository.remove(m.slot) }
  }
  val check = { ok: Boolean -> if (ok) "✓" else "✗" }
  Text(
    "Info ${check(m.hasInfo)}  Stats ${check(m.stats != null)}  EVs ${check(m.evs != null)}  " +
      "IVs ${check(m.ivs != null)}  Moves ${check(m.moves.isNotEmpty())}",
    style = small,
    color = if (m.isReady) DexColors.You else MaterialTheme.colorScheme.outline,
  )
}
