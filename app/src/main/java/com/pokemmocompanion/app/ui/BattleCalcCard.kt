package com.pokemmocompanion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokemmocompanion.app.calc.Advice
import com.pokemmocompanion.app.calc.BattleAdvisor
import com.pokemmocompanion.app.calc.MoveCalc
import com.pokemmocompanion.app.calc.MoveOrder
import com.pokemmocompanion.app.calc.OpponentProfile
import com.pokemmocompanion.app.calc.FieldState
import com.pokemmocompanion.app.calc.Revealed
import com.pokemmocompanion.app.detect.BattleLog
import com.pokemmocompanion.app.detect.Side
import com.pokemmocompanion.app.calc.SpeedVerdict
import com.pokemmocompanion.app.calc.Stat
import com.pokemmocompanion.app.capture.CaptureState
import com.pokemmocompanion.app.party.PartyRepository
import com.pokemmocompanion.app.ui.dex.DexBar
import com.pokemmocompanion.app.ui.dex.DexChip
import com.pokemmocompanion.app.ui.dex.DexColors
import com.pokemmocompanion.app.ui.dex.SpriteIcon
import com.pokemmocompanion.app.ui.dex.hpColor
import kotlin.math.roundToInt

/**
 * The battle calc, in the order it's read mid-battle: recommended moves, who moves first, and the battle overview
 * (both sides' HP, status and stat changes, plus the biggest threat). The opponent header and the log are separate.
 */
@Composable
fun BattleCalcCard(state: CaptureState) {
  val data = rememberGameData()
  val battle = state.currentBattle
  val reading = state.active
  val small = MaterialTheme.typography.bodySmall
  if (data == null || battle == null || reading == null) {
    Text(
      when {
        battle == null -> "Reading the opponent…"
        else -> "Reading your Pokémon…"
      },
      style = small,
      color = DexColors.InkMuted,
    )
    return
  }
  val party by PartyRepository.party.collectAsStateWithLifecycle()
  val members = party.members.filterNotNull()
  val active = state.activeSlot?.let { party.members.getOrNull(it) }

  // Strongest opponent in the battle (hordes are usually one species; take the highest level).
  val target = battle.mons.maxByOrNull { it.level } ?: return
  val base = remember(target, data) { OpponentProfile.build(data, target.name, target.level) }
  // Battle memory: revealed moves/ability and stats narrowed from damage. NPC trainers' Pokémon have no EVs (as in
  // the original games), so trainers get the same 0-EV worst case as wild Pokémon.
  val revealed = base?.let { state.revealed["${it.species.name}|${target.level}"] ?: Revealed(it.species.name, target.level, emptyList(), null, null, emptyMap(), emptyList(), 0) }
  val profile = remember(base, revealed) { base?.let { revealed!!.narrow(it, data, wild = true) } }
  if (profile == null) {
    Text("Couldn't identify the opponent yet.", style = small)
    return
  }
  val horde = battle.mons.size > 1
  val statuses = remember(state.battleLog) { BattleLog.statuses(state.battleLog) }
  val field = state.field.copy(yourStatus = statuses[Side.YOU], itsStatus = statuses[Side.FOE])
  val advice =
    remember(active, reading, profile, horde, members, state.yourStages, state.oppStages, field) {
      active?.let { BattleAdvisor.advise(data, it, reading.hp, profile, horde, members, state.yourStages, state.oppStages, field) }
    }
  val yourName = active?.shownName ?: reading.name
  when {
    active == null -> Text("Your Pokémon isn't in your stored party yet. Use Read party (or Edit) on the Party tab.", style = small)
    advice == null -> Text("Missing data for $yourName: read its Stats and Moves tabs (Party tab).", style = small)
    else -> {
      FieldLine(field)
      Moves(advice, yourName, revealed?.measured.orEmpty())
      SpeedBanner(advice.speed)
      Overview(state, yourName, active.dexId, reading.level, reading.hp, reading.maxHp, profile, horde, advice)
    }
  }
}

@Composable
private fun Section(title: String) {
  Text(title, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun Moves(a: Advice, yourName: String, measured: Map<String, List<String>>) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Section("RECOMMENDED MOVES · $yourName")
    a.yourMoves.forEachIndexed { i, m -> MoveRow(m, best = i == 0, measured[m.move.name]) }
    if (a.statusMoves.isNotEmpty()) {
      Text("Status: " + a.statusMoves.joinToString { it.name }, style = MaterialTheme.typography.labelSmall, color = DexColors.InkMuted)
    }
    a.switchTo?.let { s ->
      Text(
        "Switch to ${s.member.shownName}: takes ≤${pct(s.takesMaxPercent)}, " +
          "hits ${pct(s.dealsPercent.min)}–${pct(s.dealsPercent.max)} with ${s.move.name}",
        style = MaterialTheme.typography.bodySmall,
        color = DexColors.Teal,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun MoveRow(m: MoveCalc, best: Boolean, measured: List<String>?) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      m.move.name,
      modifier = Modifier.weight(1f),
      fontWeight = if (best) FontWeight.Bold else FontWeight.Normal,
      color = if (best) DexColors.You else DexColors.Ink,
    )
    Text("${pct(m.percent.min)}–${pct(m.percent.max)} · ${m.label}", style = MaterialTheme.typography.bodySmall)
  }
  // Bar to 100% of its HP (a KO fills it); the best move is the strong blue.
  DexBar((m.percent.max / 100).toFloat(), if (best) DexColors.Lens else DexColors.YouLight)
  val extras =
    listOfNotNull(
      m.move.type.label,
      "crit ${pct(m.critMaxPercent)}".takeIf { m.percent.max > 0 },
      "hits all foes".takeIf { m.spread },
      "${m.move.accuracy}% acc".takeIf { m.move.accuracy in 1..99 },
    )
  Text(extras.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = DexColors.InkFaint)
  m.abilityNotes.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = DexColors.Warn) }
  // What it really did this battle: the check on the calc.
  if (!measured.isNullOrEmpty()) {
    Text("Seen: ${measured.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = DexColors.Teal, fontWeight = FontWeight.Bold)
  }
}

/** Weather, screens and burns the calcs are using right now (hidden when there are none). */
@Composable
private fun FieldLine(f: FieldState) {
  val parts =
    listOfNotNull(
      f.weather?.label,
      f.yourScreens.takeIf { it.isNotEmpty() }?.let { "Your ${it.joinToString(" + ")}" },
      f.itsScreens.takeIf { it.isNotEmpty() }?.let { "Its ${it.joinToString(" + ")}" },
      "You're burned".takeIf { f.yourStatus == "BRN" },
      "It's burned".takeIf { f.itsStatus == "BRN" },
    )
  if (parts.isEmpty()) return
  Text("Field: " + parts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = DexColors.Warn, fontWeight = FontWeight.Bold)
}

/** Big colored banner: green = you move first, amber = depends on its IVs/nature, red = it moves first. */
@Composable
private fun SpeedBanner(s: SpeedVerdict) {
  val (bg, fg) =
    when (s.order) {
      MoveOrder.YOU_FIRST -> Color(0xFFC0DD97) to Color(0xFF27500A)
      MoveOrder.DEPENDS -> Color(0xFFFAC775) to Color(0xFF633806)
      MoveOrder.IT_FIRST -> Color(0xFFF7C1C1) to Color(0xFF791F1F)
    }
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 10.dp, vertical = 6.dp)
  ) {
    Text(s.headline, color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    Text(s.detail, color = fg, style = MaterialTheme.typography.labelSmall)
  }
}

/** Both sides' HP, status and stat changes, and the opponent's most dangerous move. */
@Composable
private fun Overview(
  state: CaptureState,
  yourName: String,
  yourDex: Int?,
  yourLevel: Int?,
  hp: Int?,
  maxHp: Int?,
  profile: OpponentProfile,
  horde: Boolean,
  a: Advice,
) {
  val statuses = remember(state.battleLog) { BattleLog.statuses(state.battleLog) }
  Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Section("BATTLE OVERVIEW")
    val yourFraction = if (hp != null && maxHp != null && maxHp > 0) hp.toFloat() / maxHp else null
    SideRow(
      dex = yourDex,
      label = "$yourName${yourLevel?.let { " Lv. $it" } ?: ""}",
      hpText = if (hp != null && maxHp != null) "$hp/$maxHp" else "?",
      fraction = yourFraction,
      status = statuses[Side.YOU],
      stages = state.yourStages,
    )
    val oppFraction = if (horde) null else state.opponentHp
    SideRow(
      dex = profile.species.id,
      label = "${profile.species.name} Lv. ${profile.level}",
      hpText = oppFraction?.let { "${(it * 100).roundToInt()}%" } ?: if (horde) "horde" else "?",
      fraction = oppFraction,
      status = statuses[Side.FOE],
      stages = state.oppStages,
    )
    a.threats.firstOrNull()?.let { t ->
      Text(
        "Biggest threat: ${t.move.name} up to ${t.maxDamage} HP (${pct(t.maxPercent)}) · crit ${pct(t.critMaxPercent)}" +
          if (t.canKo) " · CAN KO YOU" else "",
        style = MaterialTheme.typography.bodySmall,
        color = if (t.canKo) DexColors.Foe else DexColors.Ink,
        fontWeight = if (t.canKo) FontWeight.Bold else FontWeight.Normal,
      )
      t.abilityNotes.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = DexColors.Warn) }
    } ?: Text("It has no damaging moves.", style = MaterialTheme.typography.bodySmall)
  }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SideRow(dex: Int?, label: String, hpText: String, fraction: Float?, status: String?, stages: Map<Stat, Int>) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    SpriteIcon(dex, 32.dp)
    Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
    Text(hpText, style = MaterialTheme.typography.bodySmall)
  }
  if (fraction != null) DexBar(fraction, hpColor(fraction))
  if (status != null || stages.isNotEmpty()) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      status?.let { DexChip(it, Color(0xFFCECBF6), Color(0xFF3C3489)) }
      for ((s, n) in stages.entries.sortedBy { it.key.ordinal }) {
        if (n > 0) DexChip("+$n ${s.short}", Color(0xFFC0DD97), Color(0xFF27500A))
        else DexChip("$n ${s.short}", Color(0xFFF7C1C1), Color(0xFF791F1F))
      }
    }
  }
}

private fun pct(v: Double) = "${v.roundToInt()}%"
