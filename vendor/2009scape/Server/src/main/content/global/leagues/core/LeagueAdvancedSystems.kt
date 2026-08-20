package content.global.leagues.core

// ---- Shattered Relics fragments ------------------------------------------------

data class FragmentDefinition(
    val id: String,
    val name: String,
    val levelThresholds: List<Long> = listOf(0, 5_000, 15_000),
    val setTags: Set<String> = emptySet(),
    val source: LeagueSource = LeagueSource.SHATTERED_RELICS,
    val requirements: List<LeagueRequirement> = emptyList(),
    val effectsByLevel: Map<Int, Set<LeagueEffect>> = emptyMap()
) {
    val maxLevel: Int get() = levelThresholds.size

    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]*")))
        require(name.isNotBlank())
        require(levelThresholds.isNotEmpty() && levelThresholds.first() == 0L)
        levelThresholds.zipWithNext().forEach { (a, b) -> require(b > a) }
    }

    fun levelForXp(xp: Long): Int {
        val safeXp = xp.coerceAtLeast(0)
        return levelThresholds.indexOfLast { safeXp >= it } + 1
    }
}

data class FragmentSetDefinition(
    val id: String,
    val name: String,
    val tag: String,
    val requiredFragments: Int,
    val effects: Set<LeagueEffect> = emptySet()
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]*")))
        require(name.isNotBlank())
        require(tag.isNotBlank())
        require(requiredFragments > 0)
    }
}

class FragmentRegistry(
    fragments: Collection<FragmentDefinition>,
    sets: Collection<FragmentSetDefinition> = emptyList()
) {
    val fragments = fragments.toList()
    val sets = sets.toList()
    private val byId = this.fragments.associateBy { it.id }

    init {
        require(byId.size == this.fragments.size) { "Duplicate fragment id" }
        require(this.sets.map { it.id }.distinct().size == this.sets.size) { "Duplicate fragment set id" }
    }

    fun get(id: String) = byId[id]
}

class FragmentEngine(
    private val registry: FragmentRegistry,
    private val context: LeagueRequirementContext = LeagueRequirementContext()
) {
    fun addXp(profile: LeagueProfile, fragmentId: String, amount: Long): LeagueUnlockResult {
        require(amount >= 0)
        val fragment = registry.get(fragmentId) ?: return LeagueUnlockResult.fail(LeagueUnlockFailure.UNKNOWN_CONTENT)
        val unmet = fragment.requirements.unmet(profile, context)
        if (unmet.isNotEmpty()) return LeagueUnlockResult.fail(LeagueUnlockFailure.REQUIREMENTS_NOT_MET, unmet)
        if (amount == 0L) return LeagueUnlockResult.success()
        val oldXp = profile.fragmentXp[fragmentId] ?: 0L
        val newXp = if (oldXp > Long.MAX_VALUE - amount) Long.MAX_VALUE else oldXp + amount
        profile.fragmentXp[fragmentId] = newXp
        profile.fragmentLevels[fragmentId] = fragment.levelForXp(newXp)
        return LeagueUnlockResult.success()
    }

    fun equip(profile: LeagueProfile, fragmentId: String, maxSlots: Int): LeagueUnlockResult {
        require(maxSlots >= 0)
        if (registry.get(fragmentId) == null) return LeagueUnlockResult.fail(LeagueUnlockFailure.UNKNOWN_CONTENT)
        if ((profile.fragmentLevels[fragmentId] ?: 0) <= 0) {
            return LeagueUnlockResult.fail(LeagueUnlockFailure.REQUIREMENTS_NOT_MET)
        }
        if (fragmentId in profile.equippedFragments) return LeagueUnlockResult.success()
        if (profile.equippedFragments.size >= maxSlots) return LeagueUnlockResult.fail(LeagueUnlockFailure.INVALID_SELECTION)
        profile.equippedFragments += fragmentId
        return LeagueUnlockResult.success()
    }

    fun unequip(profile: LeagueProfile, fragmentId: String): Boolean = profile.equippedFragments.remove(fragmentId)

    fun activeSets(profile: LeagueProfile): Set<String> {
        val activeFragments = profile.equippedFragments.mapNotNull(registry::get)
        return registry.sets.filter { set ->
            activeFragments.count { set.tag in it.setTags } >= set.requiredFragments
        }.mapTo(mutableSetOf()) { it.id }
    }
}

// ---- Raging Echoes combat masteries --------------------------------------------

data class MasteryDefinition(
    val id: String,
    val name: String,
    val maxRank: Int = 6,
    val pointCostPerRank: Long = 1,
    val requirements: List<LeagueRequirement> = emptyList(),
    val effectsByRank: Map<Int, Set<LeagueEffect>> = emptyMap()
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]*")))
        require(name.isNotBlank())
        require(maxRank > 0)
        require(pointCostPerRank > 0)
    }
}

class MasteryRegistry(masteries: Collection<MasteryDefinition>) {
    val masteries = masteries.toList()
    private val byId = this.masteries.associateBy { it.id }
    init { require(byId.size == this.masteries.size) { "Duplicate mastery id" } }
    fun get(id: String) = byId[id]
}

class MasteryEngine(
    private val registry: MasteryRegistry,
    private val context: LeagueRequirementContext = LeagueRequirementContext(),
    private val currencyId: String = "mastery_point"
) {
    fun rankUp(profile: LeagueProfile, masteryId: String): LeagueUnlockResult {
        val mastery = registry.get(masteryId) ?: return LeagueUnlockResult.fail(LeagueUnlockFailure.UNKNOWN_CONTENT)
        val current = profile.masteryRanks[masteryId] ?: 0
        if (current >= mastery.maxRank) return LeagueUnlockResult.fail(LeagueUnlockFailure.ALREADY_UNLOCKED)
        val unmet = mastery.requirements.unmet(profile, context)
        if (unmet.isNotEmpty()) return LeagueUnlockResult.fail(LeagueUnlockFailure.REQUIREMENTS_NOT_MET, unmet)
        if (!spendCurrency(profile, currencyId, mastery.pointCostPerRank)) {
            return LeagueUnlockResult.fail(LeagueUnlockFailure.INSUFFICIENT_CURRENCY)
        }
        profile.masteryRanks[masteryId] = current + 1
        return LeagueUnlockResult.success()
    }
}

// ---- Echo bosses ----------------------------------------------------------------

data class EchoDefinition(
    val id: String,
    val name: String,
    val bossKey: String,
    val tier: Int = 1,
    val requirements: List<LeagueRequirement> = emptyList(),
    val rewardKeys: Set<String> = emptySet()
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._:-]*")))
        require(name.isNotBlank())
        require(bossKey.isNotBlank())
        require(tier > 0)
    }
}

class EchoRegistry(echoes: Collection<EchoDefinition>) {
    val echoes = echoes.toList()
    private val byId = this.echoes.associateBy { it.id }
    init { require(byId.size == this.echoes.size) { "Duplicate Echo id" } }
    fun get(id: String) = byId[id]
}

class EchoEngine(
    private val registry: EchoRegistry,
    private val context: LeagueRequirementContext = LeagueRequirementContext()
) {
    fun unlock(profile: LeagueProfile, echoId: String): LeagueUnlockResult {
        val echo = registry.get(echoId) ?: return LeagueUnlockResult.fail(LeagueUnlockFailure.UNKNOWN_CONTENT)
        if (echoId in profile.unlockedEchoes) return LeagueUnlockResult.fail(LeagueUnlockFailure.ALREADY_UNLOCKED)
        val unmet = echo.requirements.unmet(profile, context)
        if (unmet.isNotEmpty()) return LeagueUnlockResult.fail(LeagueUnlockFailure.REQUIREMENTS_NOT_MET, unmet)
        profile.unlockedEchoes += echoId
        return LeagueUnlockResult.success()
    }

    fun recordKill(profile: LeagueProfile, echoId: String, amount: Long = 1): LeagueUnlockResult {
        require(amount > 0)
        if (registry.get(echoId) == null) return LeagueUnlockResult.fail(LeagueUnlockFailure.UNKNOWN_CONTENT)
        if (echoId !in profile.unlockedEchoes) return LeagueUnlockResult.fail(LeagueUnlockFailure.REQUIREMENTS_NOT_MET)
        val old = profile.echoKills[echoId] ?: 0L
        profile.echoKills[echoId] = if (old > Long.MAX_VALUE - amount) Long.MAX_VALUE else old + amount
        return LeagueUnlockResult.success()
    }
}

// ---- RS3 League blessings --------------------------------------------------------

data class BlessingDefinition(
    val id: String,
    val name: String,
    val source: LeagueSource = LeagueSource.RS3_EQUILIBRIUM,
    val requirements: List<LeagueRequirement> = emptyList(),
    val effects: Set<LeagueEffect> = emptySet()
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]*")))
        require(name.isNotBlank())
    }
}

class BlessingRegistry(blessings: Collection<BlessingDefinition>) {
    val blessings = blessings.toList()
    private val byId = this.blessings.associateBy { it.id }
    init { require(byId.size == this.blessings.size) { "Duplicate blessing id" } }
    fun get(id: String) = byId[id]
}

class BlessingEngine(
    private val registry: BlessingRegistry,
    private val context: LeagueRequirementContext = LeagueRequirementContext()
) {
    fun unlock(profile: LeagueProfile, blessingId: String): LeagueUnlockResult {
        val blessing = registry.get(blessingId) ?: return LeagueUnlockResult.fail(LeagueUnlockFailure.UNKNOWN_CONTENT)
        if (blessingId in profile.unlockedBlessings) return LeagueUnlockResult.fail(LeagueUnlockFailure.ALREADY_UNLOCKED)
        val unmet = blessing.requirements.unmet(profile, context)
        if (unmet.isNotEmpty()) return LeagueUnlockResult.fail(LeagueUnlockFailure.REQUIREMENTS_NOT_MET, unmet)
        profile.unlockedBlessings += blessingId
        return LeagueUnlockResult.success()
    }
}
