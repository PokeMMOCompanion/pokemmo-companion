package com.pokemmocompanion.app.detect

import com.pokemmocompanion.app.calc.Damage
import com.pokemmocompanion.app.calc.Field
import com.pokemmocompanion.app.calc.FieldState
import com.pokemmocompanion.app.calc.GameData
import com.pokemmocompanion.app.calc.Build
import com.pokemmocompanion.app.calc.Nature
import com.pokemmocompanion.app.calc.Weather
import com.pokemmocompanion.app.party.DexStore
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldAndMessagesTest {
  private val data =
    GameData.parse(File("src/main/assets/pokemmo/species.json").readText(), File("src/main/assets/pokemmo/moves.json").readText())

  @Test
  fun followsWeatherAndScreens() {
    var f = FieldState()
    f = BattleField.apply(f, "It. is rainiig.") // OCR'd "It is raining."
    assertEquals(Weather.RAIN, f.weather)
    f = BattleField.apply(f, "The rain stopped.")
    assertNull(f.weather)
    f = BattleField.apply(f, "Reflect raised your team's Defense!")
    f = BattleField.apply(f, "Light Screen raised the foe's team's Special Defense!")
    assertEquals(setOf("Reflect"), f.yourScreens)
    assertEquals(setOf("Light Screen"), f.itsScreens)
    assertTrue(f.forIt().reflect)
    assertTrue(f.forYou().lightScreen)
    f = BattleField.apply(f, "Your team's Reflect wore off!")
    assertTrue(f.yourScreens.isEmpty())
  }

  @Test
  fun fieldChangesDamage() {
    val att = Build(data.species("Blastoise")!!, 50, Nature.MODEST).battler()
    val def = Build(data.species("Rhydon")!!, 50).battler()
    val surf = data.move("Surf")!!
    val plain = Damage.range(att, def, surf)!!
    val rain = Damage.range(att, def, surf, field = Field(Weather.RAIN))!!
    assertTrue("rain $rain vs $plain", rain.max > plain.max * 14 / 10)
    val screen = Damage.range(att, def, surf, field = Field(lightScreen = true))!!
    assertTrue(screen.max <= plain.max / 2 + 1)
    // Crits ignore screens.
    assertEquals(Damage.range(att, def, surf, crit = true)!!.max, Damage.range(att, def, surf, crit = true, field = Field(lightScreen = true))!!.max)
    // Sandstorm: Rock types take less from special moves.
    assertTrue(Damage.range(att, def, surf, field = Field(Weather.SAND))!!.max < plain.max)
    // Burn halves physical damage.
    // (Machamp's first ability, Guts, would ignore the burn: use No Guard.)
    val machamp = Build(data.species("Machamp")!!, 50, Nature.ADAMANT, ability = "No Guard").battler()
    val punch = data.move("Cross Chop")!!
    assertTrue(Damage.range(machamp, def, punch, field = Field(burned = true))!!.max <= Damage.range(machamp, def, punch)!!.max / 2 + 1)
  }

  @Test
  fun partyMessages() {
    assertEquals("Charmeleon" to 17, PartyMessages.levelUp("Charmeleon grew to level 17!"))
    assertEquals("Charmeleon" to "Metal Claw", PartyMessages.learned("Charmeleon learned Metal Claw!"))
    assertNull(PartyMessages.learned("Charmeleon did not learn Slash."))
  }

  @Test
  fun caughtNeverGoesBack() {
    val store = DexStore(Files.createTempFile("dex", ".json").toFile())
    assertTrue(store.record(74, false))
    assertTrue(store.record(74, true))
    assertFalse(store.record(74, false))
    assertEquals(true, store.caught[74])
  }
}
