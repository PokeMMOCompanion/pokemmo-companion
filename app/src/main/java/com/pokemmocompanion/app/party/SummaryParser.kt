package com.pokemmocompanion.app.party

import com.pokemmocompanion.app.calc.Nature
import com.pokemmocompanion.app.detect.EncounterParser
import com.pokemmocompanion.app.detect.Species
import com.pokemmocompanion.app.detect.KnownNames

enum class SummaryPageKind { INFO, STATS, EVS, IVS, MOVES, OTHER }

/** Everything readable from one summary page. Fields not on this page are null. */
data class SummaryPage(
  val kind: SummaryPageKind,
  val nickname: String? = null,
  /** Name above the sprite (top right), shown on every page; used to check the page belongs to the slot. */
  val headerName: String? = null,
  val level: Int? = null,
  val item: String? = null,
  val dexId: Int? = null,
  val nature: String? = null,
  val currentHp: Int? = null,
  /** HP (max), Atk, Def, SpA, SpD, Spe; only set when all six were read. */
  val stats: List<Int>? = null,
  val evs: List<Int>? = null,
  val ivs: List<Int>? = null,
  val moves: List<String>? = null,
  val ability: String? = null,
) {
  /** Everything this page is supposed to provide was read. */
  val isComplete: Boolean
    get() =
      when (kind) {
        SummaryPageKind.INFO -> dexId != null && nature != null
        SummaryPageKind.STATS -> stats != null
        SummaryPageKind.EVS -> evs != null
        SummaryPageKind.IVS -> ivs != null
        SummaryPageKind.MOVES -> !moves.isNullOrEmpty() && ability != null
        SummaryPageKind.OTHER -> true
      }
}

enum class FieldType {
  TEXT,
  /** One number, e.g. an IV. */
  NUMBER,
  /** "29 / 35" (current / max HP). */
  NUMBER_PAIR,
}

/** A value box on the summary screen, in 1920×1080 frame coordinates. */
data class FieldBox(
  val id: String,
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int,
  val type: FieldType = FieldType.TEXT,
)

/**
 * Where each value sits on PokeMMO's summary screen (measured on the Thor's 1920×1080 top screen).
 * Values are dark text in white boxes, so they read well on every page background; the white labels next to them
 * don't (pale backgrounds for Normal/Electric types), so labels aren't read at all. The tab says which page it is.
 */
object SummaryLayout {
  private const val VX0 = 472
  private const val VX1 = 762
  val STAT_IDS = listOf("hp", "atk", "def", "spa", "spd", "spe")

  /** Left-column value box k on the info/EV/IV pages: rows 90 px apart from y=136. */
  private fun row(id: String, k: Int, type: FieldType = FieldType.TEXT) =
    FieldBox(id, VX0, 136 + 90 * k, VX1, 192 + 90 * k, type)

  private val COMMON =
    listOf(
      FieldBox("item", 1309, 823, 1659, 879),
      FieldBox("level", 1580, 165, 1700, 205),
      // Name above the sprite, after the Poké Ball icon and before the gender symbol.
      FieldBox("header", 1140, 163, 1520, 205),
    )

  fun fields(kind: SummaryPageKind): List<FieldBox> =
    COMMON +
      when (kind) {
        SummaryPageKind.INFO -> listOf(row("dex", 0, FieldType.NUMBER), row("name", 1), row("nature", 2))
        // HP box at the top, happiness below it, then the five stats 90 px apart from y=400.
        SummaryPageKind.STATS ->
          listOf(FieldBox("hp", VX0, 136, VX1, 192, FieldType.NUMBER_PAIR)) +
            STAT_IDS.drop(1).mapIndexed { i, id -> FieldBox(id, VX0, 400 + 90 * i, VX1, 456 + 90 * i, FieldType.NUMBER) }
        SummaryPageKind.EVS, SummaryPageKind.IVS -> STAT_IDS.mapIndexed { i, id -> row(id, i, FieldType.NUMBER) }
        // Move name = top line of each move box (boxes 120 px apart), ability box below them.
        SummaryPageKind.MOVES ->
          (0 until 4).map { k -> FieldBox("move$k", 117, 140 + 120 * k, 335, 180 + 120 * k) } +
            FieldBox("ability", VX0, 616, VX1, 672)
        SummaryPageKind.OTHER -> emptyList()
      }
}

/** Turns the OCR text of each [SummaryLayout] box into a [SummaryPage]. */
object SummaryParser {
  private val LEVEL = Regex("""[Ll][vV][.,:]?\s*(\d{1,3})""")

  /**
   * @param texts OCR text per field id; a missing id means nothing was read in that box
   * @param moveName resolves OCR text to a real move name, or null if it isn't one
   * @param itemName resolves OCR text to a real item name ("None" when nothing is held), or null if it isn't one
   */
  fun fromFields(
    kind: SummaryPageKind,
    texts: Map<String, String>,
    moveName: (String) -> String?,
    itemName: (String) -> String? = { cleanName(it) },
  ): SummaryPage {
    fun text(id: String) = texts[id]?.trim()?.takeIf { it.isNotEmpty() }
    fun six(): List<Int>? =
      SummaryLayout.STAT_IDS.map { id -> text(id)?.let(::number) }.takeIf { v -> v.all { it != null } }?.map { it!! }

    var page =
      SummaryPage(
        kind,
        level = text("level")?.let { LEVEL.find(it)?.groupValues?.get(1)?.toIntOrNull() ?: number(it) },
        item = text("item")?.let { if (KnownNames.key(it) == "none") "None" else itemName(cleanName(it)) },
        headerName = text("header")?.let(::cleanName)?.takeIf { it.length >= 2 },
      )
    page =
      when (kind) {
        SummaryPageKind.INFO ->
          page.copy(
            dexId = text("dex")?.let(::number)?.takeIf { it in 1..649 },
            // Nicknames are free text; one that's the species name (no nickname) gets the species' spelling.
            nickname = text("name")?.let(::cleanName)?.takeIf { it.length >= 2 }?.let { n -> Species.match(n)?.takeIf { s -> EncounterParser.editDistance(KnownNames.key(s), KnownNames.key(n)) <= 2 } ?: n },
            nature = text("nature")?.let(::nature),
          )
        SummaryPageKind.STATS -> {
          val hp = text("hp")?.let(::numbers).orEmpty() // "29 / 35"
          val rest = SummaryLayout.STAT_IDS.drop(1).map { id -> text(id)?.let(::number) }
          val all = listOf(hp.getOrNull(1)) + rest
          page.copy(currentHp = hp.getOrNull(0), stats = all.takeIf { s -> s.all { it != null } }?.map { it!! })
        }
        SummaryPageKind.EVS -> page.copy(evs = six()?.takeIf { it.all { v -> v in 0..255 } })
        SummaryPageKind.IVS -> page.copy(ivs = six()?.takeIf { it.all { v -> v in 0..31 } })
        SummaryPageKind.MOVES ->
          page.copy(
            moves = (0 until 4).mapNotNull { k -> text("move$k")?.let { resolveMove(it, moveName) } }.ifEmpty { null },
            ability = text("ability")?.let(::cleanName)?.takeIf { it.length >= 3 },
          )
        SummaryPageKind.OTHER -> page
      }
    return page
  }

  /** Tries the whole text, then shorter word runs (the type badge can bleed into the name box). */
  private fun resolveMove(text: String, moveName: (String) -> String?): String? {
    val words = text.trim().split(Regex("\\s+"))
    for (n in words.size downTo 1) moveName(words.take(n).joinToString(" "))?.let { return it }
    return null
  }

  /** One of the 25 natures, allowing one misread letter. */
  private fun nature(text: String): String? {
    val t = cleanName(text).lowercase()
    val match =
      Nature.entries.firstOrNull { it.name.lowercase() == t }
        ?: Nature.entries.singleOrNull { EncounterParser.editDistance(it.name.lowercase(), t) <= 1 }
    return match?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
  }

  /** OCR often reads 0 as O and 1 as l/I in these boxes. */
  private fun digits(text: String) =
    text.map {
      when (it) {
        'O', 'o', 'D', 'Q' -> '0'
        'l', 'I', '|', 'i' -> '1'
        'S', 's' -> '5'
        'B' -> '8'
        else -> it
      }
    }.joinToString("")

  /** The first run of digits as written (keeps leading zeros, e.g. "004"). */
  internal fun digitRun(text: String): String? = Regex("""\d+""").find(digits(text))?.value

  internal fun number(text: String): Int? = Regex("""\d+""").find(digits(text))?.value?.toIntOrNull()

  internal fun numbers(text: String): List<Int> = Regex("""\d+""").findAll(digits(text)).map { it.value.toInt() }.toList()

  /** Drops icon junk OCR'd at the edges of a name. */
  private fun cleanName(text: String) =
    text.trim().trim { !it.isLetterOrDigit() && it != '.' && it != '\'' }.replace(Regex("\\s+"), " ")
}
