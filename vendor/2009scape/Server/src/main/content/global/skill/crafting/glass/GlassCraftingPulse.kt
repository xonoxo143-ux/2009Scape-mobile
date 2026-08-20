package content.global.skill.crafting.glass

import content.global.leagues.GrandLeagueManager
import content.global.leagues.core.LeagueOutputKind
import core.api.*
import core.game.event.ResourceProducedEvent
import core.game.node.entity.player.Player
import core.game.node.entity.skill.Skills
import core.game.system.task.Pulse
import org.rs09.consts.Items
import org.rs09.consts.Sounds

class GlassCraftingPulse(
        private val player: Player,
        private val product: GlassProduct,
        private var amount: Int
) : Pulse() {

    override fun pulse(): Boolean {
        if (amount < 1) return true

        val plan = GrandLeagueManager.outputPlan(player, 1, LeagueOutputKind.PRODUCTION)
        val toProcess = if (plan.instantBatch) amount else 1
        var processed = 0

        repeat(toProcess) {
            if (!inInventory(player, Items.GLASSBLOWING_PIPE_1785) || !inInventory(player, Items.MOLTEN_GLASS_1775)) return@repeat

            animate(player, 884)
            playAudio(player, Sounds.GLASSBLOWING_2724)
            if (product.producedItemId in intArrayOf(Items.UNPOWERED_ORB_567, Items.OIL_LAMP_4525)) {
                sendMessage(player, "You make an ${product.name.lowercase().replace("_", " ")}.")
            } else {
                sendMessage(player, "You make a ${product.name.lowercase().replace("_", " ")}.")
            }

            if (!removeItem(player, Items.MOLTEN_GLASS_1775)) return@repeat
            val output = GrandLeagueManager.resolveOutput(player, 1, LeagueOutputKind.PRODUCTION)
            addItem(player, product.producedItemId, product.amount * output.baseAmount)
            GrandLeagueManager.deliverBonusOutput(player, product.producedItemId, output, product.amount)
            rewardXP(player, Skills.CRAFTING, product.experience * output.experienceUnits)
            player.dispatch(ResourceProducedEvent(product.producedItemId, product.amount * output.amount, player, Items.MOLTEN_GLASS_1775))
            processed++
        }

        amount -= processed
        if (plan.instantBatch) return true
        delay = 3
        return amount < 1 || processed == 0
    }

}