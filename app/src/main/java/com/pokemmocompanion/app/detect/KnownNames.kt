package com.pokemmocompanion.app.detect

/**
 * Snaps OCR text to the closest entry of a known list, so nothing misspelled reaches the screen. Names are compared
 * on letters and digits only, allowing about one wrong letter in four; anything further off is rejected (null).
 */
class KnownNames(names: Collection<String>) {
  private val byKey: Map<String, String> = names.filter { it.isNotBlank() }.associateBy(::key)

  fun match(raw: String): String? {
    val k = key(raw)
    if (k.length < 2) return null
    byKey[k]?.let { return it }
    var best: String? = null
    var bestD = Int.MAX_VALUE
    for ((key, name) in byKey) {
      if (kotlin.math.abs(key.length - k.length) > maxOf(2, key.length / 4)) continue
      val d = EncounterParser.editDistance(k, key)
      if (d < bestD) {
        bestD = d
        best = name
      }
    }
    return best?.takeIf { bestD <= maxOf(1, key(it).length / 4) }
  }

  companion object {
    fun key(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
  }
}
