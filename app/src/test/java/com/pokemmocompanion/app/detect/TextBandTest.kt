package com.pokemmocompanion.app.detect

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class TextBandTest {
  /** "Charmander was hurt by poison!" over Charmander's sprite: only the text survives binarization. */
  @Test
  fun keepsOnlyTheWhiteText() {
    val f = File("../samples/frame_20261001_092537_214.png")
    assumeTrue(f.exists())
    val img = ImageIO.read(f)
    val top = (0.76 * img.height).toInt()
    val h = (0.98 * img.height).toInt() - top
    val w = (0.78 * img.width).toInt()
    val px = img.getRGB(0, top, w, h, null, 0, w)
    val (bw, bh, out) = TextBand.binarize(px, w, h)
    val black = out.count { it == -0x1000000 }
    assertTrue("black $black", black in 5_000..20_000) // the text strokes, not the sprite or band
    // Ink sits in the text rows (y≈867-927 on screen), not over the sprite below them.
    val inkRows = (0 until bh).filter { y -> (0 until bw).any { x -> out[y * bw + x] == -0x1000000 } }
    val firstScreenY = inkRows.first() - 16 + top
    val lastScreenY = inkRows.last() - 16 + top
    assertTrue("rows $firstScreenY..$lastScreenY", firstScreenY in 840..880 && lastScreenY in 900..960)
  }
}
