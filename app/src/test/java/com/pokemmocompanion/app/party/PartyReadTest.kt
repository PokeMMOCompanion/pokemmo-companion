package com.pokemmocompanion.app.party

import com.pokemmocompanion.app.calc.GameData
import com.pokemmocompanion.app.detect.Frame
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PartyReadTest {
  @get:Rule val tmp = TemporaryFolder()

  private val data =
    GameData.parse(
      File("src/main/assets/pokemmo/species.json").readText(),
      File("src/main/assets/pokemmo/moves.json").readText(),
    )
  private val moveName: (String) -> String? = { data.matchMove(it)?.name }

  // ---- Party slot highlight, on real frames ----

  private fun frame(name: String): Frame {
    val f = File("../samples", name)
    assumeTrue("Missing sample $f", f.exists())
    val img = ImageIO.read(f)
    return object : Frame {
      override val width = img.width
      override val height = img.height

      override fun pixel(x: Int, y: Int) = img.getRGB(x, y)
    }
  }

  @Test
  fun slotHighlightOnSummaryScreens() {
    assertEquals(0, PartySlot.highlighted(frame("frame_20261001_070958_092.png"))) // Charmander
    assertEquals(2, PartySlot.highlighted(frame("frame_20261001_071048_197.png"))) // Rattata
    assertEquals(5, PartySlot.highlighted(frame("frame_20261001_071102_840.png"))) // Pikachu
  }

  @Test
  fun pageFromHighlightedTab() {
    val expected =
      mapOf(
        "frame_20261001_070958_092.png" to SummaryPageKind.INFO,
        "frame_20261001_071001_373.png" to SummaryPageKind.STATS,
        "frame_20261001_071002_746.png" to SummaryPageKind.EVS,
        "frame_20261001_071004_427.png" to SummaryPageKind.IVS,
        "frame_20261001_071006_172.png" to SummaryPageKind.MOVES,
        "frame_20261001_071007_678.png" to SummaryPageKind.OTHER, // caught location
        "frame_20261001_071048_197.png" to SummaryPageKind.IVS, // Rattata
        "frame_20261001_071103_830.png" to SummaryPageKind.MOVES, // Pikachu
      )
    for ((file, kind) in expected) assertEquals(file, kind, SummaryTabs.highlighted(frame(file)))
  }

  @Test
  fun noSlotOutsideSummary() {
    assertNull(PartySlot.highlighted(frame("frame_20261001_071032_086.png"))) // party menu over overworld
    assertNull(PartySlot.highlighted(frame("frame_20260930_212753_768.png"))) // battle
    assertNull(PartySlot.highlighted(frame("frame_20260930_212609_713.png"))) // overworld
  }

  // ---- Fixed value-box layout, checked on real frames ----

  /** Every value box in [SummaryLayout] should land on a white value box (not the page background). */
  @Test
  fun layoutBoxesSitOnWhiteValueBoxes() {
    val pages =
      mapOf(
        SummaryPageKind.INFO to listOf("frame_20261001_070958_092.png", "frame_20261001_071044_733.png", "frame_20261001_071058_791.png"),
        SummaryPageKind.STATS to listOf("frame_20261001_071001_373.png", "frame_20261001_071046_020.png", "frame_20261001_071100_007.png"),
        SummaryPageKind.EVS to listOf("frame_20261001_071002_746.png", "frame_20261001_071047_233.png", "frame_20261001_071101_453.png"),
        SummaryPageKind.IVS to listOf("frame_20261001_071004_427.png", "frame_20261001_071048_197.png", "frame_20261001_071102_840.png"),
        SummaryPageKind.MOVES to listOf("frame_20261001_071006_172.png", "frame_20261001_071103_830.png"),
      )
    for ((kind, files) in pages) for (file in files) {
      val f = frame(file)
      // "level" and "header" are white text on the page background, not value boxes.
      for (box in SummaryLayout.fields(kind).filter { it.id != "level" && it.id != "header" }) {
        var white = 0
        var total = 0
        for (y in box.top + 4 until box.bottom - 4 step 3) for (x in box.left + 4 until box.right - 4 step 3) {
          val p = f.pixel(x, y)
          val min = minOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
          total++
          if (min >= 200) white++
        }
        assertTrue("$file $kind ${box.id}: white ${white * 100 / total}%", white * 100 / total >= 60)
      }
    }
  }

  // ---- Page parsing from per-box OCR text ----

  private fun page(kind: SummaryPageKind, vararg texts: Pair<String, String>) =
    SummaryParser.fromFields(kind, texts.toMap(), moveName)

  @Test
  fun infoPage() {
    val p = page(SummaryPageKind.INFO, "dex" to "004", "name" to "Charmander", "nature" to "Hardy", "level" to "Lv. 13", "item" to "None")
    assertEquals(4, p.dexId)
    assertEquals("Hardy", p.nature)
    assertEquals("Charmander", p.nickname)
    assertEquals(13, p.level)
    assertEquals("None", p.item)
    assertTrue(p.isComplete)
    // Misread nature letter still lands on a real nature.
    assertEquals("Quirky", page(SummaryPageKind.INFO, "dex" to "019", "nature" to "Ouirky").nature)
  }

  @Test
  fun statsPage() {
    val p =
      page(SummaryPageKind.STATS, "hp" to "29 / 35", "atk" to "20", "def" to "18", "spa" to "22", "spd" to "19", "spe" to "24")
    assertEquals(29, p.currentHp)
    assertEquals(listOf(35, 20, 18, 22, 19, 24), p.stats)
    // One box unread: no partial stats.
    val partial = page(SummaryPageKind.STATS, "hp" to "15 / 15", "atk" to "9", "def" to "7", "spa" to "6", "spd" to "7")
    assertNull(partial.stats)
    assertFalse(partial.isComplete)
  }

  @Test
  fun evAndIvPages() {
    val evs = page(SummaryPageKind.EVS, "hp" to "5", "atk" to "1", "def" to "1", "spa" to "0", "spd" to "1", "spe" to "11")
    assertEquals(listOf(5, 1, 1, 0, 1, 11), evs.evs)
    // OCR slips: 0 read as "O", 1 as "l".
    val ivs = page(SummaryPageKind.IVS, "hp" to "O", "atk" to "18", "def" to "23", "spa" to "31", "spd" to "l3", "spe" to "3")
    assertEquals(listOf(0, 18, 23, 31, 13, 3), ivs.ivs)
    // An impossible IV means a misread: reject the page rather than store it.
    assertNull(page(SummaryPageKind.IVS, "hp" to "21", "atk" to "81", "def" to "10", "spa" to "6", "spd" to "27", "spe" to "12").ivs)
  }

  @Test
  fun movesPage() {
    val p =
      page(
        SummaryPageKind.MOVES,
        "move0" to "DragonBreath dra",
        "move1" to "Metal Claw",
        "move2" to "Tackle",
        "move3" to "Ember",
        "ability" to "Blaze",
        "item" to "Super Potion",
      )
    assertEquals(listOf("DragonBreath", "Metal Claw", "Tackle", "Ember"), p.moves)
    assertEquals("Blaze", p.ability)
    assertEquals("Super Potion", p.item)
    assertTrue(p.isComplete)
  }

  // ---- Merging into the stored party ----

  @Test
  fun storeMergesPagesAndPersists() {
    val file = File(tmp.root, "party.json")
    val store = PartyStore(file, speciesName = { data.species(it)?.name })
    store.apply(0, SummaryPage(SummaryPageKind.INFO, nickname = "Charmander", level = 13, dexId = 4, nature = "Hardy"))
    store.apply(0, SummaryPage(SummaryPageKind.STATS, nickname = "Charmander", stats = listOf(35, 20, 18, 22, 19, 24)))
    assertFalse(store.party.members[0]!!.isReady)
    store.apply(0, SummaryPage(SummaryPageKind.MOVES, nickname = "Charmander", moves = listOf("Ember", "Tackle")))
    assertTrue(store.party.members[0]!!.isReady)

    val reloaded = PartyStore(file, speciesName = { data.species(it)?.name }).party.members[0]!!
    assertEquals("Charmander", reloaded.species)
    assertEquals(listOf(35, 20, 18, 22, 19, 24), reloaded.stats)
  }

  @Test
  fun storeResetsSlotOnlyOnConfirmedDexChange() {
    val store = PartyStore(File(tmp.root, "p.json"), { data.species(it)?.name }, { data.species(it)?.abilities.orEmpty() })
    store.apply(1, SummaryPage(SummaryPageKind.INFO, nickname = "Hoothoot", dexId = 163, nature = "Calm"))
    store.apply(1, SummaryPage(SummaryPageKind.MOVES, moves = listOf("Tackle"), ability = "Insomnia"))
    assertEquals("Insomnia", store.party.members[1]!!.ability)

    // A single misread number is ignored...
    store.apply(1, SummaryPage(SummaryPageKind.INFO, dexId = 168, nature = "Calm"))
    assertEquals("Hoothoot", store.party.members[1]!!.species)
    store.apply(1, SummaryPage(SummaryPageKind.INFO, dexId = 163, nature = "Calm"))
    assertEquals(listOf("Tackle"), store.party.members[1]!!.moves)

    // ...but a new number read twice is a different Pokémon.
    store.apply(1, SummaryPage(SummaryPageKind.INFO, nickname = "Rattata", dexId = 19, nature = "Quirky"))
    store.apply(1, SummaryPage(SummaryPageKind.INFO, nickname = "Rattata", dexId = 19, nature = "Quirky"))
    val m = store.party.members[1]!!
    assertEquals("Rattata", m.species)
    assertEquals(emptyList<String>(), m.moves)
  }

  @Test
  fun storeRejectsAbilityTheSpeciesCantHave() {
    val store = PartyStore(File(tmp.root, "a.json"), { data.species(it)?.name }, { data.species(it)?.abilities.orEmpty() })
    store.apply(0, SummaryPage(SummaryPageKind.INFO, dexId = 4, nature = "Hardy"))
    store.apply(0, SummaryPage(SummaryPageKind.MOVES, moves = listOf("Ember"), ability = "Powers up Fire"))
    assertNull(store.party.members[0]!!.ability)
    store.apply(0, SummaryPage(SummaryPageKind.MOVES, moves = listOf("Ember"), ability = "Blaze"))
    assertEquals("Blaze", store.party.members[0]!!.ability)
  }

  @Test
  fun otherTabsWaitForInfoTab() {
    val store = PartyStore(File(tmp.root, "n.json"), speciesName = { data.species(it)?.name })
    val stats = SummaryPage(SummaryPageKind.STATS, headerName = "Sparky", stats = listOf(18, 9, 10, 11, 10, 15))
    assertEquals(ApplyResult.NEEDS_INFO, store.apply(5, stats))
    assertNull(store.party.members[5])
    assertEquals(ApplyResult.STORED, store.apply(5, SummaryPage(SummaryPageKind.INFO, dexId = 25, nickname = "Sparky", nature = "Timid")))
    assertEquals(ApplyResult.STORED, store.apply(5, stats))
    val m = store.party.members[5]!!
    assertEquals("Pikachu", m.species) // species from the Pokédex number
    assertEquals("Sparky", m.nickname) // nickname from the Name box
  }

  @Test
  fun pageForAnotherPokemonIsNotRecorded() {
    val store = PartyStore(File(tmp.root, "m.json"), speciesName = { data.species(it)?.name })
    store.apply(5, SummaryPage(SummaryPageKind.INFO, dexId = 25, nickname = "Sparky", nature = "Timid"))
    // Header shows a different Pokémon: e.g. the player scrolled to the next party member.
    val wrong = SummaryPage(SummaryPageKind.IVS, headerName = "Charmander", ivs = List(6) { 15 })
    assertEquals(ApplyResult.NAME_MISMATCH, store.apply(5, wrong))
    assertNull(store.party.members[5]!!.ivs)
    // OCR noise on the right name still counts as a match: ball icon as a stray letter, one misread letter.
    assertEquals(ApplyResult.STORED, store.apply(5, SummaryPage(SummaryPageKind.IVS, headerName = "C Sparky", ivs = List(6) { 31 })))
    assertEquals(ApplyResult.STORED, store.apply(5, SummaryPage(SummaryPageKind.EVS, headerName = "Sparkv", evs = List(6) { 0 })))
    // Header not readable at all: accepted (the party-bar highlight is still the guard).
    assertEquals(ApplyResult.STORED, store.apply(5, SummaryPage(SummaryPageKind.MOVES, moves = listOf("Charm"), ability = "Static")))
  }

  @Test
  fun similarSpeciesNamesDontMatch() {
    val store = PartyStore(File(tmp.root, "s.json"), speciesName = { data.species(it)?.name })
    assertFalse(store.sameName("Charmeleon", "Charmander"))
    assertFalse(store.sameName("Raichu", "Pikachu"))
    assertTrue(store.sameName("2 Charmander", "Charmander"))
  }
}
