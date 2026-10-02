package com.pokemmocompanion.app.party

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pokémon pictures, one PNG per Pokédex number in the app's private storage. Nothing here is shipped with the app:
 * - captured: party icons cut from the user's own screen (see [PartyIcon]);
 * - imported: PokemonDB sprites downloaded on the device when the user asks (see [SpriteImporter]). These win.
 */
object SpriteStore {
  private var dir: File? = null
  private var importedDir: File? = null
  private val cache = ConcurrentHashMap<Int, Bitmap>()
  private val _version = MutableStateFlow(0)
  /** Bumps whenever an icon is added, so the UI reloads. */
  val version: StateFlow<Int> = _version.asStateFlow()

  fun init(context: Context) {
    if (dir == null) dir = File(context.filesDir, "sprites").apply { mkdirs() }
    if (importedDir == null) importedDir = File(context.filesDir, "sprites-imported").apply { mkdirs() }
  }

  private fun file(dexId: Int) = dir?.let { File(it, "$dexId.png") }

  private fun importedFile(dexId: Int) = importedDir?.let { File(it, "$dexId.png") }

  private fun existing(dexId: Int) = importedFile(dexId)?.takeIf { it.exists() } ?: file(dexId)?.takeIf { it.exists() }

  fun has(dexId: Int): Boolean = cache.containsKey(dexId) || existing(dexId) != null

  fun get(dexId: Int): Bitmap? =
    cache[dexId] ?: existing(dexId)?.let { BitmapFactory.decodeFile(it.path) }?.also { cache[dexId] = it }

  fun hasImported(dexId: Int) = importedFile(dexId)?.exists() == true

  val importedCount: Int
    get() = importedDir?.list()?.size ?: 0

  /** Stores an imported sprite; call [importDone] after a batch so the UI reloads once. */
  fun saveImported(dexId: Int, bmp: Bitmap) {
    val f = importedFile(dexId) ?: return
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    cache.remove(dexId)
  }

  fun importDone() {
    cache.clear()
    _version.value++
  }

  fun clearImported() {
    importedDir?.listFiles()?.forEach { it.delete() }
    importDone()
  }

  fun save(dexId: Int, icon: Icon) {
    val f = file(dexId) ?: return
    val bmp = Bitmap.createBitmap(icon.pixels, icon.width, icon.height, Bitmap.Config.ARGB_8888)
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    if (importedFile(dexId)?.exists() != true) cache[dexId] = bmp
    _version.value++
  }

  fun clear() {
    dir?.listFiles()?.forEach { it.delete() }
    cache.clear()
    _version.value++
  }
}
