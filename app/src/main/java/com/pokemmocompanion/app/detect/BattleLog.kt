package com.pokemmocompanion.app.detect

enum class Side { YOU, FOE, OTHER }

/** One line of the battle history: a move (with what happened) or another battle message. */
data class BattleEvent(
  val side: Side,
  /** Pokémon that used the move. Null for non-move messages. */
  val actor: String? = null,
  val move: String? = null,
  /** "super effective", "critical hit", "missed", ... attached to the move they followed. */
  val outcomes: List<String> = emptyList(),
  /** The clean message to show (matched to a known battle message where possible). */
  val text: String,
  /** Matched to a known battle message (or a recognizable "X used Y!"). Unknown reads are kept for the app's own
   * use (abilities, items, weather) but not shown in the history. */
  val known: Boolean = true,
)

/**
 * Builds the move history of the current battle from the messages in the battle text box (bottom of the screen).
 *
 * - Each read is matched to a known battle message ([BattleMessages]) and shown in its clean form, with Pokémon and
 *   move names filled in from what the app knows ([BattleContext]), so OCR slips never reach the screen.
 * - The box is read about twice a second; a message that stays up for several reads (even garbled differently each
 *   time while an animation plays over it) is recorded once.
 * - Messages type out letter by letter: a short partial read means a new message is starting, so even a word-for-
 *   word repeat (two Pidgey in a horde using Gust) counts again. A blank box or the move menu does the same.
 * - Only sentences count (end in "!", "." or "?"), so menu buttons like FIGHT/BAG/RUN are ignored.
 */
class BattleLog(private val context: BattleContext = BattleContext.NONE) {
  private val _events = mutableListOf<BattleEvent>()
  val events: List<BattleEvent>
    get() = _events

  /** Raw and clean text of the message currently on screen (already recorded). */
  private var lastRaw: String? = null
  private var lastClean: String? = null
  private var blankReads = 0

  fun reset() {
    _events.clear()
    forget()
    blankReads = 0
  }

  /** Feeds one read of the text box. Returns true if the history changed. */
  fun onText(lines: List<String>): Boolean {
    val raw = tidy(lines.joinToString(" "))
    if (!isMessage(raw)) {
      val last = lastRaw
      val partial = norm(raw)
      if (last != null && partial.length >= 3 && partial.length <= norm(last).length * 7 / 10 && norm(last).startsWith(partial)) {
        // The box is typing a message out again from the start: the next full read is a new message.
        forget()
      } else if (++blankReads >= 2) {
        forget()
      }
      return false
    }
    blankReads = 0
    val parsed = parse(raw)
    val last = lastRaw
    if (last != null && _events.isNotEmpty()) {
      val a = norm(raw)
      val b = norm(last)
      val same = parsed.text == lastClean || a.startsWith(b) || b.startsWith(a) || similar(a, b)
      if (same) {
        // Same message read again (typing out, or garbled differently): keep one entry, the better reading.
        if (parsed.text != lastClean && (a.length > b.length || isBetter(parsed, _events.last()))) {
          if (outcomeOf(parsed) == null) _events[_events.lastIndex] = parsed.copy(outcomes = _events.last().outcomes)
          lastRaw = raw
          lastClean = parsed.text
          return true
        }
        return false
      }
    }
    lastRaw = raw
    lastClean = parsed.text
    return add(parsed)
  }

  private fun forget() {
    lastRaw = null
    lastClean = null
  }

  private fun add(e: BattleEvent): Boolean {
    outcomeOf(e)?.let { o ->
      // Effectiveness, crits and misses belong to the move just before them.
      val i = _events.indexOfLast { it.move != null }
      if (i >= 0) {
        val m = _events[i]
        if (o !in m.outcomes) _events[i] = m.copy(outcomes = m.outcomes + o)
        return true
      }
      return false
    }
    _events += e
    return true
  }

  /** Outcome messages are parsed as marker events carrying the outcome in [BattleEvent.move]; see [parse]. */
  private fun outcomeOf(e: BattleEvent): String? = if (e.actor == OUTCOME_MARK) e.move else null

  private fun parse(raw: String): BattleEvent {
    BattleMessages.match(raw, context)?.let { msg ->
      return when (msg.kind) {
        MessageKind.MOVE -> BattleEvent(msg.side, msg.actor, msg.move, emptyList(), msg.text)
        MessageKind.OUTCOME -> BattleEvent(Side.OTHER, OUTCOME_MARK, msg.outcome, emptyList(), msg.text)
        MessageKind.OTHER -> BattleEvent(Side.OTHER, text = msg.text)
      }
    }
    // Not a known message: best-effort word correction, and still recognize "X used Y!".
    val text = BattleText.clean(raw)
    outcome(text)?.let { return BattleEvent(Side.OTHER, OUTCOME_MARK, it, emptyList(), text) }
    val m = USED.find(text) ?: return BattleEvent(Side.OTHER, text = text, known = false)
    var actor = m.groupValues[2].trim()
    val owned = actor.lastIndexOf("'s ")
    val foe = m.groupValues[1].isNotBlank() || owned >= 0
    if (owned >= 0) actor = actor.substring(owned + 3)
    // Shown only when both are known; otherwise it's an unknown read (kept, not shown).
    val pokemon = context.pokemon(actor) ?: return BattleEvent(Side.OTHER, text = text, known = false)
    val rawMove = m.groupValues[3].trim().trimEnd('.', '!', ',', ' ')
    val move = context.move(pokemon, rawMove) ?: return BattleEvent(Side.OTHER, text = text, known = false)
    // Shown rebuilt from the resolved names, never the raw read.
    val shown = (if (foe) "The foe's " else "") + "$pokemon used $move!"
    return BattleEvent(if (foe) Side.FOE else Side.YOU, pokemon, move, emptyList(), shown)
  }

  /** Prefer a reading that matched a move over one that didn't. */
  private fun isBetter(new: BattleEvent, old: BattleEvent): Boolean =
    new.side != Side.OTHER && old.side == Side.OTHER

  companion object {
    private const val OUTCOME_MARK = "\u0000outcome"

    private val USED =
      Regex(
        """^((?:T\s*h\s*e\s+)?(?:w\s*i\s*l\s*d|f\s*o\s*e'?\s*s?|o\s*p\s*p\s*o\s*s\s*i\s*n\s*g)\s+)?(.+?)\s+u\s*s\s*e\s*d\s+(.+?)[\s!.]*$""",
        RegexOption.IGNORE_CASE,
      )

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Collapses spaces and fixes OCR spacing around punctuation: "Metapod' s" → "Metapod's", "Harden !" → "Harden!". */
    internal fun tidy(s: String): String =
      s.replace(Regex("\\s+"), " ").replace(Regex("\\s*'\\s*"), "'").replace(Regex("\\s+([!.?,])"), "$1").trim()

    /** Same message read twice with OCR noise: at most ~25% of the letters differ. */
    internal fun similar(a: String, b: String): Boolean {
      val longest = maxOf(a.length, b.length)
      if (longest == 0) return true
      return EncounterParser.editDistance(a, b) <= maxOf(2, longest / 4)
    }

    /** A battle sentence, not a menu label or OCR noise. */
    internal fun isMessage(text: String): Boolean =
      text.length >= 6 && text.count { it.isLetter() } >= 4 && text.contains(' ') && text.trimEnd().last() in "!.?"

    /**
     * Current major status of each side, from the (clean) battle messages in order: "was poisoned" → PSN,
     * "woke up" → none, "fainted" → FNT, ... Messages starting with "The wild"/"The foe's" are the opponent's.
     */
    fun statuses(events: List<BattleEvent>): Map<Side, String> {
      val out = mutableMapOf<Side, String>()
      for (e in events) {
        if (e.move != null) continue
        val t = e.text.lowercase()
        val side = if (t.startsWith("the wild") || t.startsWith("the foe")) Side.FOE else Side.YOU
        val status =
          when {
            "fainted" in t -> "FNT"
            "poison" in t -> "PSN"
            "burn" in t -> "BRN"
            "paralyzed" in t -> "PAR"
            "fell asleep" in t || "fast asleep" in t -> "SLP"
            "frozen solid" in t -> "FRZ"
            "woke up" in t || "thawed out" in t -> ""
            "in charge" in t || "come back" in t || "sent out" in t || t.startsWith("go!") -> "" // switched
            else -> null
          }
        if (status != null) if (status.isEmpty()) out.remove(side) else out[side] = status
      }
      return out
    }

    internal fun outcome(text: String): String? {
      val t = text.lowercase()
      return when {
        "super effective" in t -> "super effective"
        "not very effective" in t -> "not very effective"
        "doesn't affect" in t || "doesnt affect" in t || "no effect" in t -> "no effect"
        "critical hit" in t -> "critical hit"
        "missed" in t || "avoided the attack" in t -> "missed"
        "but it failed" in t -> "failed"
        else -> null
      }
    }
  }
}
