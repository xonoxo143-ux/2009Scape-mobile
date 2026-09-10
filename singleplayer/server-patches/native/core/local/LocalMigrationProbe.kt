package core.local

import core.auth.AuthResponse
import core.cache.crypto.ISAACCipher
import core.cache.crypto.ISAACPair
import core.game.bots.AIPlayer
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

        // InProcessBootstrap historically coupled this value to the client's
        // visual distance. Correct it before any human entity can enter the
        // repository so retained five-bit relative entity coordinates stay safe.
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
        details.communication.parse(accountInfo)
        details.session = session

        val player = Player(details)
        Repository.addPlayer(player)
        session.lastPing = System.currentTimeMillis()

        return try {
            // false = normal first entry, not the old network reconnect mode.
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

    /**
     * Restore the server-side entity synchronization radius independently of the
     * client terrain/view distance. MapDistance stores the value in a private
     * final field, so use the same narrow reflective technique already used by
     * the migration bootstrap and verify the result immediately.
     */
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
