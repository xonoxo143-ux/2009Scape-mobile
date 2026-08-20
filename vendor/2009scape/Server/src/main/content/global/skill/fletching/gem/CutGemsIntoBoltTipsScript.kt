package content.global.skill.fletching.gem

import content.global.leagues.GrandLeagueManager
import content.global.leagues.core.LeagueOutputKind
import core.api.*
import core.game.event.ResourceProducedEvent
import core.game.interaction.Clocks
import core.game.node.entity.player.Player
import core.game.node.entity.skill.Skills
import core.game.node.item.Item

/**
 * Represents the queue script for cutting gems into bolt tips
 * @author Ceikry
 * @param player the player.
 * @param gemBoltCraftInfo represents the crafting info for the gem we're cutting.
 * @param amount the amount to make.
 */
class CutGemsIntoBoltTipsScript(
    private val player: Player,
    private val gemBoltCraftInfo: GemBoltsCraftInfo,
    private val amount: Int
) {

    private val initialDelay = 1
    private val craftDelay = 5
    private val animationDelay = 6

    private var craftingFinished = false

    private fun invokeAnimationLoop() {
        queueScript(player, 0) {
            if (craftingFinished) {
                return@queueScript stopExecuting(player)
            }
            animate(player, gemBoltCraftInfo.gemCutAnimationId)
            return@queueScript delayScript(player, animationDelay)
        }
    }

    private fun invokeCraftLoop() {
        val productionPlan = GrandLeagueManager.outputPlan(player, 1, LeagueOutputKind.PRODUCTION)
        queueScript(player, 0) { stage ->
            if (!clockReady(player, Clocks.SKILLING)) return@queueScript keepRunning(player)

            if (productionPlan.instantBatch) {
                var crafted = 0
                while (crafted < amount) {
                    if (!canCraftCurrentLevel()) {
                        craftingFinished = true
                        return@queueScript stopExecuting(player)
                    }
                    if (!craftOne()) break
                    crafted++
                }
                craftingFinished = true
                return@queueScript stopExecuting(player)
            }

            if (!canCraftCurrentLevel()) {
                craftingFinished = true
                return@queueScript stopExecuting(player)
            }
            if (!craftOne()) {
                craftingFinished = true
                return@queueScript stopExecuting(player)
            }

            if (stage >= amount - 1) {
                craftingFinished = true
                return@queueScript stopExecuting(player)
            }

            return@queueScript delayClock(player, Clocks.SKILLING, craftDelay, true)
        }
    }

    private fun canCraftCurrentLevel(): Boolean {
        if (getDynLevel(player, Skills.FLETCHING) >= gemBoltCraftInfo.level) return true
        GemBoltListeners.sendGemTipCutLevelCheckFailDialog(player, gemBoltCraftInfo.level)
        return false
    }

    private fun craftOne(): Boolean {
        val amountOfTipsToCraft = when (gemBoltCraftInfo) {
            GemBoltsCraftInfo.PEARLS -> 24
            GemBoltsCraftInfo.PEARL -> 6
            GemBoltsCraftInfo.ONYX -> 24
            else -> 12
        }

        if (!removeItem(player, Item(gemBoltCraftInfo.gemItemId))) return false

        val output = GrandLeagueManager.resolveOutput(player, 1, LeagueOutputKind.PRODUCTION)
        addItem(player, gemBoltCraftInfo.tipItemId, amountOfTipsToCraft * output.baseAmount)
        GrandLeagueManager.deliverBonusOutput(player, gemBoltCraftInfo.tipItemId, output, amountOfTipsToCraft)
        rewardXP(player, Skills.FLETCHING, gemBoltCraftInfo.experience * output.experienceUnits)
        player.dispatch(
            ResourceProducedEvent(
                gemBoltCraftInfo.tipItemId,
                amountOfTipsToCraft * output.amount,
                player,
                gemBoltCraftInfo.gemItemId
            )
        )
        return true
    }

    fun invoke() {
        queueScript(player, initialDelay) {
            invokeAnimationLoop()
            invokeCraftLoop()
            return@queueScript stopExecuting(player)
        }
    }
}