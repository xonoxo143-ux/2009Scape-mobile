package core.local.league

import content.global.skill.cooking.CookableItems
import content.global.skill.firemaking.Log
import core.api.addItemOrDrop
import core.game.node.entity.player.Player
import core.game.node.entity.skill.Skills
import core.game.node.item.Item
import core.local.LeagueRuntime
import kotlin.math.min

/** Retained-skill seam for the Demonic Pacts gathering relics. */
object EndlessHarvestHooks {
    @JvmStatic
    fun isActive(player: Player): Boolean =
        LeagueRuntime.hasRelic(player, DemonicPactsRelics.ENDLESS_HARVEST)

    @JvmStatic
    fun barbarianGathering(player: Player): Boolean =
        LeagueRuntime.hasRelic(player, DemonicPactsRelics.BARBARIAN_GATHERING)

    @JvmStatic
    fun hotfoot(player: Player): Boolean =
        LeagueRuntime.hasRelic(player, DemonicPactsRelics.HOTFOOT)

    @JvmStatic
    fun woodsman(player: Player): Boolean =
        LeagueRuntime.hasRelic(player, DemonicPactsRelics.WOODSMAN)

    @JvmStatic
    fun flowState(player: Player): Boolean =
        LeagueRuntime.hasRelic(player, DemonicPactsRelics.FLOW_STATE)

    @JvmStatic
    fun preventDepletion(player: Player): Boolean = isActive(player)

    @JvmStatic
    fun bypassInventoryCapacity(player: Player, skillId: Int): Boolean =
        isActive(player) || (skillId == Skills.WOODCUTTING && woodsman(player))

    /** Failed gathering gets an independent 50% success roll with Barbarian Gathering. */
    @JvmStatic
    fun secondChance(player: Player): Boolean = barbarianGathering(player) && Math.random() < 0.5

    /** Flow State clamps supported gathering loops to a two-tick action cycle. */
    @JvmStatic
    fun actionDelay(player: Player, normalDelay: Int): Int =
        if (flowState(player)) min(2, normalDelay) else normalDelay

    /**
     * Transform and route the primary gathering reward before it ever reaches
     * inventory. Returns false when no selected relic needs to replace the
     * retained reward path.
     */
    @JvmStatic
    fun routeGatheredReward(player: Player, skillId: Int, itemId: Int, amount: Int): Boolean {
        if (amount <= 0) return isActive(player) || hotfoot(player) || woodsman(player)

        var outputId = itemId
        var outputAmount = amount
        val endless = isActive(player)
        if (endless) outputAmount *= 2

        if (skillId == Skills.FISHING && hotfoot(player)) {
            val cookable = CookableItems.forId(itemId)
            if (cookable != null && cookable.cooked > 0) {
                outputId = cookable.cooked
                player.skills.addExperience(Skills.COOKING, cookable.experience * outputAmount, false)
            }
        }

        if (skillId == Skills.MINING && hotfoot(player)) {
            val smelted = smeltedOre(itemId)
            if (smelted != null) {
                outputId = smelted.first
                player.skills.addExperience(Skills.SMITHING, smelted.second * outputAmount, false)
            }
        }

        if (skillId == Skills.WOODCUTTING && woodsman(player)) {
            val log = Log.forId(itemId)
            if (log != null) {
                player.skills.addExperience(Skills.FIREMAKING, log.xp * outputAmount, false)
                return true
            }
        }

        val transformed = outputId != itemId || outputAmount != amount
        if (!endless && !transformed) return false

        if (endless) {
            val bankable = min(outputAmount, player.bank.getMaximumAdd(Item(outputId, outputAmount)))
            if (bankable > 0) player.bank.add(Item(outputId, bankable))
            val remainder = outputAmount - bankable
            if (remainder > 0) addItemOrDrop(player, outputId, remainder)
        } else {
            addItemOrDrop(player, outputId, outputAmount)
        }
        return true
    }

    /** 2009-era metallic ores that can sensibly auto-smelt as a single mined resource. */
    private fun smeltedOre(oreId: Int): Pair<Int, Double>? = when (oreId) {
        440 -> 2351 to 12.5   // iron -> iron bar
        442 -> 2355 to 13.7   // silver -> silver bar
        444 -> 2357 to 22.5   // gold -> gold bar
        447 -> 2359 to 30.0   // mithril -> mithril bar
        449 -> 2361 to 37.5   // adamantite -> adamant bar
        451 -> 2363 to 50.0   // runite -> rune bar
        else -> null
    }
}
