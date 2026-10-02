package com.pokemmocompanion.app.ui.dex

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokemmocompanion.app.party.SpriteStore

/** A captured Pokémon icon (see SpriteStore), drawn pixel-sharp. Draws nothing until the species has an icon. */
@Composable
fun SpriteIcon(dexId: Int?, size: Dp = 36.dp) {
  if (dexId == null) return
  val version by SpriteStore.version.collectAsStateWithLifecycle()
  val bitmap = remember(dexId, version) { SpriteStore.get(dexId)?.asImageBitmap() } ?: return
  Image(bitmap, contentDescription = null, modifier = Modifier.size(size), filterQuality = FilterQuality.None)
}
