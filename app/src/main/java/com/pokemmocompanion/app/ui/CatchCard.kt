package com.pokemmocompanion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pokemmocompanion.app.calc.Ball
import com.pokemmocompanion.app.calc.Catch
import com.pokemmocompanion.app.calc.CatchStatus
import com.pokemmocompanion.app.capture.CaptureState
import com.pokemmocompanion.app.detect.BattleLog
import com.pokemmocompanion.app.detect.Side
import com.pokemmocompanion.app.ui.dex.DexBar
import com.pokemmocompanion.app.ui.dex.DexColors
import com.pokemmocompanion.app.ui.dex.smashText

/**
 * The Catch button on Battle Assistant (single wild battles only). It opens the catch helper in a popup, which follows
 * the battle live and closes by itself when the battle ends.
 */
@Composable
fun CatchButton(state: CaptureState) {
  val data = rememberGameData() ?: return
  val battle = state.currentBattle ?: return
  val mon = battle.mons.singleOrNull() ?: return
  if (!battle.wild || state.opponentDown || data.species(mon.name) == null) return
  var open by rememberSaveable { mutableStateOf(false) }
  val outline = DexColors.palette.tileOutline
  Text(
    "◓  Catch ${mon.name}",
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(outline)
        .clickable { open = true }
        .padding(vertical = 10.dp),
    color = DexColors.palette.tileText,
    style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.Black,
    textAlign = TextAlign.Center,
  )
  if (open) {
    Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
      Column(
        Modifier.fillMaxWidth(0.94f)
          .clip(RoundedCornerShape(12.dp))
          .background(DexColors.Screen)
          .border(3.dp, outline, RoundedCornerShape(12.dp))
          .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("CATCH ${mon.name.uppercase()}", style = smashText(DexColors.Ink, DexColors.Track, 20), modifier = Modifier.weight(1f))
          Text(
            "✕",
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { open = false }.padding(10.dp),
            color = DexColors.InkMuted,
            fontWeight = FontWeight.Bold,
          )
        }
        CatchDetails(state)
      }
    }
  }
}

/**
 * Catch helper: the chance per Poké Ball for one throw, from the Pokémon's catch rate, its HP bar and its status
 * (PokeMMO Hub's formula, see [Catch]). Balls whose bonus clearly doesn't apply are left out; situational ones show
 * their condition.
 */
@Composable
private fun CatchDetails(state: CaptureState) {
  val data = rememberGameData() ?: return
  val mon = state.currentBattle?.mons?.singleOrNull() ?: return
  val species = data.species(mon.name) ?: return

  val hp = state.opponentHp?.toDouble()
  val status = CatchStatus.fromCode(BattleLog.statuses(state.battleLog)[Side.FOE])
  val rows =
    Ball.entries
      .mapNotNull { ball ->
        when (Catch.applies(ball, species.types, mon.caught, status == CatchStatus.SLEEP)) {
          false -> null // no bonus: same as a Poké Ball
          else -> ball to Catch.chance(species.catchRate, hp ?: 1.0, ball.rate, status.rate)
        }
      }
      .sortedByDescending { it.second }
  val small = MaterialTheme.typography.bodySmall

  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      listOf(
          "Catch rate ${species.catchRate}",
          hp?.let { "HP ${(it * 100).toInt()}%" } ?: "HP not read (assuming full)",
          status.label,
        )
        .joinToString(" · "),
      style = small,
      color = DexColors.InkMuted,
    )
    rows.forEach { (ball, chance) ->
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.width(118.dp)) {
          Text(ball.label, style = small, fontWeight = FontWeight.Bold)
          ball.condition?.takeIf { Catch.applies(ball, species.types, mon.caught, status == CatchStatus.SLEEP) == null }?.let {
            Text("if $it", style = MaterialTheme.typography.labelSmall, color = DexColors.InkMuted)
          }
        }
        DexBar(chance.toFloat(), chanceColor(chance), Modifier.weight(1f))
        Text(
          pct(chance),
          style = small,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.width(48.dp),
          textAlign = TextAlign.End,
        )
        val throws = Catch.throwsFor(chance)
        Text(
          if (throws == 1) "1 ball" else if (throws > 99) "99+" else "~$throws",
          style = MaterialTheme.typography.labelSmall,
          color = DexColors.InkMuted,
          modifier = Modifier.width(44.dp),
          textAlign = TextAlign.End,
        )
      }
    }
    Text(
      "~N = balls for a 90% chance." + if (status == CatchStatus.NONE) " Sleep doubles every chance; paralysis ×1.5." else "",
      style = MaterialTheme.typography.labelSmall,
      color = DexColors.InkMuted,
    )
  }
}

private fun pct(p: Double) =
  when {
    p >= 1.0 -> "100%"
    p >= 0.1 -> "${(p * 100).toInt()}%"
    else -> "%.1f%%".format(p * 100)
  }

private fun chanceColor(p: Double) =
  when {
    p >= 0.5 -> DexColors.Good
    p >= 0.2 -> DexColors.Warn
    else -> DexColors.Bad
  }
