package content.global.leagues

import content.global.leagues.core.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class GrandLeagueVerticalSliceTests {
    @Test
    fun completeVerticalSliceSurvivesRelog() {
        val content = GrandLeagueBootstrapContent.create()
        val session = GrandLeagueSession(content)
        session.enable(reset = true)
        session.signal(LeagueSignal(LeagueSignalKind.RESOURCE, "log"))
        session.signal(LeagueSignal(LeagueSignalKind.RESOURCE, "fish", 10))
        Assertions.assertTrue(session.selectRelic("endless-harvest").success)
        session.signal(LeagueSignal(LeagueSignalKind.NPC_KILL, "cow", 10))
        session.signal(LeagueSignal(LeagueSignalKind.QUEST, "complete", 3))
        Assertions.assertTrue(session.unlockRegion("asgarnia").success)
        session.signal(LeagueSignal(LeagueSignalKind.METRIC, "total-level", value = 250))
        Assertions.assertTrue(session.unlockFragment("trailblazer").success)
        Assertions.assertTrue(session.unlockFragment("homewrecker").success)
        Assertions.assertTrue(session.equipFragment("trailblazer").success)
        Assertions.assertTrue(session.equipFragment("homewrecker").success)
        session.signal(LeagueSignal(LeagueSignalKind.NPC_KILL, "general-graardor"))
        Assertions.assertTrue(session.unlockMastery("combat-root").success)
        Assertions.assertTrue(session.unlockMastery("melee-echo").success)
        session.signal(LeagueSignal(LeagueSignalKind.METRIC, "total-level", value = 500))
        Assertions.assertTrue(session.unlockPact("demonic-root").success)
        Assertions.assertTrue(session.unlockPact("echo-pact").success)
        session.signal(LeagueSignal(LeagueSignalKind.METRIC, "quest-points", value = 50))
        session.signal(LeagueSignal(LeagueSignalKind.NPC_KILL, "tztok-jad"))
        Assertions.assertTrue(session.unlockRegion("wilderness").success)
        Assertions.assertTrue(session.recordEchoKill("kbd", EchoDifficulty.ECHO).success)

        val restored = LeagueProfileCodec.decode(LeagueProfileCodec.encode(session.profile))
        Assertions.assertEquals(session.profile, restored)
        Assertions.assertEquals(6, restored.tier)
        Assertions.assertEquals(1, restored.echoKills["kbd:echo"])
    }

    @Test
    fun sharedTriggerCountsOncePerSignal() {
        val base = GrandLeagueBootstrapContent.create()
        val content = base.copy(tasks = listOf(
            LeagueTaskDefinition("one", "One", 1, CountTrigger(LeagueSignalKind.RESOURCE, "log", 1)),
            LeagueTaskDefinition("ten", "Ten", 10, CountTrigger(LeagueSignalKind.RESOURCE, "log", 10))
        ))
        val session = GrandLeagueSession(content)
        session.enable(reset = true)
        session.signal(LeagueSignal(LeagueSignalKind.RESOURCE, "log"))
        Assertions.assertEquals(1L, session.profile.progress["count:RESOURCE:log"])
        Assertions.assertEquals(setOf("one"), session.profile.completedTasks)
    }
}
