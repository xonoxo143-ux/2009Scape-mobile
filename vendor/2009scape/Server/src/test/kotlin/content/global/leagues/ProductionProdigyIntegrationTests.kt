package content.global.leagues

import TestUtils
import content.global.leagues.core.LeagueOutputKind
import content.global.skill.cooking.WineFermentListener
import content.global.skill.cooking.fermenting.WineFermentingPulse
import core.game.interaction.IntType
import core.game.interaction.InteractionListeners
import core.game.node.entity.player.Player
import core.game.node.entity.skill.Skills
import core.game.node.item.Item
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.rs09.consts.Items

class ProductionProdigyIntegrationTests {
    init { TestUtils.preTestSetup() }

    private fun enableProductionProdigy(player: Player) {
        GrandLeagueManager().login(player)
        val manager = GrandLeagueManager.getInstance(player)
        manager.enable(reset = true)
        manager.profile.points = 3_500
        Assertions.assertTrue(manager.selectRelic("production-prodigy").success)
    }

    @Test
    fun productionProdigyBatchesEveryWinePair() {
        TestUtils.getMockPlayer("league-wine-batch").use { player ->
            enableProductionProdigy(player)
            player.skills.setStaticLevel(Skills.COOKING, 35)
            player.skills.setLevel(Skills.COOKING, 35)
            player.inventory.add(Item(Items.GRAPES_1987, 4))
            player.inventory.add(Item(Items.JUG_OF_WATER_1937, 4))

            if (InteractionListeners.get(Items.GRAPES_1987, Items.JUG_OF_WATER_1937, IntType.ITEM.ordinal) == null) {
                WineFermentListener().defineListeners()
            }
            Assertions.assertTrue(
                InteractionListeners.run(
                    Item(Items.GRAPES_1987),
                    Item(Items.JUG_OF_WATER_1937),
                    IntType.ITEM,
                    player
                )
            )

            Assertions.assertEquals(0, player.inventory.getAmount(Items.GRAPES_1987))
            Assertions.assertEquals(0, player.inventory.getAmount(Items.JUG_OF_WATER_1937))
            val unfermented =
                player.inventory.getAmount(Items.UNFERMENTED_WINE_1995) +
                player.bankPrimary.getAmount(Items.UNFERMENTED_WINE_1995) +
                player.bankSecondary.getAmount(Items.UNFERMENTED_WINE_1995)
            Assertions.assertTrue(unfermented in 4..8, "Expected four base wines plus zero-to-four bonus wines, got $unfermented")

            TestUtils.advanceTicks(20, false)
            val remainingUnfermented =
                player.inventory.getAmount(Items.UNFERMENTED_WINE_1995) +
                player.bankPrimary.getAmount(Items.UNFERMENTED_WINE_1995) +
                player.bankSecondary.getAmount(Items.UNFERMENTED_WINE_1995)
            Assertions.assertEquals(0, remainingUnfermented)
        }
    }

    @Test
    fun productionBonusDuplicatesWholeMultiItemAction() {
        TestUtils.getMockPlayer("league-multi-output").use { player ->
            enableProductionProdigy(player)
            val output = GrandLeagueManager.outputPlan(player, 1, LeagueOutputKind.PRODUCTION).resolve { 0.0 }

            Assertions.assertEquals(1, output.baseAmount)
            Assertions.assertEquals(1, output.bonusAmount)
            Assertions.assertEquals(2, output.experienceUnits)
            Assertions.assertTrue(GrandLeagueManager.deliverBonusOutput(player, Items.CANNONBALL_2, output, 4))
            Assertions.assertEquals(4, player.bankPrimary.getAmount(Items.CANNONBALL_2))
            Assertions.assertEquals(0, player.inventory.getAmount(Items.CANNONBALL_2))
        }
    }

    @Test
    fun wineFermentationFindsBonusWineInSecondaryBank() {
        TestUtils.getMockPlayer("league-wine-secondary-bank").use { player ->
            player.bankSecondary.add(Item(Items.UNFERMENTED_WINE_1995))
            val pulse = WineFermentingPulse(0, player)
            var completed = false
            repeat(20) {
                if (!completed) completed = pulse.pulse()
            }

            Assertions.assertTrue(completed)
            Assertions.assertEquals(0, player.bankSecondary.getAmount(Items.UNFERMENTED_WINE_1995))
            val fermented = player.bankSecondary.getAmount(1991) + player.bankSecondary.getAmount(Items.JUG_OF_WINE_1993)
            Assertions.assertEquals(1, fermented)
        }
    }
}
