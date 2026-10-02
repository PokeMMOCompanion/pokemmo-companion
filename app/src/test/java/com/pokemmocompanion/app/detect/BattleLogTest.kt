package com.pokemmocompanion.app.detect

import com.pokemmocompanion.app.calc.GameData
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BattleLogTest {
  /** Feeds each message the way the app sees it: several identical reads, with menu/blank reads in between. */
  private fun BattleLog.play(vararg messages: String) {
    for (m in messages) {
      onText(listOf(m))
      onText(listOf(m))
      onText(listOf("FIGHT", "POKEMON", "BAG", "RUN")) // move menu between turns
      onText(emptyList())
    }
  }

  /** Real species and move lists: under the strict rule, a message shows only when every name in it is known. */
  private val gameData by lazy {
    GameData.parse(File("src/main/assets/pokemmo/species.json").readText(), File("src/main/assets/pokemmo/moves.json").readText())
  }

  /** Species and move lists, with Charmander in the player's party. */
  private val withCharmander =
    object : BattleContext {
      override fun pokemon(raw: String) = Species.match(raw)

      override fun move(actor: String?, raw: String): String? = gameData.matchMove(raw)?.name

      override fun isPlayers(pokemon: String) = pokemon == "Charmander"
    }

  @Test
  fun recordsMovesWithOutcomesInOrder() {
    val log = BattleLog(withCharmander)
    log.play(
      "Rowanl sent out Charmander!",
      "Clamperl Liam sent out Pidgey!",
      "Charmander used Ember!",
      "It's super effective!",
      "A critical hit!",
      "The wild Pidgey used Tackle!",
      "Charmander used Ember!",
      "The wild Pidgey fainted!",
      "Charmander gained 32 Exp. Points!",
    )
    val moves = log.events.filter { it.move != null }
    assertEquals(listOf("Ember", "Tackle", "Ember"), moves.map { it.move })
    assertEquals(listOf(Side.YOU, Side.FOE, Side.YOU), moves.map { it.side })
    assertEquals(listOf("super effective", "critical hit"), moves[0].outcomes)
    assertEquals("Pidgey", moves[1].actor)
    assertEquals(
      // Trainer names are never shown: OCR garbles them ("Rowanl" for "RowanI").
      listOf("Player sent out Charmander!", "Opponent sent out Pidgey!", "The wild Pidgey fainted!", "Charmander gained 32 Exp. Points!"),
      log.events.filter { it.move == null }.map { it.text },
    )
  }

  @Test
  fun missesAndTrainers() {
    val log = BattleLog(movesOnly(gameData))
    log.play("Youngster Joey's Rattata used Tackle!", "Rattata's attack missed!", "Charmander used Scratch!")
    val tackle = log.events.first()
    assertEquals(Side.FOE, tackle.side)
    assertEquals("Rattata", tackle.actor)
    assertEquals(listOf("missed"), tackle.outcomes)
    assertEquals(Side.YOU, log.events.last().side)
  }

  @Test
  fun messageStayingUpIsRecordedOnce() {
    val log = BattleLog()
    repeat(6) { log.onText(listOf("Charmander used Ember!")) }
    assertEquals(1, log.events.size)
    // Typing out: the longer read replaces the shorter one.
    val typing = BattleLog()
    typing.onText(listOf("Charmander was hurt"))
    typing.onText(listOf("Charmander was hurt by poison!"))
    assertEquals(listOf("Charmander was hurt by poison!"), typing.events.map { it.text })
  }

  @Test
  fun menusAndNoiseAreNotMessages() {
    assertFalse(BattleLog.isMessage("FIGHT POKEMON BAG RUN"))
    assertFalse(BattleLog.isMessage("XXXXX*."))
    assertFalse(BattleLog.isMessage(""))
  }

  /** The reads logged on the Thor during a Metapod vs Caterpie battle (String Shot's animation garbles the text). */
  @Test
  fun garbledRereadsDuringLongAnimationsCountOnce() {
    val data =
      GameData.parse(
        File("src/main/assets/pokemmo/species.json").readText(),
        File("src/main/assets/pokemmo/moves.json").readText(),
      )
    val log = BattleLog(movesOnly(data))
    val reads =
      listOf(
        "The wild Caterpie used Tackle!", "", "", "Metapod used Harden !", "Metapod s Defense won' t go any higher!", "", "",
        "The wild Caterpie uSed String Shot!", "The wild Caterpie USed string 5hot!", "The wild Caterpie Used Sring 5hot!",
        "The wild Caterpie USed string 5hot!",
        "Metapod' s Speed won' t go ary lower!", "Metapod' s Speed won' t go any lower!", "Metapod' s Speed wai t go ary lower!",
        "", "", "Metapod used Harden!", "Metapod s Defense won' t go any higher!", "", "",
        "The wild Caterpie used Stri rig Shot!", "The wild Caterpie used String Shot!", "The wild Caterpie Used Sring 5hot!",
        "The wi ld Caterpie used String Shot!",
        "Metapod s Speed uor t go any lower!", "Metapod' s Speed won' t go any lower!",
      )
    for (r in reads) log.onText(if (r.isEmpty()) emptyList() else listOf(r))
    val moves = log.events.filter { it.move != null }
    assertEquals(listOf("Tackle", "Harden", "String Shot", "Harden", "String Shot"), moves.map { it.move })
    assertEquals(listOf(Side.FOE, Side.YOU, Side.FOE, Side.YOU, Side.FOE), moves.map { it.side })
    assertEquals(listOf("Caterpie", "Metapod", "Caterpie", "Metapod", "Caterpie"), moves.map { it.actor })
    assertEquals(4, log.events.count { it.move == null }) // Defense x2, Speed x2 - one each time
  }

  /** Hordes: two Pidgey using Gust back to back. The box retypes the second message, which separates them. */
  @Test
  fun identicalMessagesSeparatedByRetyping() {
    val log = BattleLog(movesOnly(gameData))
    log.onText(listOf("The wild Pidgey used Gust!"))
    log.onText(listOf("The wild Pidgey used Gust!"))
    log.onText(listOf("The wild Pid")) // typing out again from the start
    log.onText(listOf("The wild Pidgey used Gust!"))
    assertEquals(2, log.events.count { it.move == "Gust" })
    // A read that only lost its "!" (not a retype) doesn't split one message into two.
    val one = BattleLog()
    one.onText(listOf("The wild Pidgey used Gust!"))
    one.onText(listOf("The wild Pidgey used Gust"))
    one.onText(listOf("The wild Pidgey used Gust!"))
    assertEquals(1, one.events.size)
  }

  @Test
  fun tidiesOcrSpacing() {
    assertEquals("Metapod's Defense won't go any higher!", BattleLog.tidy("Metapod' s Defense won' t go any higher !"))
    assertEquals("Metapod used Harden!", BattleLog.tidy("Metapod  used Harden !"))
  }

  /** Reads logged on the Thor with v0.31 (binarized text box, pixel-font slips). */
  @Test
  fun pixelFontSlipsAreCorrected() {
    val data =
      GameData.parse(
        File("src/main/assets/pokemmo/species.json").readText(),
        File("src/main/assets/pokemmo/moves.json").readText(),
      )
    val log = BattleLog(movesOnly(data))
    log.play(
      "The wild Caterpie used Tackle!",
      "Metapod used Harder!",
      "The wild Caterpie uged String 5hot.!",
      "Metapod's Speed won t go any lOWer!",
      "Metapod uzed Struggle!",
      "Metapod fainted!",
      "Tou're in charge, Rattata!",
      "The wild Cat.erpie used Tackle!",
      "Rat.tata used Tail Whip!",
    )
    val moves = log.events.filter { it.move != null }
    assertEquals(listOf("Tackle", "Harden", "String Shot", "Struggle", "Tackle", "Tail Whip"), moves.map { it.move })
    assertEquals(listOf(Side.FOE, Side.YOU, Side.FOE, Side.YOU, Side.FOE, Side.YOU), moves.map { it.side })
    assertEquals(listOf("Caterpie", "Metapod", "Caterpie", "Metapod", "Caterpie", "Rattata"), moves.map { it.actor })
    assertEquals(
      listOf("Metapod's Speed won't go any lower!", "Metapod fainted!", "You're in charge, Rattata!"),
      log.events.filter { it.move == null }.map { it.text },
    )
  }

  /** Context with only the full species and move lists (no party/opponent knowledge). */
  private fun movesOnly(data: GameData) =
    object : BattleContext {
      override fun pokemon(raw: String) = Species.match(raw)

      override fun move(actor: String?, raw: String) = data.matchMove(raw)?.name
    }

  @Test
  fun statusesFromMessages() {
    fun ev(t: String) = BattleEvent(Side.OTHER, text = t)
    val events =
      listOf(
        ev("The wild Caterpie is paralyzed! It may be unable to move!"),
        ev("Charmander was hurt by poison!"),
        ev("Charmander fainted!"),
        ev("You're in charge, Pikachu!"), // switched in: new Pokémon, no status known
      )
    assertEquals(mapOf(Side.FOE to "PAR"), BattleLog.statuses(events))
    assertEquals(mapOf(Side.YOU to "PSN", Side.FOE to "PAR"), BattleLog.statuses(events.take(2)))
    assertEquals("FNT", BattleLog.statuses(events.take(3))[Side.YOU])
  }

  @Test
  fun unknownNamesAreSkippedAndDigitsReadAsLetters() {
    val log = BattleLog(movesOnly(gameData))
    log.play("Charmeleon learned 51ash!", "Charmeleon learned Qwxyzt!")
    assertEquals(listOf("Charmeleon learned Slash!"), log.events.filter { it.known }.map { it.text })
  }
}
