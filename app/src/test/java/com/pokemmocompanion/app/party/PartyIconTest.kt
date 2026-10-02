package com.pokemmocompanion.app.party

import com.pokemmocompanion.app.detect.Frame
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PartyIconTest {
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

  /** The viewed Pokémon's icon on each slot's highlighted tile: Charmander, Hoothoot, Rattata, Spearow, Metapod, Pikachu. */
  @Test
  fun cutsOutTheViewedIcon() {
    val viewed =
      mapOf(
        0 to "frame_20261001_070958_092.png",
        1 to "party_slot2_EVS_20261001_082550_729.png",
        2 to "frame_20261001_071048_197.png",
        3 to "party_slot4_EVS_20261001_082628_375.png",
        4 to "party_slot5_EVS_20261001_081604_751.png",
        5 to "frame_20261001_071102_840.png",
      )
    for ((slot, file) in viewed) {
      val icon = PartyIcon.cutout(frame(file), slot)
      assertNotNull(file, icon)
      icon!!
      // A whole icon, not the tile or a sliver of it.
      assertTrue("$file ${icon.width}x${icon.height}", icon.width in 30..85 && icon.height in 35..85)
      val opaque = icon.pixels.count { (it ushr 24) != 0 }
      val share = opaque * 100 / icon.pixels.size
      assertTrue("$file opaque $share%", share in 25..85)
      // The top row is part of the icon, not the bar's border line across the whole tile.
      assertTrue(file, (0 until icon.width).count { (icon.pixels[it] ushr 24) != 0 } < icon.width)
    }
  }
}
