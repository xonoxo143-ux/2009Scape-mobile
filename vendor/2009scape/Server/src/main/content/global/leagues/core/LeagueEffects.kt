package content.global.leagues.core

/** Common numeric modifier keys. Custom mechanics can use [FlagLeagueEffect]. */
enum class LeagueModifierKey {
    XP_MULTIPLIER,
    RESOURCE_MULTIPLIER,
    PRODUCTION_MULTIPLIER,
    DROP_RATE_MULTIPLIER,
    DAMAGE_MULTIPLIER,
    ACCURACY_MULTIPLIER,
    ATTACK_SPEED_MULTIPLIER,
    PRAYER_DRAIN_MULTIPLIER,
    RUN_ENERGY_DRAIN_MULTIPLIER
}

enum class LeagueModifierOperation {
    MULTIPLY,
    ADD,
    MAX,
    MIN
}

sealed interface LeagueEffect

data class NumericLeagueEffect(
    val key: LeagueModifierKey,
    val operation: LeagueModifierOperation,
    val value: Double
) : LeagueEffect {
    init { require(value.isFinite()) { "League modifier values must be finite" } }
}

data class FlagLeagueEffect(val key: String) : LeagueEffect {
    init { require(key.matches(Regex("[a-z0-9][a-z0-9._:-]*"))) { "Invalid League effect flag '$key'" } }
}

/** Permanent effective-level floor above the player's static level. */
data class SkillBoostLeagueEffect(val skillId: Int, val amount: Int) : LeagueEffect {
    init {
        require(skillId >= 0) { "Skill id cannot be negative" }
        require(amount >= 0) { "Skill boost cannot be negative" }
    }
}

data class LeagueResolvedEffects(
    val numeric: Map<LeagueModifierKey, Double>,
    val flags: Set<String>,
    val skillBoosts: Map<Int, Int> = emptyMap()
) {
    fun numeric(key: LeagueModifierKey, default: Double = 1.0): Double = numeric[key] ?: default
    fun hasFlag(key: String): Boolean = key in flags
    fun skillBoost(skillId: Int): Int = skillBoosts[skillId] ?: 0
}

/**
 * Deterministic composition for effects coming from relics, fragments, masteries,
 * pacts, blessings and passive tier bonuses.
 *
 * For each numeric key: additions are applied to a base of 1.0, then multiplied;
 * MAX/MIN constraints are applied last. This gives content definitions explicit,
 * testable stacking semantics rather than relying on call order.
 */
object LeagueEffectResolver {
    fun resolve(effects: Iterable<LeagueEffect>): LeagueResolvedEffects {
        data class Acc(
            var add: Double = 0.0,
            var multiply: Double = 1.0,
            var maxFloor: Double? = null,
            var minCeiling: Double? = null
        )

        val numeric = mutableMapOf<LeagueModifierKey, Acc>()
        val flags = linkedSetOf<String>()
        val skillBoosts = mutableMapOf<Int, Int>()
        effects.forEach { effect ->
            when (effect) {
                is FlagLeagueEffect -> flags += effect.key
                is SkillBoostLeagueEffect ->
                    skillBoosts[effect.skillId] = maxOf(skillBoosts[effect.skillId] ?: 0, effect.amount)
                is NumericLeagueEffect -> {
                    val acc = numeric.getOrPut(effect.key) { Acc() }
                    when (effect.operation) {
                        LeagueModifierOperation.ADD -> acc.add += effect.value
                        LeagueModifierOperation.MULTIPLY -> acc.multiply *= effect.value
                        LeagueModifierOperation.MAX -> acc.maxFloor = maxOf(acc.maxFloor ?: Double.NEGATIVE_INFINITY, effect.value)
                        LeagueModifierOperation.MIN -> acc.minCeiling = minOf(acc.minCeiling ?: Double.POSITIVE_INFINITY, effect.value)
                    }
                }
            }
        }

        val resolved = numeric.mapValues { (_, acc) ->
            var value = (1.0 + acc.add) * acc.multiply
            acc.maxFloor?.let { value = maxOf(value, it) }
            acc.minCeiling?.let { value = minOf(value, it) }
            value
        }
        return LeagueResolvedEffects(resolved, flags, skillBoosts)
    }
}

fun Map<Int, Set<LeagueEffect>>.effectsThroughRank(rank: Int): List<LeagueEffect> =
    entries.asSequence()
        .filter { (requiredRank, _) -> requiredRank <= rank }
        .sortedBy { it.key }
        .flatMap { it.value.asSequence() }
        .toList()

/** Collects all currently-active effects from the major Grand League systems. */
class LeagueActiveEffectResolver(
    private val relics: RelicRegistry? = null,
    private val fragments: FragmentRegistry? = null,
    private val masteries: MasteryRegistry? = null,
    private val pacts: PactRegistry? = null,
    private val blessings: BlessingRegistry? = null,
    private val tierEffects: Map<Int, Set<LeagueEffect>> = emptyMap(),
    private val progression: LeagueProgression = LeagueProgression.grandLeagueDefaults()
) {
    fun resolve(profile: LeagueProfile): LeagueResolvedEffects {
        val effects = mutableListOf<LeagueEffect>()

        val tier = progression.tierFor(profile.points).index
        tierEffects.entries
            .filter { it.key <= tier }
            .sortedBy { it.key }
            .forEach { effects += it.value }

        relics?.let { registry ->
            // Traditional relics are choices: alternatives may be permanently unlocked,
            // but only the selected relic for each tier contributes active effects.
            profile.primaryRelicsByTier.toSortedMap().values
                .mapNotNull(registry::get)
                .forEach { effects += it.effects }
        }

        fragments?.let { registry ->
            profile.equippedFragments.forEach { id ->
                val fragment = registry.get(id) ?: return@forEach
                effects += fragment.effectsByLevel.effectsThroughRank(profile.fragmentLevels[id] ?: 0)
            }
            val activeSetIds = FragmentEngine(registry).activeSets(profile)
            registry.sets.filter { it.id in activeSetIds }.forEach { effects += it.effects }
        }

        masteries?.let { registry ->
            profile.masteryRanks.forEach { (id, rank) ->
                val mastery = registry.get(id) ?: return@forEach
                effects += mastery.effectsByRank.effectsThroughRank(rank)
            }
        }

        pacts?.let { registry ->
            profile.unlockedPacts.mapNotNull(registry::get).forEach { effects += it.effects }
        }

        blessings?.let { registry ->
            profile.unlockedBlessings.mapNotNull(registry::get).forEach { effects += it.effects }
        }

        return LeagueEffectResolver.resolve(effects)
    }
}
