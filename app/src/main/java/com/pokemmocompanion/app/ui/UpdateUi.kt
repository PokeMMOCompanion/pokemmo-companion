package com.pokemmocompanion.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pokemmocompanion.app.tools.Release
import com.pokemmocompanion.app.tools.UpdateChecker
import com.pokemmocompanion.app.tools.Updates
import com.pokemmocompanion.app.ui.dex.DexColors
import com.pokemmocompanion.app.ui.dex.DexPanel as Card
import kotlinx.coroutines.launch

/** Runs the automatic update check once when the app opens; shows a prompt only if a newer release exists. */
@Composable
fun AutoUpdatePrompt() {
  val context = LocalContext.current
  var release by remember { mutableStateOf<Release?>(null) }
  LaunchedEffect(Unit) { release = UpdateChecker.autoCheck(context.applicationContext) }
  release?.let { r -> UpdateDialog(r, onDismiss = { release = null }) }
}

/** "Update available": version, notes, and Download (opens the APK download in the browser) / Later / Skip. */
@Composable
fun UpdateDialog(r: Release, onDismiss: () -> Unit) {
  val context = LocalContext.current
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Update available: v${r.version}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "You have v${UpdateChecker.installedVersion(context)}. Download the new version, then open it to install. " +
            "Your data is kept.",
          style = MaterialTheme.typography.bodySmall,
        )
        if (r.notes.isNotBlank()) {
          Text(
            r.notes,
            modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodySmall,
            color = DexColors.InkMuted,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.apkUrl ?: r.pageUrl)))
          onDismiss()
        }
      ) {
        Text("Download")
      }
    },
    dismissButton = {
      Row {
        TextButton(
          onClick = {
            UpdateChecker.skip(context.applicationContext, r.version)
            onDismiss()
          }
        ) {
          Text("Skip this version")
        }
        TextButton(onClick = onDismiss) { Text("Later") }
      }
    },
  )
}

/** Settings: installed version, Check now, and the automatic check switch. */
@Composable
fun UpdatesCard() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var auto by remember { mutableStateOf(UpdateChecker.autoEnabled(context)) }
  var status by remember { mutableStateOf<String?>(null) }
  var checking by remember { mutableStateOf(false) }
  var found by remember { mutableStateOf<Release?>(null) }
  found?.let { r -> UpdateDialog(r, onDismiss = { found = null }) }
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("UPDATES", style = MaterialTheme.typography.titleSmall)
      Text("Installed: v${UpdateChecker.installedVersion(context)}", style = MaterialTheme.typography.bodySmall)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("Check automatically", style = MaterialTheme.typography.bodyMedium)
          Text(
            "When the app opens (at most twice a day). You only see something if there's a new version.",
            style = MaterialTheme.typography.labelSmall,
            color = DexColors.InkMuted,
          )
        }
        Switch(
          checked = auto,
          onCheckedChange = {
            auto = it
            UpdateChecker.setAuto(context.applicationContext, it)
          },
        )
      }
      OutlinedButton(
        enabled = !checking,
        onClick = {
          checking = true
          status = "Checking…"
          scope.launch {
            val r = UpdateChecker.latest()
            status =
              when {
                r == null -> "Couldn't reach GitHub. Check your connection and try again."
                Updates.isNewer(r.version, UpdateChecker.installedVersion(context)) -> null.also { found = r }
                else -> "You're up to date."
              }
            checking = false
          }
        },
      ) {
        Text("Check now")
      }
      status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = DexColors.InkMuted) }
    }
  }
}
