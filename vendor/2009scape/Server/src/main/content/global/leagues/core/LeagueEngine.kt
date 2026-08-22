package content.global.leagues.core

class LeagueTaskEngine(private val content: LeagueContent) {
    private val index: Map<Pair<LeagueSignalKind, String>, List<LeagueTaskDefinition>> =
        content.tasks.groupBy { it.trigger.kind to it.trigger.key }

    fun process(profile: LeagueProfile, signal: LeagueSignal): List<LeagueTaskDefinition> {
        if (!profile.active) return emptyList()
        val candidates = index[signal.kind to signal.key].orEmpty()
        if (candidates.isEmpty()) return emptyList()

        // A signal updates each underlying progress channel once, no matter how many
        // tasks observe that channel. This is essential for large milestone catalogues.
        val countKey = "count:${signal.kind}:${signal.key}"
        val metricKey = "metric:${signal.kind}:${signal.key}"
        val hasCount = candidates.any { it.trigger is CountTrigger }
        val hasMetric = candidates.any { it.trigger is MetricTrigger }
        if (hasCount) profile.progress[countKey] = (profile.progress[countKey] ?: 0L) + signal.amount
        if (hasMetric) profile.progress[metricKey] = maxOf(profile.progress[metricKey] ?: 0L, signal.value ?: signal.amount)

        val completed = mutableListOf<LeagueTaskDefinition>()
        for (task in candidates) {
            if (task.id in profile.completedTasks) continue
            val progress = when (task.trigger) {
                is CountTrigger -> profile.progress[countKey] ?: 0L
                is MetricTrigger -> profile.progress[metricKey] ?: 0L
            }
            val target = when (val trigger = task.trigger) {
                is CountTrigger -> trigger.target
                is MetricTrigger -> trigger.target
            }
            if (progress >= target && profile.completedTasks.add(task.id)) {
                profile.points += task.points
                profile.regionTokens += task.regionTokenReward
                profile.fragmentTokens += task.fragmentTokenReward
                profile.masteryPoints += task.masteryPointReward
                profile.pactPoints += task.pactPointReward
                completed += task
            }
        }
        return completed
    }
}

data class LeagueActionResult(val success: Boolean, val message: String)

data class LeagueProgressUpdate(
    val completedTasks: List<String>,
    val oldTier: Int,
    val newTier: Int,
    val points: Int
)

data class LeagueLethalHitResult(
    val intercepted: Boolean,
    val acceptedDamage: Int,
    val effectId: String? = null,
    val restoreHealth: Int = 0,
    val restorePrayer: Int = 0,
    val retaliationDamage: Int = 0,
    val retaliationRadius: Int = 0,
    val cooldownUntilMillis: Long = 0L
)

class GrandLeagueSession(
    val content: LeagueContent = GrandLeagueContent.create(),
    val profile: LeagueProfile = LeagueProfile()
) {
    private val taskEngine = LeagueTaskEngine(content)
    private val effectResolver = LeagueEffectResolver(content)
    private var modifierCache: LeagueModifierSnapshot? = null

    fun enable(reset: Boolean = false) {
        if (reset) {
            val fresh = LeagueProfile(active = true)
            copyInto(fresh, profile)
        } else profile.active = true
        invalidateModifiers()
        refreshTierRewards()
    }

    fun signal(signal: LeagueSignal): LeagueProgressUpdate {
        val oldTier = profile.tier
        val completed = taskEngine.process(profile, signal).map { it.id }
        refreshTierRewards()
        return LeagueProgressUpdate(completed, oldTier, profile.tier, profile.points)
    }

    fun selectRelic(relicId: String): LeagueActionResult {
        val relic = content.relicsById[relicId] ?: return fail("Unknown relic")
        if (!profile.active) return fail("League is not active")
        if (profile.tier < relic.tier) return fail("Relic tier is locked")
        val selected = profile.selectedRelics[relic.tier]
        if (selected != null && selected != relic.id) return fail("A relic is already selected for tier ${relic.tier}")
        profile.selectedRelics[relic.tier] = relic.id
        invalidateModifiers()
        return ok("Selected ${relic.name}")
    }

    fun unlockRegion(regionId: String): LeagueActionResult {
        if (regionId in profile.unlockedRegions) return ok("Region already unlocked")
        val region = content.regionsById[regionId] ?: return fail("Unknown region")
        if (!profile.unlockedRegions.containsAll(region.prerequisites)) return fail("Region prerequisites are locked")
        if (profile.regionTokens < region.tokenCost) return fail("Not enough region tokens")
        profile.regionTokens -= region.tokenCost
        profile.unlockedRegions += region.id
        return ok("Unlocked ${region.name}")
    }

    fun unlockFragment(fragmentId: String): LeagueActionResult {
        if (fragmentId in profile.unlockedFragments) return ok("Fragment already unlocked")
        val fragment = content.fragmentsById[fragmentId] ?: return fail("Unknown fragment")
        if (profile.fragmentTokens < fragment.tokenCost) return fail("Not enough fragment tokens")
        profile.fragmentTokens -= fragment.tokenCost
        profile.unlockedFragments += fragment.id
        return ok("Unlocked ${fragment.name}")
    }

    fun equipFragment(fragmentId: String): LeagueActionResult {
        if (fragmentId !in profile.unlockedFragments) return fail("Fragment is locked")
        profile.equippedFragments += fragmentId
        invalidateModifiers()
        return ok("Equipped fragment")
    }

    fun unequipFragment(fragmentId: String): LeagueActionResult {
        if (fragmentId !in profile.equippedFragments) return ok("Fragment is not equipped")
        profile.equippedFragments -= fragmentId
        invalidateModifiers()
        return ok("Unequipped fragment")
    }

    fun activeFragmentSets(): Set<String> = content.fragmentSets.filter { set ->
        content.fragments.count { it.id in profile.equippedFragments && set.id in it.setIds } >= set.requiredEquipped
    }.mapTo(linkedSetOf()) { it.id }

    fun modifiers(): LeagueModifierSnapshot = modifierCache ?: effectResolver.resolve(profile, activeFragmentSets()).also {
        modifierCache = it
    }

    fun interceptLethalHit(
        currentHealth: Int,
        maximumHealth: Int,
        maximumPrayer: Int,
        incomingDamage: Int,
        nowMillis: Long
    ): LeagueLethalHitResult {
        require(currentHealth >= 0 && maximumHealth > 0 && currentHealth <= maximumHealth)
        require(maximumPrayer >= 0 && incomingDamage >= 0 && nowMillis >= 0)
        if (!profile.active || currentHealth == 0 || incomingDamage < currentHealth) {
            return LeagueLethalHitResult(false, incomingDamage)
        }

        val active = modifiers().triggered(LeagueTriggeredEffectKind.LETHAL_INTERCEPT)
            .firstOrNull { effect ->
                val key = cooldownKey(effect.definition.id)
                (profile.cooldowns[key] ?: 0L) <= nowMillis
            } ?: return LeagueLethalHitResult(false, incomingDamage)

        val effect = active.definition
        val cooldownUntil = if (Long.MAX_VALUE - nowMillis < effect.cooldownMillis) Long.MAX_VALUE
            else nowMillis + effect.cooldownMillis
        profile.cooldowns[cooldownKey(effect.id)] = cooldownUntil

        val restoredHealth = kotlin.math.ceil(maximumHealth * effect.healthRestoreFraction).toInt().coerceIn(1, maximumHealth)
        val restoredPrayer = kotlin.math.floor(maximumPrayer * effect.prayerRestoreFraction).toInt().coerceIn(0, maximumPrayer)
        val avoidedDamage = minOf(currentHealth, incomingDamage)
        val retaliationDamage = kotlin.math.floor(avoidedDamage * effect.retaliationDamageMultiplier).toInt().coerceAtLeast(0)
        return LeagueLethalHitResult(
            intercepted = true,
            acceptedDamage = 0,
            effectId = effect.id,
            restoreHealth = restoredHealth,
            restorePrayer = restoredPrayer,
            retaliationDamage = retaliationDamage,
            retaliationRadius = effect.retaliationRadius,
            cooldownUntilMillis = cooldownUntil
        )
    }

    fun unlockMastery(id: String): LeagueActionResult = unlockNode(id, content.masteriesById, profile.unlockedMasteries, true)
    fun unlockPact(id: String): LeagueActionResult = unlockNode(id, content.pactsById, profile.unlockedPacts, false)

    fun recordEchoKill(echoId: String, difficulty: EchoDifficulty): LeagueActionResult {
        val echo = content.echoesById[echoId] ?: return fail("Unknown Echo")
        if (profile.tier < echo.minimumTier) return fail("Echo tier is locked")
        if (echo.regionId !in profile.unlockedRegions) return fail("Echo region is locked")
        echo.requiredMastery?.let { if (it !in profile.unlockedMasteries) return fail("Required mastery is locked") }
        echo.requiredPact?.let { if (it !in profile.unlockedPacts) return fail("Required pact is locked") }
        val key = "${echo.id}:${difficulty.name.lowercase()}"
        profile.echoKills[key] = (profile.echoKills[key] ?: 0) + 1
        return ok("Recorded ${difficulty.name} ${echo.name} kill")
    }

    private fun unlockNode(
        id: String,
        definitions: Map<String, LeagueNodeDefinition>,
        unlocked: MutableSet<String>,
        mastery: Boolean
    ): LeagueActionResult {
        if (id in unlocked) return ok("Node already unlocked")
        val node = definitions[id] ?: return fail("Unknown node")
        if (!unlocked.containsAll(node.prerequisites)) return fail("Prerequisites are locked")
        val available = if (mastery) profile.masteryPoints else profile.pactPoints
        if (available < node.pointCost) return fail("Not enough points")
        if (mastery) profile.masteryPoints -= node.pointCost else profile.pactPoints -= node.pointCost
        unlocked += id
        invalidateModifiers()
        return ok("Unlocked ${node.name}")
    }

    private fun refreshTierRewards() {
        val targetTier = content.tiers.last { profile.points >= it.minimumPoints }.tier
        if (targetTier <= profile.tier) return
        for (tier in (profile.tier + 1)..targetTier) {
            val definition = content.tiers.first { it.tier == tier }
            if (profile.claimedTierRewards.add(tier)) {
                profile.regionTokens += definition.regionTokens
                profile.fragmentTokens += definition.fragmentTokens
                profile.masteryPoints += definition.masteryPoints
                profile.pactPoints += definition.pactPoints
            }
        }
        profile.tier = targetTier
    }

    private fun invalidateModifiers() {
        modifierCache = null
    }

    private fun ok(message: String) = LeagueActionResult(true, message)
    private fun fail(message: String) = LeagueActionResult(false, message)

    private fun copyInto(from: LeagueProfile, to: LeagueProfile) {
        to.active = from.active; to.points = from.points; to.tier = from.tier
        to.completedTasks.clear(); to.completedTasks += from.completedTasks
        to.progress.clear(); to.progress.putAll(from.progress)
        to.selectedRelics.clear(); to.selectedRelics.putAll(from.selectedRelics)
        to.unlockedRegions.clear(); to.unlockedRegions += from.unlockedRegions
        to.unlockedFragments.clear(); to.unlockedFragments += from.unlockedFragments
        to.equippedFragments.clear(); to.equippedFragments += from.equippedFragments
        to.unlockedMasteries.clear(); to.unlockedMasteries += from.unlockedMasteries
        to.unlockedPacts.clear(); to.unlockedPacts += from.unlockedPacts
        to.echoKills.clear(); to.echoKills.putAll(from.echoKills)
        to.cooldowns.clear(); to.cooldowns.putAll(from.cooldowns)
        to.claimedTierRewards.clear(); to.claimedTierRewards += from.claimedTierRewards
        to.regionTokens = from.regionTokens; to.fragmentTokens = from.fragmentTokens
        to.masteryPoints = from.masteryPoints; to.pactPoints = from.pactPoints
    }

    private fun cooldownKey(effectId: String) = "trigger:$effectId"
}
