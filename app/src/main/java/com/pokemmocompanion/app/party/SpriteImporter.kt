package com.pokemmocompanion.app.party

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pokemmocompanion.app.detect.SPECIES_NAMES
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Downloads every Pokémon's sprite from PokemonDB into [SpriteStore], a few at a time. Pokémon that already have one
 * are skipped, so running it again only fetches what failed before.
 */
object SpriteImporter {
  private const val PARALLEL = 4

  private enum class Result { HAD, FETCHED, FAILED }

  /** Returns (downloaded now, failed). [progress] gets how many of the 649 are done. */
  suspend fun downloadAll(progress: (Int) -> Unit): Pair<Int, Int> =
    withContext(Dispatchers.IO) {
      val gate = Semaphore(PARALLEL)
      val done = AtomicInteger(0)
      val results =
        SPECIES_NAMES.mapIndexed { i, name ->
          async {
            val dex = i + 1
            val result =
              when {
                SpriteStore.hasImported(dex) -> Result.HAD
                gate.withPermit { fetch(dex, name) } -> Result.FETCHED
                else -> Result.FAILED
              }
            progress(done.incrementAndGet())
            result
          }
        }.awaitAll()
      SpriteStore.importDone()
      results.count { it == Result.FETCHED } to results.count { it == Result.FAILED }
    }

  private fun fetch(dex: Int, name: String): Boolean =
    try {
      val conn = URL(SpriteImport.url(name)).openConnection() as HttpURLConnection
      conn.connectTimeout = 15_000
      conn.readTimeout = 15_000
      conn.setRequestProperty("User-Agent", "PokeMMO-Companion (personal Android app)")
      try {
        if (conn.responseCode != 200) false
        else conn.inputStream.use { BitmapFactory.decodeStream(it) }?.let { store(dex, it); true } ?: false
      } finally {
        conn.disconnect()
      }
    } catch (_: Exception) {
      false
    }

  /** Trims the sprite's transparent border and saves it. */
  private fun store(dexId: Int, bmp: Bitmap) {
    val argb = if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false)
    val px = IntArray(argb.width * argb.height)
    argb.getPixels(px, 0, argb.width, 0, 0, argb.width, argb.height)
    val box = SpriteImport.trimBox(px, argb.width, argb.height)
    val out = if (box == null) argb else Bitmap.createBitmap(argb, box[0], box[1], box[2] - box[0], box[3] - box[1])
    SpriteStore.saveImported(dexId, out)
  }
}
