package com.pokemmocompanion.app.detect

import org.junit.Assert.assertEquals
import org.junit.Test

class EncounterParserTest {
  private fun parse(vararg lines: String) = EncounterParser.parse(lines.toList())

  @Test
  fun singleWild() {
    assertEquals(listOf(WildMon("Pidgey", 4, false)), parse("Pidgey Lv. 4"))
  }

  @Test
  fun ocrVariantsOfLevelAndGender() {
    assertEquals(listOf(WildMon("Electabuzz", 30, false)), parse("Electabuzz LV.30"))
    assertEquals(listOf(WildMon("Rattata", 4, false)), parse("Rattata Lv, 4 d"))
    assertEquals(listOf(WildMon("Nidorino", 24, false)), parse("Nidorino Ly. 248"))
  }

  @Test
  fun shinyPrefix() {
    assertEquals(listOf(WildMon("Amoonguss", 52, true)), parse("Shiny Amoonguss Lv. 52"))
    assertEquals(listOf(WildMon("Amoonguss", 52, true)), parse("Shlny Amoonguss Lv. 52"))
  }

  @Test
  fun hordeBoxesMergedOnOneLine() {
    assertEquals(
      listOf(WildMon("Shelmet", 28, false), WildMon("Shelmet", 28, true), WildMon("Shelmet", 28, false)),
      parse("Shelmet Lv. 28 Q Shiny Shelmet Lv. 28 O Shelmet Lv. 28"),
    )
  }

  @Test
  fun hordeAcrossLines() {
    val mons = parse("Nidorino Lv. 24 Nidorino Lv. 24", "Nidorino Lv. 23", "Nidorino Lv. 24", "Nidorino Lv. 23")
    assertEquals(5, mons.size)
    assertEquals(setOf("Nidorino"), mons.map { it.name }.toSet())
  }

  @Test
  fun multiWordNames() {
    assertEquals(listOf(WildMon("Mr. Mime", 30, false)), parse("Mr. Mime Lv. 30"))
    assertEquals(listOf(WildMon("Mr. Mime", 30, true)), parse("Shiny Mr. Mime Lv. 30"))
  }

  @Test
  fun ignoresNonNameText() {
    assertEquals(emptyList<WildMon>(), parse("33 / 33", "HP", "A wild Pidgey appeared!"))
  }

  @Test
  fun shinyStarsAreStripped() {
    assertEquals(listOf(WildMon("Ursaring", 58, true)), parse("★Shiny Ursaring★ Lv. 58 ♀"))
    assertEquals(listOf(WildMon("Ursaring", 58, true)), parse("*Shiny Ursaring* Lv. 58"))
    assertEquals(
      listOf(WildMon("Ursaring", 58, true), WildMon("Ursaring", 60, false)),
      parse("★Shiny Ursaring★ Lv. 58 ♀ Ursaring Lv. 60 ♂"),
    )
  }

  @Test
  fun levelBoxComesFromTheMatchingWord() {
    fun el(text: String, left: Int) = OcrElement(text, OcrBox(left, 100, left + 10 * text.length, 130))
    val line =
      OcrLine(
        "Ursaring Lv. 58 Ursaring Lv. 61",
        listOf(el("Ursaring", 0), el("Lv.", 90), el("58", 130), el("Ursaring", 400), el("Lv.", 490), el("61", 530)),
      )
    val parsed = EncounterParser.parseLines(listOf(line))
    assertEquals(listOf(58, 61), parsed.map { it.mon.level })
    assertEquals(listOf(130, 530), parsed.map { it.levelBox?.left })
  }
}
