package com.pokemmocompanion.app.ui.dex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokemmocompanion.app.calc.PokeType

/** One look for the whole app. Semantic colors (you/foe/HP) stay readable in every theme. */
data class DexPalette(
  val name: String,
  val shell: Color,
  val shellDark: Color,
  val shellText: Color,
  val shellMuted: Color,
  val bezel: Color,
  val screen: Color,
  val screenDots: Color,
  val ink: Color,
  val inkMuted: Color,
  val inkFaint: Color,
  val track: Color,
  val lens: Color,
  val lensRim: Color,
  /** Home tiles: Party, Battle, Hunt, More. */
  val tiles: List<Color>,
  val tileOutline: Color,
  val tileText: Color,
)

object DexThemes {
  val CLASSIC_RED =
    DexPalette(
      name = "Classic red",
      shell = Color(0xFFDC0A2D),
      shellDark = Color(0xFFA3081F),
      shellText = Color(0xFFFCEBEB),
      shellMuted = Color(0xFFFFD6DC),
      bezel = Color(0xFF6E0614),
      screen = Color(0xFFFFFFFF),
      screenDots = Color(0xFFF3D9DC),
      ink = Color(0xFF2C2C2A),
      inkMuted = Color(0xFF5F5E5A),
      inkFaint = Color(0xFF888780),
      track = Color(0xFFE4E2DA),
      lens = Color(0xFF378ADD),
      lensRim = Color(0xFFFFFFFF),
      tiles = listOf(Color(0xFFE24B4A), Color(0xFFEF9F27), Color(0xFF1D9E75), Color(0xFF378ADD)),
      tileOutline = Color(0xFF1C0A0E),
      tileText = Color(0xFFFFFFFF),
    )
  val RETRO =
    DexPalette(
      name = "RetroGB",
      shell = Color(0xFF8B8B9E),
      shellDark = Color(0xFF5E5E72),
      shellText = Color(0xFF2C2C3A),
      shellMuted = Color(0xFFE2E2EA),
      bezel = Color(0xFF4A4A5A),
      screen = Color(0xFF9BBC0F),
      screenDots = Color(0xFF8BAC0F),
      ink = Color(0xFF0F380F),
      inkMuted = Color(0xFF306230),
      inkFaint = Color(0xFF3E6B2E),
      track = Color(0xFF8BAC0F),
      lens = Color(0xFF7A1F5C),
      lensRim = Color(0xFFC9C9D6),
      tiles = listOf(Color(0xFF306230), Color(0xFF4C7A2C), Color(0xFF3B6E44), Color(0xFF5B7F1E)),
      tileOutline = Color(0xFF0F380F),
      tileText = Color(0xFFE0F8D0),
    )
  val MODERN_BLUE =
    DexPalette(
      name = "Modern blue",
      shell = Color(0xFF185FA5),
      shellDark = Color(0xFF0C447C),
      shellText = Color(0xFFE6F1FB),
      shellMuted = Color(0xFFB5D4F4),
      bezel = Color(0xFF042C53),
      screen = Color(0xFFF4F8FC),
      screenDots = Color(0xFFDCE8F5),
      ink = Color(0xFF1B2733),
      inkMuted = Color(0xFF4A5A6A),
      inkFaint = Color(0xFF7A8794),
      track = Color(0xFFDCE4EC),
      lens = Color(0xFF9FE1CB),
      lensRim = Color(0xFFE6F1FB),
      tiles = listOf(Color(0xFF185FA5), Color(0xFF1D9E75), Color(0xFF378ADD), Color(0xFF534AB7)),
      tileOutline = Color(0xFF042C53),
      tileText = Color(0xFFFFFFFF),
    )
  val ROYAL_PURPLE =
    DexPalette(
      name = "Royal purple",
      shell = Color(0xFF6B3FA0),
      shellDark = Color(0xFF4A2A73),
      shellText = Color(0xFFF3ECFB),
      shellMuted = Color(0xFFD9C8F0),
      bezel = Color(0xFF2E1A4A),
      screen = Color(0xFFFBF8FF),
      screenDots = Color(0xFFE9DFF7),
      ink = Color(0xFF231A2E),
      inkMuted = Color(0xFF5A4E68),
      inkFaint = Color(0xFF8A7F96),
      track = Color(0xFFE6E0EE),
      lens = Color(0xFFF2C14E),
      lensRim = Color(0xFFF3ECFB),
      tiles = listOf(Color(0xFF8E5BD1), Color(0xFFC2459C), Color(0xFF2A9D8F), Color(0xFF4F5BD5)),
      tileOutline = Color(0xFF1E1230),
      tileText = Color(0xFFFFFFFF),
    )
  val SUNNY_YELLOW =
    DexPalette(
      name = "Sunny yellow",
      shell = Color(0xFFF6C915),
      shellDark = Color(0xFFD4A500),
      // Dark lettering: white doesn't read on yellow.
      shellText = Color(0xFF3A2A00),
      shellMuted = Color(0xFF5C4600),
      bezel = Color(0xFF7A5A00),
      screen = Color(0xFFFFFDF2),
      screenDots = Color(0xFFF5ECC8),
      ink = Color(0xFF2B2410),
      inkMuted = Color(0xFF5E5536),
      inkFaint = Color(0xFF8C8466),
      track = Color(0xFFEDE6CF),
      lens = Color(0xFFE24B4A),
      lensRim = Color(0xFFFFFDF2),
      tiles = listOf(Color(0xFFE8A317), Color(0xFFE2553A), Color(0xFF3C9A5F), Color(0xFF3D7CC9)),
      tileOutline = Color(0xFF2B1F00),
      tileText = Color(0xFFFFFFFF),
    )
  val PRETTY_PINK =
    DexPalette(
      name = "Pretty pink",
      shell = Color(0xFFF48FB1),
      shellDark = Color(0xFFE0609A),
      shellText = Color(0xFFFFFFFF),
      shellMuted = Color(0xFFFFE1EC),
      bezel = Color(0xFFB03A72),
      screen = Color(0xFFFFF7FA),
      screenDots = Color(0xFFFBE0EA),
      ink = Color(0xFF3A2230),
      inkMuted = Color(0xFF6E5462),
      inkFaint = Color(0xFF9C8592),
      track = Color(0xFFF5E1EA),
      lens = Color(0xFFB39DDB),
      lensRim = Color(0xFFFFFFFF),
      tiles = listOf(Color(0xFFE8578E), Color(0xFFA66BD6), Color(0xFF3FAE9C), Color(0xFFF0839A)),
      tileOutline = Color(0xFF4A1531),
      tileText = Color(0xFFFFFFFF),
    )
  val ALL = listOf(CLASSIC_RED, RETRO, MODERN_BLUE, ROYAL_PURPLE, SUNNY_YELLOW, PRETTY_PINK)
}

/**
 * Colors for the current theme. Backed by Compose state, so switching themes recolors everything at once.
 * Semantic colors (you, foe, HP) are the same in every theme so bars and chips stay readable.
 */
object DexColors {
  var palette by mutableStateOf(DexThemes.CLASSIC_RED)

  val Shell get() = palette.shell
  val ShellDark get() = palette.shellDark
  val ShellText get() = palette.shellText
  val ShellMuted get() = palette.shellMuted
  val Bezel get() = palette.bezel
  val Screen get() = palette.screen
  val ScreenDots get() = palette.screenDots
  val Ink get() = palette.ink
  val InkMuted get() = palette.inkMuted
  val InkFaint get() = palette.inkFaint
  val Track get() = palette.track
  val Lens get() = palette.lens
  val LensRim get() = palette.lensRim
  val You = Color(0xFF185FA5)
  val YouLight = Color(0xFF85B7EB)
  val Foe = Color(0xFFA32D2D)
  val FoeBar = Color(0xFFE24B4A)
  val Good = Color(0xFF639922)
  val Warn = Color(0xFFEF9F27)
  val Bad = Color(0xFFE24B4A)
  val Teal = Color(0xFF0F6E56)
  val LightRed = Color(0xFFE24B4A)
  val LightAmber = Color(0xFFEF9F27)
  val LightGreen = Color(0xFF8FD14F)
  val LightOff get() = palette.shellDark.copy(alpha = 0.7f)
}

/** Material colors mapped onto the Pokédex screen, so existing components pick up the look. */
@Composable
fun DexTheme(content: @Composable () -> Unit) {
  val scheme =
    lightColorScheme(
      primary = DexColors.You,
      onPrimary = Color.White,
      secondary = DexColors.ShellDark,
      tertiary = DexColors.Teal,
      error = DexColors.Foe,
      background = DexColors.Shell,
      surface = DexColors.Screen,
      onSurface = DexColors.Ink,
      onBackground = DexColors.Ink,
      outline = DexColors.InkMuted,
      surfaceVariant = DexColors.Screen,
    )
  val base = Typography()
  val typography =
    base.copy(
      // Section titles: small, spaced, muted, like the mockup's headers.
      titleSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp, color = DexColors.InkMuted),
      bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
      labelSmall = base.labelSmall.copy(fontSize = 11.sp),
    )
  MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}

/**
 * A section of the screen (replaces cards: on an LCD-style screen, sections are separated by space, not boxes).
 * Same shape as Material's Card so existing panels can switch with an import alias.
 */
@Composable
fun DexPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Column(modifier = modifier.padding(bottom = 4.dp), content = content)
}

/** Horizontal bar, [fraction] 0..1. */
@Composable
fun DexBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
  Box(
    modifier
      .fillMaxWidth()
      .height(6.dp)
      .clip(RoundedCornerShape(3.dp))
      .background(DexColors.Track)
  ) {
    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(color))
  }
}

@Composable
fun DexChip(text: String, background: Color, foreground: Color) {
  Text(
    text,
    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(background).padding(horizontal = 6.dp, vertical = 1.dp),
    color = foreground,
    style = MaterialTheme.typography.labelSmall,
  )
}

/** Type chip with a light fill and dark text from the same hue. */
@Composable
fun TypeChip(type: PokeType) {
  val (bg, fg) = TYPE_COLORS.getValue(type)
  DexChip(type.label, Color(bg), Color(fg))
}

private val TYPE_COLORS: Map<PokeType, Pair<Long, Long>> =
  mapOf(
    PokeType.NORMAL to (0xFFD3D1C7 to 0xFF444441),
    PokeType.FIRE to (0xFFF5C4B3 to 0xFF712B13),
    PokeType.WATER to (0xFFB5D4F4 to 0xFF0C447C),
    PokeType.ELECTRIC to (0xFFFAC775 to 0xFF633806),
    PokeType.GRASS to (0xFFC0DD97 to 0xFF27500A),
    PokeType.ICE to (0xFF9FE1CB to 0xFF085041),
    PokeType.FIGHTING to (0xFFF09595 to 0xFF791F1F),
    PokeType.POISON to (0xFFCECBF6 to 0xFF3C3489),
    PokeType.GROUND to (0xFFFAEEDA to 0xFF633806),
    PokeType.FLYING to (0xFFE6F1FB to 0xFF0C447C),
    PokeType.PSYCHIC to (0xFFF4C0D1 to 0xFF72243E),
    PokeType.BUG to (0xFFEAF3DE to 0xFF3B6D11),
    PokeType.ROCK to (0xFFB4B2A9 to 0xFF2C2C2A),
    PokeType.GHOST to (0xFFAFA9EC to 0xFF26215C),
    PokeType.DRAGON to (0xFF7F77DD to 0xFFEEEDFE),
    PokeType.DARK to (0xFF5F5E5A to 0xFFF1EFE8),
    PokeType.STEEL to (0xFFF1EFE8 to 0xFF444441),
  )

/** HP bar color: green above half, amber above a fifth, red below. */
fun hpColor(fraction: Float): Color =
  when {
    fraction > 0.5f -> DexColors.Good
    fraction > 0.2f -> DexColors.Warn
    else -> DexColors.Bad
  }
