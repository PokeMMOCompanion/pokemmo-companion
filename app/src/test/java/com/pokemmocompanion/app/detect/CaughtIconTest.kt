package com.pokemmocompanion.app.detect

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Level-number boxes measured by hand on local sample frames (gitignored, skipped when missing).
 * Each box is shifted left by up to 40px to make sure an imprecise OCR box still finds the ball.
 */
class CaughtIconTest {
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

  private fun assertCaught(file: String, box: OcrBox, expected: Boolean) {
    val frame = load(file)
    for (shift in listOf(-40, -20, 0)) {
      val b = box.copy(left = box.left + shift, right = box.right + shift)
      val score = CaughtIcon.score(frame, b)
      if (expected) assertTrue("$file shift $shift score $score", CaughtIcon.isCaught(frame, b))
      else assertFalse("$file shift $shift score $score", CaughtIcon.isCaught(frame, b))
    }
  }

  @Test
  fun thorSingleCaught() {
    assertCaught("frame_20260930_212753_768.png", OcrBox(222, 112, 240, 140), true) // Pidgey Lv. 4
    assertCaught("frame_20260930_212824_754.png", OcrBox(227, 112, 245, 140), true) // Rattata Lv. 4
  }

  @Test
  fun notCaughtHasNoBall() {
    assertCaught("qwg0fsaefgmg1.png", OcrBox(440, 110, 482, 142), false) // Electabuzz Lv. 30
  }

  @Test
  fun hordeBoxesCaughtIncludingShiny() {
    assertCaught("frpn61b0i2qh1.jpg", OcrBox(745, 105, 782, 132), true) // Ursaring Lv. 58
    assertCaught("frpn61b0i2qh1.jpg", OcrBox(833, 213, 870, 243), true) // ★Shiny Ursaring★ Lv. 58 ♀
  }
}
