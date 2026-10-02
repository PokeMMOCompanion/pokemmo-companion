package com.pokemmocompanion.app.party

import java.text.Normalizer

/**
 * Where each Pokémon's picture comes from, and how it's tidied up. Plain Kotlin so it can be tested; the download
 * itself is [SpriteImporter]'s job.
 *
 * Pictures are PokemonDB's Black/White sprites (pokemondb.net/sprites), fetched on the user's device when they ask
 * for it; nothing is shipped with the app.
 */
object SpriteImport {
  private const val BASE = "https://img.pokemondb.net/sprites/black-white/normal/"

  /** PokemonDB's file name for a species: "Mr. Mime" → "mr-mime", "Nidoran♀" → "nidoran-f", "Farfetch'd" → "farfetchd". */
  fun slug(name: String): String {
    val n = Normalizer.normalize(name.replace("♀", "-f").replace("♂", "-m").lowercase(), Normalizer.Form.NFD)
    return n.replace(". ", "-").replace(' ', '-').filter { it in 'a'..'z' || it in '0'..'9' || it == '-' }.trim('-')
  }

  fun url(name: String) = "$BASE${slug(name)}.png"

  /**
   * Bounding box of the non-transparent pixels (ARGB), so the Pokémon fills small icons instead of floating in
   * empty space. Returns null when the image has no transparency to trim (or is fully transparent).
   */
  fun trimBox(pixels: IntArray, width: Int, height: Int): IntArray? {
    var x0 = width
    var y0 = height
    var x1 = -1
    var y1 = -1
    for (y in 0 until height) for (x in 0 until width) {
      if ((pixels[y * width + x] ushr 24) > 16) {
        if (x < x0) x0 = x
        if (x > x1) x1 = x
        if (y < y0) y0 = y
        if (y > y1) y1 = y
      }
    }
    if (x1 < 0) return null
    if (x0 == 0 && y0 == 0 && x1 == width - 1 && y1 == height - 1) return null
    return intArrayOf(x0, y0, x1 + 1, y1 + 1)
  }
}
