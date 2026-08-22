package content.global.leagues

import content.global.skill.cooking.CookableItems
import content.global.skill.firemaking.Log
import content.global.skill.smithing.smelting.Bar
import core.game.event.ResourceProducedEvent
import core.game.event.ResourceSkill
import core.game.node.entity.player.Player
import core.game.node.entity.skill.Skills

/**
 * Resolves Infernal Gathering/Chef's Catch style processing without teaching
 * every gathering producer about specific relic ids.
 */
data class LeagueGatheringRecipe(
    val outputItemId: Int?,
    val secondarySkillId: Int,
    val xpPerItem: Double
)

object LeagueGatheringProcessor {
    @JvmStatic
    fun resolve(player: Player, event: ResourceProducedEvent): LeagueGatheringRecipe? = when (event.skill) {
        ResourceSkill.WOODCUTTING -> woodcutting(player, event.itemId)
        ResourceSkill.FISHING -> fishing(player, event.itemId)
        ResourceSkill.MINING -> mining(player, event.itemId)
        else -> null
    }

    private fun woodcutting(player: Player, itemId: Int): LeagueGatheringRecipe? {
        val log = Log.forId(itemId) ?: return null
        if (player.skills.getLevel(Skills.FIREMAKING) < log.level) return null
        // Burning consumes the gathered resource, so there is no output item.
        return LeagueGatheringRecipe(null, Skills.FIREMAKING, log.xp)
    }

    private fun fishing(player: Player, itemId: Int): LeagueGatheringRecipe? {
        val food = CookableItems.forId(itemId) ?: return null
        if (food.cooked <= 0 || player.skills.getLevel(Skills.COOKING) < food.level) return null
        return LeagueGatheringRecipe(food.cooked, Skills.COOKING, food.experience)
    }

    private fun mining(player: Player, itemId: Int): LeagueGatheringRecipe? {
        // Trailblazer-style auto-smelting does not require coal. Iron intentionally
        // becomes steel; primary higher-tier ores become their matching bars.
        val bar = when (itemId) {
            436, 438 -> Bar.BRONZE       // copper / tin
            668 -> Bar.BLURITE
            440 -> Bar.STEEL             // iron -> steel
            442 -> Bar.SILVER
            444 -> Bar.GOLD
            447 -> Bar.MITHRIL
            449 -> Bar.ADAMANT
            451 -> Bar.RUNITE
            else -> return null           // coal, clay, gems, essence, etc.
        }
        if (player.skills.getLevel(Skills.SMITHING) < bar.level) return null
        return LeagueGatheringRecipe(bar.product.id, Skills.SMITHING, bar.experience)
    }
}
