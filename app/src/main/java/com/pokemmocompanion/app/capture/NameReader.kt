package com.pokemmocompanion.app.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.pokemmocompanion.app.detect.GameText
import com.pokemmocompanion.app.detect.OcrBox
import com.pokemmocompanion.app.detect.OcrElement
import com.pokemmocompanion.app.detect.OcrLine
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable
import java.util.concurrent.TimeUnit

/** ML Kit OCR with the bundled on-device model. Blocking, so call it off the main thread. */
class NameReader : Closeable {
  private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

  /** Lines with word positions, in [bitmap] pixel coordinates shifted down by [offsetY]. */
  fun readLines(bitmap: Bitmap, offsetY: Int = 0): List<OcrLine> {
    val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 3, TimeUnit.SECONDS)
    return text.textBlocks.flatMap { block ->
      block.lines.map { line ->
        // Cleaned the same way for the line and its words, so word offsets within the line still line up.
        OcrLine(
          GameText.clean(line.text),
          line.elements.mapNotNull { el -> el.boundingBox?.let { OcrElement(GameText.clean(el.text), it.toOcrBox(offsetY)) } },
        )
      }
    }
  }

  /**
   * Text in one small value box. The crop is enlarged 2× onto a white margin first: ML Kit easily misses a lone
   * digit like "6" in a tight crop at native size.
   */
  fun readField(crop: Bitmap): String {
    val scale = 2
    val pad = 24
    val out = Bitmap.createBitmap(crop.width * scale + 2 * pad, crop.height * scale + 2 * pad, Bitmap.Config.ARGB_8888)
    Canvas(out).apply {
      drawColor(Color.WHITE)
      drawBitmap(crop, null, Rect(pad, pad, pad + crop.width * scale, pad + crop.height * scale), Paint(Paint.FILTER_BITMAP_FLAG))
    }
    return try {
      readLines(out).joinToString(" ") { it.text }.trim()
    } finally {
      out.recycle()
    }
  }

  /** OCR lines (with word positions) of ready-made pixels, e.g. a stack of binarized number boxes. */
  fun readStack(width: Int, height: Int, pixels: IntArray): List<OcrLine> {
    val bmp = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    return try {
      readLines(bmp)
    } finally {
      bmp.recycle()
    }
  }

  private fun Rect.toOcrBox(offsetY: Int) = OcrBox(left, top + offsetY, right, bottom + offsetY)

  override fun close() = recognizer.close()
}
