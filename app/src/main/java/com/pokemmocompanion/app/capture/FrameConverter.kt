package com.pokemmocompanion.app.capture

import android.graphics.Bitmap
import android.graphics.Rect
import android.media.Image
import com.pokemmocompanion.app.detect.Frame

/** Turns RGBA_8888 [Image]s from the ImageReader into Bitmaps. Reuses its full-size buffer between frames. */
class FrameConverter {
  private var padded: Bitmap? = null
  private var lastWidth = 0
  private var lastHeight = 0

  /** Zero-copy view of the most recently converted frame, valid until the next conversion. */
  fun latestFrame(): Frame? {
    val bmp = padded ?: return null
    return object : Frame {
      override val width = lastWidth
      override val height = lastHeight

      override fun pixel(x: Int, y: Int) = bmp.getPixel(x, y)
    }
  }

  /** Full-resolution copy of a region of the latest frame, given as fractions of its width and height. */
  fun copyRegion(left: Float, top: Float, right: Float, bottom: Float): Bitmap? {
    val bmp = padded ?: return null
    val x = (left * lastWidth).toInt()
    val y = (top * lastHeight).toInt()
    val w = ((right - left) * lastWidth).toInt().coerceAtMost(lastWidth - x)
    val h = ((bottom - top) * lastHeight).toInt().coerceAtMost(lastHeight - y)
    return Bitmap.createBitmap(bmp, x, y, w, h)
  }

  /**
   * Pixels of a rectangle (fractions of the latest frame) in one bulk read: width, height, ARGB row by row.
   * Much cheaper than reading pixels one at a time for large areas like the battle text box.
   */
  fun pixels(left: Float, top: Float, right: Float, bottom: Float): Triple<Int, Int, IntArray>? {
    val bmp = padded ?: return null
    val x = (left * lastWidth).toInt()
    val y = (top * lastHeight).toInt()
    val w = ((right - left) * lastWidth).toInt().coerceAtMost(lastWidth - x)
    val h = ((bottom - top) * lastHeight).toInt().coerceAtMost(lastHeight - y)
    if (w <= 0 || h <= 0) return null
    val out = IntArray(w * h)
    bmp.getPixels(out, 0, w, x, y, w, h)
    return Triple(w, h, out)
  }

  /** Copy of a rectangle given in 1920×1080 reference coordinates, scaled to the actual frame size. */
  fun copyRefRect(left: Int, top: Int, right: Int, bottom: Int): Bitmap? {
    val bmp = padded ?: return null
    val sx = lastWidth / 1920f
    val sy = lastHeight / 1080f
    val x = (left * sx).toInt().coerceIn(0, lastWidth - 1)
    val y = (top * sy).toInt().coerceIn(0, lastHeight - 1)
    val w = ((right - left) * sx).toInt().coerceIn(1, lastWidth - x)
    val h = ((bottom - top) * sy).toInt().coerceIn(1, lastHeight - y)
    return Bitmap.createBitmap(bmp, x, y, w, h)
  }

  /** Copies the frame into a reusable bitmap. Its width may exceed the frame width because of row padding. */
  private fun copyFull(image: Image): Bitmap {
    val plane = image.planes[0]
    val paddedWidth = plane.rowStride / plane.pixelStride
    val bmp =
      padded?.takeIf { it.width == paddedWidth && it.height == image.height }
        ?: Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888).also { padded = it }
    val buffer = plane.buffer
    buffer.rewind()
    bmp.copyPixelsFromBuffer(buffer)
    lastWidth = image.width
    lastHeight = image.height
    return bmp
  }

  /** Copies the frame into the reusable buffer so [latestFrame] and the copy/crop helpers see it. */
  fun load(image: Image) {
    copyFull(image)
  }

}
