package com.pokemmocompanion.app.detect

/** Read-only view of a captured frame. Kept free of Android types so detection can be unit tested on the JVM. */
interface Frame {
  val width: Int
  val height: Int

  /** Pixel as 0xAARRGGBB. */
  fun pixel(x: Int, y: Int): Int
}
