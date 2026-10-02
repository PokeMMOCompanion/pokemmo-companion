package com.pokemmocompanion.app.party

import com.pokemmocompanion.app.detect.EncounterParser
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One party member as read from the summary pages. Fields stay null until their page has been read. */
@Serializable
data class PartyMember(
  val slot: Int,
  val nickname: String? = null,
  val level: Int? = null,
  val dexId: Int? = null,
  val species: String? = null,
  val nature: String? = null,
  val currentHp: Int? = null,
  /** HP (max), Atk, Def, SpA, SpD, Spe as shown in game. */
  val stats: List<Int>? = null,
  val evs: List<Int>? = null,
  val ivs: List<Int>? = null,
  val moves: List<String> = emptyList(),
  val ability: String? = null,
  val item: String? = null,
  /** Pages to read again after a level-up ("stats") or a new move that didn't fit ("moves"), seen in battle. */
  val outdated: Set<String> = emptySet(),
) {
  val hasInfo: Boolean
    get() = dexId != null && nature != null

  /**
   * The nickname, unless it's just the species name as the game shows it ("Nidoran" for Nidoran♂, "Mr Mime"):
   * compared on letters and digits only.
   */
  val realNickname: String?
    get() {
      val n = nickname ?: return null
      val s = species ?: return n
      fun key(x: String) = x.lowercase().filter { it.isLetterOrDigit() }
      return n.takeUnless { key(it) == key(s) }
    }

  /** How to show this Pokémon: its real nickname, else the species name. */
  val shownName: String
    get() = realNickname ?: species ?: nickname ?: "Slot ${slot + 1}"

  /** Enough for damage calcs: species, real stats and moves. */
  val isReady: Boolean
    get() = species != null && stats != null && moves.isNotEmpty()
}

@Serializable data class Party(val members: List<PartyMember?> = List(6) { null })

/** What happened to a page handed to [PartyStore.apply]. */
enum class ApplyResult {
  STORED,
  UNCHANGED,
  /** Not the info tab, and this slot's info tab hasn't been read yet (species unknown). */
  NEEDS_INFO,
  /** The name above the sprite isn't this slot's Pokémon: the player probably scrolled to another one. */
  NAME_MISMATCH,
  /** A different Pokédex number was read once; waiting to see it again before resetting the slot. */
  DEX_PENDING,
}

/**
 * Merges summary pages into the stored party and saves it as JSON.
 * The info tab comes first: its Pokédex number sets the species and its Name box the nickname. Other tabs are
 * only recorded once that's known, and only if the name above the sprite matches the stored nickname.
 * A slot is cleared only when its info page shows a different Pokédex number (the player swapped that Pokémon
 * out). Names are never used for that: OCR adds stray letters from the icons around them.
 */
class PartyStore(
  private val file: File,
  private val speciesName: (Int) -> String?,
  /** Abilities the species can have, to reject OCR noise; empty means "don't check". */
  private val abilitiesOf: (String) -> List<String> = { emptyList() },
) {
  private val json = Json { ignoreUnknownKeys = true }

  /** A different Pokédex number seen once for a slot; it must repeat before the slot is reset. */
  private val pendingDex = IntArray(6) { -1 }

  var party: Party = load()
    private set

  /** Applies one page for [slot]. */
  fun apply(slot: Int, page: SummaryPage): ApplyResult {
    if (slot !in 0..5) return ApplyResult.UNCHANGED
    var m = party.members[slot] ?: PartyMember(slot)
    if (page.kind != SummaryPageKind.INFO) {
      if (m.dexId == null) return ApplyResult.NEEDS_INFO
      val nick = m.nickname
      if (page.headerName != null && nick != null && !sameName(page.headerName, nick)) return ApplyResult.NAME_MISMATCH
    }
    val dex = page.dexId?.takeIf { speciesName(it) != null }
    if (dex != null && m.dexId != null && dex != m.dexId) {
      // One misread number must not wipe the slot: wait until the new number is read twice in a row.
      if (pendingDex[slot] != dex) {
        pendingDex[slot] = dex
        return ApplyResult.DEX_PENDING
      }
      m = PartyMember(slot)
    }
    pendingDex[slot] = -1
    val species = dex?.let(speciesName) ?: m.species
    val ability =
      page.ability?.let { a ->
        val known = species?.let(abilitiesOf).orEmpty()
        if (known.isEmpty()) a else known.firstOrNull { it.filter(Char::isLetter).equals(a.filter(Char::isLetter), true) }
      }

    m =
      m.copy(
        nickname = page.nickname ?: m.nickname,
        level = page.level ?: m.level,
        item = page.item ?: m.item,
        dexId = dex ?: m.dexId,
        species = species,
        nature = page.nature ?: m.nature,
        currentHp = page.currentHp ?: m.currentHp,
        stats = page.stats ?: m.stats,
        evs = page.evs ?: m.evs,
        ivs = page.ivs ?: m.ivs,
        // A partial moves read never replaces a fuller one.
        moves = page.moves?.takeIf { it.size >= m.moves.size } ?: m.moves,
        ability = ability ?: m.ability,
        outdated = m.outdated - setOfNotNull("stats".takeIf { page.stats != null }, "moves".takeIf { page.moves != null }),
      )
    if (m == party.members[slot]) return ApplyResult.UNCHANGED
    party = Party(party.members.toMutableList().also { it[slot] = m })
    save()
    return ApplyResult.STORED
  }

  /** Updates only a member's current HP (from the overworld rings or the battle HP box). */
  fun updateHp(slot: Int, hp: Int): Boolean {
    val m = party.members.getOrNull(slot) ?: return false
    if (m.currentHp == hp) return false
    party = Party(party.members.toMutableList().also { it[slot] = m.copy(currentHp = hp) })
    save()
    return true
  }

  /** Battle said "[slot] grew to level [level]": new level; stats are out of date until the Stats page is read. */
  fun leveledUp(slot: Int, level: Int): Boolean {
    val m = party.members.getOrNull(slot) ?: return false
    if (m.level == level) return false
    put(m.copy(level = level, outdated = m.outdated + "stats"))
    return true
  }

  /** Battle said "[slot] learned [move]": added if there's room; with four moves one was replaced, so re-read. */
  fun learned(slot: Int, move: String): Boolean {
    val m = party.members.getOrNull(slot) ?: return false
    if (m.moves.any { it.equals(move, ignoreCase = true) }) return false
    put(if (m.moves.size < 4) m.copy(moves = m.moves + move) else m.copy(outdated = m.outdated + "moves"))
    return true
  }

  /** Replaces a slot with values entered by hand (manual override when OCR can't read a page). */
  fun set(m: PartyMember) {
    if (m.slot !in 0..5) return
    put(m.copy(outdated = emptySet()))
  }

  fun remove(slot: Int) {
    if (slot !in 0..5) return
    party = Party(party.members.toMutableList().also { it[slot] = null })
    save()
  }

  private fun put(m: PartyMember) {
    party = Party(party.members.toMutableList().also { it[m.slot] = m })
    save()
  }

  fun clear() {
    party = Party()
    save()
  }

  /**
   * Header name vs stored nickname. OCR of the white header text can drop or add a letter, and the Poké Ball
   * icon before it can show up as a stray character, so allow small differences.
   */
  internal fun sameName(header: String, nickname: String): Boolean {
    fun norm(s: String) = s.split(Regex("\\s+")).dropWhile { it.length <= 1 }.joinToString("")
      .lowercase().filter { it.isLetterOrDigit() }
    val h = norm(header)
    val n = norm(nickname)
    if (h.isEmpty() || n.isEmpty()) return true
    if (h == n || h.contains(n) || n.contains(h) && h.length >= 3) return true
    return EncounterParser.editDistance(h, n) <= maxOf(1, n.length / 4)
  }

  private fun load(): Party =
    try {
      if (file.exists()) json.decodeFromString(file.readText()) else Party()
    } catch (e: Exception) {
      Party()
    }

  private fun save() {
    file.parentFile?.mkdirs()
    file.writeText(json.encodeToString(Party.serializer(), party))
  }
}
