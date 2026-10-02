package com.pokemmocompanion.app.calc

import com.pokemmocompanion.app.detect.BattleLog
import com.pokemmocompanion.app.detect.BattleMessages
import com.pokemmocompanion.app.detect.MessageKind
import com.pokemmocompanion.app.detect.Side
import com.pokemmocompanion.app.detect.WildMon
import com.pokemmocompanion.app.party.PartyMember
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Battle messages as read on the Thor (v0.32 log), turned into clean sentences using the party and opponent. */
class BattleKnowledgeTest {
  private val data =
    GameData.parse(
      File("src/main/assets/pokemmo/species.json").readText(),
      File("src/main/assets/pokemmo/moves.json").readText(),
    )
  private val party =
    listOf(
      PartyMember(slot = 0, nickname = "Charmander", species = "Charmander", level = 15, moves = listOf("DragonBreath", "Metal Claw", "Tackle", "Ember")),
      PartyMember(slot = 2, nickname = "Rattata", species = "Rattata", level = 4, moves = listOf("Tackle", "Tail Whip")),
      PartyMember(slot = 5, nickname = "Pikachu", species = "Pikachu", level = 5, moves = listOf("Charm", "ThunderShock", "Thunder Wave", "Growl")),
    )
  private val ctx = BattleKnowledge(data, party, listOf(WildMon("Caterpie", 5, false)))

  private fun clean(raw: String) = BattleMessages.match(raw, ctx)?.text

  @Test
  fun garbledReadsBecomeCleanSentences() {
    assertEquals("The wild Caterpie's Attack fell!", clean("The wild Caterpie's Attack fel1!"))
    assertEquals("The wild Caterpie used String Shot!", clean("The wild Caterpie Used String 5hot!"))
    assertEquals("Pikachu used Thunder Wave!", clean("Pikachu used Thurder laWe!"))
    assertEquals("Pikachu used Charm!", clean("Pikachu used CharM!"))
    assertEquals("The wild Caterpie is paralyzed! It may be unable to move!", clean("The wild Caterpie is paralyzed! It may be unable to mIOWe!"))
    assertEquals("Pikachu, switch out! Come back!", clean("Pikachu, switch out.! Come back!"))
    assertEquals("Charmander used Tackle!", clean("Charmander UEed Tackle!"))
    assertEquals("The wild Caterpie's Defense fell!", clean("The wild Caterpie Defense fell!"))
    assertEquals("Charmander gained 18 Exp. Points!", clean("Charmander gained 18 Exp. Points!"))
    assertEquals("You're in charge, Pikachu!", clean("Tou're in charge, Pikachu!"))
    assertEquals("Pikachu used Tackle!", clean("Pikaclnu uzed tackie!")) // the example from the chat
    assertEquals("The wild Caterpie used Tackle!", clean("The wi ld Caterpie used Tackle!"))
  }

  @Test
  fun kindsAndSides() {
    val crit = BattleMessages.match("A Critical it.!", ctx)!!
    assertEquals(MessageKind.OUTCOME, crit.kind)
    assertEquals("critical hit", crit.outcome)
    val foe = BattleMessages.match("The wild Caterpie Used String 5hot!", ctx)!!
    assertEquals(Side.FOE, foe.side)
    assertEquals("String Shot", foe.move)
    assertEquals(Side.YOU, BattleMessages.match("Pikachu used CharM!", ctx)!!.side)
    assertNull(BattleMessages.match("FIGHT POKEMON BAG RUN", ctx))
  }

  @Test
  fun historyFromTheLog() {
    val log = BattleLog(ctx)
    for (r in listOf(
      "Pikachu used Growl!", "The wild Caterpie's Attack fel1!", "", "",
      "Pikachu used Thurder laWe!", "A Critical it.!", "", "",
      "The wild Caterpie Used String 5hot!", "The wild Caterpie used String Shot!", "Pikachu's Speed fell!", "", "",
      "Pikachu, switch out.! Come back!", "You're in charge, Charmander!", "Charmander UEed Tackle!",
      "The wild Caterpie fainted!",
    )) log.onText(if (r.isEmpty()) emptyList() else listOf(r))
    assertEquals(
      listOf(
        "Pikachu used Growl!", "The wild Caterpie's Attack fell!", "Pikachu used Thunder Wave!",
        "The wild Caterpie used String Shot!", "Pikachu's Speed fell!", "Pikachu, switch out! Come back!",
        "You're in charge, Charmander!", "Charmander used Tackle!", "The wild Caterpie fainted!",
      ),
      log.events.map { it.text },
    )
    assertEquals(listOf("critical hit"), log.events.first { it.move == "Thunder Wave" }.outcomes)
  }
}
