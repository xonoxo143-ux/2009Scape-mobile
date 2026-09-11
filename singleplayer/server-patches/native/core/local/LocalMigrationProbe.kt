package core.local

import core.api.Event as EventType
import core.auth.AuthResponse
import core.cache.crypto.ISAACCipher
import core.cache.crypto.ISAACPair
import core.game.bots.AIPlayer
import core.game.event.Event as GameEvent
import core.game.event.EventHook
import core.game.node.entity.Entity
import core.game.node.entity.player.Player
import core.game.node.entity.player.info.ClientInfo
import core.game.node.entity.player.info.PlayerDetails
import core.game.node.entity.player.info.login.LoginParser
import core.game.world.GameWorld
import core.game.world.map.MapDistance
import core.game.world.repository.Repository
import core.net.IoSession
import core.net.packet.Context
import core.net.packet.`in`.Packet
import core.net.producer.LoginEventProducer
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Migration instrumentation and the local human-player boundary.
 *
 * The retained 2009Scape world remains authoritative for account/profile
 * storage, Player construction, LoginParser initialization, incoming typed
 * commands and outgoing packet encoding. Remote-server ceremony is intentionally
 * excluded from the local path: no IP policing, daily-account limits, master
 * server presence, socket login packet, or network session is involved.
 */
object LocalMigrationProbe {
    /**
     * Revision-530 NPC/player add packets encode signed relative coordinates in
     * five bits, so the retained entity synchronization radius must remain 15.
     * This is deliberately independent from RT4's terrain/visual view distance.
     */
    private const val ENTITY_SYNC_DISTANCE = 15

    private val incomingCounts = ConcurrentHashMap<String, AtomicLong>()
    private val outgoingCounts = ConcurrentHashMap<String, AtomicLong>()

    private val localSessionExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "singleplayer-local-session").apply { isDaemon = true }
    }

    @Volatile private var presentationResolved = false
    @Volatile private var presentationRequested: Method? = null
    @Volatile private var presentationOffer: Method? = null

    @JvmStatic
    fun observeIncoming(packet: Packet) {
        increment(incomingCounts, packet.javaClass.simpleName, "IN")
    }

    @JvmStatic
    fun observeOutgoing(handler: Class<*>, context: Context) {
        val key = handler.simpleName + ":" + context.javaClass.simpleName
        increment(outgoingCounts, key, "OUT")
    }

    /**
     * Create the one human player directly in the retained world model.
     *
     * DevelopmentAuthenticator is deliberately retained because it is the
     * existing local account/profile store. LoginParser is deliberately retained
     * because it owns save parsing, spawn restoration, login hooks, player.init,
     * varp restoration and the normal initial world presentation packets.
     */
    @JvmStatic
    @Synchronized
    fun beginLocalLogin(
        username: String,
        displayMode: Int,
        windowMode: Int,
        screenWidth: Int,
        screenHeight: Int,
        seed: IntArray
    ): Boolean {
        if (!java.lang.Boolean.getBoolean("singleplayer")) return false
        if (username.isBlank() || seed.size != 4) return false

        // Keep entity synchronization protocol-safe regardless of the client
        // terrain/view radius selected by the Android runtime.
        enforceEntitySyncDistance()

        if (Repository.getPlayerByName(username) != null) return false

        val (response, accountInfo) = GameWorld.authenticator.checkLogin(username, "local")
        if (response != AuthResponse.Success || accountInfo == null) {
            System.err.println("SINGLEPLAYER_LOCAL_LOGIN: authentication failed: $response")
            return false
        }

        val session = IoSession(null, localSessionExecutor, "127.0.0.1")
        session.associatedUsername = username
        session.clientInfo = ClientInfo(displayMode, windowMode, screenWidth, screenHeight)

        // Preserve the stock revision-530 cipher relationship so the retained
        // outgoing encoder and RT4 decoder can continue to speak exactly the same
        // internal protocol while the transport itself is in memory.
        val inputSeed = seed.copyOf()
        val outputSeed = IntArray(seed.size) { index -> seed[index] + 50 }
        session.isaacPair = ISAACPair(ISAACCipher(inputSeed), ISAACCipher(outputSeed))
        session.producer = LoginEventProducer()
        session.promoteToLocalTransport()

        val details = PlayerDetails(username)
        details.accountInfo = accountInfo
        // DevelopmentAuthenticator can return a persisted account object whose
        // username field is blank. Player(details) takes its authoritative entity
        // name from details.getUsername(), so restore the requested local profile
        // name before constructing the Player. Otherwise revision-530 appearance
        // packets encode name37=0 and RT4 rejects them during live player updates.
        details.accountInfo.setUsername(username)
        details.communication.parse(accountInfo)
        details.session = session

        val player = Player(details)
        Repository.addPlayer(player)
        session.lastPing = System.currentTimeMillis()

        return try {
            // LoginParser performs the actual profile parse/player.init on the
            // world pulse. LeagueRuntime is attached later, after RT4 receives
            // the resulting successful rebuild, so persisted league attributes
            // are guaranteed to have been parsed first.
            LoginParser(details).initialize(player, false)
            println("SINGLEPLAYER_LOCAL_LOGIN: SESSION_CREATED username=$username")
            true
        } catch (failure: Throwable) {
            Repository.removePlayer(player)
            session.disconnect()
            System.err.println(
                "SINGLEPLAYER_LOCAL_LOGIN: initialization failed: " +
                    "${failure.javaClass.simpleName}: ${failure.message}"
            )
            false
        }
    }

    /** Attach league rules only after normal save parsing/player.init completed. */
    @JvmStatic
    @Synchronized
    fun attachLeagueRuntime(username: String): Boolean {
        if (!java.lang.Boolean.getBoolean("singleplayer") || username.isBlank()) return false
        val player = Repository.getPlayerByName(username) ?: return false
        if (!player.getAttribute("logged-in-fully", false)) return false
        LeagueRuntime.attach(player)
        return true
    }

    /** Restore the protocol-safe entity synchronization radius. */
    private fun enforceEntitySyncDistance() {
        val rendering = MapDistance.RENDERING
        if (rendering.distance == ENTITY_SYNC_DISTANCE) return

        val field = MapDistance::class.java.getDeclaredField("distance")
        field.isAccessible = true
        field.setInt(rendering, ENTITY_SYNC_DISTANCE)

        val actual = rendering.distance
        if (actual != ENTITY_SYNC_DISTANCE) {
            throw IllegalStateException(
                "Entity sync distance mismatch: $actual != $ENTITY_SYNC_DISTANCE"
            )
        }
        println("SINGLEPLAYER_RUNTIME: ENTITY_SYNC_DISTANCE=$actual")
    }

    /**
     * Logical local logout. Retain IoSession.disconnect() so Player.clear(), save
     * hooks and repository cleanup remain authoritative 2009Scape behavior.
     */
    @JvmStatic
    @Synchronized
    fun endLocalSession(username: String): Boolean {
        if (!java.lang.Boolean.getBoolean("singleplayer")) return false
        if (username.isBlank()) return true
        val player = Repository.getPlayerByName(username) ?: return true
        return try {
            LeagueRuntime.detach(player)
            player.session.disconnect()
            println("SINGLEPLAYER_LOCAL_LOGIN: SESSION_CLOSED username=$username")
            true
        } catch (failure: Throwable) {
            System.err.println(
                "SINGLEPLAYER_LOCAL_LOGIN: session close failed: ${failure.javaClass.simpleName}: ${failure.message}"
            )
            false
        }
    }

    /** Route retained outgoing world bytes directly into RT4's local stream. */
    @JvmStatic
    fun routeOutgoingBytes(session: IoSession, buffer: ByteBuffer, canActivate: Boolean): Boolean {
        if (!java.lang.Boolean.getBoolean("singleplayer")) return false
        resolvePresentationBridge()

        val requested = presentationRequested ?: return false
        val offer = presentationOffer ?: return false
        val wantsCutover = try {
            requested.invoke(null) == true
        } catch (_: Throwable) {
            return false
        }
        if (!wantsCutover) return false

        val player = session.player ?: return false
        if (player is AIPlayer || player.isArtificial) return false

        val copy = ByteArray(buffer.remaining())
        buffer.duplicate().get(copy)
        return try {
            val routed = offer.invoke(null, copy, canActivate) == true
            if (routed) session.promoteToLocalTransport()
            routed
        } catch (failure: Throwable) {
            System.err.println(
                "SINGLEPLAYER_LOCAL_PRESENTATION: route failed: ${failure.javaClass.simpleName}: ${failure.message}"
            )
            false
        }
    }

    @JvmStatic
    fun incomingSnapshot(): Map<String, Long> =
        incomingCounts.mapValues { it.value.get() }.toSortedMap()

    @JvmStatic
    fun outgoingSnapshot(): Map<String, Long> =
        outgoingCounts.mapValues { it.value.get() }.toSortedMap()

    @JvmStatic
    fun reset() {
        incomingCounts.clear()
        outgoingCounts.clear()
    }

    @Synchronized
    private fun resolvePresentationBridge() {
        if (presentationResolved) return
        presentationResolved = true
        try {
            val bridge = Class.forName("rt4.LocalPresentationBridge")
            presentationRequested = bridge.getMethod("isCutoverRequested")
            presentationOffer = bridge.getMethod(
                "offerServerBytes",
                ByteArray::class.java,
                Boolean::class.javaPrimitiveType
            )
        } catch (_: Throwable) {
            presentationRequested = null
            presentationOffer = null
        }
    }

    private fun increment(
        target: ConcurrentHashMap<String, AtomicLong>,
        key: String,
        direction: String
    ) {
        val counter = target.computeIfAbsent(key) { AtomicLong() }
        if (counter.incrementAndGet() == 1L) {
            println("SINGLEPLAYER_MIGRATION: $direction $key")
        }
    }
}

/**
 * Stable single-player gameplay extension seam.
 *
 * This deliberately sits on top of 2009Scape's existing per-entity event bus
 * instead of forking skills, quests, NPC scripts, or combat code. Future league
 * implementations install one RuleSet and receive the semantic gameplay events
 * the retained game already produces.
 */
object LeagueRuntime {
    private const val ATTACHED = "league:runtime-attached"
    private const val POINTS = "league:points"
    private const val TASKS = "league:tasks"
    private const val RELICS = "league:relics"
    private const val SEP = ","

    @Volatile private var rules: RuleSet = RuleSet.VANILLA
    private val forwarders = ConcurrentHashMap<String, EventHook<GameEvent>>()

    interface RuleSet {
        fun onAttach(player: Player) {}
        fun onDetach(player: Player) {}
        fun onEvent(player: Player, event: GameEvent) {}

        companion object {
            @JvmField
            val VANILLA: RuleSet = object : RuleSet {}
        }
    }

    /** Install one ruleset for the single local human world. */
    @JvmStatic
    fun install(ruleSet: RuleSet?) {
        rules = ruleSet ?: RuleSet.VANILLA
        Repository.players
            .filter { !it.isArtificial }
            .forEach { rules.onAttach(it) }
    }

    @JvmStatic
    fun attach(player: Player) {
        if (player.isArtificial || player.getAttribute(ATTACHED, false)) return

        val forwarder = object : EventHook<GameEvent> {
            override fun process(entity: Entity, event: GameEvent) {
                if (entity is Player && !entity.isArtificial) {
                    rules.onEvent(entity, event)
                }
            }
        }

        // Retained semantic events useful for league tasks and relic effects.
        player.hook(EventType.ResourceProduced, forwarder)
        player.hook(EventType.NPCKilled, forwarder)
        player.hook(EventType.BoneBuried, forwarder)
        player.hook(EventType.Teleported, forwarder)
        player.hook(EventType.FireLit, forwarder)
        player.hook(EventType.LightSourceLit, forwarder)
        player.hook(EventType.Interacted, forwarder)
        player.hook(EventType.ButtonClicked, forwarder)
        player.hook(EventType.DialogueOpened, forwarder)
        player.hook(EventType.DialogueOptionSelected, forwarder)
        player.hook(EventType.DialogueClosed, forwarder)
        player.hook(EventType.UsedWith, forwarder)
        player.hook(EventType.SelfDeath, forwarder)
        player.hook(EventType.Tick, forwarder)
        player.hook(EventType.PickedUp, forwarder)
        player.hook(EventType.InterfaceOpened, forwarder)
        player.hook(EventType.InterfaceClosed, forwarder)
        player.hook(EventType.SpellCast, forwarder)
        player.hook(EventType.SpellbookChanged, forwarder)
        player.hook(EventType.ItemAlchemized, forwarder)
        player.hook(EventType.ItemEquipped, forwarder)
        player.hook(EventType.ItemUnequipped, forwarder)
        player.hook(EventType.ItemPurchased, forwarder)
        player.hook(EventType.ItemSold, forwarder)
        player.hook(EventType.JobAssigned, forwarder)
        player.hook(EventType.FairyRingDialed, forwarder)
        player.hook(EventType.VarbitUpdated, forwarder)
        player.hook(EventType.DynamicSkillLevelChanged, forwarder)
        player.hook(EventType.SummoningPointsRecharged, forwarder)
        player.hook(EventType.PrayerPointsRecharged, forwarder)
        player.hook(EventType.XpGained, forwarder)
        player.hook(EventType.PrayerActivated, forwarder)
        player.hook(EventType.PrayerDeactivated, forwarder)

        forwarders[player.username.lowercase()] = forwarder
        player.setAttribute(ATTACHED, true)
        rules.onAttach(player)
        println("SINGLEPLAYER_LEAGUE: EVENT_HOOKS_ATTACHED username=${player.username}")
    }

    @JvmStatic
    fun detach(player: Player) {
        val forwarder = forwarders.remove(player.username.lowercase())
        if (forwarder != null) player.unhook(forwarder)
        player.removeAttribute(ATTACHED)
        rules.onDetach(player)
    }

    /** Skills.experienceMultiplier is already persisted by PlayerSaver. */
    @JvmStatic
    fun setExperienceMultiplier(player: Player, multiplier: Double) {
        player.skills.experienceMultiplier = multiplier.coerceIn(0.0, 60.0)
    }

    @JvmStatic
    fun points(player: Player): Int = player.getAttribute(POINTS, 0)

    @JvmStatic
    fun addPoints(player: Player, amount: Int): Int {
        if (amount == 0) return points(player)
        val updated = (points(player) + amount).coerceAtLeast(0)
        player.setAttribute("/save:$POINTS", updated)
        return updated
    }

    @JvmStatic
    fun hasCompletedTask(player: Player, id: String): Boolean =
        decodeIds(player.getAttribute(TASKS, "")).contains(id)

    /** Event-driven task completion is idempotent by task ID. */
    @JvmStatic
    fun completeTask(player: Player, id: String, rewardPoints: Int): Boolean {
        if (id.isBlank()) return false
        val tasks = decodeIds(player.getAttribute(TASKS, "")).toMutableSet()
        if (!tasks.add(id)) return false
        player.setAttribute("/save:$TASKS", encodeIds(tasks))
        if (rewardPoints != 0) addPoints(player, rewardPoints)
        println(
            "SINGLEPLAYER_LEAGUE: TASK_COMPLETE id=$id points=$rewardPoints total=${points(player)}"
        )
        return true
    }

    @JvmStatic
    fun hasRelic(player: Player, id: String): Boolean =
        decodeIds(player.getAttribute(RELICS, "")).contains(id)

    @JvmStatic
    fun unlockRelic(player: Player, id: String): Boolean {
        if (id.isBlank()) return false
        val relics = decodeIds(player.getAttribute(RELICS, "")).toMutableSet()
        if (!relics.add(id)) return false
        player.setAttribute("/save:$RELICS", encodeIds(relics))
        println("SINGLEPLAYER_LEAGUE: RELIC_UNLOCK id=$id")
        return true
    }

    @JvmStatic
    fun completedTasks(player: Player): Set<String> =
        decodeIds(player.getAttribute(TASKS, ""))

    @JvmStatic
    fun unlockedRelics(player: Player): Set<String> =
        decodeIds(player.getAttribute(RELICS, ""))

    private fun decodeIds(encoded: String): Set<String> =
        if (encoded.isBlank()) emptySet()
        else encoded.split(SEP).map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun encodeIds(ids: Set<String>): String = ids.toSortedSet().joinToString(SEP)
}
