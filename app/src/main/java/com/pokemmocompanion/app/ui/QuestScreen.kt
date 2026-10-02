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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokemmocompanion.app.party.PartyRepository
import com.pokemmocompanion.app.quest.Milestone
import com.pokemmocompanion.app.quest.MilestoneKind
import com.pokemmocompanion.app.quest.QuestMatchup
import com.pokemmocompanion.app.quest.QuestRepository
import com.pokemmocompanion.app.quest.Story
import com.pokemmocompanion.app.ui.dex.DexColors
import com.pokemmocompanion.app.ui.dex.TypeChip
import com.pokemmocompanion.app.ui.dex.smashText

/**
 * Quests: a story checklist per region (gyms, Pokémon League, HMs) with the next battle and how the party matches up
 * against it, plus the player's own to-dos. Everything is checked off by hand.
 */
@Composable
fun QuestScreen() {
  val state by QuestRepository.state.collectAsStateWithLifecycle()
  val region = Story.region(state.region)

  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Story.REGIONS.forEach { r ->
      val on = r.name == region.name
      Text(
        r.name,
        modifier =
          Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (on) DexColors.palette.tileOutline else DexColors.Track)
            .clickable { QuestRepository.setRegion(r.name) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = if (on) DexColors.palette.tileText else DexColors.Ink,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
      )
    }
  }

  NextUp(state.next(region), state.badges(region))

  MilestoneKind.entries.forEach { kind ->
    val items = region.milestones.filter { it.kind == kind }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        "${kind.heading.uppercase()}  ${items.count { it.id in state.checked }}/${items.size}",
        style = MaterialTheme.typography.titleSmall,
      )
      items.forEach { m -> MilestoneRow(m, m.id in state.checked) { QuestRepository.toggle(m.id) } }
    }
  }

  MyTasks()
}

@Composable
private fun NextUp(next: Milestone?, badges: Int) {
  val party by PartyRepository.party.collectAsStateWithLifecycle()
  val data = rememberGameData()
  Column(
    Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .border(2.dp, DexColors.palette.tileOutline, RoundedCornerShape(10.dp))
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text("NEXT UP · $badges/8 badges", style = MaterialTheme.typography.labelMedium, color = DexColors.InkMuted)
    if (next == null) {
      Text("Region complete!", style = smashText(DexColors.Ink, DexColors.Track, 20))
      return@Column
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(next.title, style = smashText(DexColors.Ink, DexColors.Track, 20), modifier = Modifier.weight(1f, fill = false))
      next.type?.let { TypeChip(it) }
    }
    Text(next.detail, style = MaterialTheme.typography.bodySmall, color = DexColors.InkMuted)
    val type = next.type
    val members = party.members.filterNotNull()
    if (type != null && data != null && members.isNotEmpty()) {
      val m = QuestMatchup.against(type, members, data)
      Text(
        if (m.strong.isEmpty()) "No super-effective moves in your party."
        else "Super effective: " + m.strong.joinToString { (who, move) -> "$who ($move)" },
        style = MaterialTheme.typography.bodySmall,
        color = if (m.strong.isEmpty()) DexColors.Bad else DexColors.Good,
      )
      if (m.weak.isNotEmpty()) {
        Text("Weak to ${type.label}: " + m.weak.joinToString(), style = MaterialTheme.typography.bodySmall, color = DexColors.Warn)
      }
    } else if (type != null && members.isEmpty()) {
      Text("Read your party to see how it matches up.", style = MaterialTheme.typography.bodySmall, color = DexColors.InkMuted)
    }
  }
}

@Composable
private fun MilestoneRow(m: Milestone, done: Boolean, onToggle: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    CheckBox(done)
    Column(Modifier.weight(1f)) {
      Text(
        m.title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        color = if (done) DexColors.InkMuted else DexColors.Ink,
        textDecoration = if (done) TextDecoration.LineThrough else null,
      )
      if (m.kind != MilestoneKind.HM) Text(m.detail, style = MaterialTheme.typography.labelSmall, color = DexColors.InkMuted)
    }
    m.type?.let { TypeChip(it) }
  }
}

@Composable
private fun CheckBox(done: Boolean) {
  val outline = DexColors.palette.tileOutline
  Box(
    Modifier.size(22.dp)
      .clip(RoundedCornerShape(5.dp))
      .background(if (done) outline else DexColors.Screen)
      .border(2.dp, outline, RoundedCornerShape(5.dp)),
    contentAlignment = Alignment.Center,
  ) {
    if (done) Text("✓", color = DexColors.palette.tileText, fontWeight = FontWeight.Black)
  }
}

@Composable
private fun MyTasks() {
  val state by QuestRepository.state.collectAsStateWithLifecycle()
  var text by rememberSaveable { mutableStateOf("") }
  val keyboard = LocalSoftwareKeyboardController.current
  val focus = LocalFocusManager.current
  fun add() {
    QuestRepository.addTask(text)
    text = ""
    focus.clearFocus()
    keyboard?.hide()
  }

  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("MY TASKS", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
      if (state.tasks.any { it.done }) {
        Text(
          "Clear done",
          modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { QuestRepository.clearDone() }.padding(6.dp),
          style = MaterialTheme.typography.labelMedium,
          color = DexColors.InkMuted,
        )
      }
    }
    OutlinedTextField(
      value = text,
      onValueChange = { text = it },
      modifier = Modifier.fillMaxWidth(),
      singleLine = true,
      placeholder = { Text("Add a task, e.g. Catch Abra") },
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(onDone = { add() }),
      trailingIcon = {
        if (text.isNotBlank()) {
          Text(
            "Add",
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { add() }.padding(10.dp),
            fontWeight = FontWeight.Bold,
          )
        }
      },
    )
    state.tasks.forEach { t ->
      Row(
        Modifier.fillMaxWidth().clickable { QuestRepository.toggleTask(t.id) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        CheckBox(t.done)
        Text(
          t.text,
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.bodyMedium,
          color = if (t.done) DexColors.InkMuted else DexColors.Ink,
          textDecoration = if (t.done) TextDecoration.LineThrough else null,
        )
        Text(
          "✕",
          modifier = Modifier.clip(RoundedCornerShape(50)).clickable { QuestRepository.removeTask(t.id) }.padding(8.dp),
          color = DexColors.InkMuted,
        )
      }
    }
  }
}
