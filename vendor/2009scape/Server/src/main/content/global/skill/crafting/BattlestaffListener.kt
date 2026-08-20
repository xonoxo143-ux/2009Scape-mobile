package content.global.skill.crafting

import content.global.leagues.GrandLeagueManager
import content.global.leagues.core.LeagueOutputKind
import core.api.*
import core.game.event.ResourceProducedEvent
import core.game.interaction.IntType
import core.game.interaction.InteractionListener
import core.game.node.entity.player.link.diary.DiaryType
import core.game.node.entity.skill.Skills
import org.rs09.consts.Items
import kotlin.math.min

class BattlestaffListener : InteractionListener {

    private val battlestaff = Items.BATTLESTAFF_1391
    val orbs = BattlestaffProduct.values().map { it.requiredOrbItemId }.toIntArray()

    override fun defineListeners() {
        onUseWith(IntType.ITEM, orbs, battlestaff) { player, used, with ->
            val product = BattlestaffProduct.productMap[used.id] ?: return@onUseWith true

            fun getMaxAmount(_unused: Int = 0): Int =
                min(amountInInventory(player, with.id), amountInInventory(player, used.id))

            fun craftOne(): Boolean {
                if (!removeItem(player, product.requiredOrbItemId) || !removeItem(player, Items.BATTLESTAFF_1391)) return false
                val output = GrandLeagueManager.resolveOutput(player, 1, LeagueOutputKind.PRODUCTION)
                addItem(player, product.producedItemId, product.amountProduced * output.baseAmount)
                GrandLeagueManager.deliverBonusOutput(player, product.producedItemId, output, product.amountProduced)
                rewardXP(player, Skills.CRAFTING, product.experience * output.experienceUnits)
                player.dispatch(
                    ResourceProducedEvent(
                        product.producedItemId,
                        product.amountProduced * output.amount,
                        player,
                        product.requiredOrbItemId
                    )
                )
                if (product.producedItemId == Items.AIR_BATTLESTAFF_1397) {
                    player.achievementDiaryManager.finishTask(player, DiaryType.VARROCK, 2, 6)
                }
                return true
            }

            if (!hasLevelDyn(player, Skills.CRAFTING, product.minimumLevel)) {
                sendMessage(player, "You need a Crafting level of ${product.minimumLevel} to make this.")
                return@onUseWith true
            }

            val plan = GrandLeagueManager.outputPlan(player, 1, LeagueOutputKind.PRODUCTION)

            if (getMaxAmount() == 1) {
                craftOne()
                return@onUseWith true
            }

            sendSkillDialogue(player) {
                withItems(product.producedItemId)
                create { _, amount ->
                    val requested = min(amount, getMaxAmount())
                    if (plan.instantBatch) {
                        var remaining = requested
                        while (remaining-- > 0 && craftOne()) { }
                    } else {
                        runTask(player, 2, requested) {
                            if (amount < 1) return@runTask
                            if (!craftOne()) return@runTask
                        }
                    }
                }

                calculateMaxAmount(::getMaxAmount)
            }

            return@onUseWith true
        }
    }

    enum class BattlestaffProduct(
            val requiredOrbItemId: Int,
            val producedItemId: Int,
            val amountProduced: Int,
            val minimumLevel: Int,
            val experience: Double,
    ) {
        WATER_BATTLESTAFF(Items.WATER_ORB_571, Items.WATER_BATTLESTAFF_1395, 1, 54, 100.0),
        EARTH_BATTLESTAFF(Items.EARTH_ORB_575, Items.EARTH_BATTLESTAFF_1399, 1, 58, 112.5),
        FIRE_BATTLESTAFF(Items.FIRE_ORB_569, Items.FIRE_BATTLESTAFF_1393, 1, 62, 125.0),
        AIR_BATTLESTAFF(Items.AIR_ORB_573, Items.AIR_BATTLESTAFF_1397, 1, 66, 137.5);

        companion object {
            val productMap = HashMap<Int, BattlestaffProduct>()

            init {
                for (product in BattlestaffProduct.values()) {
                    productMap[product.requiredOrbItemId] = product
                }
            }
        }
    }
}