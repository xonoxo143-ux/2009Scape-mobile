package core.local

import core.game.node.entity.player.Player
import core.game.world.repository.Repository
import core.net.packet.PacketProcessor
import core.net.packet.`in`.Packet

/**
 * Direct, typed ingress into the existing 2009Scape command processor.
 *
 * This is the replacement boundary for client->world TCP. The existing Packet
 * subclasses are deliberately reused as game commands so content processing is
 * not rewritten during the transport migration.
 */
object LocalCommandIngress {
    @JvmStatic
    fun hasPlayer(playerName: String): Boolean = findPlayer(playerName) != null

    @JvmStatic
    fun ping(playerName: String): Boolean = withPlayer(playerName) {
        PacketProcessor.enqueue(Packet.Ping(it))
    }

    @JvmStatic
    fun worldspaceWalk(
        playerName: String,
        destX: Int,
        destY: Int,
        run: Boolean
    ): Boolean = withPlayer(playerName) {
        PacketProcessor.enqueue(Packet.WorldspaceWalk(it, destX, destY, run))
    }

    @JvmStatic
    fun minimapWalk(
        playerName: String,
        destX: Int,
        destY: Int,
        clickedX: Int,
        clickedY: Int,
        rotation: Int,
        run: Boolean
    ): Boolean = withPlayer(playerName) {
        PacketProcessor.enqueue(
            Packet.MinimapWalk(
                it,
                destX,
                destY,
                clickedX,
                clickedY,
                rotation,
                run
            )
        )
    }

    @JvmStatic
    fun npcAction(
        playerName: String,
        option: Int,
        npcIndex: Int
    ): Boolean = withPlayer(playerName) {
        PacketProcessor.enqueue(Packet.NpcAction(it, option, npcIndex))
    }

    @JvmStatic
    fun playerAction(
        playerName: String,
        option: Int,
        otherIndex: Int
    ): Boolean = withPlayer(playerName) {
        PacketProcessor.enqueue(Packet.PlayerAction(it, option, otherIndex))
    }

    @JvmStatic
    fun sceneryAction(
        playerName: String,
        option: Int,
        sceneryId: Int,
        x: Int,
        y: Int
    ): Boolean = withPlayer(playerName) {
        PacketProcessor.enqueue(
            Packet.SceneryAction(it, option, sceneryId, x, y)
        )
    }

    @JvmStatic
    fun groundItemAction(
        playerName: String,
        option: Int,
        itemId: Int,
        x: Int,
        y: Int
    ): Boolean = withPlayer(playerName) {
        PacketProcessor.enqueue(
            Packet.GroundItemAction(it, option, itemId, x, y)
        )
    }

    @JvmStatic
    fun itemAction(
        playerName: String,
        option: Int,
        itemId: Int,
        slot: Int,
        iface: Int,
        child: Int
    ): Boolean = withPlayer(playerName) {
        PacketProcessor.enqueue(
            Packet.ItemAction(it, option, itemId, slot, iface, child)
        )
    }

    @JvmStatic
    fun interfaceAction(
        playerName: String,
        opcode: Int,
        option: Int,
        iface: Int,
        child: Int,
        slot: Int,
        itemId: Int
    ): Boolean = withPlayer(playerName) {
        PacketProcessor.enqueue(
            Packet.IfAction(
                it,
                opcode,
                option,
                iface,
                child,
                slot,
                itemId
            )
        )
    }

    @JvmStatic
    fun continueOption(
        playerName: String,
        iface: Int,
        child: Int,
        slot: Int,
        opcode: Int
    ): Boolean = withPlayer(playerName) {
        PacketProcessor.enqueue(
            Packet.ContinueOption(it, iface, child, slot, opcode)
        )
    }

    @JvmStatic
    fun closeInterface(playerName: String): Boolean = withPlayer(playerName) {
        PacketProcessor.enqueue(Packet.CloseIface(it))
    }

    @JvmStatic
    fun inputPromptString(playerName: String, response: String): Boolean =
        withPlayer(playerName) {
            PacketProcessor.enqueue(Packet.InputPromptResponse(it, response))
        }

    @JvmStatic
    fun inputPromptInt(playerName: String, response: Int): Boolean =
        withPlayer(playerName) {
            PacketProcessor.enqueue(Packet.InputPromptResponse(it, response))
        }

    private inline fun withPlayer(
        playerName: String,
        action: (Player) -> Unit
    ): Boolean {
        val player = findPlayer(playerName) ?: return false
        action(player)
        return true
    }

    private fun findPlayer(playerName: String): Player? =
        Repository.getPlayerByName(playerName)
}
