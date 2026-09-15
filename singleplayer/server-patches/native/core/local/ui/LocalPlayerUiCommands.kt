package core.local.ui

import core.api.TickListener
import core.game.global.action.EquipHandler
import core.game.interaction.IntType
import core.game.interaction.InteractionListeners
import core.game.node.entity.player.Player
import core.game.node.item.Item
import core.game.world.repository.Repository
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Semantic Android/native-UI -> world command boundary.
 *
 * Calls may originate on an Android/JNI thread, but game mutation never does.
 * Commands are only validated structurally at enqueue time and are drained on
 * the authoritative world tick before invoking retained 2009Scape handlers.
 */
object LocalPlayerUiCommands {
    private sealed interface Command {
        fun apply(player: Player)
    }

    private data class EquipInventory(val slot: Int) : Command {
        override fun apply(player: Player) {
            val item = player.inventory[slot] ?: return
            val wearable = item.definition.options.any {
                it?.equals("equip", true) == true ||
                    it?.equals("wield", true) == true ||
                    it?.equals("wear", true) == true
            }
            if (!wearable) {
                player.packetDispatch.sendMessage("You can't wear that.")
                return
            }
            InteractionListeners.run(item.id, IntType.ITEM, "equip", player, item)
        }
    }

    private data class UnequipEquipment(val slot: Int) : Command {
        override fun apply(player: Player) {
            val item = player.equipment[slot] ?: return
            EquipHandler.unequip(player, slot, item.id)
        }
    }

    private data class InventoryAction(val slot: Int, val action: String) : Command {
        override fun apply(player: Player) {
            val item = player.inventory[slot] ?: return
            runItemAction(player, item, action, false)
        }
    }

    private data class EquipmentAction(val slot: Int, val action: String) : Command {
        override fun apply(player: Player) {
            val item = player.equipment[slot] ?: return
            if (action.equals("unequip", true) || action.equals("remove", true)) {
                EquipHandler.unequip(player, slot, item.id)
                return
            }
            if (action.equals("operate", true)) {
                if (InteractionListeners.run(item.id, IntType.ITEM, "operate", player, item)) return
                if (item.operateHandler?.handle(player, item, "operate") == true) return
                player.packetDispatch.sendMessage("You can't operate that.")
                return
            }
            runItemAction(player, item, action, true)
        }
    }

    private val pending = ConcurrentLinkedQueue<Command>()

    @JvmStatic
    fun equipInventorySlot(slot: Int): Boolean {
        if (slot !in 0 until 28) return false
        pending.add(EquipInventory(slot))
        return true
    }

    @JvmStatic
    fun unequipEquipmentSlot(slot: Int): Boolean {
        if (slot !in 0 until 14) return false
        pending.add(UnequipEquipment(slot))
        return true
    }

    @JvmStatic
    fun inventoryAction(slot: Int, action: String?): Boolean {
        if (slot !in 0 until 28) return false
        val semantic = normalize(action) ?: return false
        pending.add(InventoryAction(slot, semantic))
        return true
    }

    @JvmStatic
    fun equipmentAction(slot: Int, action: String?): Boolean {
        if (slot !in 0 until 14) return false
        val semantic = normalize(action) ?: return false
        pending.add(EquipmentAction(slot, semantic))
        return true
    }

    internal fun drain(player: Player) {
        var count = 0
        while (count++ < 64) {
            val command = pending.poll() ?: break
            try {
                command.apply(player)
            } catch (failure: Throwable) {
                System.err.println(
                    "SINGLEPLAYER_NATIVE_UI: command failed: " +
                        failure.javaClass.simpleName + ": " + failure.message
                )
            }
        }
    }

    internal fun discardPending() {
        pending.clear()
    }

    private fun runItemAction(player: Player, item: Item, action: String, equipment: Boolean) {
        if (player.locks.isInteractionLocked) return
        val option = item.interaction.options.firstOrNull {
            it?.name?.equals(action, true) == true
        }
        if (option != null) {
            item.interaction.handleItemOption(
                player,
                option,
                if (equipment) player.equipment else player.inventory
            )
            player.scripts.reset()
            return
        }

        // InteractionListeners also contains catch-all semantic handlers that do
        // not necessarily appear in an individual definition's option array.
        if (InteractionListeners.run(item.id, IntType.ITEM, action, player, item)) return
        player.packetDispatch.sendMessage("Nothing interesting happens.")
    }

    private fun normalize(action: String?): String? {
        val normalized = action?.trim()?.lowercase(Locale.ROOT) ?: return null
        if (normalized.isEmpty() || normalized.length > 32) return null
        return normalized
    }
}

/** Drain Android/native UI commands on the authoritative game thread. */
class LocalPlayerUiCommandProcessor : TickListener {
    override fun tick() {
        if (!java.lang.Boolean.getBoolean("singleplayer")) return
        val player = Repository.players.firstOrNull {
            !it.isArtificial && it.getAttribute("logged-in-fully", false)
        }
        if (player == null) {
            LocalPlayerUiCommands.discardPending()
            return
        }
        LocalPlayerUiCommands.drain(player)
    }
}
