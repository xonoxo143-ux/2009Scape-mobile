package content.global.leagues.core

import kotlin.math.floor

enum class LeagueOutputKind {
    RESOURCE,
    PRODUCTION
}

data class LeagueResolvedOutput(
    /** Ordinary output that follows the skill's normal delivery path. */
    val baseAmount: Int,
    /** League-created output beyond the ordinary base amount. */
    val bonusAmount: Int,
    /** Number of output units that should contribute XP. */
    val experienceUnits: Int,
    /** Route the complete output to the bank (resource relic behavior). */
    val autoBank: Boolean,
    /** Route only League-created bonus output to the bank (Production Prodigy). */
    val bonusAutoBank: Boolean,
    val instantBatch: Boolean
) {
    val amount: Int
        get() = baseAmount + bonusAmount
}

data class LeagueOutputPlan(
    val baseAmount: Int,
    /** Whole copies of every base output item that are guaranteed. */
    val guaranteedCopiesPerUnit: Int,
    /** Independent chance for one additional copy per base output item. */
    val fractionalBonusChancePerUnit: Double,
    val bonusOutputGrantsExperience: Boolean = false,
    val autoBank: Boolean = false,
    val bonusAutoBank: Boolean = false,
    val instantBatch: Boolean = false
) {
    init {
        require(baseAmount >= 0) { "Base output amount cannot be negative" }
        require(guaranteedCopiesPerUnit >= 0) { "Guaranteed copies cannot be negative" }
        require(fractionalBonusChancePerUnit in 0.0..1.0) { "Fractional bonus chance must be within 0..1" }
    }

    val guaranteedAmount: Int
        get() = baseAmount * guaranteedCopiesPerUnit

    /**
     * Resolve per-unit fractional bonuses using a caller-supplied [0,1) source.
     * Per-unit rolls preserve mechanics such as Production Prodigy's 25% chance
     * for each item in a batch instead of replacing it with one aggregate roll.
     */
    fun resolve(nextRoll: () -> Double): LeagueResolvedOutput {
        var amount = guaranteedAmount
        if (fractionalBonusChancePerUnit > 0.0) {
            repeat(baseAmount) {
                val roll = nextRoll()
                require(roll >= 0.0 && roll < 1.0) { "Output roll must be within [0,1)" }
                if (roll < fractionalBonusChancePerUnit) amount++
            }
        }
        val ordinaryAmount = minOf(baseAmount, amount)
        val bonusAmount = (amount - ordinaryAmount).coerceAtLeast(0)
        val xpUnits = if (bonusOutputGrantsExperience) amount else ordinaryAmount
        return LeagueResolvedOutput(
            baseAmount = ordinaryAmount,
            bonusAmount = bonusAmount,
            experienceUnits = xpUnits,
            autoBank = autoBank,
            bonusAutoBank = bonusAutoBank,
            instantBatch = instantBatch
        )
    }

    /** Convenience for the overwhelmingly common one-unit gathering action. */
    fun resolveAmount(roll: Double): Int = resolve { roll }.amount
}

/**
 * Pure output planning for League gathering/production rewards.
 *
 * The engine deliberately does not add items itself. Individual gameplay paths
 * decide whether the resolved output belongs in inventory, bank, ground, or a
 * source-specific destination. This avoids post-event duplication bugs.
 */
object LeagueOutputPlanner {
    fun plan(baseAmount: Int, kind: LeagueOutputKind, effects: LeagueResolvedEffects): LeagueOutputPlan {
        require(baseAmount >= 0) { "Base output amount cannot be negative" }
        val key = when (kind) {
            LeagueOutputKind.RESOURCE -> LeagueModifierKey.RESOURCE_MULTIPLIER
            LeagueOutputKind.PRODUCTION -> LeagueModifierKey.PRODUCTION_MULTIPLIER
        }
        val multiplier = effects.numeric(key).coerceAtLeast(0.0)
        val whole = floor(multiplier).toInt()
        val fraction = (multiplier - whole).coerceIn(0.0, 1.0)
        val prefix = if (kind == LeagueOutputKind.RESOURCE) "resource" else "production"
        return LeagueOutputPlan(
            baseAmount = baseAmount,
            guaranteedCopiesPerUnit = whole,
            fractionalBonusChancePerUnit = fraction,
            bonusOutputGrantsExperience = effects.hasFlag("$prefix.extra-xp"),
            autoBank = kind == LeagueOutputKind.RESOURCE && effects.hasFlag("resource.auto-bank"),
            bonusAutoBank = kind == LeagueOutputKind.PRODUCTION && effects.hasFlag("production.bonus-auto-bank"),
            instantBatch = kind == LeagueOutputKind.PRODUCTION && effects.hasFlag("production.instant-batch")
        )
    }
}
