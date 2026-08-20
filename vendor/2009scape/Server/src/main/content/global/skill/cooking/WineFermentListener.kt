package content.global.skill.cooking

import content.global.leagues.GrandLeagueManager
import content.global.leagues.core.LeagueOutputKind
import content.global.skill.cooking.fermenting.WineFermentingPulse
import core.api.*
import core.game.interaction.IntType
import core.game.interaction.InteractionListener
import core.game.node.entity.skill.Skills
import core.game.world.GameWorld.Pulser
import org.rs09.consts.Items
import kotlin.math.min

class WineFermentListener : InteractionListener {

    override fun defineListeners() {
        onUseWith(IntType.ITEM, Items.GRAPES_1987, Items.JUG_OF_WATER_1937) { player, used, with ->
            if (getDynLevel(player, Skills.COOKING) < 35) {
                sendMessage(player, "You need a cooking level of 35 to do this.")
                return@onUseWith true
            }
            val plan = GrandLeagueManager.outputPlan(player, 1, LeagueOutputKind.PRODUCTION)
            if (plan.instantBatch) {
                val pairs = min(
                    amountInInventory(player, Items.GRAPES_1987),
                    amountInInventory(player, Items.JUG_OF_WATER_1937)
                )
                var produced = 0
                repeat(pairs) {
                    if (!removeItem(player, Items.GRAPES_1987) || !removeItem(player, Items.JUG_OF_WATER_1937)) return@repeat
                    val output = GrandLeagueManager.resolveOutput(player, 1, LeagueOutputKind.PRODUCTION)
                    addItem(player, Items.UNFERMENTED_WINE_1995, output.baseAmount)
                    GrandLeagueManager.deliverBonusOutput(player, Items.UNFERMENTED_WINE_1995, output)
                    repeat(output.amount) { Pulser.submit(WineFermentingPulse(1, player)) }
                    produced += output.amount
                }
                if (produced > 0) {
                    sendMessage(player, "You make the grapes into unfermented wine.")
                    return@onUseWith true
                }
                return@onUseWith false
            }

            if (removeItem(player, used.id) && removeItem(player, with.id)) {
                addItem(player, Items.UNFERMENTED_WINE_1995)
                Pulser.submit(WineFermentingPulse(1, player))
                return@onUseWith true
            }
            return@onUseWith false
        }
    }
}