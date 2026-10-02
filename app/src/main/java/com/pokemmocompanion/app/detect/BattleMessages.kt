package com.pokemmocompanion.app.detect

/** What the app knows during a battle, used to fill message slots with real names instead of OCR text. */
interface BattleContext {
  /** Closest known Pokémon (party nickname/species, current opponents, then the species list), or null. */
  fun pokemon(raw: String): String?

  /** Closest move for [actor] (its known moves first, then the full move list), or null. */
  fun move(actor: String?, raw: String): String?

  /** Whether [pokemon] (already resolved) is one of the player's own party. */
  fun isPlayers(pokemon: String): Boolean = false

  /** A held item or ability named in a message ("{P}'s {X} restored a little HP!"), or null if it isn't one. */
  fun named(raw: String): String? = null

  companion object {
    /** No battle knowledge: species list only. */
    val NONE =
      object : BattleContext {
        override fun pokemon(raw: String) = Species.match(raw)

        override fun move(actor: String?, raw: String): String? = null
      }
  }
}

enum class MessageKind { MOVE, OUTCOME, OTHER }

/** A battle message matched to a known template, with its slots filled from what the app knows. */
data class BattleMessage(
  val kind: MessageKind,
  /** The clean sentence to show, e.g. "The wild Caterpie's Attack fell!". */
  val text: String,
  val side: Side = Side.OTHER,
  val actor: String? = null,
  val move: String? = null,
  /** For [MessageKind.OUTCOME]: "super effective", "critical hit", "missed", ... */
  val outcome: String? = null,
)

/**
 * Matches OCR'd battle messages to the fixed set of messages battles use, so a garbled read like
 * "Pikaclnu uzed tackie!" is shown as "Pikachu used Tackle!".
 *
 * Each template is a word sequence with slots ({P} Pokémon, {M} move, {S} stat, {N} number, {X} free text). A read is
 * aligned to every template word by word (misspelled, extra or missing words cost a little each); the closest
 * template wins if it's close enough, and its slots are filled from [BattleContext].
 */
object BattleMessages {
  private const val SENT_OUT = "{X} sent out {P}!"

  private class Template(val pattern: String, val kind: MessageKind, val outcome: String? = null) {
    val tokens: List<String> = tokenize(pattern)
    val literals = tokens.count { !it.startsWith("{") }
    /** Letters only, for fixed messages (no slots): compared letter by letter when OCR splits or merges words. */
    val letters: String? = if (tokens.none { it.startsWith("{") }) letters(pattern) else null
  }

  /** Messages that start with a Pokémon get "The wild …" and "The foe's …" variants (opponent side). */
  private val BASE =
    listOf(
      "{P} used {M}!" to MessageKind.MOVE,
      "{P}'s {S} rose!" to MessageKind.OTHER,
      "{P}'s {S} rose sharply!" to MessageKind.OTHER,
      "{P}'s {S} rose drastically!" to MessageKind.OTHER,
      "{P}'s {S} fell!" to MessageKind.OTHER,
      "{P}'s {S} harshly fell!" to MessageKind.OTHER,
      "{P}'s {S} severely fell!" to MessageKind.OTHER,
      "{P}'s {S} won't go any higher!" to MessageKind.OTHER,
      "{P}'s {S} won't go any lower!" to MessageKind.OTHER,
      "{P}'s attack missed!" to MessageKind.OUTCOME,
      "{P} avoided the attack!" to MessageKind.OUTCOME,
      "{P} fainted!" to MessageKind.OTHER,
      "{P} was hurt by poison!" to MessageKind.OTHER,
      "{P} was hurt by its burn!" to MessageKind.OTHER,
      "{P} was poisoned!" to MessageKind.OTHER,
      "{P} was badly poisoned!" to MessageKind.OTHER,
      "{P} was burned!" to MessageKind.OTHER,
      "{P} is paralyzed! It may be unable to move!" to MessageKind.OTHER,
      "{P} is paralyzed! It can't move!" to MessageKind.OTHER,
      "{P} fell asleep!" to MessageKind.OTHER,
      "{P} is fast asleep." to MessageKind.OTHER,
      "{P} woke up!" to MessageKind.OTHER,
      "{P} was frozen solid!" to MessageKind.OTHER,
      "{P} is frozen solid!" to MessageKind.OTHER,
      "{P} thawed out!" to MessageKind.OTHER,
      "{P} became confused!" to MessageKind.OTHER,
      "{P} is confused!" to MessageKind.OTHER,
      "{P} snapped out of its confusion!" to MessageKind.OTHER,
      "{P} flinched!" to MessageKind.OTHER,
      "{P} is damaged by recoil!" to MessageKind.OTHER,
      "{P} restored its health!" to MessageKind.OTHER,
      "{P} protected itself!" to MessageKind.OTHER,
      "{P} must recharge!" to MessageKind.OTHER,
      "{P} hurt itself in its confusion!" to MessageKind.OTHER,
      "{P} regained health!" to MessageKind.OTHER,
      "{P} is buffeted by the sandstorm!" to MessageKind.OTHER,
      "{P} is buffeted by the hail!" to MessageKind.OTHER,
      "{P} endured the hit!" to MessageKind.OTHER,
      "{P} hung on using its {X}!" to MessageKind.OTHER,
      "{P} ate its {X}!" to MessageKind.OTHER,
      "{P} restored a little HP using its {X}!" to MessageKind.OTHER,
      "{P}'s {X} restored a little HP!" to MessageKind.OTHER,
      "{P}'s {X} cuts {P}'s attack!" to MessageKind.OTHER,
      "{P}'s {X} prevents its {S} from being lowered!" to MessageKind.OTHER,
      "{P} is tightening its focus!" to MessageKind.OTHER,
      "{P} can't move!" to MessageKind.OTHER,
    )

  private val TEMPLATES: List<Template> =
    BASE.flatMap { (p, kind) ->
      val outcome =
        when {
          "missed" in p || "avoided" in p -> "missed"
          else -> null
        }
      listOf(p, "The wild $p", "The foe's $p").map { Template(it, kind, outcome) }
    } +
      listOf(
        Template("It's super effective!", MessageKind.OUTCOME, "super effective"),
        Template("It's not very effective...", MessageKind.OUTCOME, "not very effective"),
        Template("It doesn't affect {P}...", MessageKind.OUTCOME, "no effect"),
        Template("It doesn't affect the wild {P}...", MessageKind.OUTCOME, "no effect"),
        Template("A critical hit!", MessageKind.OUTCOME, "critical hit"),
        Template("But it failed!", MessageKind.OUTCOME, "failed"),
        Template("A wild {P} appeared!", MessageKind.OTHER),
        Template("A wild horde appeared!", MessageKind.OTHER),
        Template("You're in charge, {P}!", MessageKind.OTHER),
        Template("Go! {P}!", MessageKind.OTHER),
        Template("{P}, switch out! Come back!", MessageKind.OTHER),
        Template(SENT_OUT, MessageKind.OTHER),
        Template("{P} gained {N} Exp. Points!", MessageKind.OTHER),
        Template("{P} grew to level {N}!", MessageKind.OTHER),
        Template("{P} learned {M}!", MessageKind.OTHER),
        Template("Got away safely!", MessageKind.OTHER),
        // Weather and screens
        Template("It started to rain!", MessageKind.OTHER),
        Template("Rain continues to fall.", MessageKind.OTHER),
        Template("It is raining.", MessageKind.OTHER),
        Template("The rain stopped.", MessageKind.OTHER),
        Template("The sunlight turned harsh!", MessageKind.OTHER),
        Template("The sunlight is strong.", MessageKind.OTHER),
        Template("The sunlight faded.", MessageKind.OTHER),
        Template("A sandstorm kicked up!", MessageKind.OTHER),
        Template("The sandstorm rages.", MessageKind.OTHER),
        Template("The sandstorm subsided.", MessageKind.OTHER),
        Template("It started to hail!", MessageKind.OTHER),
        Template("Hail continues to fall.", MessageKind.OTHER),
        Template("The hail stopped.", MessageKind.OTHER),
        Template("Reflect raised your team's Defense!", MessageKind.OTHER),
        Template("Reflect raised the foe's team's Defense!", MessageKind.OTHER),
        Template("Light Screen raised your team's Special Defense!", MessageKind.OTHER),
        Template("Light Screen raised the foe's team's Special Defense!", MessageKind.OTHER),
        Template("Your team's Reflect wore off!", MessageKind.OTHER),
        Template("The foe's team's Reflect wore off!", MessageKind.OTHER),
        Template("Your team's Light Screen wore off!", MessageKind.OTHER),
        Template("The foe's team's Light Screen wore off!", MessageKind.OTHER),
        Template("But nothing happened!", MessageKind.OTHER),
        Template("There's no PP left for this move!", MessageKind.OTHER),
        Template("You can't escape!", MessageKind.OTHER),
        Template("Gotcha! {P} was caught!", MessageKind.OTHER),
        Template("Oh, no! The Pokémon broke free!", MessageKind.OTHER),
        Template("{P}'s data was added to the Pokédex.", MessageKind.OTHER),
        Template("{P} was transferred to {X} in someone's PC!", MessageKind.OTHER),
      )

  private val STATS = listOf("Attack", "Defense", "Speed", "Sp. Atk", "Sp. Def", "Special Attack", "Special Defense", "accuracy", "evasiveness")

  /** Most words a slot can cover. */
  private fun maxWords(slot: String) =
    when (slot) {
      "{P}" -> 3
      "{M}" -> 3
      "{S}" -> 2
      "{N}" -> 1
      else -> 4
    }

  /** Average per-literal-word mismatch (0 = exact) above which a read isn't trusted as that template. */
  private const val MAX_COST = 0.34

  /** Letter-level mismatch allowed for fixed messages ("It's super affect. i ye!" is 2 of 17 letters off). */
  private const val MAX_LETTER_COST = 0.3

  private fun letters(s: String) = s.lowercase().filter { it in 'a'..'z' }

  /** Words, with a possessive "'s" split off ("Caterpie's" → "Caterpie", "'s"), punctuation dropped. */
  internal fun tokenize(text: String): List<String> =
    text.replace(Regex("""(?i)(\S)'s\b"""), "$1 's")
      .split(Regex("\\s+"))
      .map { it.trim { c -> !c.isLetterOrDigit() && c != '\'' && c != '{' && c != '}' } }
      .filter { it.isNotEmpty() && it != "'" }

  private fun norm(w: String) = w.lowercase().filter { it.isLetterOrDigit() || it == '\'' }

  private fun wordCost(a: String, b: String): Double {
    val x = norm(a)
    val y = norm(b)
    if (x == y) return 0.0
    return (EncounterParser.editDistance(x, y).toDouble() / maxOf(x.length, y.length)).coerceAtMost(1.0)
  }

  /** The best-matching template for an OCR read, or null if nothing is close enough. */
  fun match(text: String, context: BattleContext = BattleContext.NONE): BattleMessage? {
    val words = tokenize(text)
    if (words.isEmpty()) return null
    var best: Pair<Template, List<List<String>>>? = null
    var bestCost = Double.MAX_VALUE
    val textLetters = letters(text)
    for (t in TEMPLATES) {
      // Fixed messages are also compared letter by letter: OCR often splits or merges words in them.
      t.letters?.let { tl ->
        val c = EncounterParser.editDistance(textLetters, tl).toDouble() / tl.length.coerceAtLeast(1)
        if (c <= MAX_LETTER_COST && c < bestCost - 1e-9) {
          bestCost = c
          best = t to emptyList()
        }
      }
      val (cost, slots) = align(t, words) ?: continue
      val normalized = cost / t.literals.coerceAtLeast(1)
      // On a tie, the template with more fixed words wins ("The wild {P} used {M}" over "{P} used {M}").
      if (normalized < bestCost - 1e-9 || (normalized < bestCost + 1e-9 && t.literals > (best?.first?.literals ?: -1))) {
        bestCost = normalized
        best = t to slots
      }
    }
    val (t, slots) = best ?: return null
    if (bestCost > MAX_COST) return null
    return fill(t, slots, context)
  }

  /**
   * Word alignment (edit-distance style): a literal matches one word (cost = its spelling difference), a slot takes
   * 1..n words for free, and a missing or extra word costs 1. Returns the cost and each slot's words, in order.
   */
  private fun align(t: Template, words: List<String>): Pair<Double, List<List<String>>>? {
    val n = t.tokens.size
    val m = words.size
    val inf = Double.MAX_VALUE / 4
    val dp = Array(n + 1) { DoubleArray(m + 1) { inf } }
    val back = Array(n + 1) { IntArray(m + 1) { -1 } } // previous j
    dp[0][0] = 0.0
    for (j in 1..m) {
      dp[0][j] = j.toDouble()
      back[0][j] = j - 1
    }
    for (i in 1..n) {
      val tok = t.tokens[i - 1]
      val slot = tok.startsWith("{")
      for (j in 0..m) {
        if (slot) {
          for (k in 1..minOf(maxWords(tok), j)) {
            val c = dp[i - 1][j - k]
            if (c < dp[i][j]) {
              dp[i][j] = c
              back[i][j] = j - k
            }
          }
        } else {
          // literal skipped (missing in the read)
          if (dp[i - 1][j] + 1 < dp[i][j]) {
            dp[i][j] = dp[i - 1][j] + 1
            back[i][j] = j
          }
          if (j > 0) {
            val c = dp[i - 1][j - 1] + wordCost(tok, words[j - 1])
            if (c < dp[i][j]) {
              dp[i][j] = c
              back[i][j] = j - 1
            }
          }
          // OCR split one word in two ("wi ld" for "wild")
          if (j > 1) {
            val c = dp[i - 1][j - 2] + wordCost(tok, words[j - 2] + words[j - 1])
            if (c < dp[i][j]) {
              dp[i][j] = c
              back[i][j] = j - 2
            }
          }
        }
        // extra word in the read (OCR noise) after this token
        if (j > 0 && dp[i][j - 1] + 1 < dp[i][j]) {
          dp[i][j] = dp[i][j - 1] + 1
          back[i][j] = -(j - 1) - 2 // marks "skip word, stay on token i"
        }
      }
    }
    if (dp[n][m] >= inf) return null
    // Walk back to collect slot words.
    val slots = ArrayDeque<List<String>>()
    var i = n
    var j = m
    while (i > 0) {
      val b = back[i][j]
      if (b <= -2) {
        j = -(b + 2)
        continue
      }
      val tok = t.tokens[i - 1]
      if (tok.startsWith("{")) slots.addFirst(words.subList(b, j))
      j = b
      i--
    }
    return dp[n][m] to slots.toList()
  }

  /** Rebuilds the message from known values only; null when any slot isn't something known (the read is skipped). */
  private fun fill(t: Template, slots: List<List<String>>, context: BattleContext): BattleMessage? {
    var slot = 0
    var actor: String? = null
    var move: String? = null
    val out = StringBuilder()
    // Rebuild the sentence from the template, replacing slots with resolved values.
    val parts = Regex("""\{[PMSNX]\}""").split(t.pattern)
    val names = Regex("""\{[PMSNX]\}""").findAll(t.pattern).map { it.value }.toList()
    for ((k, literal) in parts.withIndex()) {
      out.append(literal)
      if (k < names.size) {
        val raw = slots.getOrNull(slot++)?.joinToString(" ").orEmpty()
        val value =
          when (names[k]) {
            "{P}" -> (context.pokemon(raw) ?: return null).also { if (actor == null) actor = it }
            "{M}" -> (context.move(actor, raw) ?: return null).also { move = it }
            "{S}" -> STATS.minByOrNull { wordCost(it.replace(" ", ""), raw.replace(" ", "")) } ?: return null
            "{N}" -> Regex("""\d+""").find(raw.replace('l', '1').replace('O', '0'))?.value ?: return null
            // The trainer in "{X} sent out {P}!" is never shown; other {X} are items or abilities.
            else -> if (t.pattern == SENT_OUT) "" else context.named(raw) ?: return null
          }
        out.append(value)
      }
    }
    val foe = t.pattern.startsWith("The wild") || t.pattern.startsWith("The foe's")
    // Trainer names are never shown (OCR can't be trusted with them): "Player sent out ..." / "Opponent sent out ...".
    val text =
      if (t.pattern == SENT_OUT && actor != null) "${if (context.isPlayers(actor!!)) "Player" else "Opponent"} sent out $actor!"
      else out.toString()
    return BattleMessage(
      kind = t.kind,
      text = text,
      side = if (t.kind == MessageKind.MOVE) (if (foe) Side.FOE else Side.YOU) else Side.OTHER,
      actor = actor,
      move = move,
      outcome = t.outcome,
    )
  }
}
