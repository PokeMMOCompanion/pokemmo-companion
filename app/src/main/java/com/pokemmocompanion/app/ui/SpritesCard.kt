package com.pokemmocompanion.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokemmocompanion.app.party.SpriteImporter
import com.pokemmocompanion.app.party.SpriteStore
import com.pokemmocompanion.app.ui.dex.DexColors
import com.pokemmocompanion.app.ui.dex.DexPanel as Card
import kotlinx.coroutines.launch

/** Downloads every Pokémon's sprite from PokemonDB once; they stay in the app's private storage. */
@Composable
fun SpritesCard() {
  val scope = rememberCoroutineScope()
  val version by SpriteStore.version.collectAsStateWithLifecycle()
  val count = remember(version) { SpriteStore.importedCount }
  var status by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("POKÉMON SPRITES", style = MaterialTheme.typography.titleSmall)
      Text(
        if (count > 0) "$count of 649 downloaded." else "Download sprites of all 649 Pokémon (needs internet once).",
        style = MaterialTheme.typography.bodySmall,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (count < 649) {
          Button(
            onClick = {
              busy = true
              status = "Downloading…"
              scope.launch {
                val (got, failed) = SpriteImporter.downloadAll { done -> status = "Downloading… $done / 649" }
                status =
                  if (failed == 0) "Done: $got downloaded."
                  else "$got downloaded, $failed failed. Check the connection and tap Download again."
                busy = false
              }
            },
            enabled = !busy,
          ) {
            Text(if (count > 0) "Download missing" else "Download")
          }
        }
        if (count > 0) OutlinedButton(onClick = { SpriteStore.clearImported() }, enabled = !busy) { Text("Remove") }
      }
      status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DexColors.InkMuted) }
    }
  }
}
