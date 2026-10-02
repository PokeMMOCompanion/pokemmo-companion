package com.pokemmocompanion.app.party

import com.pokemmocompanion.app.detect.Frame
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Character segmentation in the summary number boxes, checked against values visible in the sample frames. */
class ValueBoxTest {
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

  private fun boxes(kind: SummaryPageKind) = SummaryLayout.fields(kind).filter { it.type == FieldType.NUMBER }

  /** Values as shown on screen, in layout order (stats page: Atk..Spe; EV/IV pages: HP..Spe; info: dex). */
  private val known =
    listOf(
      Triple("party_slot1_EVS_20261001_081435_334.png", SummaryPageKind.EVS, listOf("5", "1", "1", "1", "1", "11")),
      Triple("party_slot4_IVS_20261001_081546_639.png", SummaryPageKind.IVS, listOf("18", "28", "15", "1", "8", "20")),
      Triple("party_slot5_EVS_20261001_081604_751.png", SummaryPageKind.EVS, List(6) { "0" }),
      Triple("party_slot6_EVS_20261001_081618_246.png", SummaryPageKind.EVS, List(6) { "0" }),
      Triple("frame_20261001_071004_427.png", SummaryPageKind.IVS, List(6) { "15" }),
      Triple("frame_20261001_071048_197.png", SummaryPageKind.IVS, listOf("21", "31", "10", "6", "27", "12")), // 31 green
      Triple("frame_20261001_071102_840.png", SummaryPageKind.IVS, listOf("0", "18", "23", "31", "13", "3")), // 0 red
      Triple("frame_20261001_071001_373.png", SummaryPageKind.STATS, listOf("20", "18", "22", "19", "24")),
      Triple("frame_20261001_071046_020.png", SummaryPageKind.STATS, listOf("9", "7", "6", "7", "9")),
      Triple("frame_20261001_071100_007.png", SummaryPageKind.STATS, listOf("9", "10", "11", "10", "15")),
      Triple("frame_20261001_070958_092.png", SummaryPageKind.INFO, listOf("004")),
      Triple("frame_20261001_071044_733.png", SummaryPageKind.INFO, listOf("019")),
    )

  @Test
  fun characterCountAndNarrowOnes() {
    for ((file, kind, values) in known) {
      val f = frame(file)
      boxes(kind).zip(values).forEach { (box, value) ->
        val glyphs = ValueBox.glyphs(f, box)
        assertEquals("$file ${box.id}: count for \"$value\"", value.length, glyphs.size)
        assertEquals("$file ${box.id}: narrow flags for \"$value\"", value.map { it == '1' }, glyphs.map { it.isNarrow })
      }
    }
  }

  /** What went wrong on the Thor: OCR returned nothing (or "I") for a lone "1". */
  @Test
  fun missedOnesAreRecovered() {
    val f = frame("party_slot1_EVS_20261001_081435_334.png")
    val evBoxes = boxes(SummaryPageKind.EVS)
    val ocr = listOf("5", "", "I", "1", "I", "11") // as logged, with Atk's lone "1" missing
    val fixed = evBoxes.zip(ocr).map { (box, text) -> ValueBox.reconcile(text, ValueBox.glyphs(f, box)) }
    assertEquals(listOf(5, 1, 1, 1, 1, 11), fixed.map { SummaryParser.number(it) })
    // Zeros OCR dropped (Metapod's all-zero EV page) are read from their shape.
    val zeros = frame("party_slot5_EVS_20261001_081604_751.png")
    for (box in evBoxes) assertEquals("0", ValueBox.reconcile("", ValueBox.glyphs(zeros, box), zeros))
    // A digit without a shape signature (here the "2" of "28") is never guessed: the page gets retried.
    val pidgey = frame("party_slot4_IVS_20261001_081546_639.png")
    assertEquals("", ValueBox.reconcile("", ValueBox.glyphs(pidgey, boxes(SummaryPageKind.IVS)[1]), pidgey))
  }

  /** Shape reading must never contradict the real digit; and it must know 0, 1, 4, 6, 8, 9. */
  @Test
  fun shapeClassifierAgreesWithKnownDigits() {
    val seen = mutableSetOf<Char>()
    for ((file, kind, values) in known) {
      val f = frame(file)
      boxes(kind).zip(values).forEach { (box, value) ->
        ValueBox.glyphs(f, box).zip(value.toList()).forEach { (g, digit) ->
          val shape = ValueBox.classify(f, g)
          if (shape != null) assertEquals("$file ${box.id} \"$value\"", digit, shape)
          if (digit in "014689") assertEquals("$file ${box.id} \"$value\" digit $digit", digit, shape)
          seen += digit
        }
      }
    }
    assertEquals("0123456789".toSet(), seen) // the samples cover every digit
  }

  @Test
  fun stackRowsMapBack() {
    val f = frame("party_slot4_IVS_20261001_081546_639.png")
    val glyphs = boxes(SummaryPageKind.IVS).map { ValueBox.glyphs(f, it) }
    for (scale in listOf(2, 3, 4)) {
      val stack = ValueBox.stack(f, glyphs, scale)
      assertNotNull(stack)
      stack!!
      assertEquals(6, stack.rows)
      // Find each row's ink and check it maps back to that row.
      for (row in 0 until 6) {
        val inkRows =
          (0 until stack.height).filter { y -> (0 until stack.width).any { x -> stack.pixels[y * stack.width + x] != -1 } }
        val center = inkRows.filter { stack.rowAt(it) == row }
        assert(center.isNotEmpty()) { "scale $scale row $row has no ink" }
      }
    }
  }
}
