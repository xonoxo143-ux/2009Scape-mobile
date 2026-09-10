package core.local

import core.auth.AuthResponse
import core.cache.crypto.ISAACCipher
import core.cache.crypto.ISAACPair
import core.game.bots.AIPlayer
import core.game.node.entity.player.info.ClientInfo
import core.game.node.entity.player.info.PlayerDetails
import core.game.world.GameWorld
import core.game.world.repository.Repository
import core.net.IoSession
import core.net.packet.Context
import core.net.packet.`in`.Login
import core.net.packet.`in`.Packet
import core.net.producer.LoginEventProducer
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Migration instrumentation and narrow local-transport adapter.
 *
 * Commands are observed after decoding and presentations before encoding. The
 * optional byte route lets the retained outgoing encoders feed RT4 in-process
 * without making 2009Scape compile against the RT4 migration implementation.
 */
object LocalMigrationProbe {
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
     * Create the human player's normal 2009Scape session without parsing a TCP
     * login packet. The existing authenticator, PlayerDetails, Login.proceedWith
     * and LoginParser initialization are deliberately retained.
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
        if (Repository.getPlayerByName(username) != null) return false

        val (response, accountInfo) = GameWorld.authenticator.checkLogin(username, "local")
        if (response != AuthResponse.Success || accountInfo == null) {
            System.err.println("SINGLEPLAYER_LOCAL_LOGIN: authentication failed: $response")
            return false
        }

        val session = IoSession(null, localSessionExecutor, "127.0.0.1")
        session.associatedUsername = username
        session.clientInfo = ClientInfo(displayMode, windowMode, screenWidth, screenHeight)

        val inputSeed = seed.copyOf()
        val outputSeed = IntArray(seed.size) { index -> seed[index] + 50 }
        session.isaacPair = ISAACPair(ISAACCipher(inputSeed), ISAACCipher(outputSeed))

        // The TCP handshake normally performs this state transition. Keep the
        // existing LoginWriteEvent/GameEventProducer path, but establish its
        // producer directly because there is no handshake packet anymore.
        session.producer = LoginEventProducer()

        // There is no transport to detach for a fresh local session, but this
        // puts IoSession into its explicit local mode before LoginParser begins
        // emitting the normal success response and initial world packets.
        session.promoteToLocalTransport()

        val details = PlayerDetails(username)
        details.accountInfo = accountInfo
        details.communication.parse(accountInfo)

        Login.proceedWith(session, details, 16)
        println("SINGLEPLAYER_LOCAL_LOGIN: SESSION_CREATED username=$username")
        return true
    }

    /**
     * Logical local logout. This deliberately uses the retained IoSession
     * disconnect path so Player.clear(), save hooks and repository cleanup stay
     * authoritative instead of being recreated in the Android/client layer.
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

    /**
     * Move an already-encoded world -> RT4 byte sequence into the shared
     * in-process stream. A successful route also promotes any legacy-established
     * human session away from its NIO channel; fresh local sessions are already
     * promoted.
     */
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
            if (routed) {
                session.promoteToLocalTransport()
            }
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
