package com.pokemmocompanion.app.calc

/** What the app can know about a wild opponent from its name and level alone, assuming the worst case. */
data class OpponentProfile(
  val species: SpeciesData,
  val level: Int,
  /** 31 IVs, 0 EVs, nature boosting that stat. */
  val maxStats: Map<Stat, Int>,
  /** Attacking types that hit it for more than 1×, strongest first. */
  val weaknesses: List<Pair<PokeType, Double>>,
  /** Attacking types that hit it for less than 1× (0 = immune), weakest first. */
  val resistances: List<Pair<PokeType, Double>>,
  /** Every level-up move it may know by now; damaging ones first, strongest first. */
  val possibleMoves: List<MoveData>,
  /** Speed range narrowed by turn order in this battle (null = unknown). */
  val speedRange: IntRange? = null,
  /** Ability it revealed in battle (null = unknown: assume the worst). */
  val knownAbility: String? = null,
) {
  companion object {
    fun build(data: GameData, speciesName: String, level: Int): OpponentProfile? {
      val species = data.species(speciesName) ?: return null
      val maxStats = Stat.entries.associateWith { Stats.max(species, it, level) }
      val matchups = PokeType.entries.map { it to TypeChart.effectiveness(it, species.types) }
      val moves =
        WildMoves.possible(species, level)
          .mapNotNull(data::move)
          .sortedWith(compareByDescending<MoveData> { it.isDamaging }.thenByDescending { it.power })
      return OpponentProfile(
        species = species,
        level = level,
        maxStats = maxStats,
        weaknesses = matchups.filter { it.second > 1.0 }.sortedByDescending { it.second },
        resistances = matchups.filter { it.second < 1.0 }.sortedBy { it.second },
        possibleMoves = moves,
      )
    }
  }
}
