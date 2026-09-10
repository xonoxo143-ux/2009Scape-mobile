package core.local

import core.game.bots.AIPlayer
import core.net.IoSession
import core.net.packet.Context
import core.net.packet.`in`.Packet
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
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
     * Try to move an already-encoded world -> RT4 byte sequence into the shared
     * in-process stream. false means the legacy socket path remains responsible
     * for the buffer; true means ownership has moved to the local bridge.
     *
     * The first successful route also promotes the established human session
     * away from its NIO channel. That operation intentionally preserves the
     * Player/session/ISAAC state; it only retires the physical loopback link.
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
            // This class lives in the RT4 patch source set so the same compiled
            // replacement that reads packets also owns the transitional stream.
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
