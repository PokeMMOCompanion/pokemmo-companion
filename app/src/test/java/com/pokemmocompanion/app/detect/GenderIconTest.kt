package com.pokemmocompanion.app.detect

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class GenderIconTest {
  private fun frame(name: String): Frame {
    val f = File("../samples", name)
    assumeTrue("Missing sample $f", f.exists())
    val img = ImageIO.read(f)
    return object : Frame {
      override val width = img.width
      override val height = img.height

      override fun pixel(x: Int, y: Int) = img.getRGB(x, y)
    }
  }

  /** "Lv. N" word box in the opponent's name box, measured on the Thor frames (top left). */
  private val level = OcrBox(240, 118, 330, 146)

  @Test
  fun femaleAndMaleNidoran() {
    assertEquals(Gender.FEMALE, GenderIcon.detect(frame("frame_20261001_085616_240.png"), level)) // Nidoran♀ Lv. 5
    assertEquals(Gender.MALE, GenderIcon.detect(frame("frame_20261001_085453_928.png"), level)) // Nidoran♂ Lv. 6
    // Shifted box (imprecise OCR) still finds it.
    assertEquals(Gender.FEMALE, GenderIcon.detect(frame("frame_20261001_085616_240.png"), level.copy(left = 220, right = 310)))
  }

  @Test
  fun resolvesNidoranOnly() {
    assertEquals("Nidoran♀", GenderIcon.resolveName("Nidoran", Gender.FEMALE))
    assertEquals("Nidoran♂", GenderIcon.resolveName("Nidoran", Gender.MALE))
    assertEquals("Nidoran", GenderIcon.resolveName("Nidoran", null))
    assertEquals("Rattata", GenderIcon.resolveName("Rattata", Gender.MALE))
    // The icon beats whatever OCR made of the symbol, but never touches Nidorina/Nidorino.
    assertEquals("Nidoran♂", GenderIcon.resolveName("Nidoranở", Gender.MALE))
    assertEquals("Nidoran♀", GenderIcon.resolveName("Nidoran♂", Gender.FEMALE))
    assertEquals("Nidorino", GenderIcon.resolveName("Nidorino", Gender.MALE))
  }
}
