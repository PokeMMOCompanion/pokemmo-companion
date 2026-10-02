package com.pokemmocompanion.app.ui

import android.app.ActivityManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.pokemmocompanion.app.capture.CaptureService

/**
 * Settings: "Start over" erases everything the app has stored (party, sprites, encounters, settings, memory) and
 * closes the app; the next launch is a first run with the tour.
 */
@Composable
fun StartOverButton() {
  val context = LocalContext.current
  var confirm by remember { mutableStateOf(false) }
  OutlinedButton(onClick = { confirm = true }) { Text("Start over…") }
  if (confirm) {
    AlertDialog(
      onDismissRequest = { confirm = false },
      title = { Text("Start over?") },
      text = {
        Text(
          "This erases everything the app has saved: your party, Pokémon sprites, encounters, battle memory, " +
            "theme and settings. The app then closes; open it again to start with the tour."
        )
      },
      confirmButton = {
        Button(
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828), contentColor = Color.White),
          onClick = {
            CaptureService.stop(context)
            // Wipes all app data (prefs, files, databases, caches) and ends the process.
            context.getSystemService(ActivityManager::class.java).clearApplicationUserData()
          },
        ) {
          Text("Erase everything")
        }
      },
      dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
  }
}
