package com.pokemmocompanion.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pokemmocompanion.app.ui.dex.DexColors
import com.pokemmocompanion.app.ui.dex.smashText

/** The tools behind the Tools tile. */
enum class Tool(val label: String, val blurb: String) {
  BERRIES("Berries", "Farm timers with water, harvest and wither reminders; every berry's seeds and yield"),
  BREEDING("Breeding", "IV breeding plan: which 1×31 parents to get, and the order to breed them"),
  EGG_MOVES("Egg moves", "Which parents pass an egg move, step by step"),
  GTL("GTL prices", "Current lowest GTL prices and price history"),
  ENCOUNTERS("Encounters", "Wild Pokémon seen, shinies and the last battles"),
  SETTINGS("Settings", "Alerts, theme, capture, Pokémon sprites, credits"),
}

/** Tools: a menu of tools, each opening full screen with a back button. */
@Composable
fun ToolsScreen(modifier: Modifier = Modifier, encounters: @Composable () -> Unit, settings: @Composable () -> Unit) {
  var tool by rememberSaveable { mutableStateOf<Tool?>(null) }
  BackHandler(enabled = tool != null) { tool = null }
  val current = tool
  if (current == null) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Tool.entries.forEach { t -> ToolButton(t) { tool = t } }
    }
    return
  }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    BackHeader("◀ Tools", current.label) { tool = null }
    val body = Modifier.weight(1f)
    when (current) {
      Tool.BERRIES -> BerriesTool(body)
      Tool.BREEDING -> BreedingTool(body)
      Tool.EGG_MOVES -> EggMovesTool(body)
      Tool.GTL -> GtlTool(body)
      Tool.ENCOUNTERS -> Column(body.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { encounters() }
      Tool.SETTINGS -> Column(body.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { settings() }
    }
  }
}

@Composable
private fun ToolButton(t: Tool, onClick: () -> Unit) {
  val outline = DexColors.palette.tileOutline
  Row(
    Modifier.fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .border(2.dp, outline, RoundedCornerShape(10.dp))
      .clickable(onClick = onClick)
      .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(outline), contentAlignment = Alignment.Center) {
      Canvas(Modifier.size(20.dp)) { drawToolIcon(t, DexColors.palette.tileText) }
    }
    Column(Modifier.weight(1f)) {
      Text(t.label, style = smashText(DexColors.Ink, DexColors.Track, 18))
      Text(t.blurb, style = MaterialTheme.typography.bodySmall, color = DexColors.InkMuted)
    }
    Text("▶", color = DexColors.InkMuted)
  }
}

/** Plain shape icons per tool (no icon library, no game art). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawToolIcon(t: Tool, c: Color) {
  val w = size.width
  when (t) {
    Tool.BERRIES -> {
      drawCircle(c, w * 0.32f, Offset(w * 0.5f, w * 0.6f))
      drawLine(c, Offset(w * 0.5f, w * 0.3f), Offset(w * 0.65f, w * 0.05f), w * 0.1f)
    }
    Tool.BREEDING -> {
      drawOval(c, topLeft = Offset(w * 0.22f, w * 0.05f), size = androidx.compose.ui.geometry.Size(w * 0.56f, w * 0.9f))
    }
    Tool.EGG_MOVES -> {
      drawOval(c, topLeft = Offset(w * 0.1f, w * 0.15f), size = androidx.compose.ui.geometry.Size(w * 0.45f, w * 0.75f))
      drawLine(c, Offset(w * 0.6f, w * 0.5f), Offset(w * 0.95f, w * 0.5f), w * 0.1f)
      drawLine(c, Offset(w * 0.8f, w * 0.35f), Offset(w * 0.95f, w * 0.5f), w * 0.1f)
      drawLine(c, Offset(w * 0.8f, w * 0.65f), Offset(w * 0.95f, w * 0.5f), w * 0.1f)
    }
    Tool.GTL -> {
      drawLine(c, Offset(0f, w * 0.85f), Offset(w * 0.35f, w * 0.45f), w * 0.12f)
      drawLine(c, Offset(w * 0.35f, w * 0.45f), Offset(w * 0.6f, w * 0.65f), w * 0.12f)
      drawLine(c, Offset(w * 0.6f, w * 0.65f), Offset(w, w * 0.15f), w * 0.12f)
    }
    Tool.ENCOUNTERS -> {
      // Tally marks
      for (i in 0 until 4) drawLine(c, Offset(w * (0.15f + i * 0.2f), w * 0.15f), Offset(w * (0.15f + i * 0.2f), w * 0.85f), w * 0.1f)
      drawLine(c, Offset(0f, w * 0.75f), Offset(w, w * 0.25f), w * 0.1f)
    }
    Tool.SETTINGS -> {
      drawCircle(c, w * 0.42f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.16f))
      drawCircle(c, w * 0.14f, center)
    }
  }
}

/** "◀ Back  Title" row used inside sections. */
@Composable
fun BackHeader(back: String, title: String, onBack: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      back,
      modifier =
        Modifier.clip(RoundedCornerShape(6.dp))
          .background(DexColors.palette.tileOutline)
          .clickable(onClick = onBack)
          .padding(horizontal = 10.dp, vertical = 6.dp),
      color = DexColors.palette.tileText,
      style = MaterialTheme.typography.labelLarge,
    )
    Text(title, style = smashText(DexColors.Ink, DexColors.Track, 20))
  }
}

/** Single-line search box that closes the keyboard on Enter and when it leaves the screen. */
@Composable
fun SearchBox(query: String, placeholder: String, onChange: (String) -> Unit) {
  val keyboard = LocalSoftwareKeyboardController.current
  val focus = LocalFocusManager.current
  DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
  OutlinedTextField(
    value = query,
    onValueChange = onChange,
    modifier = Modifier.fillMaxWidth(),
    singleLine = true,
    placeholder = { Text(placeholder) },
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions =
      KeyboardActions(
        onSearch = {
          focus.clearFocus()
          keyboard?.hide()
        }
      ),
    trailingIcon = {
      if (query.isNotEmpty()) {
        Text(
          "✕",
          modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onChange("") }.padding(10.dp),
          color = DexColors.InkMuted,
          fontWeight = FontWeight.Bold,
        )
      }
    },
  )
}

/** "3h 20m", "45m", "now". */
fun duration(ms: Long): String {
  if (ms <= 0) return "now"
  val m = ms / 60_000
  return when {
    m >= 60 -> "${m / 60}h ${m % 60}m"
    else -> "${m}m"
  }
}
