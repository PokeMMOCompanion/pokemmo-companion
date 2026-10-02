package com.pokemmocompanion.app.detect

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs the detector against frames saved from the Thor. The frames are game screenshots, so they stay local
 * in <project>/samples (gitignored); these tests are skipped when a file is missing.
 */
class BattleDetectorTest {

  private class ImageFrame(private val img: BufferedImage) : Frame {
    override val width = img.width
    override val height = img.height

    override fun pixel(x: Int, y: Int) = img.getRGB(x, y)
  }

  private val samplesDir = File("../samples")

  private val O = ScreenState.OVERWORLD
  private val B = ScreenState.BATTLE

  /** Frames in capture order with the expected raw reading (null = can't tell). */
  private val labeled: List<Pair<String, ScreenState?>> =
    listOf(
      "frame_20260930_212445_458.png" to B, // FIGHT/BAG/RUN menu
      "frame_20260930_212609_713.png" to O,
      "frame_20260930_212618_022.png" to O, // town
      "frame_20260930_212639_434.png" to O, // NPC dialog open
      "frame_20260930_212716_697.png" to O,
      "frame_20260930_212726_810.png" to O,
      "frame_20260930_212739_254.png" to O, // battle-start fade
      "frame_20260930_212741_209.png" to null, // intro, no HUD yet
      "frame_20260930_212743_566.png" to null, // "A wild Pidgey appeared!"
      "frame_20260930_212747_674.png" to B,
      "frame_20260930_212751_204.png" to B, // move selection
      "frame_20260930_212753_002.png" to B,
      "frame_20260930_212753_768.png" to B,
      "frame_20260930_212754_771.png" to B,
      "frame_20260930_212757_023.png" to B, // "The wild Pidgey fainted!"
      "frame_20260930_212758_077.png" to B, // exp gain
      "frame_20260930_212800_081.png" to O,
      "frame_20260930_212803_653.png" to O,
      "frame_20260930_212812_027.png" to O, // battle-start fade
      "frame_20260930_212812_244.png" to O,
      "frame_20260930_212824_754.png" to B,
      "frame_20260930_212827_045.png" to B,
      "frame_20260930_212828_339.png" to B,
      "frame_20260930_212828_755.png" to B,
      "frame_20260930_212830_287.png" to B,
      "frame_20260930_212830_935.png" to B,
      "frame_20260930_212831_201.png" to B,
      "frame_20260930_212831_605.png" to B,
    )

  private fun load(name: String): Frame {
    val file = File(samplesDir, name)
    assumeTrue("Missing sample $file", file.exists())
    return ImageFrame(ImageIO.read(file))
  }

  /** Same frame shrunk, to check the scaled coordinates work at other capture sizes. */
  private fun Frame.scaled(w: Int, h: Int): Frame {
    val src = (this as ImageFrame)
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until h) for (x in 0 until w) out.setRGB(x, y, src.pixel(x * width / w, y * height / h))
    return ImageFrame(out)
  }

  @Test
  fun rawReadingsMatchLabels() {
    for ((name, expected) in labeled) {
      val features = BattleDetector.features(load(name))
      assertEquals("$name $features", expected, BattleDetector.classify(features))
    }
  }

  @Test
  fun rawReadingsHoldAtHalfResolution() {
    for ((name, expected) in labeled) {
      val features = BattleDetector.features(load(name).scaled(960, 540))
      assertEquals("$name $features", expected, BattleDetector.classify(features))
    }
  }

  /** Ball throws: bag menu, zoomed-in throw and shakes, "broke free", "Gotcha!". All battle, never overworld. */
  @Test
  fun catchSequenceStaysInBattle() {
    val files = samplesDir.listFiles { f -> f.name.startsWith("frame_20261001_06") }?.sorted().orEmpty()
    assumeTrue("No catch samples", files.isNotEmpty())
    for (file in files) {
      val features = BattleDetector.features(load(file.name))
      assertEquals("${file.name} $features", B, BattleDetector.classify(features))
    }
  }

  @Test
  fun trackerFollowsCaptureSequence() {
    val tracker = BattleTracker(confirmFrames = 2)
    val states = labeled.map { (name, _) ->
      tracker.update(BattleDetector.classify(BattleDetector.features(load(name))))
      tracker.state
    }
    val U = ScreenState.UNKNOWN
    // A single battle frame first, then overworld confirmed on the 2nd overworld frame, and so on.
    val expected = listOf(U, U, O, O, O, O, O, O, O, O, B, B, B, B, B, B, B, O, O, O, O, B, B, B, B, B, B, B)
    assertEquals(expected, states)
  }
}
