package com.pokemmocompanion.app.display

import android.view.Display
import android.view.Surface

/** Width and height in pixels as currently shown on screen (native panel size, swapped when rotated). */
fun Display.orientedSize(): Pair<Int, Int> {
  val sideways = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
  return if (sideways) mode.physicalHeight to mode.physicalWidth else mode.physicalWidth to mode.physicalHeight
}
