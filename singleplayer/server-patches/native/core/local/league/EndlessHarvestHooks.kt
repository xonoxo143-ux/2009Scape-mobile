package core.local.league

import core.api.addItemOrDrop
import core.game.node.entity.player.Player
import core.game.node.item.Item
import core.local.LeagueRuntime
import kotlin.math.min

/** Narrow retained-skill seam for Endless Harvest gathering behavior. */
object EndlessHarvestHooks {
    @JvmStatic
    fun isActive(player: Player): Boolean =
        LeagueRuntime.hasRelic(player, FirstPassRelics.ENDLESS_HARVEST)

    @JvmStatic
    fun preventDepletion(player: Player): Boolean = isActive(player)

    /** Endless Harvest can keep gathering even when the inventory itself is full. */
    @JvmStatic
    fun bypassInventoryCapacity(player: Player): Boolean = isActive(player)

    /**
     * Route the primary gathering reward straight to the bank. The doubled
     * amount never enters inventory, so there is no one-tick inventory flash.
     * Returns false when the relic is inactive so retained skill code can use
     * its original reward path unchanged.
     */
    @JvmStatic
    fun routeGatheredReward(player: Player, itemId: Int, amount: Int): Boolean {
        if (!isActive(player)) return false
        if (amount <= 0) return true

        val doubled = amount * 2
        val bankable = min(doubled, player.bank.getMaximumAdd(Item(itemId, doubled)))
        if (bankable > 0) {
            player.bank.add(Item(itemId, bankable))
        }

        // A completely/full bank should not delete a harvest. Only the overflow
        // falls back to the retained inventory/drop behavior.
        val remainder = doubled - bankable
        if (remainder > 0) addItemOrDrop(player, itemId, remainder)
        return true
    }
}
