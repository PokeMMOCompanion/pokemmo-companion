package com.pokemmocompanion.app.calc

import com.pokemmocompanion.app.party.PartyMember

/** One of the player's moves against the opponent. Percentages are of the opponent's worst-case (max) HP. */
data class MoveCalc(
  val move: MoveData,
  /** HP damage range (one hit) and the HP it's measured against. */
  val damage: DamageRange,
  val targetHp: Int,
  val percent: PercentRange,
  val critMaxPercent: Double,
  val label: String,
  /** Hits every foe in a horde (with the 0.75 spread penalty included). */
  val spread: Boolean,
  /**
   * Possible opponent abilities that would change this (its ability isn't known yet), e.g. "if Levitate: no effect",
   * "if Sturdy: survives one hit at 1 HP". The numbers above assume none of them.
   */
  val abilityNotes: List<String> = emptyList(),
)

/**
 * One of the opponent's possible moves against the player's Pokémon. Percentages are of its total HP (the usual
 * way calcs show damage); whether it can KO is judged against its current HP.
 */
data class Threat(
  val move: MoveData,
  val maxDamage: Int,
  val critMaxDamage: Int,
  val maxPercent: Double,
  val critMaxPercent: Double,
  val currentHp: Int,
  /** Possible abilities that would make it hit harder, e.g. "if Hustle: up to 62% (could KO)". */
  val abilityNotes: List<String> = emptyList(),
) {
  val canKo: Boolean
    get() = maxDamage >= currentHp
}

data class SwitchSuggestion(val member: PartyMember, val takesMaxPercent: Double, val dealsPercent: PercentRange, val move: MoveData)

enum class MoveOrder { YOU_FIRST, DEPENDS, IT_FIRST }

/** Who moves first: the player's speed against the opponent's possible range (0-31 IVs, any nature). */
data class SpeedVerdict(val order: MoveOrder, val yours: Int, val itsMin: Int, val itsMax: Int) {
  val headline: String
    get() =
      when (order) {
        MoveOrder.YOU_FIRST -> "You move first"
        MoveOrder.DEPENDS -> "It might move first"
        MoveOrder.IT_FIRST -> "It moves first"
      }

  val detail: String
    get() = "Your Spe $yours · its $itsMin–$itsMax"
}

data class Advice(
  val yourMoves: List<MoveCalc>,
  val statusMoves: List<MoveData>,
  val threats: List<Threat>,
  val speed: SpeedVerdict,
  val switchTo: SwitchSuggestion?,
)

/**
 * Turns the player's party data and a worst-case opponent into battle advice.
 * Worst case for the player throughout: the opponent has 31 IVs, a nature boosting whichever stat matters,
 * and whichever of its possible abilities is worst for the player.
 */
/**
 * The battle's conditions as the app has read them: weather, screens per side and major statuses ("BRN", ...).
 * [forYou] / [forIt] give the [Field] for your hits on it and its hits on you.
 */
data class FieldState(
  val weather: Weather? = null,
  val yourScreens: Set<String> = emptySet(),
  val itsScreens: Set<String> = emptySet(),
  val yourStatus: String? = null,
  val itsStatus: String? = null,
) {
  fun forYou() = Field(weather, reflect = "Reflect" in itsScreens, lightScreen = "Light Screen" in itsScreens, burned = yourStatus == "BRN")

  fun forIt() = Field(weather, reflect = "Reflect" in yourScreens, lightScreen = "Light Screen" in yourScreens, burned = itsStatus == "BRN")

  val isEmpty: Boolean
    get() = weather == null && yourScreens.isEmpty() && itsScreens.isEmpty()

  companion object {
    val NONE = FieldState()
  }
}

object BattleAdvisor {
  /** The stored party member as a battler; null until its species and stats are known. */
  fun battler(data: GameData, m: PartyMember, currentHp: Int? = null, stages: Map<Stat, Int> = emptyMap()): Battler? {
    val s = m.stats ?: return null
    val species = m.species?.let(data::species) ?: return null
    return Battler(
      name = m.realNickname ?: species.name,
      types = species.types,
      level = m.level ?: return null,
      hp = currentHp ?: m.currentHp ?: s[0],
      maxHp = s[0],
      atk = s[1],
      def = s[2],
      spa = s[3],
      spd = s[4],
      spe = s[5],
      ability = m.ability,
      item = m.item?.takeUnless { it.equals("None", ignoreCase = true) },
      stages = stages,
    )
  }

  fun opponentBattler(p: OpponentProfile, ability: String?, stages: Map<Stat, Int> = emptyMap()): Battler {
    val st = p.maxStats
    return Battler(
      name = p.species.name,
      types = p.species.types,
      level = p.level,
      hp = st.getValue(Stat.HP),
      maxHp = st.getValue(Stat.HP),
      atk = st.getValue(Stat.ATK),
      def = st.getValue(Stat.DEF),
      spa = st.getValue(Stat.SPA),
      spd = st.getValue(Stat.SPD),
      spe = st.getValue(Stat.SPE),
      ability = ability,
      stages = stages,
    )
  }

  /** Its possible abilities ("--" = empty slot in the Hub data), unless battle revealed which one it has. */
  private fun possibleAbilities(p: OpponentProfile): List<String> =
    if (p.knownAbility != null) emptyList() else p.species.abilities.filter { it != "--" }.distinct()

  private fun pctText(v: Double) = "${v.toInt()}%"

  /**
   * Player's move vs opponent. The numbers assume its ability doesn't matter (or the ability it revealed); each
   * possible ability that would change things is listed as a note instead of being assumed.
   */
  fun moveCalc(
    data: GameData,
    you: Battler,
    p: OpponentProfile,
    move: MoveData,
    horde: Boolean,
    oppStages: Map<Stat, Int> = emptyMap(),
    field: Field = Field.NONE,
  ): MoveCalc? {
    val spread = horde && move.hitsMultipleFoes
    val foe = opponentBattler(p, p.knownAbility, oppStages)
    val r = Damage.range(you, foe, move, spread = spread, field = field) ?: return null
    val crit = Damage.range(you, foe, move, crit = true, spread = spread, field = field)!!
    val hp = p.maxStats.getValue(Stat.HP)
    val notes = mutableListOf<String>()
    if (p.knownAbility == "Sturdy" && r.max >= hp) notes += "Sturdy: survives one hit at 1 HP from full"
    for (a in possibleAbilities(p)) {
      if (a == "Sturdy") {
        if (r.max >= hp) notes += "if Sturdy: survives one hit at 1 HP from full"
        continue
      }
      val ra = Damage.range(you, opponentBattler(p, a, oppStages), move, spread = spread, field = field) ?: continue
      if (ra == r) continue
      notes += if (ra.max == 0) "if $a: no effect" else "if $a: ${pctText(ra.min * 100.0 / hp)}–${pctText(ra.max * 100.0 / hp)}"
    }
    return MoveCalc(move, r, hp, r.percentOf(hp), crit.max * 100.0 / hp, Damage.koLabel(r, hp), spread, notes)
  }

  /** Opponent's move vs a player battler: numbers without an ability effect, possible abilities as notes. */
  fun threat(you: Battler, p: OpponentProfile, move: MoveData, oppStages: Map<Stat, Int> = emptyMap(), field: Field = Field.NONE): Threat? {
    val foe = opponentBattler(p, p.knownAbility, oppStages)
    val r = Damage.range(foe, you, move, field = field) ?: return null
    val crit = Damage.range(foe, you, move, crit = true, field = field)!!
    val total = you.maxHp.coerceAtLeast(1)
    val notes =
      possibleAbilities(p).mapNotNull { a ->
        val ra = Damage.range(opponentBattler(p, a, oppStages), you, move, field = field) ?: return@mapNotNull null
        if (ra.max <= r.max) return@mapNotNull null
        "if $a: up to ${pctText(ra.max * 100.0 / total)}" + if (ra.max >= you.hp && r.max < you.hp) " (could KO)" else ""
      }
    return Threat(move, r.max, crit.max, r.max * 100.0 / total, crit.max * 100.0 / total, you.hp, notes)
  }

  fun advise(
    data: GameData,
    active: PartyMember,
    activeHp: Int?,
    p: OpponentProfile,
    horde: Boolean,
    party: List<PartyMember>,
    yourStages: Map<Stat, Int> = emptyMap(),
    oppStages: Map<Stat, Int> = emptyMap(),
    field: FieldState = FieldState.NONE,
  ): Advice? {
    val you = battler(data, active, activeHp, yourStages) ?: return null
    val moves = active.moves.mapNotNull(data::move)
    val calcs =
      moves.mapNotNull { moveCalc(data, you, p, it, horde, oppStages, field.forYou()) }
        .sortedWith(compareByDescending<MoveCalc> { it.percent.min }.thenByDescending { it.percent.max })
    val threats = p.possibleMoves.mapNotNull { threat(you, p, it, oppStages, field.forIt()) }.sortedByDescending { it.maxPercent }

    return Advice(
      yourMoves = calcs,
      statusMoves = moves.filter { !it.isDamaging },
      threats = threats,
      speed = speed(you, p, oppStages),
      // A switched-in Pokémon starts with clean stages; the opponent keeps its own.
      switchTo = if (threats.firstOrNull()?.canKo == true) bestSwitch(data, active, p, horde, party, oppStages, field) else null,
    )
  }

  private fun speed(you: Battler, p: OpponentProfile, oppStages: Map<Stat, Int>): SpeedVerdict {
    val base = p.species.base(Stat.SPE)
    val stage = oppStages[Stat.SPE] ?: 0
    val fastest = Stages.apply(p.speedRange?.last ?: p.maxStats.getValue(Stat.SPE), stage)
    val slowest = Stages.apply(p.speedRange?.first ?: Stats.value(Stat.SPE, base, 0, 0, p.level, Nature.BRAVE), stage) // 0 IVs, -Spe nature
    val yours = you.effectiveSpe
    val order =
      when {
        yours > fastest -> MoveOrder.YOU_FIRST
        yours < slowest -> MoveOrder.IT_FIRST
        else -> MoveOrder.DEPENDS
      }
    return SpeedVerdict(order, yours, slowest, fastest)
  }

  /** A party member that survives the opponent's worst move and hits back, if the active one can be KO'd. */
  private fun bestSwitch(
    data: GameData,
    active: PartyMember,
    p: OpponentProfile,
    horde: Boolean,
    party: List<PartyMember>,
    oppStages: Map<Stat, Int>,
    field: FieldState,
  ): SwitchSuggestion? =
    party
      .filter { it.slot != active.slot && it.isReady && (it.currentHp ?: 1) > 0 }
      .mapNotNull { m ->
        val b = battler(data, m) ?: return@mapNotNull null
        val worst = p.possibleMoves.mapNotNull { threat(b, p, it, oppStages, field.forIt()) }.maxByOrNull { it.maxDamage }
        if (worst?.canKo == true) return@mapNotNull null // it would go down too
        val takes = worst?.maxPercent ?: 0.0
        val best =
          m.moves.mapNotNull(data::move).mapNotNull { moveCalc(data, b, p, it, horde, oppStages, field.forYou()) }.maxByOrNull { it.percent.min }
            ?: return@mapNotNull null
        SwitchSuggestion(m, takes, best.percent, best.move)
      }
      .filter { it.dealsPercent.max > 0 }
      .maxByOrNull { it.dealsPercent.min - it.takesMaxPercent }
}
