import content.global.leagues.core.*

private fun unlockDefenceCapstone(session: GrandLeagueSession) {
    check(session.unlockPact("demonic-root").success)
    for (tier in 1..6) check(session.unlockPact("pact-defence-$tier").success)
}

fun main() {
    val content = GrandLeagueContent.create()
    val session = GrandLeagueSession(
        content,
        LeagueProfile(active = true, tier = 8, pactPoints = 7)
    )

    check(session.selectRelic("executioner").success)
    val execution = session.modifiers().combat(LeagueCombatStyle.MELEE)
    check(!execution.shouldExecute(currentHealth = 20, maximumHealth = 100, boss = false))
    check(execution.shouldExecute(currentHealth = 19, maximumHealth = 100, boss = false))
    check(!execution.shouldExecute(currentHealth = 10, maximumHealth = 100, boss = true))
    check(execution.shouldExecute(currentHealth = 9, maximumHealth = 100, boss = true))

    // Undying Retribution is the primary lethal interceptor.
    val survival = GrandLeagueSession(
        content,
        LeagueProfile(active = true, tier = 8, pactPoints = 7)
    )
    check(survival.selectRelic("undying-retribution").success)
    unlockDefenceCapstone(survival)
    val undying = survival.interceptLethalHit(
        currentHealth = 40,
        maximumHealth = 100,
        maximumPrayer = 70,
        incomingDamage = 40,
        nowMillis = 1_000L
    )
    check(undying.intercepted)
    check(undying.acceptedDamage == 0)
    check(undying.effectId == "undying-retribution")
    check(undying.restoreHealth == 100)
    check(undying.restorePrayer == 70)
    check(undying.retaliationDamage == 80)
    check(undying.retaliationRadius == 3)
    check(undying.cooldownUntilMillis == 181_000L)

    // While the relic is cooling down, Immortal Shell provides a weaker fallback.
    val shell = survival.interceptLethalHit(40, 100, 70, 80, 1_001L)
    check(shell.intercepted)
    check(shell.effectId == "immortal-shell")
    check(shell.restoreHealth == 25)
    check(shell.restorePrayer == 0)
    check(shell.retaliationDamage == 0)
    check(shell.cooldownUntilMillis == 301_001L)

    // With both effects cooling down, lethal damage is accepted unchanged.
    val exhausted = survival.interceptLethalHit(40, 100, 70, 80, 1_002L)
    check(!exhausted.intercepted)
    check(exhausted.acceptedDamage == 80)

    // Non-lethal damage never consumes an interceptor.
    val cooldownsBefore = survival.profile.cooldowns.toMap()
    val nonLethal = survival.interceptLethalHit(40, 100, 70, 39, 200_000L)
    check(!nonLethal.intercepted)
    check(survival.profile.cooldowns == cooldownsBefore)

    // Cooldowns persist across relog and v1 profiles migrate with no active cooldowns.
    val restored = LeagueProfileCodec.decode(LeagueProfileCodec.encode(survival.profile))
    check(restored == survival.profile)
    val v1 = LeagueProfileCodec.encode(LeagueProfile(active = true))
        .replaceFirst("v=2", "v=1")
        .replace(Regex("\\|cooldowns=[^|]*"), "")
    check(LeagueProfileCodec.decode(v1).cooldowns.isEmpty())

    println("GRAND LEAGUE COMBAT CAPSTONES PASS")
}
