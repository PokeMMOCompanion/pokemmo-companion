package com.pokemmocompanion.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokemmocompanion.app.capture.DebugLog
import com.pokemmocompanion.app.ui.dex.DexColors
import com.pokemmocompanion.app.ui.dex.DexPanel as Card

/** What happens to the screen capture, in plain words, and the opt-in diagnostic log. */
@Composable
fun PrivacyCard() {
  val logOn by DebugLog.enabled.collectAsStateWithLifecycle()
  val small = MaterialTheme.typography.bodySmall
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("PRIVACY", style = MaterialTheme.typography.titleSmall)
      Text(
        "The screen capture is only looked at on this phone, a couple of times a second, and then thrown away. " +
          "The app never saves screenshots or recordings and never uploads what's on your screen. It has no " +
          "account, login, ads or tracking, and it can't tap, type or control the game.",
        style = small,
      )
      Text(
        "Internet is only used when you tap Download (Pokémon sprites from PokemonDB), open GTL prices (PokeMMO " +
          "Hub), or to check GitHub for a new version of this app (can be turned off under Updates). Google's on-device text recognition (ML Kit) sends Google anonymous performance stats like phone " +
          "model and timing, never the screen or its text.",
        style = small,
        color = DexColors.InkMuted,
      )
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("Diagnostic log", style = MaterialTheme.typography.bodyMedium)
          Text(
            "Saves a text log of what the app reads, on this phone only, for bug reports. Turning it off deletes it.",
            style = MaterialTheme.typography.labelSmall,
            color = DexColors.InkMuted,
          )
        }
        Switch(checked = logOn, onCheckedChange = DebugLog::setEnabled)
      }
    }
  }
}
