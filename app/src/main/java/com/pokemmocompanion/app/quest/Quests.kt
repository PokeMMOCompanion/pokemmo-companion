package com.pokemmocompanion.app.quest

import com.pokemmocompanion.app.calc.GameData
import com.pokemmocompanion.app.calc.PokeType
import com.pokemmocompanion.app.calc.TypeChart
import com.pokemmocompanion.app.party.PartyMember
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A to-do the player wrote themselves ("Catch Abra", "Grind Speed EVs"). */
@Serializable data class Task(val id: Long, val text: String, val done: Boolean = false)

@Serializable
data class QuestState(
  val region: String = "Kanto",
  /** Checked [Milestone.id]s, across all regions. */
  val checked: Set<String> = emptySet(),
  val tasks: List<Task> = emptyList(),
) {
  /** The first unchecked gym or League battle in [region], or null when it's all done. */
  fun next(region: Region = Story.region(this.region)): Milestone? =
    region.milestones.firstOrNull { it.kind != MilestoneKind.HM && it.id !in checked }

  fun badges(region: Region = Story.region(this.region)): Int =
    region.milestones.count { it.kind == MilestoneKind.GYM && it.id in checked }
}

/** Quest progress saved as JSON (files/quests.json). Every change is written straight away. */
class QuestStore(private val file: File) {
  private val json = Json { ignoreUnknownKeys = true }

  var state: QuestState =
    runCatching { json.decodeFromString<QuestState>(file.readText()) }.getOrDefault(QuestState())
    private set

  fun update(change: (QuestState) -> QuestState): QuestState {
    state = change(state)
    file.writeText(json.encodeToString(QuestState.serializer(), state))
    return state
  }

  fun toggle(id: String) = update { s -> s.copy(checked = if (id in s.checked) s.checked - id else s.checked + id) }

  fun setRegion(name: String) = update { it.copy(region = name) }

  fun addTask(text: String) =
    update { s ->
      val t = text.trim()
      if (t.isEmpty()) s else s.copy(tasks = s.tasks + Task((s.tasks.maxOfOrNull { it.id } ?: 0) + 1, t))
    }

  fun toggleTask(id: Long) = update { s -> s.copy(tasks = s.tasks.map { if (it.id == id) it.copy(done = !it.done) else it }) }

  fun removeTask(id: Long) = update { s -> s.copy(tasks = s.tasks.filterNot { it.id == id }) }

  fun clearDone() = update { s -> s.copy(tasks = s.tasks.filterNot { it.done }) }
}

/**
 * How the party lines up against a gym's type: who has a super-effective move (and which), and who takes
 * super-effective hits from that type.
 */
data class Matchup(val strong: List<Pair<String, String>>, val weak: List<String>)

object QuestMatchup {
  fun against(type: PokeType, party: List<PartyMember>, data: GameData): Matchup {
    val strong = mutableListOf<Pair<String, String>>()
    val weak = mutableListOf<String>()
    for (m in party) {
      val species = m.dexId?.let(data::species) ?: m.species?.let(data::species) ?: continue
      val name = m.realNickname ?: species.name
      val best =
        m.moves
          .mapNotNull(data::move)
          .filter { it.isDamaging }
          .map { it to TypeChart.effectiveness(it.type, listOf(type)) }
          .filter { it.second >= 2.0 }
          .maxByOrNull { it.second * it.first.power }
      if (best != null) strong += name to best.first.name
      if (TypeChart.effectiveness(type, species.types) >= 2.0) weak += name
    }
    return Matchup(strong, weak)
  }
}
