# PokeMMO Companion

An Android companion app for [PokeMMO](https://pokemmo.com), made for dual-screen handhelds like the **AYN Thor**:
the game runs on the top screen, the companion on the bottom screen. It watches the game through Android's screen
capture and helps with what's on screen. It never touches the game.

## Features

- **Alerts:** a strong buzz and sound for **shinies** and for Pokémon **still needed for your Pokédex**.
- **Battle Assistant:** reads your Pokémon and the opponent, ranks your moves by damage (PokeMMO mechanics, weather,
  screens, burn, stat stages), shows threats, speed and possible abilities, and remembers what trainers reveal:
  moves, abilities, items, and stats worked out from the damage dealt and taken. Catch chances per ball, and a team
  builder for test matchups.
- **Party:** read from the in-game summary screens (or entered by hand), with HP from the overworld rings, EV
  tracking toward targets, and level-up / new-move detection.
- **Pokédex:** all 649 Pokémon with stats, EV yields, evolutions and wild locations; **Here** shows what spawns
  where you are right now (from the pause menu); an **EV horde finder**; caught / needed tracking.
- **Tools:** berry farm timers with water/harvest/wither reminders, breeding planner, egg-move chains, GTL prices,
  encounter counter.
- **Quests:** gym and League checklist per region, with your party's matchup against the next one.

## Fair play

PokeMMO's rules forbid automating the game, modifying the client and reading its memory or network traffic. This
app does none of that. It only looks at the screen (Android MediaProjection) and only notifies, vibrates, plays
sounds and counts. A person always plays the game.

## Privacy

Screen capture sounds scary, so here is exactly what happens:

- **Nothing leaves your phone.** Frames are looked at in memory, a couple of times a second, then thrown away. The
  app never saves screenshots or recordings and never uploads what's on your screen.
- **No account, login, ads or tracking.** The app never asks for your PokeMMO login and has no way to control the
  game.
- **Internet is only used** when you tap **Download** (Pokémon sprites from PokemonDB), open **GTL prices** (PokeMMO
  Hub's price API), or to **check for app updates** on this GitHub page (when the app opens, at most twice a day; can
  be turned off in Settings → Updates). None of these requests contain anything from your screen.
- **Text recognition runs on the phone** (Google ML Kit, bundled model). Per
  [Google's ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure), ML Kit sends Google
  anonymous performance stats (phone model, OS version, timing), never the images or the text it reads.
- **What it keeps on the phone:** your party, settings, berry timers, encounter counts and Pokédex progress. A
  diagnostic log of what it reads is **off** unless you turn it on (Settings → Privacy), and turning it off deletes it.
- Android shows its own warning when capture starts and a notification while it runs. Tap the lens or the
  notification's **Stop** to end it any time.

The source code is right here if you want to check.

## Install

1. Download the latest APK from [Releases](../../releases/latest) and open it on your device to install (Android will
   ask you to allow installs from your browser or file manager).
2. Put the app on the bottom screen, PokeMMO on the top screen.
3. Tap the **lens** (top left) and allow screen capture of the **entire screen**.
4. Party → **Read party**, then open each Pokémon's summary in the game and page through its tabs.
5. Optional: Tools → Settings → Pokémon sprites → **Download**.

Built for a 1920×1080 top screen running PokeMMO in landscape. Android 10 or newer, arm64.

Updating: the app tells you when a new version is out (Settings → Updates). Install the newer APK over the old one;
your data is kept.

## Credits

Data from [PokeMMO Hub](https://pokemmohub.com) and [PokéAPI](https://pokeapi.co); sprites from
[PokemonDB](https://pokemondb.net/sprites), downloaded on the device. Details and licenses in [NOTICE.md](NOTICE.md).
Unofficial fan project, not affiliated with PokeMMO, Nintendo, Game Freak or The Pokémon Company.

## License

[MIT](LICENSE) for this project's code. Third-party data keeps its own terms (see NOTICE.md).
