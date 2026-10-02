package com.pokemmocompanion.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pokemmocompanion.app.capture.DebugLog
import com.pokemmocompanion.app.party.DexProgress
import com.pokemmocompanion.app.party.EvRepository
import com.pokemmocompanion.app.party.PartyRepository
import com.pokemmocompanion.app.party.SpriteStore
import com.pokemmocompanion.app.theme.PokeMMOCompanionTheme
import com.pokemmocompanion.app.quest.QuestRepository
import com.pokemmocompanion.app.tools.FarmRepository
import com.pokemmocompanion.app.tools.GtlRepository
import com.pokemmocompanion.app.ui.MainScreen

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    PartyRepository.init(this)
    DebugLog.init(this)
    SpriteStore.init(this)
    QuestRepository.init(this)
    EvRepository.init(this)
    DexProgress.init(this)
    FarmRepository.rearm(this)
    GtlRepository.init(this)

    enableEdgeToEdge()
    setContent {
      PokeMMOCompanionTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainScreen() }
      }
    }
  }
}
