package com.pokemmocompanion.app.ui

import android.Manifest
import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import com.pokemmocompanion.app.ui.dex.DexPanel as Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokemmocompanion.app.capture.Alerts
import com.pokemmocompanion.app.capture.CaptureRepository
import com.pokemmocompanion.app.capture.CaptureService
import com.pokemmocompanion.app.capture.CaptureState
import com.pokemmocompanion.app.detect.ScreenState
import com.pokemmocompanion.app.ui.dex.DexColors
import com.pokemmocompanion.app.ui.dex.DexFrame
import com.pokemmocompanion.app.ui.dex.DexSection
import com.pokemmocompanion.app.ui.dex.DexThemes
import com.pokemmocompanion.app.ui.dex.SectionHeader
import com.pokemmocompanion.app.ui.dex.HomeGrid
import com.pokemmocompanion.app.ui.dex.HomeTile
import com.pokemmocompanion.app.party.PartyMember
import com.pokemmocompanion.app.party.PartyRepository
import com.pokemmocompanion.app.quest.QuestRepository
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.pokemmocompanion.app.ui.dex.DexTheme

@Composable
fun MainScreen() {
  val context = LocalContext.current
  val state by CaptureRepository.state.collectAsStateWithLifecycle()
  val party by PartyRepository.party.collectAsStateWithLifecycle()
  // null = Home
  var section by rememberSaveable { mutableStateOf<DexSection?>(null) }
  var beforeBattle by rememberSaveable { mutableStateOf<DexSection?>(null) }
  var autoOpened by rememberSaveable { mutableStateOf(false) }

  // Saved theme
  LaunchedEffect(Unit) {
    val name = context.getSharedPreferences(PREFS, 0).getString(THEME_KEY, null)
    // "Retro" was renamed "RetroGB": keep a saved choice working.
    DexThemes.ALL.firstOrNull { it.name == name || (name == "Retro" && it == DexThemes.RETRO) }?.let { DexColors.palette = it }
  }

  // Open Battle when a battle starts; go back to where you were when it ends.
  LaunchedEffect(state.screenState) {
    if (state.screenState == ScreenState.BATTLE) {
      if (section != DexSection.BATTLE) {
        beforeBattle = section
        autoOpened = true
        section = DexSection.BATTLE
      }
    } else if (autoOpened) {
      section = beforeBattle
      beforeBattle = null
      autoOpened = false
    }
  }

  val status =
    when {
      !state.running -> "Tap here to start"
      state.partyReading -> "Reading party"
      state.screenState == ScreenState.BATTLE -> "In battle"
      state.screenState == ScreenState.OVERWORLD -> "Overworld"
      else -> "Watching…"
    }
  val go: (DexSection?) -> Unit = {
    section = it
    autoOpened = false // a manual choice sticks
  }
  val startCapture = rememberCaptureStarter()
  DexTheme {
    DexFrame(
      status = status,
      lights = Triple(state.screenState == ScreenState.BATTLE, state.partyReading, state.running),
      capturing = state.running,
      onCapture = { if (state.running) CaptureService.stop(context) else startCapture() },
      // Home fills the screen exactly; the Pokédex and Tools scroll their own lists.
      scrollable = section != null && section != DexSection.POKEDEX && section != DexSection.TOOLS,
      contentKey = section,
    ) {
      val current = section
      if (current == null) {
        Home(state, party.members.filterNotNull(), onOpen = go)
      } else {
        SectionHeader(current.label, current.icon, current.color, onHome = { go(null) })
        // Capture is the switch for everything else: offer it until it's on.
        if (!state.running && current in setOf(DexSection.PARTY, DexSection.BATTLE)) CaptureCard()
        when (current) {
          DexSection.PARTY -> PartyCard()
          DexSection.BATTLE -> {
            if (state.screenState == ScreenState.BATTLE) {
              if (state.opponentDown) {
                Text("Opponent fainted — battle ending.", style = MaterialTheme.typography.bodySmall, color = DexColors.InkMuted)
              } else {
                state.currentBattle?.let { OpponentHeader(it.mons) }
                CatchButton(state)
                RevealedCard(state)
                BattleCalcCard(state)
              }
            } else {
              Text("No battle right now.", style = MaterialTheme.typography.bodySmall, color = DexColors.InkMuted)
            }
            BattleLogCard(state.battleLog, inBattle = state.screenState == ScreenState.BATTLE)
            TeamBuilderButton()
          }
          DexSection.POKEDEX -> PokedexScreen(Modifier.weight(1f))
          DexSection.QUESTS -> QuestScreen()
          DexSection.TOOLS ->
            ToolsScreen(Modifier.weight(1f), encounters = { EncountersCard() }) {
              ThemePicker()
              SpritesCard()
              AlertsCard()
              PrivacyCard()
            }
        }
      }
    }
  }
}

private const val PREFS = "ui"
private const val THEME_KEY = "theme"

/** The DS-style Home screen: four corner tiles around the Pokédex button. */
@Composable
private fun ColumnScope.Home(state: CaptureState, members: List<PartyMember>, onOpen: (DexSection) -> Unit) {
  val fainted = members.count { it.currentHp == 0 }
  val quests by QuestRepository.state.collectAsStateWithLifecycle()
  fun tile(s: DexSection, status: String) = HomeTile(s.tileLabel, status, s.color, s.icon) { onOpen(s) }
  HomeGrid(
    tiles =
      listOf(
        tile(DexSection.PARTY, if (members.isEmpty()) "Not read yet" else "${members.size} in party" + if (fainted > 0) " · $fainted fainted" else ""),
        tile(
          DexSection.BATTLE,
          when {
            state.screenState == ScreenState.BATTLE -> "In battle"
            state.lastBattle != null -> "Last: ${state.lastBattle.mons.first().name}"
            else -> "No battle yet"
          },
        ),
        tile(DexSection.QUESTS, quests.next()?.let { "Next: ${it.title}" } ?: "${quests.region} complete"),
        tile(DexSection.TOOLS, "Berries · Breeding · GTL"),
      ),
    onCenter = { onOpen(DexSection.POKEDEX) },
    modifier = Modifier.weight(1f),
  )
  state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun ThemePicker() {
  val context = LocalContext.current
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("THEME", style = MaterialTheme.typography.titleSmall)
      // Two rows of three.
      for (rowThemes in DexThemes.ALL.chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (t in rowThemes) {
          val on = DexColors.palette == t
          Text(
            t.name,
            modifier =
              Modifier.weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(t.shell)
                .border(if (on) 3.dp else 1.dp, t.tileOutline, RoundedCornerShape(6.dp))
                .clickable {
                  DexColors.palette = t
                  context.getSharedPreferences(PREFS, 0).edit().putString(THEME_KEY, t.name).apply()
                }
                .padding(vertical = 10.dp),
            color = t.shellText,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }
    }
  }
}

@Composable
private fun AlertsCard() {
  val context = LocalContext.current
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("Alerts", style = MaterialTheme.typography.titleSmall)
      Text(
        "Shiny: three long buzzes. Needed for Pokédex: two short taps.",
        style = MaterialTheme.typography.bodySmall,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { Alerts.test(context, shiny = true) }) { Text("Test shiny") }
        OutlinedButton(onClick = { Alerts.test(context, shiny = false) }) { Text("Test Pokédex") }
      }
    }
  }
}

@Composable
private fun EncountersCard() {
  val state by CaptureRepository.state.collectAsStateWithLifecycle()
  val e = state.encounters
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("Encounters", style = MaterialTheme.typography.titleSmall)
      Text("Battles ${e.battles} · Pokémon ${e.pokemon} · Shinies ${e.shinies}", fontWeight = FontWeight.Bold)
      e.last?.let { last ->
        val time = remember(last.timeMillis) {
          java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(last.timeMillis))
        }
        val needed = last.mons.filter { it.caught == false }.map { it.name }.distinct()
        if (needed.isNotEmpty()) {
          Text(
            "NEEDED FOR POKÉDEX: ${needed.joinToString()}",
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.Bold,
          )
        }
        Text("Last ($time):", style = MaterialTheme.typography.bodySmall)
        for (mon in last.mons) {
          val dex =
            when (mon.caught) {
              true -> "  ● caught"
              false -> "  ○ not caught"
              null -> ""
            }
          Text(
            (if (mon.shiny) "★ SHINY " else "") + "${mon.name} Lv. ${mon.level}$dex",
            color = if (mon.shiny) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (mon.shiny) FontWeight.Bold else FontWeight.Normal,
          )
        }
      }
      if (e.bySpecies.isNotEmpty()) {
        Text(
          e.bySpecies.take(10).joinToString("\n") { (name, n) -> "%4d  %s".format(n, name) },
          fontFamily = FontFamily.Monospace,
          style = MaterialTheme.typography.bodySmall,
        )
      }
      if (state.trainerBattles > 0) {
        Text("Trainer battles skipped this session: ${state.trainerBattles}", style = MaterialTheme.typography.bodySmall)
      }
      state.lastBattle?.takeIf { !it.wild }?.let { b ->
        Text(
          "Last battle not counted (no \"A wild … appeared!\" seen): " +
            b.mons.joinToString { "${it.name} Lv. ${it.level}" },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline,
        )
      }
      if (!state.running && e.battles == 0) {
        Text("Start capture to load and record encounters.", style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

/**
 * Starts capture: asks for the notification permission (only affects whether the capture notification is visible),
 * then for Android's screen-capture permission, then starts the service. Used by the More screen and the Home button.
 */
@Composable
fun rememberCaptureStarter(): () -> Unit {
  val context = LocalContext.current
  val projectionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val data = result.data
      if (result.resultCode == Activity.RESULT_OK && data != null) {
        CaptureService.start(context, result.resultCode, data)
      } else {
        CaptureRepository.update { it.copy(error = "Screen capture permission was denied") }
      }
    }
  fun launchProjection() {
    val mpm = context.getSystemService(MediaProjectionManager::class.java)
    projectionLauncher.launch(mpm.createScreenCaptureIntent())
  }
  val notificationLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { launchProjection() }
  return {
    CaptureRepository.update { it.copy(error = null) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      launchProjection()
    }
  }
}

/** Shown while capture is off (Party, Battle): everything there needs it. The lens also starts and stops it. */
@Composable
private fun CaptureCard() {
  val state by CaptureRepository.state.collectAsStateWithLifecycle()
  val startCapture = rememberCaptureStarter()
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Capture is off", style = MaterialTheme.typography.titleSmall)
      Button(onClick = startCapture) { Text("Start capture") }
      state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
  }
}
