package com.pokemmocompanion.app.capture

import android.content.Context
import android.media.MediaScannerConnection
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plain-text log of what the app reads and decides, so it can be checked on a PC without screenshots.
 *
 * One file per capture session: Android/data/com.pokemmocompanion.app/files/logs/companion-log_<date>_<time>.txt
 * (the newest [KEEP_FILES] are kept). Windows sees phone files through Android's media index, which doesn't notice a
 * file growing, so the file is re-scanned every few seconds and when the session ends; otherwise a USB copy can
 * show an old version.
 */
object DebugLog {
  private const val RECENT_LINES = 30
  private const val KEEP_FILES = 15
  private const val SCAN_INTERVAL_MS = 5_000L

  private val writer = Executors.newSingleThreadExecutor()
  private val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
  private val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
  @Volatile private var app: Context? = null
  @Volatile private var dir: File? = null
  @Volatile private var file: File? = null
  private var lastScan = 0L

  private val _recent = MutableStateFlow<List<String>>(emptyList())
  val recent: StateFlow<List<String>> = _recent.asStateFlow()

  private val _path = MutableStateFlow<String?>(null)
  val path: StateFlow<String?> = _path.asStateFlow()

  fun init(context: Context) {
    if (dir != null) return
    app = context.applicationContext
    dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs").apply { mkdirs() }
  }

  /** Starts a new log file (called when capture starts). */
  fun startSession() {
    val d = dir ?: return
    writer.execute {
      val f = File(d, "companion-log_${stamp.format(Date())}.txt")
      file = f
      _path.value = f.absolutePath
      d.listFiles { x -> x.name.startsWith("companion-log") && x.name.endsWith(".txt") }
        ?.sortedByDescending { it.name }
        ?.drop(KEEP_FILES)
        ?.forEach { it.delete() }
    }
  }

  /** Makes the current file visible to a PC right away (called when capture stops). */
  fun endSession() {
    writer.execute { file?.let(::scan) }
  }

  fun log(tag: String, message: String) {
    Log.i("Companion/$tag", message)
    val line = "${time.format(Date())} [$tag] $message"
    _recent.value = (_recent.value + line).takeLast(RECENT_LINES)
    writer.execute {
      val f = file ?: dir?.let { File(it, "companion-log_${stamp.format(Date())}.txt") }?.also {
        file = it
        _path.value = it.absolutePath
      } ?: return@execute
      try {
        f.appendText(line + "\n")
        val now = SystemClock.elapsedRealtime()
        if (now - lastScan >= SCAN_INTERVAL_MS) {
          lastScan = now
          scan(f)
        }
      } catch (e: Exception) {
        Log.w("Companion/DebugLog", "write failed", e)
      }
    }
  }

  /** Tells Android's media index about a new or changed file so it shows up (current) over USB. */
  fun scan(f: File) {
    val context = app ?: return
    MediaScannerConnection.scanFile(context, arrayOf(f.absolutePath), null, null)
  }
}
