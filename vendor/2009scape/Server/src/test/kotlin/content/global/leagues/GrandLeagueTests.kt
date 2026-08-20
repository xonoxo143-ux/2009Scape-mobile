package content.global.leagues

import TestUtils
import content.global.leagues.core.*
import core.game.event.ResourceProducedEvent
import core.game.event.StaticSkillLevelUpEvent
import core.game.node.entity.skill.Skills
import org.json.simple.JSONObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class GrandLeagueTests {
    init {
        TestUtils.preTestSetup()
    }

    @Test
    fun taskCompletionIsExactOnceThroughRealPlayerEventDispatch() {
        TestUtils.getMockPlayer("league-task-once").use { player ->
            GrandLeagueManager().login(player)
            val manager = GrandLeagueManager.getInstance(player)
            manager.enable(reset = true)

            player.dispatch(ResourceProducedEvent(1521, 1, player))
            Assertions.assertTrue("bootstrap.cut-oak" in manager.profile.completedTasks)
            Assertions.assertEquals(10L, manager.profile.points)

            player.dispatch(ResourceProducedEvent(1521, 1, player))
            Assertions.assertEquals(10L, manager.profile.points, "Repeated matching signals must not duplicate task points")
        }
    }

    @Test
    fun metricThresholdTasksUseCurrentValuesInsteadOfBespokeMilestoneEvents() {
        TestUtils.getMockPlayer("league-metrics").use { player ->
            GrandLeagueManager().login(player)
            val manager = GrandLeagueManager.getInstance(player)
            manager.enable(reset = true)

            player.skills.staticLevels.fill(21)
            player.dispatch(StaticSkillLevelUpEvent(Skills.MINING, 19, 20))

            Assertions.assertTrue("bootstrap.level-20-mining" in manager.profile.completedTasks)
            Assertions.assertTrue("bootstrap.total-level-500" in manager.profile.completedTasks)
        }
    }

    @Test
    fun inactiveAccountsKeepNormalXpAndLeagueAccountsReceiveTierXpMultiplier() {
        TestUtils.getMockPlayer("league-xp-disabled").use { player ->
            GrandLeagueManager().login(player)
            val before = player.skills.getExperience(Skills.MINING)
            player.skills.addExperience(Skills.MINING, 100.0)
            Assertions.assertEquals(before + 100.0, player.skills.getExperience(Skills.MINING), 0.001)
        }

        TestUtils.getMockPlayer("league-xp-enabled").use { player ->
            GrandLeagueManager().login(player)
            GrandLeagueManager.getInstance(player).enable(reset = true)
            val before = player.skills.getExperience(Skills.MINING)
            player.skills.addExperience(Skills.MINING, 100.0)
            Assertions.assertEquals(before + 500.0, player.skills.getExperience(Skills.MINING), 0.001)
        }
    }

    @Test
    fun leagueProfilePersistsWithoutLeakingIntoNormalAccounts() {
        TestUtils.getMockPlayer("league-persist").use { player ->
            val listener = GrandLeagueManager()
            listener.login(player)
            val manager = GrandLeagueManager.getInstance(player)
            manager.enable(reset = true)
            manager.process(ResourceProducedSignal(1521))
            manager.profile.currencies["pact_point"] = 7

            val save = JSONObject()
            listener.savePlayer(player, save)

            TestUtils.getMockPlayer("league-persist-relog").use { relogged ->
                listener.login(relogged)
                listener.parsePlayer(relogged, save)
                Assertions.assertEquals(manager.profile.snapshot(), GrandLeagueManager.getInstance(relogged).profile.snapshot())
            }
        }
    }
    @Test
    fun endlessHarvestOutputIsSelectedExplicitlyAndDeliveredToBank() {
        TestUtils.getMockPlayer("league-output").use { player ->
            GrandLeagueManager().login(player)
            val manager = GrandLeagueManager.getInstance(player)
            manager.enable(reset = true)

            val normal = GrandLeagueManager.resolveOutput(player, 1, LeagueOutputKind.RESOURCE)
            Assertions.assertEquals(1, normal.amount)
            Assertions.assertFalse(normal.autoBank)

            manager.profile.points = 500
            Assertions.assertTrue(manager.selectRelic("endless-harvest").success)

            val boosted = GrandLeagueManager.resolveOutput(player, 1, LeagueOutputKind.RESOURCE)
            Assertions.assertEquals(2, boosted.amount)
            Assertions.assertEquals(2, boosted.experienceUnits)
            Assertions.assertTrue(boosted.autoBank)
            Assertions.assertTrue(GrandLeagueManager.deliverOutput(player, 1521, boosted))
            Assertions.assertEquals(2, player.bankPrimary.getAmount(1521))
            Assertions.assertEquals(0, player.inventory.getAmount(1521))
        }
    }


    @Test
    fun productionProdigySeparatesBaseAndBankedBonusOutput() {
        TestUtils.getMockPlayer("league-production-output").use { player ->
            GrandLeagueManager().login(player)
            val manager = GrandLeagueManager.getInstance(player)
            manager.enable(reset = true)
            manager.profile.points = 3_500
            Assertions.assertTrue(manager.selectRelic("production-prodigy").success)

            val plan = GrandLeagueManager.outputPlan(player, 4, LeagueOutputKind.PRODUCTION)
            Assertions.assertTrue(plan.instantBatch)
            Assertions.assertTrue(plan.bonusAutoBank)
            val rolls = ArrayDeque(listOf(0.10, 0.90, 0.20, 0.80))
            val output = plan.resolve { rolls.removeFirst() }

            Assertions.assertEquals(4, output.baseAmount)
            Assertions.assertEquals(2, output.bonusAmount)
            Assertions.assertEquals(6, output.experienceUnits)
            Assertions.assertEquals(6, output.amount)
            Assertions.assertTrue(GrandLeagueManager.deliverBonusOutput(player, 379, output))
            Assertions.assertEquals(2, player.bankPrimary.getAmount(379))
            Assertions.assertEquals(0, player.inventory.getAmount(379))
        }
    }

    @Test
    fun productionProdigyProvidesPermanentEffectiveSkillFloor() {
        TestUtils.getMockPlayer("league-production-boost").use { player ->
            GrandLeagueManager().login(player)
            val manager = GrandLeagueManager.getInstance(player)
            manager.enable(reset = true)
            manager.profile.points = 3_500

            player.skills.setStaticLevel(Skills.COOKING, 50)
            player.skills.setLevel(Skills.COOKING, 50)
            player.skills.setStaticLevel(Skills.MINING, 50)
            player.skills.setLevel(Skills.MINING, 50)
            Assertions.assertEquals(50, player.skills.getLevel(Skills.COOKING))

            Assertions.assertTrue(manager.selectRelic("production-prodigy").success)
            Assertions.assertEquals(62, player.skills.getLevel(Skills.COOKING))
            Assertions.assertEquals(50, player.skills.getLevel(Skills.MINING))

            player.skills.setLevel(Skills.COOKING, 70)
            Assertions.assertEquals(70, player.skills.getLevel(Skills.COOKING))
            player.skills.setLevel(Skills.COOKING, 40)
            Assertions.assertEquals(62, player.skills.getLevel(Skills.COOKING))
        }
    }

}
