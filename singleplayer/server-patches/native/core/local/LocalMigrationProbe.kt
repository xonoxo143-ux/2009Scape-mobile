package core.local

import core.net.packet.Context
import core.net.packet.`in`.Packet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Shadow-mode instrumentation for removing the local network protocol safely.
 * It observes the already-decoded command and pre-encoding presentation types;
 * it never changes gameplay behavior.
 */
object LocalMigrationProbe {
    private val incomingCounts = ConcurrentHashMap<String, AtomicLong>()
    private val outgoingCounts = ConcurrentHashMap<String, AtomicLong>()

    @JvmStatic
    fun observeIncoming(packet: Packet) {
        increment(incomingCounts, packet.javaClass.simpleName, "IN")
    }

    @JvmStatic
    fun observeOutgoing(handler: Class<*>, context: Context) {
        val key = handler.simpleName + ":" + context.javaClass.simpleName
        increment(outgoingCounts, key, "OUT")
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
