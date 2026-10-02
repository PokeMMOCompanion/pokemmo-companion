package com.pokemmocompanion.app.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.pokemmocompanion.app.MainActivity
import com.pokemmocompanion.app.calc.BattleKnowledge
import com.pokemmocompanion.app.calc.GameDataLoader
import com.pokemmocompanion.app.calc.Stat
import com.pokemmocompanion.app.detect.BattleDetector
import com.pokemmocompanion.app.detect.BattleContext
import com.pokemmocompanion.app.calc.BattleAdvisor
import com.pokemmocompanion.app.calc.HitTracker
import com.pokemmocompanion.app.calc.Observation
import com.pokemmocompanion.app.calc.OpponentMemory
import com.pokemmocompanion.app.calc.Places
import com.pokemmocompanion.app.calc.Reveal
import com.pokemmocompanion.app.calc.SeenLibrary
import com.pokemmocompanion.app.calc.Sighting
import com.pokemmocompanion.app.calc.FieldState
import com.pokemmocompanion.app.detect.BattleEvent
import com.pokemmocompanion.app.detect.BattleField
import com.pokemmocompanion.app.detect.KnownNames
import com.pokemmocompanion.app.detect.PartyMessages
import com.pokemmocompanion.app.detect.BattleIdentifier
import com.pokemmocompanion.app.detect.Side
import com.pokemmocompanion.app.detect.WildMon
import com.pokemmocompanion.app.detect.BattleLog
import com.pokemmocompanion.app.detect.BattleResult
import com.pokemmocompanion.app.detect.BattleTracker
import com.pokemmocompanion.app.detect.CaughtIcon
import com.pokemmocompanion.app.detect.EncounterLog
import com.pokemmocompanion.app.detect.EncounterParser
import com.pokemmocompanion.app.detect.Badge
import com.pokemmocompanion.app.detect.Frame
import com.pokemmocompanion.app.detect.FrameFeatures
import com.pokemmocompanion.app.detect.StatBadges
import com.pokemmocompanion.app.detect.TextBand
import com.pokemmocompanion.app.detect.GenderIcon
import com.pokemmocompanion.app.detect.PartyRail
import com.pokemmocompanion.app.detect.PauseMenu
import com.pokemmocompanion.app.detect.OcrLine
import com.pokemmocompanion.app.detect.ScreenState
import com.pokemmocompanion.app.display.orientedSize
import com.pokemmocompanion.app.party.ActivePokemon
import com.pokemmocompanion.app.tools.Gtl
import com.pokemmocompanion.app.party.BattleOutcome
import com.pokemmocompanion.app.party.DexProgress
import com.pokemmocompanion.app.quest.MilestoneKind
import com.pokemmocompanion.app.quest.Story
import com.pokemmocompanion.app.party.EvRepository
import com.pokemmocompanion.app.party.EvRules
import com.pokemmocompanion.app.party.ApplyResult
import com.pokemmocompanion.app.party.FieldBox
import com.pokemmocompanion.app.party.FieldType
import com.pokemmocompanion.app.party.PartyIcon
import com.pokemmocompanion.app.party.PartyRepository
import com.pokemmocompanion.app.party.SpriteStore
import com.pokemmocompanion.app.party.PartySlot
import com.pokemmocompanion.app.party.SummaryPageKind
import com.pokemmocompanion.app.party.SummaryLayout
import com.pokemmocompanion.app.party.SummaryParser
import com.pokemmocompanion.app.party.SummaryTabs
import com.pokemmocompanion.app.party.ValueBox
import java.io.File
import kotlin.math.roundToInt
import java.util.concurrent.Executors

/**
 * Foreground service that holds the MediaProjection and receives frames through an ImageReader.
 * Frames are only processed a few times per second; the rest are acquired and dropped right away.
 * This service only reads pixels. It never sends input to any app.
 */
class CaptureService : Service() {

  companion object {
    private const val TAG = "CaptureService"
    private const val EXTRA_RESULT_CODE = "resultCode"
    private const val EXTRA_RESULT_DATA = "resultData"
    private const val ACTION_STOP = "com.pokemmocompanion.app.STOP_CAPTURE"
    private const val ACTION_SAVE_FRAME = "com.pokemmocompanion.app.SAVE_FRAME"
    private const val CHANNEL_ID = "capture"
    private const val NOTIFICATION_ID = 1
    private const val PROCESS_INTERVAL_MS = 500L // ~2 frames per second
    private const val ACTIVE_READ_EVERY = 4 // ~every 2 s in battle
    private const val MENU_READ_INTERVAL_MS = 1500L
    private const val OPPONENT_REREAD_FRAMES = 20 // ~10 s
    private const val SIGHTING_MAX_AGE_MS = 20 * 60_000L
    private const val MAX_SIGHTINGS = 6
    // Opponent name boxes (single and both horde rows) as fractions of frame height.
    private const val NAME_BAND_TOP = 0.07f
    private const val NAME_BAND_BOTTOM = 0.27f
    // Battle text box (intro text sits at ~0.80-0.86; leave room for a second line).
    private const val INTRO_BAND_TOP = 0.76f
    private const val INTRO_BAND_BOTTOM = 0.98f
    private const val TEXT_BOX_RIGHT = 0.78f // ~1500 px: messages never reach further
    // Summary screen: below the tab bar, above the party bar (header, left column and Item Held all inside).
    private const val SUMMARY_TOP = 0.10f
    private const val SUMMARY_BOTTOM = 0.84f

    fun start(context: Context, resultCode: Int, resultData: Intent) {
      val intent =
        Intent(context, CaptureService::class.java)
          .putExtra(EXTRA_RESULT_CODE, resultCode)
          .putExtra(EXTRA_RESULT_DATA, resultData)
      ContextCompat.startForegroundService(context, intent)
    }

    fun saveFrame(context: Context) {
      context.startService(Intent(context, CaptureService::class.java).setAction(ACTION_SAVE_FRAME))
    }

    fun stop(context: Context) {
      context.startService(Intent(context, CaptureService::class.java).setAction(ACTION_STOP))
    }
  }

  private lateinit var thread: HandlerThread
  private lateinit var handler: Handler
  private var projection: MediaProjection? = null
  private var virtualDisplay: VirtualDisplay? = null
  private var imageReader: ImageReader? = null
  private val converter = FrameConverter()
  private val saveExecutor = Executors.newSingleThreadExecutor()
  private val tracker = BattleTracker()
  private val identifier = BattleIdentifier()
  /** Battle-message slots are filled from the current party and opponents (see [BattleKnowledge]). */
  private val battleLog by lazy {
    BattleLog(
      object : BattleContext {
        private fun current() =
          BattleKnowledge(
            GameDataLoader.get(this@CaptureService),
            PartyRepository.party.value.members.filterNotNull(),
            CaptureRepository.state.value.currentBattle?.mons.orEmpty(),
          )

        override fun pokemon(raw: String) = current().pokemon(raw)

        override fun move(actor: String?, raw: String) = current().move(actor, raw)

        override fun named(raw: String) = knownItemsAndAbilities.match(raw)

        override fun isPlayers(pokemon: String) =
          PartyRepository.party.value.members.filterNotNull().any { m ->
            listOfNotNull(m.nickname, m.species).any { it.equals(pokemon, ignoreCase = true) }
          }
      }
    )
  }
  @Volatile private var stopping = false
  private var battleFrames = 0
  private var lastRingArcs: List<Int>? = null
  private var activeReadThisBattle = false
  private var opponentGoneFrames = 0
  private var lastSummaryKey: Pair<Int, SummaryPageKind>? = null
  private var lastSummaryComplete = false
  private var incompleteReads = 0
  private val debugFramesSaved = mutableSetOf<Pair<Int, SummaryPageKind>>()
  private lateinit var nameReader: NameReader
  private val encounterLog by lazy { EncounterLog(File(filesDir, "encounters.csv")) }
  private val places by lazy { Places(GameDataLoader.spawns(this)) }
  private val knownPlaces by lazy {
    KnownNames(places.all().map { it.name } + Story.REGIONS.flatMap { r -> r.milestones.filter { it.kind == MilestoneKind.GYM }.map { it.detail.substringBefore(" · ") } })
  }
  private var lastMenuReadAt = 0L
  /** Region of the last place known for sure; settles route numbers that exist in both Kanto and Unova. */
  private var lastRegion: String? = null
  /** Recent wild encounters (time, species, level) for working out the place between menu reads. */
  private val sightings = ArrayDeque<Pair<Long, Sighting>>()
  /** Battle memory: what each opposing Pokémon revealed this battle, and its narrowed stats. */
  private val memories = HashMap<String, OpponentMemory>()
  private val hitTracker = HitTracker()
  private var eventsSeen = 0
  /** Weather and screens of the current battle. */
  private var field = FieldState.NONE
  private var activeReadNow = false
  /** Frames left to re-read the opponent's name after a trainer sends out a new Pokémon. */
  private var opponentReread = 0
  private val seenLibrary by lazy { SeenLibrary(File(filesDir, "battle_memory.json")) }
  private val knownItems by lazy { KnownNames(itemNames) }
  private val knownItemsAndAbilities by lazy {
    KnownNames(itemNames + GameDataLoader.get(this).species.flatMap { it.abilities }.filter { it != "--" }.distinct())
  }
  /** Text of the newest battle event when it was handled (a fuller read can replace it at the same index). */
  private var lastEventText: String? = null
  /** The Pokémon a trainer just announced ("Opponent sent out Metapod!"), awaited in the name box. */
  private var expectedOpponent: String? = null
  /** Item names to spot in battle messages, longest first ("Sitrus Berry" before "Berry"). */
  private val itemNames by lazy {
    assets.open("pokemmo/items.json").bufferedReader().use { Gtl.parseItems(it.readText()) }
      .map { it.name }.filter { it.length >= 4 }.distinct().sortedByDescending { it.length }
  }
  /** Party slots that battled in the current battle (they share its EVs). */
  private val participants = mutableSetOf<Int>()

  private var lastProcessedAt = 0L

  private var framesReceived = 0L
  private var fpsWindowStart = 0L
  private var fpsWindowFrames = 0

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    thread = HandlerThread("capture").apply { start() }
    handler = Handler(thread.looper)
    nameReader = NameReader()
    PartyRepository.init(this)
    DebugLog.init(this)
    SpriteStore.init(this)
    EvRepository.init(this)
    DexProgress.init(this)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopSelf()
      return START_NOT_STICKY
    }
    if (intent?.action == ACTION_SAVE_FRAME) {
      if (projection != null) handler.post(::saveLatestFrame)
      return START_NOT_STICKY
    }

    // Android 14+ requires the mediaProjection foreground service to be running before getMediaProjection().
    startInForeground()

    val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
    val resultData = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_RESULT_DATA, Intent::class.java) }
    if (resultData == null || projection != null) {
      if (projection == null) stopSelf()
      return START_NOT_STICKY
    }

    try {
      startCapture(resultCode, resultData)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start capture", e)
      CaptureRepository.update { CaptureState(error = "Failed to start: ${e.message}") }
      stopSelf()
    }
    return START_NOT_STICKY
  }

  private fun startInForeground() {
    val nm = getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Screen capture", NotificationManager.IMPORTANCE_LOW))

    val openApp =
      PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    val stop =
      PendingIntent.getService(
        this,
        1,
        Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE,
      )
    val notification: Notification =
      NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_view)
        .setContentTitle("PokeMMO Companion")
        .setContentText("Watching the screen")
        .setContentIntent(openApp)
        .addAction(0, "Stop", stop)
        .setOngoing(true)
        .build()

    ServiceCompat.startForeground(
      this,
      NOTIFICATION_ID,
      notification,
      ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
    )
  }

  private fun startCapture(resultCode: Int, resultData: Intent) {
    val mpm = getSystemService(MediaProjectionManager::class.java)
    val mp = mpm.getMediaProjection(resultCode, resultData) ?: error("MediaProjection was null")
    projection = mp

    // Must be registered before createVirtualDisplay() on Android 14+.
    mp.registerCallback(
      object : MediaProjection.Callback() {
        override fun onStop() {
          Log.i(TAG, "Projection stopped by system or user")
          stopSelf()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
          CaptureRepository.update { it.copy(contentWidth = width, contentHeight = height) }
        }
      },
      handler,
    )

    // MediaProjection mirrors the default display, so size the capture to match it.
    val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
    val (width, height) = display.orientedSize()
    val densityDpi = resources.displayMetrics.densityDpi

    val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    reader.setOnImageAvailableListener(::onImageAvailable, handler)
    imageReader = reader

    virtualDisplay =
      mp.createVirtualDisplay(
        "PokeMMOCompanionCapture",
        width,
        height,
        densityDpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        reader.surface,
        null,
        handler,
      )

    fpsWindowStart = SystemClock.elapsedRealtime()
    DebugLog.startSession()
    CaptureRepository.update {
      CaptureState(
        running = true,
        sourceDisplayId = Display.DEFAULT_DISPLAY,
        frameWidth = width,
        frameHeight = height,
        encounters = encounterLog.summary(),
      )
    }
    DebugLog.log("capture", "Capture started: ${width}x$height @ $densityDpi dpi")
  }

  private fun onImageAvailable(reader: ImageReader) {
    if (stopping) return
    try {
      processImage(reader)
    } catch (e: Exception) {
      // A bad frame (or one arriving mid-shutdown) must never take the app down.
      Log.w(TAG, "Frame failed", e)
      DebugLog.log("error", "Frame failed: $e")
    }
  }

  private fun processImage(reader: ImageReader) {
    val image = reader.acquireLatestImage() ?: return
    image.use {
      framesReceived++
      fpsWindowFrames++
      val now = SystemClock.elapsedRealtime()
      if (now - lastProcessedAt < PROCESS_INTERVAL_MS) return
      lastProcessedAt = now

      val elapsed = now - fpsWindowStart
      val fps = if (elapsed > 0) fpsWindowFrames * 1000f / elapsed else 0f
      fpsWindowStart = now
      fpsWindowFrames = 0

      converter.load(it)
      val features = converter.latestFrame()?.let(BattleDetector::features)
      val changed = features?.let { f -> tracker.update(BattleDetector.classify(f)) }
      if (changed != null) DebugLog.log("battle", "Screen state -> $changed ($features)")
      if (CaptureRepository.state.value.partyReading) readSummaryIfShown()
      if (features != null) {
        identifier.onFrame(railVisible = features.railRings > 0, changed = changed)?.let(::onBattleDecided)
        if (identifier.needsNameRead) readNames()
        if (changed == ScreenState.BATTLE) {
          participants.clear()
          memories.clear()
          hitTracker.reset()
          field = FieldState.NONE
          eventsSeen = 0
          lastEventText = null
          expectedOpponent = null
          opponentReread = 0
          battleFrames = 0
          activeReadThisBattle = false
          opponentGoneFrames = 0
          CaptureRepository.update {
            it.copy(
              currentBattle = null,
              opponentDown = false,
              opponentHp = null,
              yourStages = emptyMap(),
              oppStages = emptyMap(),
              battleLog = emptyList(),
              revealed = emptyMap(),
              field = FieldState.NONE,
            )
          }
          battleLog.reset()
        }
        // The bottom text box: the wild intro before the battle is confirmed, then every message for the history.
        if (identifier.needsIntroRead || tracker.state == ScreenState.BATTLE) readTextBox()
        if (tracker.state == ScreenState.BATTLE) {
          // Until this battle's active Pokémon has been read, try every frame (the box is hidden on the move menu).
          val due = !activeReadThisBattle || battleFrames % ACTIVE_READ_EVERY == 0
          battleFrames++
          if (features.playerBox >= 0.6f && due) readActive(features.playerBoxY)
          trackOpponentDown(features)
          if (features.opponentHp != null && CaptureRepository.state.value.currentBattle?.mons?.size == 1) {
            CaptureRepository.update { it.copy(opponentHp = features.opponentHp) }
          }
          val single = CaptureRepository.state.value.currentBattle?.mons?.size == 1
          hitTracker.onFrame(
            features.opponentHp?.toDouble()?.takeIf { single && features.opponentBox >= 0.6f },
            CaptureRepository.state.value.active?.hp?.takeIf { activeReadNow },
          )?.let(::observe)
          activeReadNow = false
          if (opponentReread > 0) rereadOpponent()
        }
        if (tracker.state != ScreenState.BATTLE && now - lastMenuReadAt >= MENU_READ_INTERVAL_MS) {
          converter.latestFrame()?.takeIf(PauseMenu::isOpen)?.let {
            lastMenuReadAt = now
            readPauseMenu()
          }
        }
        if (tracker.state == ScreenState.BATTLE) CaptureRepository.state.value.activeSlot?.let(participants::add)
        // Only while the rail is really on screen: menus, the summary screen and transitions keep the tracker on
        // Overworld but show no rings, which would read as a fainted party.
        if (tracker.state == ScreenState.OVERWORLD && features.railRings > 0) readPartyRings(features.ringArcs)
        else lastRingArcs = null
        if (changed == ScreenState.OVERWORLD) {
          awardEvs()
          if (CaptureRepository.state.value.currentBattle?.wild == false) {
            memories.values.forEach { seenLibrary.remember(it.snapshot(), System.currentTimeMillis()) }
          }
          // Keep the last active Pokémon: it's usually the one leading into the next battle.
          CaptureRepository.update { it.copy(currentBattle = null, opponentDown = false, activeOcr = null) }
        }
      }
      CaptureRepository.update { s ->
        s.copy(
          screenState = tracker.state,
          features = features,
          battlesSeen = s.battlesSeen + if (changed == ScreenState.BATTLE) 1 else 0,
          frameWidth = it.width,
          frameHeight = it.height,
          framesReceived = framesReceived,
          framesPerSecond = fps,
        )
      }
    }
  }

  /** The player's HP box: name + level above the bar, "29 / 35" in the dark strip (y0 = top of that strip). */
  private fun readActive(y0: Int) {
    val name = ocrField(FieldBox("active", 1470, y0 - 82, 1850, y0 - 26))
    val hp = ocrField(FieldBox("activeHp", 1540, y0 + 2, 1820, y0 + 50))
    val raw = "$name | $hp"
    val reading = ActivePokemon.parse(name, hp)
    val member = reading?.let { ActivePokemon.match(PartyRepository.party.value.members.filterNotNull(), it) }
    val prev = CaptureRepository.state.value
    if (prev.activeOcr != raw || prev.activeSlot != member?.slot) {
      DebugLog.log("battle", "Active (box y=$y0): \"$name\" / \"$hp\" -> $reading = slot ${member?.slot?.plus(1)}")
    }
    if (member != null) activeReadThisBattle = true
    activeReadNow = reading?.hp != null
    // A read that doesn't match the party (sprite or animation over the name) mustn't replace the known Pokémon;
    // keep it and take only a plausible HP number ("18| 24" once read as 181).
    val known = prev.activeSlot?.let { PartyRepository.party.value.members.getOrNull(it) }
    val maxHp = (member ?: known)?.stats?.get(0) ?: reading?.maxHp
    val hpNow = reading?.hp?.takeIf { maxHp == null || it <= maxHp }
    if (member != null && hpNow != null) PartyRepository.updateHp(member.slot, hpNow)
    val updated =
      when {
        member != null -> reading.copy(hp = hpNow ?: prev.active?.hp)
        prev.active != null -> prev.active.copy(hp = hpNow ?: prev.active.hp)
        else -> reading
      }
    readStages(y0)
    CaptureRepository.update {
      it.copy(active = updated ?: it.active, activeSlot = member?.slot ?: it.activeSlot, activeOcr = raw)
    }
  }

  /**
   * Party HP from the overworld rings. A reading is applied once two samples in a row agree (a sprite walking over a
   * ring can't change anything), and an exact HP (from the battle box) isn't replaced by the coarser ring estimate
   * unless the ring really moved. Both samples must show the rail with the same number of rings (a fading rail
   * loses rings one by one).
   */
  private fun readPartyRings(arcs: List<Int>) {
    val previous = lastRingArcs
    lastRingArcs = arcs
    if (previous == null || previous.size != arcs.size) return
    if (previous.count { it > PartyRail.FAINTED_MAX } != arcs.count { it > PartyRail.FAINTED_MAX }) return
    val party = PartyRepository.party.value.members
    for (slot in arcs.indices) {
      val m = party.getOrNull(slot) ?: continue
      val maxHp = m.stats?.get(0) ?: continue
      if (kotlin.math.abs(arcs[slot] - previous[slot]) > 3) continue // not stable yet
      val estimate = (PartyRail.hpFraction(arcs[slot]) * maxHp).roundToInt()
      val current = m.currentHp
      val step = maxHp * 3 / PartyRail.POINTS + 1 // what the ring can resolve
      val changed = current == null || (estimate == 0) != (current == 0) || kotlin.math.abs(estimate - current) > step
      if (changed) {
        DebugLog.log("party", "Ring ${slot + 1}: arc ${arcs[slot]}/${PartyRail.POINTS} -> HP $estimate/$maxHp (was $current)")
        PartyRepository.updateHp(slot, estimate)
      }
    }
  }

  /**
   * Stat-stage badges: under the player's HP box (y0 = top of its dark strip), and in single battles to the right
   * of the opponent's name box. A badge whose text can't be read keeps its previous value.
   */
  private fun readStages(y0: Int) {
    val frame = converter.latestFrame() ?: return
    val state = CaptureRepository.state.value
    val yours = stagesIn(frame, StatBadges.find(frame, 1400, y0 + 55, 1920, y0 + 130), state.yourStages)
    val single = state.currentBattle?.mons?.size == 1
    val theirs = if (single) stagesIn(frame, StatBadges.find(frame, 490, 145, 1300, 212), state.oppStages) else emptyMap()
    if (yours != state.yourStages || theirs != state.oppStages) {
      DebugLog.log("battle", "Stat stages: you $yours, opponent $theirs")
      CaptureRepository.update { it.copy(yourStages = yours, oppStages = theirs) }
    }
  }

  private fun stagesIn(frame: Frame, badges: List<Badge>, previous: Map<Stat, Int>): Map<Stat, Int> {
    val out = mutableMapOf<Stat, Int>()
    for (b in badges) {
      val text = ocrField(FieldBox("badge", b.left - 4, b.top - 4, b.right + 4, b.bottom + 4))
      val parsed = StatBadges.parse(text, b.raised)
      if (parsed != null) {
        out[parsed.first] = parsed.second
      } else if (text.isNotBlank() && !text.contains("Acc", true) && !text.contains("Eva", true)) {
        DebugLog.log("battle", "Unreadable stat badge: \"$text\"")
        return previous // don't drop a stage because one badge didn't read
      }
    }
    return out
  }

  /**
   * Single battles: once the opponent's HP box (top left) has been gone for two samples after its name was read,
   * it fainted and the battle is only showing its closing messages. Ball throws keep the box on screen.
   */
  private fun trackOpponentDown(features: FrameFeatures) {
    val battle = CaptureRepository.state.value.currentBattle ?: return
    if (battle.mons.size != 1) return // horde boxes sit elsewhere
    opponentGoneFrames = if (features.opponentBox < 0.3f) opponentGoneFrames + 1 else 0
    val down = opponentGoneFrames >= 2
    if (down != CaptureRepository.state.value.opponentDown) {
      DebugLog.log("battle", if (down) "Opponent's HP box gone: fainted" else "Opponent's HP box back")
      CaptureRepository.update { it.copy(opponentDown = down) }
    }
  }

  /**
   * The battle text box as clean black-on-white text (see [TextBand]), OCR'd in one go. Messages are
   * left-aligned, so only the left part of the screen is read.
   */
  private fun ocrTextBox(): List<String> {
    val (w, h, px) = converter.pixels(0f, INTRO_BAND_TOP, TEXT_BOX_RIGHT, INTRO_BAND_BOTTOM) ?: return emptyList()
    val (bw, bh, bin) = TextBand.binarize(px, w, h)
    return try {
      nameReader.readStack(bw, bh, bin).map { it.text }
    } catch (e: Exception) {
      Log.w(TAG, "Text box OCR failed", e)
      emptyList()
    }
  }

  /** OCRs a horizontal band of the latest frame (fractions of its height), in frame coordinates. Capture thread only. */
  private fun ocrBand(top: Float, bottom: Float): List<OcrLine> {
    val band = converter.copyRegion(0f, top, 1f, bottom) ?: return emptyList()
    val offsetY = (top * (converter.latestFrame()?.height ?: 0)).toInt() // same rounding as copyRegion
    return try {
      nameReader.readLines(band, offsetY)
    } catch (e: Exception) {
      Log.w(TAG, "OCR failed", e)
      emptyList()
    } finally {
      band.recycle()
    }
  }

  /** Bottom text box, where "A wild X appeared!" shows at the start of a wild battle. */
  private fun readTextBox() {
    val lines = ocrTextBox()
    if (identifier.needsIntroRead) {
      if (lines.isNotEmpty()) {
        CaptureRepository.update { it.copy(lastIntroText = lines.joinToString(" | ")) }
        DebugLog.log("ocr", "Intro: ${lines.joinToString(" | ")}")
      }
      identifier.onIntroRead(lines)
    }
    if (tracker.state == ScreenState.BATTLE && lines.any { "FIGHT" in it } && lines.any { "BAG" in it || "RUN" in it }) {
      hitTracker.onTurnStart()
    }
    if (tracker.state == ScreenState.BATTLE && battleLog.onText(lines)) {
      val events = battleLog.events.toList()
      DebugLog.log("battle", "History: ${events.last()}")
      CaptureRepository.update { it.copy(battleLog = events) }
      // The newest event can be replaced by a fuller read of the same message: handle it again when it changes.
      val from = if (eventsSeen > 0 && events.size >= eventsSeen && events[eventsSeen - 1].text != lastEventText) eventsSeen - 1 else eventsSeen
      for (i in from until events.size) onBattleEvent(i, events[i])
      eventsSeen = events.size
      lastEventText = events.lastOrNull()?.text
    }
  }

  /** Opponent name boxes at the top of the screen (single and both horde rows), plus each one's Pokédex ball. */
  private fun readNames() {
    identifier.onNameRead(scanNames())?.let(::onBattleDecided)
  }

  private fun scanNames(): List<WildMon> {
    val lines = ocrBand(NAME_BAND_TOP, NAME_BAND_BOTTOM)
    val frame = converter.latestFrame()
    val mons =
      EncounterParser.parseLines(lines).map { (mon, levelBox) ->
        if (frame == null || levelBox == null) {
          mon
        } else {
          // The gender icon also settles Nidoran♀/♂ when OCR dropped the symbol from the name.
          val name = GenderIcon.resolveName(mon.name, GenderIcon.detect(frame, levelBox))
          mon.copy(name = name, caught = CaughtIcon.isCaught(frame, levelBox))
        }
      }
    CaptureRepository.update { it.copy(lastOcrText = lines.joinToString(" | ") { l -> l.text }) }
    DebugLog.log("ocr", "Names: ${lines.joinToString(" | ") { l -> l.text }} -> $mons")
    return mons
  }

  /** While "Read party" is on: if a summary screen is showing, read its page into the party slot it highlights. */
  private fun readSummaryIfShown() {
    val frame = converter.latestFrame() ?: return
    val slot = PartySlot.highlighted(frame) ?: return
    val kind = SummaryTabs.highlighted(frame) ?: return // mid page-flip: no tab highlighted yet
    captureIcon(frame, slot)
    val key = slot to kind
    if (key != lastSummaryKey) {
      lastSummaryKey = key
      lastSummaryComplete = false
      incompleteReads = 0
    } else if (lastSummaryComplete) {
      return // this page was already read fully; wait for the next page or Pokémon
    }
    if (kind == SummaryPageKind.OTHER) {
      lastSummaryComplete = true
      CaptureRepository.update { it.copy(partyStatus = "Slot ${slot + 1} · (page not needed)") }
      return
    }

    val data = GameDataLoader.get(this)
    val fields = SummaryLayout.fields(kind)
    val texts =
      (ocrNumberFields(fields.filter { it.type != FieldType.TEXT }, attempt = incompleteReads) +
          fields.filter { it.type == FieldType.TEXT }.associate { f -> f.id to ocrField(f) })
        .filterValues { it.isNotEmpty() }
    val page = SummaryParser.fromFields(kind, texts, { data.matchMove(it)?.name }, knownItems::match)
    val result = PartyRepository.apply(slot, page)
    // A rejected page isn't done: keep reading in case it was a misread, and report why.
    lastSummaryComplete = page.isComplete && (result == ApplyResult.STORED || result == ApplyResult.UNCHANGED)
    DebugLog.log("party", "slot ${slot + 1} $kind complete=${page.isComplete} $result read=$texts -> $page")
    DebugLog.log("party", "slot ${slot + 1} stored: ${PartyRepository.party.value.members[slot]}")

    if (!page.isComplete && ++incompleteReads == 3 && debugFramesSaved.add(key)) {
      saveFrame("party_slot${slot + 1}_$kind") // ground truth for whatever didn't read
    }
    val note =
      when (result) {
        ApplyResult.NEEDS_INFO -> " — open the first tab (Pokédex/Name) first"
        ApplyResult.NAME_MISMATCH -> " — name \"${page.headerName}\" isn't this slot's Pokémon, not recorded"
        ApplyResult.DEX_PENDING -> " (checking Pokédex number)"
        else -> if (page.isComplete) " ✓" else " (retrying)"
      }
    CaptureRepository.update { it.copy(partyStatus = "Slot ${slot + 1} · $kind$note") }
  }

  /**
   * Saves the viewed Pokémon's icon (full strength on the highlighted tile) once per species, keyed by its
   * Pokédex number from the info tab. Captured from the user's own screen; stays on the device.
   */
  private fun captureIcon(frame: Frame, slot: Int) {
    val dex = PartyRepository.party.value.members.getOrNull(slot)?.dexId ?: return
    if (SpriteStore.has(dex)) return
    val icon = PartyIcon.cutout(frame, slot) ?: return
    SpriteStore.save(dex, icon)
    DebugLog.log("party", "Saved icon for #$dex (${icon.width}x${icon.height}) from slot ${slot + 1}")
  }

  /** OCR of one summary value box. Capture thread only. */
  private fun ocrField(f: FieldBox): String {
    val crop = converter.copyRefRect(f.left, f.top, f.right, f.bottom) ?: return ""
    return try {
      nameReader.readField(crop)
    } catch (e: Exception) {
      Log.w(TAG, "Field OCR failed", e)
      ""
    } finally {
      crop.recycle()
    }
  }

  /**
   * Number boxes: all of a page's digits as one black-on-white column (see [ValueBox.stack]), OCR'd once and split
   * back into rows by height. Each retry uses a different enlargement, so it isn't the same image again. A single-
   * number row whose digit count doesn't match its characters falls back to "all ones" when they're all narrow.
   */
  private fun ocrNumberFields(fields: List<FieldBox>, attempt: Int): Map<String, String> {
    if (fields.isEmpty()) return emptyMap()
    val frame = converter.latestFrame() ?: return emptyMap()
    val glyphs = fields.map { ValueBox.glyphs(frame, it) }
    val scale = intArrayOf(3, 4, 2)[attempt % 3]
    val stack = ValueBox.stack(frame, glyphs, scale) ?: return emptyMap()
    val lines =
      try {
        nameReader.readStack(stack.width, stack.height, stack.pixels)
      } catch (e: Exception) {
        Log.w(TAG, "Number OCR failed", e)
        emptyList()
      }
    val perRow = arrayOfNulls<String>(fields.size)
    for (line in lines) {
      if (line.elements.isEmpty()) continue
      val row = stack.rowAt((line.elements.minOf { it.box.top } + line.elements.maxOf { it.box.bottom }) / 2) ?: continue
      perRow[row] = listOfNotNull(perRow[row], line.text).joinToString(" ")
    }
    return fields.mapIndexed { i, f ->
      val text = perRow[i].orEmpty()
      val result = if (f.type == FieldType.NUMBER) ValueBox.reconcile(text, glyphs[i], frame) else text
      if (result != text) DebugLog.log("party", "${f.id}: OCR \"$text\" -> \"$result\" (read from digit shapes)")
      f.id to result
    }.toMap()
  }

  /** Wild battles are logged as encounters; trainer battles (no "wild" intro seen) are only shown. */
  /** The opposing Pokémon's memory (single battles), created on first use with any "seen before" set. */
  private fun memory(): OpponentMemory? {
    val mon = CaptureRepository.state.value.currentBattle?.mons?.singleOrNull() ?: return null
    val species = GameDataLoader.get(this).species(mon.name) ?: return null
    return memories.getOrPut("${species.name}|${mon.level}") { OpponentMemory(species, mon.level) }
  }

  private fun publishMemory() {
    val wild = CaptureRepository.state.value.currentBattle?.wild ?: true
    val revealed = memories.mapValues { (_, m) -> m.snapshot(if (wild) null else seenLibrary.get(m.species.name, m.level)) }
    CaptureRepository.update { it.copy(revealed = revealed) }
  }

  /** A new battle message: moves and what they did, revealed abilities/items, faints and trainer switches. */
  private fun onBattleEvent(index: Int, e: BattleEvent) {
    val mem = memory()
    if (e.move != null) {
      hitTracker.onMove(index, e).forEach(::observe)
      if (e.side == Side.FOE && mem != null) {
        mem.revealMove(GameDataLoader.get(this).move(e.move)?.name ?: e.move)
        publishMemory()
      }
      return
    }
    val text = e.text
    val lower = text.lowercase()
    val newField = BattleField.apply(field, text)
    if (newField != field) {
      field = newField
      DebugLog.log("battle", "Field: $field")
      CaptureRepository.update { it.copy(field = field) }
    }
    PartyMessages.levelUp(text)?.let { (name, level) ->
      PartyRepository.byName(name)?.let { m ->
        DebugLog.log("party", "${m.species} grew to Lv $level (stats need a re-read)")
        PartyRepository.leveledUp(m.slot, level)
      }
    }
    PartyMessages.learned(text)?.let { (name, moveText) ->
      val move = GameDataLoader.get(this).matchMove(moveText)?.name ?: return@let
      PartyRepository.byName(name)?.let { m ->
        DebugLog.log("party", "${m.species} learned $move")
        PartyRepository.learned(m.slot, move)
      }
    }
    if ("gotcha" in lower || "was caught" in lower) {
      CaptureRepository.state.value.currentBattle?.mons?.forEach { mon ->
        if (mon.name.lowercase() in lower) GameDataLoader.get(this).species(mon.name)?.let { DexProgress.record(it.id, true) }
      }
    }
    val party = PartyRepository.party.value.members.filterNotNull()
    val ourNames = party.flatMap { listOfNotNull(it.nickname, it.species) }.map { it.lowercase() }
    if ("fainted" in lower) {
      val yours = ourNames.any { lower.startsWith(it) }
      hitTracker.onFainted(yours)?.let(::observe)
    }
    if ("sent out" in lower && ourNames.none { lower.contains("sent out $it") }) {
      // A trainer's next Pokémon: read its name box again until it shows the one announced.
      expectedOpponent = Regex("""sent out (.+?)!?$""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.trim()
      opponentReread = OPPONENT_REREAD_FRAMES
    }
    if (mem != null) {
      val ability = Reveal.ability(text, mem.species)
      val item = Reveal.item(text, mem.species, itemNames)
      if (ability != null || item != null) {
        ability?.let(mem::revealAbility)
        item?.let(mem::revealItem)
        DebugLog.log("memory", "${mem.species.name}: ability $ability, item $item from \"$text\"")
        publishMemory()
      }
    }
  }

  /** A trainer sent out another Pokémon: read the opponent's name box until it shows a new one. */
  private fun rereadOpponent() {
    opponentReread--
    val mon = scanNames().singleOrNull() ?: return
    val current = CaptureRepository.state.value.currentBattle ?: return
    val old = current.mons.singleOrNull()
    // The fainted Pokémon's box can still be up for a moment: wait for the announced one (or, if the announcement
    // wasn't readable, any different species/level).
    val wanted = expectedOpponent
    if (wanted != null && !mon.name.equals(wanted, ignoreCase = true)) return
    if (wanted == null && old != null && old.name == mon.name && old.level == mon.level) return
    opponentReread = 0
    expectedOpponent = null
    hitTracker.reset()
    DebugLog.log("memory", "Opponent switched to $mon")
    CaptureRepository.update { it.copy(currentBattle = current.copy(mons = listOf(mon)), opponentHp = null, oppStages = emptyMap()) }
    memory()
    publishMemory()
  }

  /** A measured hit or turn order: narrow the opponent's stats. */
  private fun observe(o: Observation) {
    val mem = memory() ?: return
    val state = CaptureRepository.state.value
    val data = GameDataLoader.get(this)
    val member = state.activeSlot?.let { PartyRepository.party.value.members.getOrNull(it) } ?: return
    val events = state.battleLog
    val statuses = BattleLog.statuses(events)
    fun move(i: Int) = events.getOrNull(i)?.move?.let(data::move)
    fun crit(i: Int) = events.getOrNull(i)?.outcomes?.any { "critical" in it } == true
    when (o) {
      is Observation.YouHit -> {
        val m = move(o.event) ?: return
        val you = BattleAdvisor.battler(data, member, null, state.yourStages) ?: return
        mem.onHitTaken(you, m, crit(o.event), o.before, o.after, state.oppStages, fieldNow(statuses).forYou())
      }
      is Observation.ItHit -> {
        val m = move(o.event) ?: return
        val you = BattleAdvisor.battler(data, member, o.before, state.yourStages) ?: return
        mem.onHitDealt(you, m, crit(o.event), o.before - o.after, o.ko, state.oppStages, fieldNow(statuses).forIt())
      }
      is Observation.Order -> {
        val a = move(o.first) ?: return
        val b = move(o.second) ?: return
        if (a.priority != 0 || b.priority != 0 || "PAR" in statuses.values) return
        val you = BattleAdvisor.battler(data, member, null, state.yourStages) ?: return
        mem.onTurnOrder(o.itFirst, you.effectiveSpe, state.oppStages[Stat.SPE] ?: 0)
      }
    }
    DebugLog.log("memory", "${mem.species.name}: $o -> ${mem.snapshot().ranges}${mem.notes.lastOrNull()?.let { " ($it)" } ?: ""}")
    publishMemory()
  }

  private fun fieldNow(statuses: Map<Side, String>) = field.copy(yourStatus = statuses[Side.YOU], itsStatus = statuses[Side.FOE])

  /** Pause menu open: read its header for the place, channel and game clock. */
  private fun readPauseMenu() {
    val bmp = converter.copyRefRect(PauseMenu.HEADER_LEFT, PauseMenu.HEADER_TOP, PauseMenu.HEADER_RIGHT, PauseMenu.HEADER_BOTTOM) ?: return
    val lines =
      try {
        nameReader.readLines(bmp).map { it.text }
      } catch (e: Exception) {
        Log.w(TAG, "Menu OCR failed", e)
        emptyList()
      } finally {
        bmp.recycle()
      }
    val info = PauseMenu.parse(lines) ?: return
    // Only known names are shown: places with wild Pokémon, or the towns/cities of the gym list.
    val known = knownPlaces.match(info.place) ?: return
    val candidates = places.match(known)
    val place = places.resolve(candidates, lastRegion)
    if (place != null && candidates.map { it.region }.distinct().size == 1) lastRegion = place.region
    val prev = CaptureRepository.state.value.here
    val here =
      Here(
        place = place,
        candidates = if (place == null) candidates else emptyList(),
        menuName = known,
        season = info.season,
        timeOfDay = info.timeOfDay,
        clock = if (info.hour != null && info.minute != null) "%02d:%02d".format(info.hour, info.minute) else null,
        fromMenu = true,
        at = System.currentTimeMillis(),
      )
    if (prev?.menuName != here.menuName || prev?.clock != here.clock) DebugLog.log("place", "Menu: ${lines.joinToString(" | ")} -> $here")
    // A new place starts a new set of sightings.
    if (prev?.place != place) sightings.clear()
    CaptureRepository.update { it.copy(here = here) }
  }

  /**
   * Between menu reads, wild encounters tell where the player is: places having all the recent sightings. A menu
   * read that still fits the encounters is kept.
   */
  private fun guessPlace(result: BattleResult) {
    val data = GameDataLoader.get(this)
    val now = System.currentTimeMillis()
    val seen = result.mons.mapNotNull { m -> data.species(m.name)?.let { Sighting(it.id, m.level) } }.distinct()
    if (seen.isEmpty()) return
    sightings.removeAll { now - it.first > SIGHTING_MAX_AGE_MS }
    seen.forEach { sightings.addLast(now to it) }
    while (sightings.size > MAX_SIGHTINGS) sightings.removeFirst()
    val current = CaptureRepository.state.value.here
    // A place from the menu or picked by hand stays while the encounters still fit it.
    val fits = current?.place?.let { p -> places.fromSightings(seen, p.region).contains(p) } == true
    if (fits) return
    var guess = places.fromSightings(sightings.map { it.second }, lastRegion)
    if (guess.isEmpty()) {
      // Moved on: start over from this battle alone.
      sightings.clear()
      seen.forEach { sightings.addLast(now to it) }
      guess = places.fromSightings(seen, lastRegion).ifEmpty { places.fromSightings(seen) }
    }
    if (guess.isEmpty()) return
    val here =
      Here(
        place = guess.singleOrNull(),
        candidates = if (guess.size in 2..6) guess else emptyList(),
        season = current?.season,
        timeOfDay = current?.timeOfDay,
        clock = current?.clock,
        fromMenu = false,
        at = now,
      )
    DebugLog.log("place", "From encounters ${sightings.map { it.second }}: ${guess.take(6)}")
    CaptureRepository.update { it.copy(here = here) }
  }

  /**
   * A wild battle just ended: everything defeated gives its EV yield to the party members that battled and to
   * Exp. Share holders (doubled by a Macho Brace). Reaching a target fires the EV alert.
   */
  private fun awardEvs() {
    val state = CaptureRepository.state.value
    val battle = state.currentBattle?.takeIf { it.wild } ?: return
    val data = GameDataLoader.get(this)
    val defeated = BattleOutcome.defeated(battle.mons.map { it.name }, state.battleLog.map { it.text }).mapNotNull(data::species)
    if (defeated.isEmpty()) return
    val party = PartyRepository.party.value.members
    val fighters = participants.ifEmpty { setOfNotNull(state.activeSlot) }
    val awards = EvRules.award(defeated, fighters, party)
    if (awards.isEmpty()) return
    val reached = EvRepository.apply(awards, party)
    DebugLog.log("evs", "Defeated ${defeated.map { it.name }}, fighters $fighters -> $awards")
    for ((slot, stats) in reached) {
      val m = party.getOrNull(slot) ?: continue
      val name = m.shownName
      Alerts.evGoal(this, slot, "$name reached its ${stats.joinToString { it.short }} EV target.")
    }
  }

  private fun onBattleDecided(result: BattleResult) {
    DebugLog.log("battle", "Battle decided: $result")
    // Shiny alerts fire even without a confirmed wild intro: a missed intro read must never hide a shiny.
    // The shiny pattern wins over the Pokédex one (a second vibration would cut the first short).
    val data = GameDataLoader.get(this)
    result.mons.forEach { m -> m.caught?.let { c -> data.species(m.name)?.let { DexProgress.record(it.id, c) } } }
    val shinies = result.mons.filter { it.shiny }.map { it.name }.distinct()
    val needed = result.mons.filter { it.caught == false }.map { it.name }.distinct()
    when {
      shinies.isNotEmpty() -> Alerts.shiny(this, shinies)
      result.wild && needed.isNotEmpty() -> Alerts.pokedexNeeded(this, needed)
    }
    if (result.wild) {
      guessPlace(result)
      val summary = encounterLog.record(System.currentTimeMillis(), result.mons)
      CaptureRepository.update { it.copy(encounters = summary, lastBattle = result, currentBattle = result) }
    } else {
      CaptureRepository.update { it.copy(lastBattle = result, currentBattle = result, trainerBattles = it.trainerBattles + 1) }
    }
  }

  /** Runs on the capture thread, where the converter's buffer lives. PNG encoding happens on [saveExecutor]. */
  private fun saveLatestFrame() = saveFrame("frame")

  private fun saveFrame(prefix: String) {
    val frame = converter.copyLatestFrame()
    if (frame == null) {
      CaptureRepository.update { it.copy(error = "No frame captured yet") }
      return
    }
    CaptureRepository.update { it.copy(saving = true, error = null) }
    saveExecutor.execute {
      try {
        val file = FrameSaver.save(applicationContext, frame, prefix)
        DebugLog.log("frame", "Saved ${file.name}")
        CaptureRepository.update { it.copy(saving = false, savedCount = it.savedCount + 1, lastSavedName = file.name) }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to save frame", e)
        CaptureRepository.update { it.copy(saving = false, error = "Save failed: ${e.message}") }
      } finally {
        frame.recycle()
      }
    }
  }

  override fun onDestroy() {
    stopping = true
    // Release everything on the capture thread, after the frame it may be processing: closing the ImageReader
    // from here while that frame still reads its buffer crashed the app on stop.
    handler.post {
      virtualDisplay?.release()
      imageReader?.close()
      projection?.stop()
      virtualDisplay = null
      imageReader = null
      projection = null
      nameReader.close()
    }
    thread.quitSafely()
    saveExecutor.shutdown() // lets a save in progress finish
    DebugLog.log("capture", "Capture stopped")
    DebugLog.endSession()
    CaptureRepository.update {
      it.copy(running = false, screenState = ScreenState.UNKNOWN, currentBattle = null, opponentDown = false, activeOcr = null)
    }
    super.onDestroy()
  }
}
