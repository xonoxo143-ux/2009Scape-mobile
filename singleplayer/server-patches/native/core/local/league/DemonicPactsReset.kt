package core.local.league

import core.game.node.entity.player.Player
import core.game.node.item.Item

/** Exact custom-item cleanup used by the League UI reset path. */
object DemonicPactsReset {
    private val itemIds = intArrayOf(
        DemonicPactsItemIds.KNAPSACK,
        DemonicPactsItemIds.SEARING_BOOTS,
        DemonicPactsItemIds.FORAGERS_POUCH,
        DemonicPactsItemIds.BANKERS_BRIEFCASE,
        DemonicPactsItemIds.EVIL_EYE,
        DemonicPactsItemIds.MAP_OF_ALACRITY,
        DemonicPactsItemIds.TRANSMUTATION_LEDGER,
        DemonicPactsItemIds.BUTLERS_BELL,
        DemonicPactsItemIds.FAIRY_MUSHROOM,
        DemonicPactsItemIds.SOUL_SHARD,
        DemonicPactsItemIds.ARCANE_GRIMOIRE,
        DemonicPactsItemIds.SAGES_AXE,
        DemonicPactsItemIds.MINION_WHISTLE,
        DemonicPactsItemIds.FLASK_OF_FERVOUR
    )

    @JvmStatic
    fun removeItems(player: Player) {
        for (id in itemIds) {
            val inventoryAmount = player.inventory.getAmount(Item(id))
            if (inventoryAmount > 0) player.inventory.remove(Item(id, inventoryAmount))

            val bankAmount = player.bank.getAmount(Item(id))
            if (bankAmount > 0) player.bank.remove(Item(id, bankAmount))
        }
    }
}
