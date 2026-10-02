package com.pokemmocompanion.app.calc

import android.content.Context

/** Loads the bundled PokeMMO Hub snapshot once (≈600 KB of JSON); call off the main thread. */
object GameDataLoader {
  @Volatile private var cached: GameData? = null

  fun get(context: Context): GameData =
    cached
      ?: synchronized(this) {
        cached
          ?: GameData.parse(
              context.assets.open("pokemmo/species.json").bufferedReader().use { it.readText() },
              context.assets.open("pokemmo/moves.json").bufferedReader().use { it.readText() },
            )
            .also { cached = it }
      }

  @Volatile private var spawns: Map<Int, List<Spawn>>? = null

  /** Wild spawns per dex number (≈1 MB of JSON, loaded on first use); call off the main thread. */
  fun spawns(context: Context): Map<Int, List<Spawn>> =
    spawns
      ?: synchronized(this) {
        spawns
          ?: Spawns.parse(context.assets.open("pokemmo/locations.json").bufferedReader().use { it.readText() })
            .also { spawns = it }
      }
}
