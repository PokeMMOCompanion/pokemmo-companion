package com.pokemmocompanion.app.capture

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes frames as PNGs to the app's own pictures folder (Android/data/<package>/files/Pictures). */
object FrameSaver {
  fun folder(context: Context): File =
    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: File(context.filesDir, "Pictures")

  fun save(context: Context, frame: Bitmap, prefix: String = "frame"): File {
    val dir = folder(context).apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
    val file = File(dir, "${prefix}_$stamp.png")
    file.outputStream().use { out -> check(frame.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG encode failed" } }
    DebugLog.scan(file) // visible over USB right away
    return file
  }
}
