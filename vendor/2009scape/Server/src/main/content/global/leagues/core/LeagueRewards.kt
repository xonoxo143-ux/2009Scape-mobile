package content.global.leagues.core

data class LeagueCurrencyGrant(
    val currencyId: String,
    val amount: Long,
    val reason: String
) {
    init {
        require(currencyId.matches(Regex("[a-z0-9][a-z0-9._-]*")))
        require(amount > 0)
        require(reason.isNotBlank())
    }
}

data class TaskCurrencyRewardRule(
    val currencyId: String,
    val amountsByDifficulty: Map<LeagueTaskDifficulty, Long>,
    val requiredTags: Set<String> = emptySet(),
    val source: LeagueSource? = null
) {
    init {
        require(currencyId.matches(Regex("[a-z0-9][a-z0-9._-]*")))
        require(amountsByDifficulty.isNotEmpty())
        require(amountsByDifficulty.values.all { it >= 0 })
    }

    fun amountFor(task: LeagueTaskDefinition): Long {
        if (source != null && task.source != source) return 0
        if (!task.tags.containsAll(requiredTags)) return 0
        return amountsByDifficulty[task.difficulty] ?: 0
    }
}

data class TierCurrencyRewardRule(
    val tierIndex: Int,
    val currencyId: String,
    val amount: Long
) {
    init {
        require(tierIndex > 0)
        require(currencyId.matches(Regex("[a-z0-9][a-z0-9._-]*")))
        require(amount > 0)
    }
}

data class LeagueRewardPolicy(
    val taskRules: List<TaskCurrencyRewardRule> = emptyList(),
    val tierRules: List<TierCurrencyRewardRule> = emptyList()
) {
    init {
        val tierKeys = tierRules.map { it.tierIndex to it.currencyId }
        require(tierKeys.distinct().size == tierKeys.size) { "Duplicate League tier currency reward" }
    }
}

/**
 * Applies secondary currencies after the task engine has atomically completed
 * tasks/points. A task completion is exact-once, so its derived rewards are too.
 */
class LeagueRewardEngine(
    private val policy: LeagueRewardPolicy,
    private val progression: LeagueProgression = LeagueProgression.grandLeagueDefaults()
) {
    fun apply(profile: LeagueProfile, result: LeagueSignalResult): List<LeagueCurrencyGrant> {
        if (!profile.active) return emptyList()
        val grants = mutableListOf<LeagueCurrencyGrant>()

        result.completions.forEach { completion ->
            policy.taskRules.forEach { rule ->
                val amount = rule.amountFor(completion.task)
                if (amount > 0) {
                    grantCurrency(profile, rule.currencyId, amount)
                    grants += LeagueCurrencyGrant(rule.currencyId, amount, "task:${completion.task.id}")
                }
            }
        }

        if (result.tierChanged) {
            val crossedTierIndexes = progression.tiers.asSequence()
                .filter { it.requiredPoints > result.oldTier.requiredPoints }
                .filter { it.requiredPoints <= result.newTier.requiredPoints }
                .map { it.index }
                .toSet()
            policy.tierRules.filter { it.tierIndex in crossedTierIndexes }.forEach { rule ->
                grantCurrency(profile, rule.currencyId, rule.amount)
                grants += LeagueCurrencyGrant(rule.currencyId, rule.amount, "tier:${rule.tierIndex}")
            }
        }

        return grants
    }
}

object GrandLeagueRewardPolicy {
    private val renownByDifficulty = mapOf(
        LeagueTaskDifficulty.EASY to 1L,
        LeagueTaskDifficulty.MEDIUM to 2L,
        LeagueTaskDifficulty.HARD to 3L,
        LeagueTaskDifficulty.ELITE to 5L,
        LeagueTaskDifficulty.MASTER to 8L,
        LeagueTaskDifficulty.GRANDMASTER to 12L
    )

    /**
     * Provisional combined-League economy. Historical imports can opt specific
     * tasks into mastery/pact rewards through tags without changing the engine.
     */
    val policy = LeagueRewardPolicy(
        taskRules = listOf(
            TaskCurrencyRewardRule("sage_renown", renownByDifficulty),
            TaskCurrencyRewardRule("mastery_point", LeagueTaskDifficulty.values().associateWith { 1L }, requiredTags = setOf("mastery-point")),
            TaskCurrencyRewardRule("pact_point", LeagueTaskDifficulty.values().associateWith { 1L }, requiredTags = setOf("pact-point"))
        ),
        tierRules = listOf(
            TierCurrencyRewardRule(1, "relic_choice", 1),
            TierCurrencyRewardRule(2, "region_unlock", 1),
            TierCurrencyRewardRule(3, "relic_choice", 1),
            TierCurrencyRewardRule(4, "region_unlock", 1),
            TierCurrencyRewardRule(5, "relic_choice", 1),
            TierCurrencyRewardRule(6, "region_unlock", 1),
            TierCurrencyRewardRule(7, "relic_choice", 1),
            TierCurrencyRewardRule(8, "relic_choice", 1),
            TierCurrencyRewardRule(9, "relic_choice", 1),
            TierCurrencyRewardRule(10, "relic_choice", 1)
        )
    )
}
