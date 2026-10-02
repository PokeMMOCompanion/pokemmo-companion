package com.pokemmocompanion.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokemmocompanion.app.calc.Stat
import com.pokemmocompanion.app.party.EvRepository
import com.pokemmocompanion.app.party.EvRules
import com.pokemmocompanion.app.party.PartyMember
import com.pokemmocompanion.app.ui.dex.DexBar
import com.pokemmocompanion.app.ui.dex.DexColors

/**
 * EVs of one party member: the last summary read plus what the app counted since (battles it watched), with the
 * player's targets. Tap a stat to set its target; reaching it buzzes.
 */
@Composable
fun EvPanel(m: PartyMember) {
  val ev by EvRepository.state.collectAsStateWithLifecycle()
  val current = EvRepository.current(m.slot, m)
  val small = MaterialTheme.typography.bodySmall
  if (current == null) {
    Text("EVs: read the EVs page to start tracking.", style = small, color = DexColors.InkMuted)
    return
  }
  val gained = current.zip(m.evs!!) { c, r -> c - r }.sum()
  val targets = ev.targets[m.slot] ?: List(6) { 0 }
  var editing by rememberSaveable(m.slot) { mutableStateOf(-1) }
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "EVs ${current.sum()}/${EvRules.TOTAL_CAP}" + if (gained > 0) "  (+$gained since read)" else "",
        style = small,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.weight(1f),
      )
      if (gained > 0) {
        Text(
          "Reset",
          modifier = Modifier.clickable { EvRepository.resetGains(m.slot) }.padding(4.dp),
          style = MaterialTheme.typography.labelSmall,
          color = DexColors.InkMuted,
        )
      }
    }
    Stat.entries.forEach { st ->
      val v = current[st.ordinal]
      val t = targets[st.ordinal]
      Row(
        Modifier.fillMaxWidth().clickable { editing = if (editing == st.ordinal) -1 else st.ordinal },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(st.short, style = small, modifier = Modifier.width(30.dp))
        Text(if (t > 0) "$v/$t" else "$v", style = small, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
        DexBar(
          v.toFloat() / (if (t > 0) t else EvRules.STAT_CAP),
          if (t > 0 && v >= t) DexColors.Good else DexColors.You,
          Modifier.weight(1f),
        )
      }
      if (editing == st.ordinal) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 30.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Target", style = MaterialTheme.typography.labelMedium, color = DexColors.InkMuted)
          listOf(0, 4, 32, 100, 128, 200, 252).forEach { n ->
            Chip(if (n == 0) "None" else "$n", t == n) {
              EvRepository.setTarget(m.slot, st, n)
              editing = -1
            }
          }
        }
      }
    }
  }
}
