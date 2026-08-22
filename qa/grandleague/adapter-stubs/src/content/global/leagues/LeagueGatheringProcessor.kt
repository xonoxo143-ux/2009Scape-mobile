package content.global.leagues

import core.game.event.ResourceProducedEvent
import core.game.event.ResourceSkill
import core.game.node.entity.player.Player

data class LeagueGatheringRecipe(
    val outputItemId: Int?,
    val secondarySkillId: Int,
    val xpPerItem: Double
)

object LeagueGatheringProcessor {
    @JvmStatic
    fun resolve(@Suppress("UNUSED_PARAMETER") player: Player, event: ResourceProducedEvent): LeagueGatheringRecipe? = when {
        event.skill == ResourceSkill.FISHING && event.itemId == 317 -> LeagueGatheringRecipe(315, 7, 30.0)
        event.skill == ResourceSkill.WOODCUTTING && event.itemId == 1511 -> LeagueGatheringRecipe(null, 11, 40.0)
        else -> null
    }
}
