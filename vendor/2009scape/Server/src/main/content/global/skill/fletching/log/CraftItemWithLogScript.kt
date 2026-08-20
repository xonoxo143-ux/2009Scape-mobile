package content.global.skill.fletching.log

import content.global.leagues.GrandLeagueManager
import content.global.leagues.core.LeagueOutputKind
import content.global.skill.fletching.AchievementDiaryAttributeKeys
import content.global.skill.fletching.Zones
import content.global.skill.fletching.log.GrammarHelpers.aOrAn
import content.global.skill.fletching.log.GrammarHelpers.makeFriendlyName
import core.api.*
import core.game.event.ResourceProducedEvent
import core.game.interaction.Clocks
import core.game.node.entity.player.Player
import core.game.node.entity.player.link.diary.DiaryType
import core.game.node.entity.skill.Skills
import core.game.node.item.Item
import core.game.world.update.flag.context.Animation
import core.tools.RandomFunction
import org.rs09.consts.Items

/**
 * Represents the queueScript used to craft things by carving a log
 * @author ceik
 * @param player the player.
 * @param logCraftInfo crafting info about what we're making out of the log
 * @param amount iterations of craft to run
 */
class CraftItemWithLogScript(
    private val player: Player,
    private val logCraftInfo: LogCraftInfo,
    private val amount: Int
) {

    private val initialDelay = 1

    // src https://gitlab.com/2009scape/2009scape/-/merge_requests/1960#note_2702231552
    private val subsequentDelay = 3

    private val carveLogAnimation = Animation(1248)

    fun invoke() {
        val productionPlan = GrandLeagueManager.outputPlan(player, 1, LeagueOutputKind.PRODUCTION)
        queueScript(player, initialDelay) { stage ->
            if (!clockReady(player, Clocks.SKILLING)) return@queueScript keepRunning(player)

            if (productionPlan.instantBatch) {
                var crafted = 0
                while (crafted < amount) {
                    if (!canCraftCurrentLevel()) return@queueScript stopExecuting(player)
                    if (!craftOne()) break
                    crafted++
                }
                return@queueScript stopExecuting(player)
            }

            if (!canCraftCurrentLevel()) return@queueScript stopExecuting(player)
            if (!craftOne()) return@queueScript stopExecuting(player)

            if (stage >= amount - 1) {
                return@queueScript stopExecuting(player)
            }

            return@queueScript delayClock(player, Clocks.SKILLING, subsequentDelay, true)
        }
    }

    private fun canCraftCurrentLevel(): Boolean {
        if (getDynLevel(player, Skills.FLETCHING) >= logCraftInfo.level) return true
        LogCraftableListeners.sendLevelCheckFailDialog(player, logCraftInfo)
        return false
    }

    private fun craftOne(): Boolean {
        if (!removeItem(player, logCraftInfo.logItemId.asItem())) return false

        player.animate(carveLogAnimation)
        val finishedItemName = makeFriendlyName(Item(logCraftInfo.finishedItemId).name)
        val amountToCraft = when (logCraftInfo) {
            LogCraftInfo.OGRE_ARROW_SHAFT -> RandomFunction.random(2, 6)
            LogCraftInfo.ARROW_SHAFT -> 15
            else -> 1
        }

        if (logCraftInfo == LogCraftInfo.OGRE_COMP_BOW && !removeItem(player, Items.WOLF_BONES_2859)) {
            return false
        }

        val output = GrandLeagueManager.resolveOutput(player, 1, LeagueOutputKind.PRODUCTION)
        sendCraftMessageToPlayer(amountToCraft, finishedItemName)
        addItem(player, logCraftInfo.finishedItemId, amountToCraft * output.baseAmount)
        GrandLeagueManager.deliverBonusOutput(player, logCraftInfo.finishedItemId, output, amountToCraft)
        player.dispatch(
            ResourceProducedEvent(
                logCraftInfo.finishedItemId,
                amountToCraft * output.amount,
                player,
                logCraftInfo.logItemId
            )
        )
        rewardXP(player, Skills.FLETCHING, logCraftInfo.experience * output.experienceUnits)

        if (logCraftInfo == LogCraftInfo.MAGIC_SHORTBOW) {
            handleSeersMagicShortbowAchievement()
        }
        return true
    }

    private fun handleSeersMagicShortbowAchievement() {
        if (Zones.inAnyZone(player, Zones.seersMagicShortbowAchievementZones) &&
            !player.achievementDiaryManager.hasCompletedTask(DiaryType.SEERS_VILLAGE, 2, 2)
        ) {
            setAttribute(
                player,
                "/save:${AchievementDiaryAttributeKeys.FLETCHED_UNSTRUNG_MAGIC_SHORTBOW}",
                true
            )
        }
    }

    private fun sendCraftMessageToPlayer(amountToCraft: Int, finishedItemName: String) {
        when (logCraftInfo) {
            LogCraftInfo.ARROW_SHAFT -> sendMessage(
                player,
                "You carefully cut the wood into $amountToCraft arrow shafts."
            )

            LogCraftInfo.OGRE_ARROW_SHAFT -> sendMessage(
                player,
                "You carefully cut the wood into $amountToCraft arrow shafts."
            )

            else -> sendMessage(
                player,
                "You carefully cut the wood into ${aOrAn(finishedItemName)} $finishedItemName."
            )
        }
    }
}