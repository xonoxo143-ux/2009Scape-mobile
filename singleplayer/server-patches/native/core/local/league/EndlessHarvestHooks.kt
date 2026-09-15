package core.local.league

import core.game.node.entity.player.Player
import core.local.LeagueRuntime

/** Narrow retained-skill seam for Endless Harvest resource persistence. */
object EndlessHarvestHooks {
    @JvmStatic
    fun preventDepletion(player: Player): Boolean =
        LeagueRuntime.hasRelic(player, FirstPassRelics.ENDLESS_HARVEST)
}
