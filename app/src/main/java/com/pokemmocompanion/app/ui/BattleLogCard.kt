package com.pokemmocompanion.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.pokemmocompanion.app.ui.dex.DexPanel as Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokemmocompanion.app.detect.BattleEvent
import com.pokemmocompanion.app.detect.Side

/** Move history of the current battle, in order. Stays up after the battle until the next one starts. */
@Composable
fun BattleLogCard(all: List<BattleEvent>, inBattle: Boolean) {
  // Only messages matched to the known list are shown; unknown reads stay in the log file.
  val events = all.filter { it.known }
  if (events.isEmpty()) return
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(if (inBattle) "BATTLE LOG" else "BATTLE LOG (LAST BATTLE)", style = MaterialTheme.typography.titleSmall)
      // Fixed height, scrolls on its own and follows the newest message.
      val scroll = rememberScrollState()
      LaunchedEffect(events.size) { scroll.animateScrollTo(scroll.maxValue) }
      Column(
        Modifier.fillMaxWidth().heightIn(max = 170.dp).verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
      for (e in events) {
        if (e.move != null) {
          val who = if (e.side == Side.YOU) "You" else "Foe"
          val outcome = if (e.outcomes.isEmpty()) "" else " — " + e.outcomes.joinToString(", ")
          Text(
            "$who: ${e.actor} used ${e.move}$outcome",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color =
              when {
                "missed" in e.outcomes || "no effect" in e.outcomes || "failed" in e.outcomes -> MaterialTheme.colorScheme.outline
                e.side == Side.YOU -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
              },
          )
        } else {
          Text("   ${e.text}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
      }
      }
    }
  }
}
