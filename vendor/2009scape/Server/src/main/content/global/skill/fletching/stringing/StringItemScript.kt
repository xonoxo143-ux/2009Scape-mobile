package content.global.skill.fletching.stringing

import content.global.leagues.GrandLeagueManager
import content.global.leagues.core.LeagueOutputKind
import content.global.skill.fletching.AchievementDiaryAttributeKeys
import content.global.skill.fletching.Zones
import content.global.skill.fletching.stringing.StringableCraftInfo.Companion.applicableStringId
import core.api.*
import core.game.event.ResourceProducedEvent
import core.game.interaction.Clocks
import core.game.node.entity.player.Player
import core.game.node.entity.player.link.diary.DiaryType
import core.game.node.entity.skill.Skills
import core.game.node.item.Item

/**
 * Represents queueScript to string a bow/crossbow
 * @author Ceikry
 * @param player the player.
 * @param stringableCraftInfo contains crafting information about what we're stringing
 * @param amount the amount of items to string
 */
class StringItemScript(
    private val player: Player,
    private val stringableCraftInfo: StringableCraftInfo,
    private var amount: Int
) {
    private val initialDelay = 1
    private val subsequentDelay = 2

    fun invoke() {
        val productionPlan = GrandLeagueManager.outputPlan(player, 1, LeagueOutputKind.PRODUCTION)
        val instantBatch = !stringableCraftInfo.isCrossbow && productionPlan.instantBatch

        queueScript(player, initialDelay) { stage ->
            if (!clockReady(player, Clocks.SKILLING)) return@queueScript keepRunning(player)

            if (instantBatch) {
                var crafted = 0
                while (crafted < amount) {
                    if (!canStringCurrentLevel()) return@queueScript stopExecuting(player)
                    if (!stringOne()) break
                    crafted++
                }
                return@queueScript stopExecuting(player)
            }

            if (!canStringCurrentLevel()) return@queueScript stopExecuting(player)
            if (!stringOne()) return@queueScript stopExecuting(player)

            if (stage >= amount - 1) {
                return@queueScript stopExecuting(player)
            }

            return@queueScript delayClock(player, Clocks.SKILLING, subsequentDelay, true)
        }
    }

    private fun canStringCurrentLevel(): Boolean {
        if (getDynLevel(player, Skills.FLETCHING) >= stringableCraftInfo.level) return true
        StringingListeners.sendLevelCheckFailDialogue(player, stringableCraftInfo.level)
        return false
    }

    private fun stringOne(): Boolean {
        if (!removeItemsIfPlayerHasEnough(
                player,
                Item(stringableCraftInfo.unstrungItemId),
                Item(stringableCraftInfo.applicableStringId)
            )
        ) return false

        player.animate(stringableCraftInfo.animation)

        if (stringableCraftInfo.isCrossbow) {
            addItem(player, stringableCraftInfo.strungItemId, 1)
            rewardXP(player, Skills.FLETCHING, stringableCraftInfo.experience)
            player.dispatch(
                ResourceProducedEvent(
                    stringableCraftInfo.strungItemId,
                    1,
                    player,
                    stringableCraftInfo.unstrungItemId
                )
            )
        } else {
            val output = GrandLeagueManager.resolveOutput(player, 1, LeagueOutputKind.PRODUCTION)
            addItem(player, stringableCraftInfo.strungItemId, output.baseAmount)
            GrandLeagueManager.deliverBonusOutput(player, stringableCraftInfo.strungItemId, output)
            rewardXP(player, Skills.FLETCHING, stringableCraftInfo.experience * output.experienceUnits)
            player.dispatch(
                ResourceProducedEvent(
                    stringableCraftInfo.strungItemId,
                    output.amount,
                    player,
                    stringableCraftInfo.unstrungItemId
                )
            )
        }

        sendMessage(player, "You add a string to the bow.")
        if (stringableCraftInfo == StringableCraftInfo.MAGIC_SHORTBOW) {
            handleSeersMagicShortbowAchievement()
        }
        return true
    }

    private fun handleSeersMagicShortbowAchievement() {
        if (Zones.inAnyZone(player, Zones.seersMagicShortbowAchievementZones) &&
            getAttribute(player, AchievementDiaryAttributeKeys.FLETCHED_UNSTRUNG_MAGIC_SHORTBOW, false)
        ) {
            player.achievementDiaryManager.finishTask(player, DiaryType.SEERS_VILLAGE, 2, 2)
        }
    }
}