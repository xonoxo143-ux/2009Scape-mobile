import content.global.leagues.core.*

private fun checkOk(result: LeagueActionResult) = check(result.success) { result.message }

fun main() {
    val content = GrandLeagueContent.create()
    check(content.tasks.size >= 150) { "Expected broad task catalogue, got ${content.tasks.size}" }
    check(content.tiers.size == 9 && content.tiers.last().tier == 8)
    check(content.relics.size >= 20)
    check(content.regions.size == 7)
    check(content.fragments.size >= 20)
    check(content.fragmentSets.size >= 8)
    check(content.masteries.size == 19)
    check(content.pacts.size >= 30)
    check(content.echoes.size >= 10)
    check(content.tasks.any { it.source == LeagueSource.DEMONIC_PACTS && it.pactPointReward > 0 })
    check(content.tasks.any { it.category == "boss" && it.masteryPointReward > 0 })
    check(content.fragments.any { it.setIds.size > 1 })

    val session = GrandLeagueSession(content)
    session.enable(reset = true)

    // Drive every trigger to completion. This exercises a production-sized catalogue,
    // shared progress channels, reward currencies, all eight tiers, and persistence-facing state.
    content.tasks.forEach { task ->
        when (val trigger = task.trigger) {
            is CountTrigger -> session.signal(LeagueSignal(trigger.kind, trigger.key, amount = trigger.target))
            is MetricTrigger -> session.signal(LeagueSignal(trigger.kind, trigger.key, value = trigger.target))
        }
    }

    check(session.profile.completedTasks.size == content.tasks.size)
    check(session.profile.tier == 8)
    check(session.profile.regionTokens >= content.regions.sumOf { it.tokenCost })
    content.regions.forEach { checkOk(session.unlockRegion(it.id)) }
    check(session.profile.unlockedRegions.containsAll(content.regions.map { it.id }))

    // Relic choices lock after selection at a tier.
    checkOk(session.selectRelic("endless-harvest"))
    check(!session.selectRelic("production-prodigy").success)

    // Multi-set fragments can activate more than one set through the same equipped pool.
    listOf("trailblazer", "homewrecker", "personal-banker").forEach {
        checkOk(session.unlockFragment(it)); checkOk(session.equipFragment(it))
    }
    check("mobility" in session.activeFragmentSets())
    check("banking" in session.activeFragmentSets())

    // Production task rewards plus tier rewards should make both progression graphs traversable.
    content.masteries.forEach { checkOk(session.unlockMastery(it.id)) }
    content.pacts.forEach { checkOk(session.unlockPact(it.id)) }
    check(session.profile.unlockedMasteries.size == content.masteries.size)
    check(session.profile.unlockedPacts.size == content.pacts.size)

    content.echoes.forEach { checkOk(session.recordEchoKill(it.id, EchoDifficulty.ECHO)) }
    check(content.echoes.all { session.profile.echoKills["${it.id}:echo"] == 1 })

    val encoded = LeagueProfileCodec.encode(session.profile)
    val restored = LeagueProfileCodec.decode(encoded)
    check(restored == session.profile)

    val views = GrandLeagueSession(content, restored).taskViews()
    check(views.size == content.tasks.size && views.all { it.complete })
    check(views.any { it.regionId == "wilderness" && it.category == "boss" })
    val wildernessBosses = GrandLeagueSession(content, restored).taskViews(LeagueTaskFilter(regionId = "wilderness", category = "boss"))
    check(wildernessBosses.isNotEmpty() && wildernessBosses.all { it.regionId == "wilderness" && it.category == "boss" })
    val demonicPactTasks = GrandLeagueSession(content, restored).taskViews(LeagueTaskFilter(source = LeagueSource.DEMONIC_PACTS))
    check(demonicPactTasks.isNotEmpty() && demonicPactTasks.all { it.source == LeagueSource.DEMONIC_PACTS })

    println("GRAND LEAGUE CONTENT CATALOGUE PASS")
    println("tasks=${content.tasks.size} relics=${content.relics.size} fragments=${content.fragments.size} masteries=${content.masteries.size} pacts=${content.pacts.size} echoes=${content.echoes.size}")
    println("points=${restored.points} tier=${restored.tier} regions=${restored.unlockedRegions.size}")
}
