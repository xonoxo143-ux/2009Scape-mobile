package content.global.skill.crafting.silver

import content.global.leagues.GrandLeagueManager
import content.global.leagues.core.LeagueOutputKind
import core.api.*
import core.game.event.ResourceProducedEvent
import core.game.node.entity.player.Player
import core.game.node.entity.skill.Skills
import core.game.node.scenery.Scenery
import core.game.system.task.Pulse
import org.rs09.consts.Animations
import org.rs09.consts.Items
import org.rs09.consts.Sounds

/**
 * Handles tick-based silver crafting logic.
 *
 * @author vddCore
 */
class SilverCraftingPulse(
    private val player: Player,
    private val product: SilverProduct,
    private val furnace: Scenery,
    private var amount: Int
) : Pulse() {
    override fun pulse(): Boolean {
        if (amount < 1) return true

        val plan = GrandLeagueManager.outputPlan(player, 1, LeagueOutputKind.PRODUCTION)
        val toProcess = if (plan.instantBatch) amount else 1
        var processed = 0

        if (!inInventory(player, product.requiredItemId) || !inInventory(player, Items.SILVER_BAR_2355)) return true
        animate(player, Animations.HUMAN_FURNACE_SMELTING_3243)
        playAudio(player, Sounds.FURNACE_2725)

        repeat(toProcess) {
            if (!inInventory(player, product.requiredItemId) || !inInventory(player, Items.SILVER_BAR_2355)) return@repeat
            if (!removeItem(player, Items.SILVER_BAR_2355, Container.INVENTORY)) return@repeat

            val output = GrandLeagueManager.resolveOutput(player, 1, LeagueOutputKind.PRODUCTION)
            addItem(player, product.producedItemId, product.amountProduced * output.baseAmount)
            GrandLeagueManager.deliverBonusOutput(player, product.producedItemId, output, product.amountProduced)
            rewardXP(player, Skills.CRAFTING, product.xpReward * output.experienceUnits)
            player.dispatch(
                ResourceProducedEvent(
                    product.producedItemId,
                    product.amountProduced * output.amount,
                    furnace,
                    Items.SILVER_BAR_2355
                )
            )
            processed++
        }

        amount -= processed
        if (plan.instantBatch) return true
        delay = 5
        return amount < 1 || processed == 0
    }
}