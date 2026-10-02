package com.pokemmocompanion.app.detect

import java.text.Normalizer

/** Snaps OCR'd names to real species names, fixing small misreads ("Pidgev" → "Pidgey", "Nidorang" → "Nidoran♀"). */
object Species {
  private const val NIDORAN = "nidoran"
  // OCR usually turns the gender symbol at the end of "Nidoran♀/♂" into a look-alike character.
  private const val FEMALE_LOOKALIKES = "♀gq9"
  private const val MALE_LOOKALIKES = "♂do6σ"

  private val byKey: Map<String, String> = SPECIES_NAMES.associateBy(::key)

  /**
   * Lowercase letters and digits only, so punctuation and spacing differences don't matter ("Mr. Mime" → "mrmime").
   * Accents are stripped too: OCR has read Nidoran's ♂ as "ở", which should count as the look-alike "o".
   */
  private fun key(name: String) =
    Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD)
      .filter { it.isLetterOrDigit() || it == '♀' || it == '♂' }

  /** The species [ocrName] most likely is, or null if nothing is close enough. */
  fun match(ocrName: String): String? {
    val k = key(ocrName)
    if (k.isEmpty()) return null
    byKey[k]?.let { return it }
    nidoran(k)?.let { return it }

    var best: String? = null
    var bestDist = Int.MAX_VALUE
    var tie = false
    for ((candidateKey, name) in byKey) {
      val d = EncounterParser.editDistance(k, candidateKey)
      if (d < bestDist) {
        best = name
        bestDist = d
        tie = false
      } else if (d == bestDist) {
        tie = true
      }
    }
    return best.takeIf { !tie && bestDist <= maxDistance(k.length) }
  }

  /** Nidoran♀ and Nidoran♂ differ only by the symbol, so decide from the character OCR left at the end. */
  private fun nidoran(k: String): String? {
    // Exact prefix only, so a misread "Nidorino" or "Nidorina" can't land here.
    if (k.length !in NIDORAN.length + 1..NIDORAN.length + 2 || !k.startsWith(NIDORAN)) return null
    val suffix = k.drop(NIDORAN.length)
    return when {
      suffix.any { it in FEMALE_LOOKALIKES } -> "Nidoran♀"
      suffix.any { it in MALE_LOOKALIKES } -> "Nidoran♂"
      else -> null
    }
  }

  private fun maxDistance(length: Int) =
    when {
      length <= 5 -> 1
      length <= 9 -> 2
      else -> 3
    }
}
