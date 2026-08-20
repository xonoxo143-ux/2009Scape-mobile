package content.global.leagues.core

enum class LeagueUnlockFailure {
    UNKNOWN_CONTENT,
    ALREADY_UNLOCKED,
    REQUIREMENTS_NOT_MET,
    INSUFFICIENT_CURRENCY,
    INVALID_SELECTION
}

data class LeagueUnlockResult(
    val success: Boolean,
    val failure: LeagueUnlockFailure? = null,
    val unmetRequirements: List<LeagueRequirement> = emptyList()
) {
    companion object {
        fun success() = LeagueUnlockResult(true)
        fun fail(failure: LeagueUnlockFailure, unmet: List<LeagueRequirement> = emptyList()) =
            LeagueUnlockResult(false, failure, unmet)
    }
}

data class RelicDefinition(
    val id: String,
    val name: String,
    val tier: Int,
    val source: LeagueSource,
    val requirements: List<LeagueRequirement> = listOf(TierAtLeastRequirement(tier)),
    val effects: Set<LeagueEffect> = emptySet()
)

class RelicRegistry(relics: Collection<RelicDefinition>) {
    val relics = relics.toList()
    private val byId = this.relics.associateBy { it.id }

    init {
        require(byId.size == this.relics.size) { "Duplicate relic id" }
        this.relics.forEach {
            require(it.id.matches(Regex("[a-z0-9][a-z0-9._-]*"))) { "Invalid relic id ${it.id}" }
            require(it.name.isNotBlank()) { "Relic ${it.id} has blank name" }
            require(it.tier >= 0) { "Relic ${it.id} has negative tier" }
        }
    }

    fun get(id: String) = byId[id]
}

class RelicEngine(
    private val registry: RelicRegistry,
    private val context: LeagueRequirementContext = LeagueRequirementContext()
) {
    fun unlock(profile: LeagueProfile, relicId: String): LeagueUnlockResult {
        val relic = registry.get(relicId) ?: return LeagueUnlockResult.fail(LeagueUnlockFailure.UNKNOWN_CONTENT)
        if (relicId in profile.unlockedRelics) return LeagueUnlockResult.fail(LeagueUnlockFailure.ALREADY_UNLOCKED)
        val unmet = relic.requirements.unmet(profile, context)
        if (unmet.isNotEmpty()) return LeagueUnlockResult.fail(LeagueUnlockFailure.REQUIREMENTS_NOT_MET, unmet)
        profile.unlockedRelics += relicId
        return LeagueUnlockResult.success()
    }

    /** First selection at a tier unlocks that relic. Later selections may use separately-unlocked alternatives. */
    fun selectPrimary(profile: LeagueProfile, relicId: String): LeagueUnlockResult {
        val relic = registry.get(relicId) ?: return LeagueUnlockResult.fail(LeagueUnlockFailure.UNKNOWN_CONTENT)
        if (relicId !in profile.unlockedRelics) {
            val unlock = unlock(profile, relicId)
            if (!unlock.success) return unlock
        }
        profile.primaryRelicsByTier[relic.tier] = relicId
        return LeagueUnlockResult.success()
    }
}

data class RegionDefinition(
    val id: String,
    val name: String,
    val unlockCurrency: String = "region_unlock",
    val unlockCost: Long = 1,
    val requirements: List<LeagueRequirement> = emptyList()
)

class RegionRegistry(regions: Collection<RegionDefinition>) {
    val regions = regions.toList()
    private val byId = this.regions.associateBy { it.id }

    init {
        require(byId.size == this.regions.size) { "Duplicate region id" }
        this.regions.forEach {
            require(it.id.matches(Regex("[a-z0-9][a-z0-9._-]*"))) { "Invalid region id ${it.id}" }
            require(it.name.isNotBlank())
            require(it.unlockCost >= 0)
        }
    }

    fun get(id: String) = byId[id]
}

class RegionEngine(
    private val registry: RegionRegistry,
    private val context: LeagueRequirementContext = LeagueRequirementContext()
) {
    fun unlock(profile: LeagueProfile, regionId: String): LeagueUnlockResult {
        val region = registry.get(regionId) ?: return LeagueUnlockResult.fail(LeagueUnlockFailure.UNKNOWN_CONTENT)
        if (regionId in profile.unlockedRegions) return LeagueUnlockResult.fail(LeagueUnlockFailure.ALREADY_UNLOCKED)
        val unmet = region.requirements.unmet(profile, context)
        if (unmet.isNotEmpty()) return LeagueUnlockResult.fail(LeagueUnlockFailure.REQUIREMENTS_NOT_MET, unmet)
        if (!spendCurrency(profile, region.unlockCurrency, region.unlockCost)) {
            return LeagueUnlockResult.fail(LeagueUnlockFailure.INSUFFICIENT_CURRENCY)
        }
        profile.unlockedRegions += regionId
        return LeagueUnlockResult.success()
    }
}

data class PactNodeDefinition(
    val id: String,
    val name: String,
    val prerequisites: Set<String> = emptySet(),
    val pointCost: Long = 1,
    val requirements: List<LeagueRequirement> = emptyList(),
    val effects: Set<LeagueEffect> = emptySet()
)

class PactRegistry(nodes: Collection<PactNodeDefinition>) {
    val nodes = nodes.toList()
    private val byId = this.nodes.associateBy { it.id }

    init {
        require(byId.size == this.nodes.size) { "Duplicate pact node id" }
        this.nodes.forEach { node ->
            require(node.id.matches(Regex("[a-z0-9][a-z0-9._-]*"))) { "Invalid pact node id ${node.id}" }
            require(node.name.isNotBlank())
            require(node.pointCost > 0)
            node.prerequisites.forEach { prerequisite ->
                require(prerequisite in byId) { "Pact ${node.id} references unknown prerequisite $prerequisite" }
                require(prerequisite != node.id) { "Pact ${node.id} cannot require itself" }
            }
        }
        require(findCycle() == null) { "Pact graph contains a cycle: ${findCycle()?.joinToString(" -> ")}" }
    }

    fun get(id: String) = byId[id]

    private fun findCycle(): List<String>? {
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun visit(id: String): List<String>? {
            if (id in visiting) {
                val start = path.indexOf(id).coerceAtLeast(0)
                return path.subList(start, path.size).toList() + id
            }
            if (id in visited) return null
            visiting += id
            path += id
            for (next in byId.getValue(id).prerequisites) {
                val cycle = visit(next)
                if (cycle != null) return cycle
            }
            path.removeAt(path.lastIndex)
            visiting -= id
            visited += id
            return null
        }

        for (id in byId.keys) visit(id)?.let { return it }
        return null
    }
}

class PactEngine(
    private val registry: PactRegistry,
    private val context: LeagueRequirementContext = LeagueRequirementContext(),
    private val currencyId: String = "pact_point"
) {
    fun unlock(profile: LeagueProfile, pactId: String): LeagueUnlockResult {
        val node = registry.get(pactId) ?: return LeagueUnlockResult.fail(LeagueUnlockFailure.UNKNOWN_CONTENT)
        if (pactId in profile.unlockedPacts) return LeagueUnlockResult.fail(LeagueUnlockFailure.ALREADY_UNLOCKED)
        val missingNodes = node.prerequisites.filterNot { it in profile.unlockedPacts }
        if (missingNodes.isNotEmpty()) {
            val unmet = missingNodes.map { PactUnlockedRequirement(it) }
            return LeagueUnlockResult.fail(LeagueUnlockFailure.REQUIREMENTS_NOT_MET, unmet)
        }
        val unmet = node.requirements.unmet(profile, context)
        if (unmet.isNotEmpty()) return LeagueUnlockResult.fail(LeagueUnlockFailure.REQUIREMENTS_NOT_MET, unmet)
        if (!spendCurrency(profile, currencyId, node.pointCost)) {
            return LeagueUnlockResult.fail(LeagueUnlockFailure.INSUFFICIENT_CURRENCY)
        }
        profile.unlockedPacts += pactId
        return LeagueUnlockResult.success()
    }
}

fun grantCurrency(profile: LeagueProfile, currencyId: String, amount: Long) {
    require(currencyId.isNotBlank())
    require(amount >= 0)
    if (amount == 0L) return
    val current = profile.currencies[currencyId] ?: 0L
    profile.currencies[currencyId] = if (current > Long.MAX_VALUE - amount) Long.MAX_VALUE else current + amount
}

fun spendCurrency(profile: LeagueProfile, currencyId: String, amount: Long): Boolean {
    require(currencyId.isNotBlank())
    require(amount >= 0)
    if (amount == 0L) return true
    val current = profile.currencies[currencyId] ?: 0L
    if (current < amount) return false
    val remaining = current - amount
    if (remaining == 0L) profile.currencies.remove(currencyId) else profile.currencies[currencyId] = remaining
    return true
}
