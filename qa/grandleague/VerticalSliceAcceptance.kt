import content.global.leagues.core.*

private fun checkOk(result: LeagueActionResult) = check(result.success) { result.message }

fun main() {
    val content = GrandLeagueBootstrapContent.create()
    val session = GrandLeagueSession(content)
    session.enable(reset = true)

    // Fresh-account progression through tasks and point tiers.
    session.signal(LeagueSignal(LeagueSignalKind.RESOURCE, "log"))
    session.signal(LeagueSignal(LeagueSignalKind.RESOURCE, "fish", amount = 10))
    check(session.profile.tier == 1)
    checkOk(session.selectRelic("endless-harvest"))

    session.signal(LeagueSignal(LeagueSignalKind.NPC_KILL, "cow", amount = 10))
    session.signal(LeagueSignal(LeagueSignalKind.QUEST, "complete", amount = 3))
    check(session.profile.tier >= 2)
    checkOk(session.unlockRegion("asgarnia"))

    session.signal(LeagueSignal(LeagueSignalKind.METRIC, "total-level", value = 250))
    check(session.profile.tier >= 3)
    checkOk(session.unlockFragment("trailblazer")); checkOk(session.equipFragment("trailblazer"))
    checkOk(session.unlockFragment("homewrecker")); checkOk(session.equipFragment("homewrecker"))
    check("mobility" in session.activeFragmentSets())

    session.signal(LeagueSignal(LeagueSignalKind.NPC_KILL, "general-graardor"))
    check(session.profile.tier >= 4)
    checkOk(session.unlockMastery("combat-root")); checkOk(session.unlockMastery("melee-echo"))

    session.signal(LeagueSignal(LeagueSignalKind.METRIC, "total-level", value = 500))
    check(session.profile.tier >= 5)
    checkOk(session.unlockPact("demonic-root")); checkOk(session.unlockPact("echo-pact"))

    session.signal(LeagueSignal(LeagueSignalKind.METRIC, "quest-points", value = 50))
    session.signal(LeagueSignal(LeagueSignalKind.NPC_KILL, "tztok-jad"))
    check(session.profile.tier == 6)
    checkOk(session.unlockRegion("wilderness"))
    checkOk(session.recordEchoKill("kbd", EchoDifficulty.ECHO))

    // Exact-once points: repeating completed signals cannot award points again.
    val points = session.profile.points
    repeat(100) { session.signal(LeagueSignal(LeagueSignalKind.RESOURCE, "log")) }
    check(session.profile.points == points)

    // Relog/persistence must retain the entire vertical slice.
    val encoded = LeagueProfileCodec.encode(session.profile)
    val restored = LeagueProfileCodec.decode(encoded)
    check(restored == session.profile) { "League profile changed across save/relog\n$encoded\n$restored\n${session.profile}" }

    check(restored.selectedRelics[1] == "endless-harvest")
    check(restored.unlockedRegions.containsAll(setOf("asgarnia", "wilderness")))
    check(restored.equippedFragments.containsAll(setOf("trailblazer", "homewrecker")))
    check(restored.unlockedMasteries.containsAll(setOf("combat-root", "melee-echo")))
    check(restored.unlockedPacts.containsAll(setOf("demonic-root", "echo-pact")))
    check(restored.echoKills["kbd:echo"] == 1)

    val restoredSession = GrandLeagueSession(content, restored)
    val overview = restoredSession.overview()
    check(overview.completedTasks == 9 && overview.totalTasks == 9)
    check(overview.nextTierPoints == null)
    check("mobility" in overview.activeFragmentSets)
    check(restoredSession.taskViews().all { it.complete && it.progress == it.target })

    println("GRAND LEAGUE VERTICAL SLICE PASS")
    println("points=${restored.points} tier=${restored.tier} tasks=${restored.completedTasks.size}")
    println("regions=${restored.unlockedRegions.sorted()} fragments=${restored.equippedFragments.sorted()}")
    println("masteries=${restored.unlockedMasteries.sorted()} pacts=${restored.unlockedPacts.sorted()} echoes=${restored.echoKills}")
}
