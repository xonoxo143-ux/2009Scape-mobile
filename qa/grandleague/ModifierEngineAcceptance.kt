import content.global.leagues.core.*
import kotlin.math.abs

private fun close(actual: Double, expected: Double, epsilon: Double = 0.000001) {
    check(abs(actual - expected) <= epsilon) { "Expected $expected, got $actual" }
}

fun main() {
    val content = GrandLeagueContent.create()

    // Inactive profiles are exactly vanilla regardless of stale/untrusted selections.
    val inactive = LeagueProfile(active = false, selectedRelics = linkedMapOf(1 to "endless-harvest"))
    close(LeagueEffectResolver(content).resolve(inactive, emptySet()).gathering().resourceMultiplier, 1.0)

    // Traditional relics from different tiers compose through generic primitives.
    val profile = LeagueProfile(
        active = true,
        tier = 8,
        selectedRelics = linkedMapOf(
            1 to "endless-harvest",
            3 to "fire-sale",
            6 to "farmers-fortune"
        )
    )
    var snapshot = LeagueEffectResolver(content).resolve(profile, emptySet())
    close(snapshot.gathering().resourceMultiplier, 2.0)
    check(snapshot.gathering().bankBonusResources)
    close(snapshot.shop().priceMultiplier, 0.0)
    close(snapshot.shop().stockConsumptionMultiplier, 0.0)
    close(snapshot.farming().growthMultiplier, 5.0)
    close(snapshot.farming().yieldMultiplier, 2.0)
    check(snapshot.farming().diseaseImmune)
    close(snapshot.farming().diseaseChanceMultiplier, 0.0)

    // Trickster's skill modifiers are centrally resolved and scoped.
    profile.selectedRelics.clear()
    profile.selectedRelics[1] = "trickster"
    snapshot = LeagueEffectResolver(content).resolve(profile, emptySet())
    close(snapshot.thieving().successMultiplier, 2.0)
    check(snapshot.thieving().autoRepeat)
    close(snapshot.agility().failChanceMultiplier, 0.25)
    close(snapshot.hunter().successMultiplier, 2.0)

    // Combat effects use the same scoped resolver: ranged power must not leak into melee.
    profile.selectedRelics.clear()
    profile.equippedFragments.clear()
    profile.unlockedMasteries.clear()
    profile.unlockedPacts.clear()
    profile.selectedRelics[4] = "archers-embrace"
    snapshot = LeagueEffectResolver(content).resolve(profile, emptySet())
    close(snapshot.combat(LeagueCombatStyle.RANGED).accuracyMultiplier, 1.25)
    close(snapshot.combat(LeagueCombatStyle.RANGED).attackIntervalMultiplier, 0.50)
    close(snapshot.combat(LeagueCombatStyle.RANGED).ammoSaveChance, 0.90)
    close(snapshot.combat(LeagueCombatStyle.MELEE).accuracyMultiplier, 1.0)

    // Mastery and Pact effects compose without the combat engine knowing node ids.
    profile.selectedRelics.clear()
    profile.unlockedMasteries += setOf("combat-root", "ranged-1", "ranged-2", "ranged-3", "ranged-4")
    profile.unlockedPacts += setOf("demonic-root", "pact-ranged-1", "pact-ranged-2", "pact-ranged-3")
    snapshot = LeagueEffectResolver(content).resolve(profile, emptySet())
    close(snapshot.combat(LeagueCombatStyle.RANGED).accuracyMultiplier, 1.05 * 1.10)
    close(snapshot.combat(LeagueCombatStyle.RANGED).damageMultiplier, 1.05)
    close(snapshot.combat(LeagueCombatStyle.RANGED).attackIntervalMultiplier, 0.90)
    close(snapshot.combat(LeagueCombatStyle.RANGED).defencePenetration, 0.10)
    close(snapshot.combat(LeagueCombatStyle.RANGED).ammoSaveChance, 0.50)

    // Companion and prayer relics resolve into dedicated snapshots rather than leaking
    // their source ids into gameplay systems.
    val capstones = LeagueProfile(
        active = true,
        tier = 8,
        selectedRelics = linkedMapOf(6 to "ruinous-powers", 8 to "guardian")
    )
    snapshot = LeagueEffectResolver(content).resolve(capstones, emptySet())
    val prayer = snapshot.prayer()
    check(prayer.enabled)
    close(prayer.accuracyMultiplier, 1.10)
    close(prayer.damageMultiplier, 1.05)
    close(prayer.drainMultiplier, 1.25)
    val guardian = snapshot.guardian()
    check(guardian.enabled)
    check(guardian.minimumHit == 6 && guardian.maximumHit == 15)
    check(guardian.attackIntervalTicks == 5)
    check(guardian.accuracyRoll == 45_000)
    val evenRolls = LeagueGuardianCombat.hitChance(45_000, 45_000)
    check(evenRolls in 0.49..0.51)
    check(LeagueGuardianCombat.isAccurate(45_000, 45_000, 0.49))
    check(!LeagueGuardianCombat.isAccurate(45_000, 45_000, 0.51))

    // Second-wave combat effects cover low-HP scaling, sustain, repeat hits and defence.
    profile.unlockedMasteries.clear()
    profile.unlockedPacts.clear()
    profile.equippedFragments.clear()
    profile.selectedRelics.clear()
    profile.selectedRelics[7] = "berserker"
    snapshot = LeagueEffectResolver(content).resolve(profile, emptySet())
    close(snapshot.combat(LeagueCombatStyle.MELEE).outgoingDamageMultiplier(1.0), 1.0)
    close(snapshot.combat(LeagueCombatStyle.MELEE).outgoingDamageMultiplier(0.5), 1.5)
    close(snapshot.combat(LeagueCombatStyle.MELEE).outgoingDamageMultiplier(0.0), 2.0)

    profile.selectedRelics[7] = "soul-stealer"
    snapshot = LeagueEffectResolver(content).resolve(profile, emptySet())
    close(snapshot.combat(LeagueCombatStyle.MELEE).lifestealFraction, 0.10)
    close(snapshot.combat(LeagueCombatStyle.MAGIC).prayerRestoreFraction, 0.05)

    profile.selectedRelics[7] = "weapon-master"
    snapshot = LeagueEffectResolver(content).resolve(profile, emptySet())
    close(snapshot.combat(LeagueCombatStyle.MELEE).specialAttackCostMultiplier, 0.50)
    close(snapshot.combat(LeagueCombatStyle.RANGED).specialEnergyRestoreMultiplier, 2.0)

    val survival = GrandLeagueSession(content, LeagueProfile(active = true, tier = 8, fragmentTokens = 5))
    listOf("absolute-unit", "twin-strikes", "venomaster").forEach {
        check(survival.unlockFragment(it).success)
        check(survival.equipFragment(it).success)
    }
    check("survival" in survival.activeFragmentSets())
    val survivalCombat = survival.modifiers().combat(LeagueCombatStyle.MELEE)
    close(survivalCombat.incomingDamageMultiplier, 0.90 * 0.80)
    close(survivalCombat.reflectFraction, 0.15)
    close(survivalCombat.extraHitChance, 0.45)
    close(survivalCombat.extraHitDamageFraction, 0.75)

    // Equilibrium scales smoothly from no bonus at average to the full +50% at zero XP.
    profile.selectedRelics[6] = "equilibrium"
    snapshot = LeagueEffectResolver(content).resolve(profile, emptySet())
    close(snapshot.xpMultiplier(1_000.0, 1_000.0), 1.0)
    close(snapshot.xpMultiplier(500.0, 1_000.0), 1.25)
    close(snapshot.xpMultiplier(0.0, 1_000.0), 1.50)

    // Scope-specific effects do not leak into unrelated gathering skills.
    profile.selectedRelics.clear()
    profile.equippedFragments += "rock-solid"
    snapshot = LeagueEffectResolver(content).resolve(profile, emptySet())
    close(snapshot.gathering(setOf(LeagueEffectScope.GATHERING, LeagueEffectScope.MINING)).resourceMultiplier, 1.25)
    close(snapshot.gathering(setOf(LeagueEffectScope.GATHERING, LeagueEffectScope.FISHING)).resourceMultiplier, 1.0)

    // Fragment set activation stacks multiplicatively and is removed when the set breaks.
    val session = GrandLeagueSession(content, LeagueProfile(active = true, tier = 8, fragmentTokens = 10))
    listOf("greedy-gatherer", "production-prodigy-fragment").forEach {
        check(session.unlockFragment(it).success)
        check(session.equipFragment(it).success)
    }
    check("gathering" in session.activeFragmentSets())
    // 1.25 fragment x 1.5 set bonus = 1.875.
    close(session.modifiers().gathering().resourceMultiplier, 1.875)
    check(session.unequipFragment("production-prodigy-fragment").success)
    check("gathering" !in session.activeFragmentSets())
    close(session.modifiers().gathering().resourceMultiplier, 1.25)

    // Construction-only production effects require both scopes.
    val home = GrandLeagueSession(content, LeagueProfile(active = true, tier = 8, fragmentTokens = 2))
    check(home.unlockFragment("homewrecker").success)
    check(home.equipFragment("homewrecker").success)
    close(home.modifiers().production().speedMultiplier, 1.0)
    close(home.modifiers().production(setOf(LeagueEffectScope.PRODUCTION, LeagueEffectScope.CONSTRUCTION)).speedMultiplier, 2.0)

    // Fractional quantity math is deterministic under an injected roll.
    check(LeagueEffectMath.scaledQuantity(1, 2.0, 0.99) == 2)
    check(LeagueEffectMath.scaledQuantity(1, 1.25, 0.10) == 2)
    check(LeagueEffectMath.scaledQuantity(1, 1.25, 0.50) == 1)
    check(LeagueEffectMath.scaledQuantity(4, 1.25, 0.99) == 5)

    println("GRAND LEAGUE MODIFIER ENGINE PASS")
}
