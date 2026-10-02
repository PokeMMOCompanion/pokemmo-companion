package com.pokemmocompanion.app.detect

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PauseMenuTest {
  private class ImageFrame(private val img: BufferedImage) : Frame {
    override val width = img.width
    override val height = img.height

    override fun pixel(x: Int, y: Int) = img.getRGB(x, y)
  }

  private fun load(name: String): Frame {
    val file = File("../samples", name)
    assumeTrue("Missing sample $file", file.exists())
    return ImageFrame(ImageIO.read(file))
  }

  @Test
  fun detectsOpenMenu() {
    for (f in listOf("frame_20261001_203831_038.png", "frame_20261001_203845_877.png", "frame_20261001_203901_176.png")) {
      assertTrue(f, PauseMenu.isOpen(load(f)))
    }
    // Summary screens and battles are not the menu.
    assertFalse(PauseMenu.isOpen(load("frame_20261001_104643_558.png")))
    assertFalse(PauseMenu.isOpen(load("frame_20261001_103927_279.png")))
  }

  @Test
  fun parsesHeader() {
    val route = PauseMenu.parse(listOf("Route 2 Ch. 5", "$120", "Wednesday, 02:33, Summer"))!!
    assertEquals(MenuInfo("Route 2", 5, 2, 33, "Summer"), route)
    assertEquals("Night", route.timeOfDay)
    assertEquals("Viridian Forest", PauseMenu.parse(listOf("Viridian Forest Ch. 5", "$120", "Wednesday, 14:34, Summer"))!!.place)
    // OCR slips: icon read as a letter, comma for the dot, period for the colon.
    val slip = PauseMenu.parse(listOf("I Pewter City Ch, 5", "S120", "Wednesday, 02.35, Summer"))!!
    assertEquals("Pewter City", slip.place.removePrefix("I "))
    assertEquals(2, slip.hour)
    assertNull(PauseMenu.parse(listOf("Bag", "Trainer")))
  }
}
