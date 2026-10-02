package com.pokemmocompanion.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pokemmocompanion.app.calc.Stat
import com.pokemmocompanion.app.tools.Breeding
import com.pokemmocompanion.app.tools.Gtl
import com.pokemmocompanion.app.tools.GtlRepository
import com.pokemmocompanion.app.ui.dex.DexColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Hub's IV colors; null = nature. */
private val IV_COLORS =
  mapOf(
    Stat.HP to Color(0xFF55B651), Stat.ATK to Color(0xFFF72533), Stat.DEF to Color(0xFFF78025),
    Stat.SPA to Color(0xFFE925F7), Stat.SPD to Color(0xFFF7E225), Stat.SPE to Color(0xFF25E2F7), null to Color(0xFFB8C7CC),
  )

/** The held item that passes each IV (Power items are breeding items in PokeMMO); null = Everstone for the nature. */
private val BRACES =
  mapOf(
    Stat.HP to "Power Weight", Stat.ATK to "Power Bracer", Stat.DEF to "Power Belt",
    Stat.SPA to "Power Lens", Stat.SPD to "Power Band", Stat.SPE to "Power Anklet", null to "Everstone",
  )

private fun label(s: Stat?) = s?.short ?: "Nature"

/**
 * Breeding simulator (PokeMMO Hub's patterns): pick the perfect IVs you want, in priority order, and whether to keep
 * a nature. Shows the 1×31 parents to get, the breeding tree, and the held items with a GTL cost estimate.
 */
@Composable
fun BreedingTool(modifier: Modifier) {
  val context = LocalContext.current.applicationContext
  val breeding by produceState<Breeding?>(null) {
    value = withContext(Dispatchers.IO) { context.assets.open("pokemmo/breeding.json").bufferedReader().use { Breeding.parse(it.readText()) } }
  }
  var picked by rememberSaveable { mutableStateOf(listOf(Stat.HP.ordinal, Stat.ATK.ordinal, Stat.SPE.ordinal)) }
  var nature by rememberSaveable { mutableStateOf(true) }
  val gtl by GtlRepository.state.collectAsStateWithLifecycle()
  val stats = picked.map { Stat.entries[it] }
  val plan = breeding?.takeIf { stats.size >= 2 }?.plan(stats, nature)
  val small = MaterialTheme.typography.bodySmall

  Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Tap the perfect IVs you want, most important first (2-5).", style = small, color = DexColors.InkMuted)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Stat.entries.forEach { st ->
        val i = picked.indexOf(st.ordinal)
        Chip(if (i >= 0) "${i + 1}. ${st.short}" else st.short, i >= 0) {
          picked = if (i >= 0) picked - st.ordinal else if (picked.size < 5) picked + st.ordinal else picked
        }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      Text("Nature", style = MaterialTheme.typography.labelMedium, color = DexColors.InkMuted)
      Chip("Keep a nature", nature) { nature = true }
      Chip("Any nature", !nature) { nature = false }
    }
    if (plan == null) {
      Text("Pick at least 2 stats.", style = small)
      return@Column
    }

    Text("YOU NEED", style = MaterialTheme.typography.titleSmall)
    plan.parents.entries.sortedBy { it.key?.ordinal ?: -1 }.forEach { (st, n) ->
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(IV_COLORS.getValue(st)))
        Text(
          if (st == null) "$n× Pokémon with the nature you want" else "$n× 31 ${st.short}",
          style = small,
          fontWeight = FontWeight.Bold,
        )
      }
    }
    Text("${plan.breeds} breeds · ${plan.heldItems} held items (braces${if (nature) " + Everstone" else ""}), used up", style = small)
    val prices = (stats.map { it as Stat? } + if (nature) listOf(null) else emptyList()).mapNotNull { GtlRepository.priceOf(BRACES.getValue(it)) }
    val hub = Breeding.HUB_COST_ESTIMATE[nature]?.get(stats.size)
    Text(
      buildString {
        if (prices.isNotEmpty()) append("At current GTL prices: ≈ ${Gtl.money(plan.heldItems * prices.average().toLong())} (held items only). ")
        else if (gtl.prices.isEmpty()) append("Load GTL prices for a live cost. ")
        hub?.let { append("PokeMMO Hub's estimate: ${Gtl.money(it.toLong())}.") }
        append(" Gender selection not included.")
      },
      style = small,
      color = DexColors.InkMuted,
    )

    Text("BREEDING TREE", style = MaterialTheme.typography.titleSmall)
    Text("Bottom row first: breed each neighbouring pair, then the results, up to the top.", style = small, color = DexColors.InkMuted)
    plan.rows.reversed().forEach { row ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        row.forEach { mon ->
          val w = when {
            row.size >= 16 -> 9.dp
            row.size >= 8 -> 18.dp
            row.size >= 4 -> 36.dp
            else -> 56.dp
          }
          Canvas(Modifier.width(w).height(16.dp)) {
            val w = size.width / mon.size
            mon.forEachIndexed { i, st -> drawRect(IV_COLORS.getValue(st), topLeft = androidx.compose.ui.geometry.Offset(i * w, 0f), size = Size(w, size.height)) }
          }
        }
      }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      (listOf<Stat?>(null).takeIf { nature }.orEmpty() + stats).forEach { st ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          Box(Modifier.size(10.dp).clip(CircleShape).background(IV_COLORS.getValue(st)))
          Text("${label(st)}: ${BRACES.getValue(st)}", style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}

