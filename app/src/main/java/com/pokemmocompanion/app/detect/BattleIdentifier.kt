package com.pokemmocompanion.app.detect

/**
 * A finished battle read. [wild] is false only when trainer text was seen ("… would like to battle!"): a garbled
 * intro, or one hidden by a popup, must not cost a Pokédex or shiny alert.
 */
data class BattleResult(val mons: List<WildMon>, val wild: Boolean)

/**
 * Decides when to OCR during a battle and what the battle was.
 *
 * Intro: PokeMMO shows "A wild X appeared!" (or "A horde of wild X appeared!") right after the party rail
 * disappears, before the HP boxes are up, so the intro text is read from the moment the rail is gone.
 * Trainer battles never say "wild".
 *
 * Names: after the battle is confirmed, names are read on every sampled frame until they show up, then once
 * more (horde boxes can finish appearing a moment later); the read with more Pokémon wins.
 */
class BattleIdentifier(private val maxNameReads: Int = 20, private val maxIntroReads: Int = 12) {
  /** Between the tracker confirming BATTLE and confirming OVERWORLD again. */
  private var onBattleScreen = false
  /** Battle confirmed and names not decided yet. */
  private var battleActive = false
  private var nameReads = 0
  private var nameSuccesses = 0
  private var best: List<WildMon>? = null

  private var introWatching = false
  private var introReads = 0
  private var wildSeen = false
  private var trainerSeen = false

  /** True while the current battle still needs a name read. */
  val needsNameRead: Boolean
    get() = battleActive

  /** True while the intro text might still be on screen and hasn't said "wild" yet. */
  val needsIntroRead: Boolean
    get() = introWatching && !wildSeen && introReads < maxIntroReads

  /**
   * Call once per sampled frame, before any reads.
   * @param railVisible whether the overworld party rail is on screen
   * @param changed the tracker's state change on this frame, if any
   * @return a finished battle that ended before its names were confirmed, if any
   */
  fun onFrame(railVisible: Boolean, changed: ScreenState?): BattleResult? {
    var result: BattleResult? = null
    when (changed) {
      ScreenState.BATTLE -> {
        if (!introWatching) {
          // Intro never watched (e.g. capture started mid-battle): nothing known either way.
          wildSeen = false
          trainerSeen = false
        }
        onBattleScreen = true
        battleActive = true
        nameReads = 0
        nameSuccesses = 0
        best = null
      }
      ScreenState.OVERWORLD -> {
        result = if (battleActive) finish() else null
        onBattleScreen = false
        introWatching = false
      }
      else -> {}
    }
    if (!railVisible && !introWatching && !onBattleScreen) {
      introWatching = true
      introReads = 0
      wildSeen = false
      trainerSeen = false
    } else if (railVisible && !onBattleScreen) {
      introWatching = false // rail back without a battle: it was a menu or a blip
    }
    return result
  }

  fun onIntroRead(lines: List<String>) {
    if (!needsIntroRead) return
    introReads++
    if (IntroText.isWild(lines)) wildSeen = true
    if (IntroText.isTrainer(lines)) trainerSeen = true
  }

  /** Feeds one name read. Returns the battle once it's decided, otherwise null. */
  fun onNameRead(mons: List<WildMon>): BattleResult? {
    if (!battleActive) return null
    nameReads++
    if (mons.isNotEmpty()) {
      nameSuccesses++
      if (score(mons) > (best?.let(::score) ?: -1)) best = mons
    }
    return if (nameSuccesses >= 2 || nameReads >= maxNameReads) finish() else null
  }

  /** More Pokémon read wins (horde boxes appear over a moment); then more names that are real species. */
  private fun score(mons: List<WildMon>) = mons.size * 10 + mons.count { Species.match(it.name) == it.name }

  private fun finish(): BattleResult? {
    battleActive = false
    introWatching = false
    return best?.let { BattleResult(it, wild = wildSeen || !trainerSeen) }
  }
}

/**
 * Recognizes the battle intros: wild ("A wild X appeared!" / "A horde of wild X appeared!") and trainer ("Youngster
 * Joey would like to battle!", "… wants to battle!", "You are challenged by …"), allowing OCR slips.
 */
object IntroText {
  fun isTrainer(lines: List<String>): Boolean =
    lines.any { line ->
      val words = words(line)
      val battle = words.any { it.length >= 5 && EncounterParser.editDistance(it, "battle") <= 1 }
      val like = words.any { EncounterParser.editDistance(it, "like") <= 1 || EncounterParser.editDistance(it, "wants") <= 1 }
      (battle && like) || words.any { it.length >= 9 && EncounterParser.editDistance(it, "challenged") <= 2 }
    }

  private fun words(line: String) = line.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }

  fun isWild(lines: List<String>): Boolean =
    lines.any { line ->
      val words = words(line)
      words.any { it.length == 4 && EncounterParser.editDistance(it, "wild") <= 1 } &&
        words.any { EncounterParser.editDistance(it, "appeared") <= 2 }
    }
}
