package content.global.leagues.core

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Shared Grand League gameplay modifier primitives.
 *
 * Content definitions emit small, composable effects. Gameplay systems consume the
 * resolved snapshot rather than knowing about individual relic/fragment/node ids.
 */
enum class LeagueEffectScope {
    GATHERING,
    PRODUCTION,
    XP,
    SHOP,
    FARMING,
    MOVEMENT,
    THIEVING,
    AGILITY,
    HUNTER,
    FISHING,
    MINING,
    WOODCUTTING,
    CONSTRUCTION,
    COMBAT,
    MELEE,
    RANGED,
    MAGIC
}

enum class LeagueStacking { MULTIPLY, ADD, MAX, MIN }

enum class LeagueModifierKey(val baseValue: Double, val stacking: LeagueStacking) {
    RESOURCE_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),
    AUTO_BANK_CHANCE(0.0, LeagueStacking.MAX),
    AUTO_PROCESS_CHANCE(0.0, LeagueStacking.MAX),
    PRODUCTION_SPEED_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),
    PRODUCTION_OUTPUT_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),
    MATERIAL_SAVE_CHANCE(0.0, LeagueStacking.MAX),
    XP_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),
    BELOW_AVERAGE_XP_BONUS(0.0, LeagueStacking.ADD),
    SHOP_PRICE_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),
    SHOP_STOCK_CONSUMPTION_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),
    FARM_GROWTH_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),
    FARM_YIELD_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),
    FARM_DISEASE_CHANCE_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),
    RUN_REGEN_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),
    RUN_DRAIN_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),
    THIEVING_SUCCESS_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),
    AGILITY_FAIL_CHANCE_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),
    HUNTER_SUCCESS_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),

    COMBAT_ACCURACY_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),
    COMBAT_DAMAGE_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),
    ATTACK_INTERVAL_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),
    DEFENCE_PENETRATION(0.0, LeagueStacking.ADD),
    AMMO_SAVE_CHANCE(0.0, LeagueStacking.MAX),
    RUNE_SAVE_CHANCE(0.0, LeagueStacking.MAX),
    EXTRA_HIT_CHANCE(0.0, LeagueStacking.ADD),
    EXTRA_HIT_DAMAGE_FRACTION(0.0, LeagueStacking.MAX),
    LIFESTEAL_FRACTION(0.0, LeagueStacking.ADD),
    PRAYER_RESTORE_FRACTION(0.0, LeagueStacking.ADD),
    INCOMING_DAMAGE_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),
    DAMAGE_REFLECT_FRACTION(0.0, LeagueStacking.ADD),
    LOW_HP_MAX_DAMAGE_BONUS(0.0, LeagueStacking.ADD),
    SPECIAL_ATTACK_COST_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),
    SPECIAL_ENERGY_RESTORE_MULTIPLIER(1.0, LeagueStacking.MULTIPLY),
    EXECUTION_THRESHOLD_NORMAL(0.0, LeagueStacking.MAX),
    EXECUTION_THRESHOLD_BOSS(0.0, LeagueStacking.MAX),

    // Capability-style switches use MAX and values of 0/1.
    BANK_BONUS_RESOURCES(0.0, LeagueStacking.MAX),
    PORTABLE_NOTE(0.0, LeagueStacking.MAX),
    FAIRY_FLIGHT(0.0, LeagueStacking.MAX),
    GLOBETROTTER(0.0, LeagueStacking.MAX),
    FARM_DISEASE_IMMUNITY(0.0, LeagueStacking.MAX),
    THIEVING_AUTO_REPEAT(0.0, LeagueStacking.MAX)
}

data class LeagueEffectDefinition(
    val key: LeagueModifierKey,
    val value: Double,
    val requiredScopes: Set<LeagueEffectScope> = emptySet()
) {
    init {
        require(value >= 0.0 && value.isFinite()) { "League effect values must be finite and non-negative" }
    }
}

data class LeagueActiveEffect(
    val sourceType: String,
    val sourceId: String,
    val definition: LeagueEffectDefinition
)

data class LeagueActiveTriggeredEffect(
    val sourceType: String,
    val sourceId: String,
    val definition: LeagueTriggeredEffectDefinition
)

/** Immutable snapshot of every currently active League effect. */
class LeagueModifierSnapshot internal constructor(
    private val activeEffects: List<LeagueActiveEffect>,
    private val activeTriggeredEffects: List<LeagueActiveTriggeredEffect> = emptyList()
) {
    fun value(key: LeagueModifierKey, scopes: Set<LeagueEffectScope> = emptySet()): Double {
        var result = key.baseValue
        for (active in activeEffects) {
            val effect = active.definition
            if (effect.key != key || !scopes.containsAll(effect.requiredScopes)) continue
            result = when (key.stacking) {
                LeagueStacking.MULTIPLY -> result * effect.value
                LeagueStacking.ADD -> result + effect.value
                LeagueStacking.MAX -> max(result, effect.value)
                LeagueStacking.MIN -> min(result, effect.value)
            }
        }
        return result
    }

    fun enabled(key: LeagueModifierKey, scopes: Set<LeagueEffectScope> = emptySet()): Boolean =
        value(key, scopes) > 0.0

    fun sourcesFor(key: LeagueModifierKey, scopes: Set<LeagueEffectScope> = emptySet()): List<String> =
        activeEffects.asSequence()
            .filter { it.definition.key == key && scopes.containsAll(it.definition.requiredScopes) }
            .map { "${it.sourceType}:${it.sourceId}" }
            .toList()

    fun triggered(kind: LeagueTriggeredEffectKind): List<LeagueActiveTriggeredEffect> =
        activeTriggeredEffects.asSequence()
            .filter { it.definition.kind == kind }
            .sortedWith(
                compareByDescending<LeagueActiveTriggeredEffect> { it.definition.priority }
                    .thenBy { it.sourceType }
                    .thenBy { it.sourceId }
                    .thenBy { it.definition.id }
            )
            .toList()

    fun gathering(scopes: Set<LeagueEffectScope> = setOf(LeagueEffectScope.GATHERING)): LeagueGatheringModifiers =
        LeagueGatheringModifiers(
            resourceMultiplier = value(LeagueModifierKey.RESOURCE_MULTIPLIER, scopes),
            autoBankChance = value(LeagueModifierKey.AUTO_BANK_CHANCE, scopes).coerceIn(0.0, 1.0),
            autoProcessChance = value(LeagueModifierKey.AUTO_PROCESS_CHANCE, scopes).coerceIn(0.0, 1.0),
            bankBonusResources = enabled(LeagueModifierKey.BANK_BONUS_RESOURCES, scopes)
        )

    fun production(scopes: Set<LeagueEffectScope> = setOf(LeagueEffectScope.PRODUCTION)): LeagueProductionModifiers =
        LeagueProductionModifiers(
            speedMultiplier = value(LeagueModifierKey.PRODUCTION_SPEED_MULTIPLIER, scopes),
            outputMultiplier = value(LeagueModifierKey.PRODUCTION_OUTPUT_MULTIPLIER, scopes),
            materialSaveChance = value(LeagueModifierKey.MATERIAL_SAVE_CHANCE, scopes).coerceIn(0.0, 1.0)
        )

    fun farming(): LeagueFarmingModifiers {
        val scopes = setOf(LeagueEffectScope.FARMING)
        return LeagueFarmingModifiers(
            growthMultiplier = value(LeagueModifierKey.FARM_GROWTH_MULTIPLIER, scopes),
            yieldMultiplier = value(LeagueModifierKey.FARM_YIELD_MULTIPLIER, scopes),
            diseaseChanceMultiplier = if (enabled(LeagueModifierKey.FARM_DISEASE_IMMUNITY, scopes)) 0.0
                else value(LeagueModifierKey.FARM_DISEASE_CHANCE_MULTIPLIER, scopes),
            diseaseImmune = enabled(LeagueModifierKey.FARM_DISEASE_IMMUNITY, scopes)
        )
    }

    fun movement(): LeagueMovementModifiers {
        val scopes = setOf(LeagueEffectScope.MOVEMENT)
        return LeagueMovementModifiers(
            runRegenMultiplier = value(LeagueModifierKey.RUN_REGEN_MULTIPLIER, scopes),
            runDrainMultiplier = value(LeagueModifierKey.RUN_DRAIN_MULTIPLIER, scopes),
            fairyFlight = enabled(LeagueModifierKey.FAIRY_FLIGHT, scopes),
            globetrotter = enabled(LeagueModifierKey.GLOBETROTTER, scopes)
        )
    }

    fun shop(): LeagueShopModifiers {
        val scopes = setOf(LeagueEffectScope.SHOP)
        return LeagueShopModifiers(
            priceMultiplier = value(LeagueModifierKey.SHOP_PRICE_MULTIPLIER, scopes),
            stockConsumptionMultiplier = value(LeagueModifierKey.SHOP_STOCK_CONSUMPTION_MULTIPLIER, scopes)
        )
    }

    fun thieving(): LeagueThievingModifiers {
        val scopes = setOf(LeagueEffectScope.THIEVING)
        return LeagueThievingModifiers(
            successMultiplier = value(LeagueModifierKey.THIEVING_SUCCESS_MULTIPLIER, scopes),
            autoRepeat = enabled(LeagueModifierKey.THIEVING_AUTO_REPEAT, scopes)
        )
    }

    fun agility(): LeagueAgilityModifiers {
        val scopes = setOf(LeagueEffectScope.AGILITY)
        return LeagueAgilityModifiers(
            failChanceMultiplier = value(LeagueModifierKey.AGILITY_FAIL_CHANCE_MULTIPLIER, scopes)
        )
    }

    fun hunter(): LeagueHunterModifiers {
        val scopes = setOf(LeagueEffectScope.HUNTER)
        return LeagueHunterModifiers(
            successMultiplier = value(LeagueModifierKey.HUNTER_SUCCESS_MULTIPLIER, scopes)
        )
    }

    fun combat(style: LeagueCombatStyle): LeagueCombatModifiers {
        val scopes = setOf(LeagueEffectScope.COMBAT, style.scope)
        return LeagueCombatModifiers(
            accuracyMultiplier = value(LeagueModifierKey.COMBAT_ACCURACY_MULTIPLIER, scopes),
            damageMultiplier = value(LeagueModifierKey.COMBAT_DAMAGE_MULTIPLIER, scopes),
            attackIntervalMultiplier = value(LeagueModifierKey.ATTACK_INTERVAL_MULTIPLIER, scopes),
            defencePenetration = value(LeagueModifierKey.DEFENCE_PENETRATION, scopes).coerceIn(0.0, 0.95),
            ammoSaveChance = value(LeagueModifierKey.AMMO_SAVE_CHANCE, scopes).coerceIn(0.0, 1.0),
            runeSaveChance = value(LeagueModifierKey.RUNE_SAVE_CHANCE, scopes).coerceIn(0.0, 1.0),
            extraHitChance = value(LeagueModifierKey.EXTRA_HIT_CHANCE, scopes).coerceIn(0.0, 1.0),
            extraHitDamageFraction = value(LeagueModifierKey.EXTRA_HIT_DAMAGE_FRACTION, scopes).coerceIn(0.0, 2.0),
            lifestealFraction = value(LeagueModifierKey.LIFESTEAL_FRACTION, scopes).coerceIn(0.0, 1.0),
            prayerRestoreFraction = value(LeagueModifierKey.PRAYER_RESTORE_FRACTION, scopes).coerceIn(0.0, 1.0),
            incomingDamageMultiplier = value(LeagueModifierKey.INCOMING_DAMAGE_MULTIPLIER, scopes).coerceIn(0.05, 5.0),
            reflectFraction = value(LeagueModifierKey.DAMAGE_REFLECT_FRACTION, scopes).coerceIn(0.0, 1.0),
            lowHpMaxDamageBonus = value(LeagueModifierKey.LOW_HP_MAX_DAMAGE_BONUS, scopes).coerceAtLeast(0.0),
            specialAttackCostMultiplier = value(LeagueModifierKey.SPECIAL_ATTACK_COST_MULTIPLIER, scopes).coerceAtLeast(0.05),
            specialEnergyRestoreMultiplier = value(LeagueModifierKey.SPECIAL_ENERGY_RESTORE_MULTIPLIER, scopes).coerceAtLeast(0.0),
            normalExecutionThreshold = value(LeagueModifierKey.EXECUTION_THRESHOLD_NORMAL, scopes).coerceIn(0.0, 1.0),
            bossExecutionThreshold = value(LeagueModifierKey.EXECUTION_THRESHOLD_BOSS, scopes).coerceIn(0.0, 1.0)
        )
    }

    /**
     * Equilibrium-style XP is deliberately expressed as a smooth deficit curve instead
     * of a one-off skill patch. At/above average it contributes nothing; at zero XP it
     * contributes its full BELOW_AVERAGE_XP_BONUS.
     */
    fun xpMultiplier(currentSkillXp: Double, averageSkillXp: Double): Double {
        val scopes = setOf(LeagueEffectScope.XP)
        val base = value(LeagueModifierKey.XP_MULTIPLIER, scopes)
        val bonus = value(LeagueModifierKey.BELOW_AVERAGE_XP_BONUS, scopes)
        if (bonus <= 0.0 || averageSkillXp <= 0.0 || currentSkillXp >= averageSkillXp) return base
        val deficitRatio = ((averageSkillXp - currentSkillXp) / averageSkillXp).coerceIn(0.0, 1.0)
        return base * (1.0 + bonus * deficitRatio)
    }

    fun all(): List<LeagueActiveEffect> = activeEffects.toList()

    companion object {
        val NONE = LeagueModifierSnapshot(emptyList())
    }
}

data class LeagueGatheringModifiers(
    val resourceMultiplier: Double = 1.0,
    val autoBankChance: Double = 0.0,
    val autoProcessChance: Double = 0.0,
    val bankBonusResources: Boolean = false
)

data class LeagueProductionModifiers(
    val speedMultiplier: Double = 1.0,
    val outputMultiplier: Double = 1.0,
    val materialSaveChance: Double = 0.0
)

data class LeagueFarmingModifiers(
    val growthMultiplier: Double = 1.0,
    val yieldMultiplier: Double = 1.0,
    val diseaseChanceMultiplier: Double = 1.0,
    val diseaseImmune: Boolean = false
)

data class LeagueMovementModifiers(
    val runRegenMultiplier: Double = 1.0,
    val runDrainMultiplier: Double = 1.0,
    val fairyFlight: Boolean = false,
    val globetrotter: Boolean = false
)

data class LeagueShopModifiers(
    val priceMultiplier: Double = 1.0,
    val stockConsumptionMultiplier: Double = 1.0
)


data class LeagueThievingModifiers(
    val successMultiplier: Double = 1.0,
    val autoRepeat: Boolean = false
)

data class LeagueAgilityModifiers(
    val failChanceMultiplier: Double = 1.0
)

data class LeagueHunterModifiers(
    val successMultiplier: Double = 1.0
)

enum class LeagueCombatStyle(val scope: LeagueEffectScope) {
    MELEE(LeagueEffectScope.MELEE),
    RANGED(LeagueEffectScope.RANGED),
    MAGIC(LeagueEffectScope.MAGIC);

    companion object {
        fun fromKey(key: String): LeagueCombatStyle? = when (key.lowercase()) {
            "melee" -> MELEE
            "range", "ranged" -> RANGED
            "magic" -> MAGIC
            else -> null
        }
    }
}

data class LeagueCombatModifiers(
    val accuracyMultiplier: Double = 1.0,
    val damageMultiplier: Double = 1.0,
    val attackIntervalMultiplier: Double = 1.0,
    val defencePenetration: Double = 0.0,
    val ammoSaveChance: Double = 0.0,
    val runeSaveChance: Double = 0.0,
    val extraHitChance: Double = 0.0,
    val extraHitDamageFraction: Double = 0.0,
    val lifestealFraction: Double = 0.0,
    val prayerRestoreFraction: Double = 0.0,
    val incomingDamageMultiplier: Double = 1.0,
    val reflectFraction: Double = 0.0,
    val lowHpMaxDamageBonus: Double = 0.0,
    val specialAttackCostMultiplier: Double = 1.0,
    val specialEnergyRestoreMultiplier: Double = 1.0,
    val normalExecutionThreshold: Double = 0.0,
    val bossExecutionThreshold: Double = 0.0
) {
    fun outgoingDamageMultiplier(healthRatio: Double): Double {
        val missingHealth = 1.0 - healthRatio.coerceIn(0.0, 1.0)
        return damageMultiplier * (1.0 + lowHpMaxDamageBonus * missingHealth)
    }

    fun shouldExecute(currentHealth: Int, maximumHealth: Int, boss: Boolean): Boolean {
        if (currentHealth <= 0 || maximumHealth <= 0) return false
        val threshold = if (boss) bossExecutionThreshold else normalExecutionThreshold
        return threshold > 0.0 && currentHealth.toDouble() / maximumHealth.toDouble() < threshold
    }
}

object LeagueEffectMath {
    /** Convert a fractional multiplier into an integer quantity with an explicit roll for deterministic tests. */
    fun scaledQuantity(baseAmount: Int, multiplier: Double, roll: Double = 1.0): Int {
        require(baseAmount >= 0)
        require(multiplier >= 0.0 && multiplier.isFinite())
        require(roll in 0.0..1.0)
        val exact = baseAmount * multiplier
        val whole = floor(exact).toInt()
        val fractional = exact - whole
        return whole + if (fractional > 0.0 && roll < fractional) 1 else 0
    }
}

class LeagueEffectResolver(private val content: LeagueContent) {
    fun resolve(profile: LeagueProfile, activeFragmentSets: Set<String>): LeagueModifierSnapshot {
        if (!profile.active) return LeagueModifierSnapshot.NONE
        val active = mutableListOf<LeagueActiveEffect>()
        val triggered = mutableListOf<LeagueActiveTriggeredEffect>()

        profile.selectedRelics.values.forEach { id ->
            content.relicsById[id]?.let { relic ->
                relic.effects.forEach { active += LeagueActiveEffect("relic", id, it) }
                relic.triggeredEffects.forEach { triggered += LeagueActiveTriggeredEffect("relic", id, it) }
            }
        }
        profile.equippedFragments.forEach { id ->
            content.fragmentsById[id]?.effects.orEmpty().forEach { active += LeagueActiveEffect("fragment", id, it) }
        }
        activeFragmentSets.forEach { id ->
            content.fragmentSetsById[id]?.effects.orEmpty().forEach { active += LeagueActiveEffect("fragment-set", id, it) }
        }
        profile.unlockedMasteries.forEach { id ->
            content.masteriesById[id]?.let { node ->
                node.effects.forEach { active += LeagueActiveEffect("mastery", id, it) }
                node.triggeredEffects.forEach { triggered += LeagueActiveTriggeredEffect("mastery", id, it) }
            }
        }
        profile.unlockedPacts.forEach { id ->
            content.pactsById[id]?.let { node ->
                node.effects.forEach { active += LeagueActiveEffect("pact", id, it) }
                node.triggeredEffects.forEach { triggered += LeagueActiveTriggeredEffect("pact", id, it) }
            }
        }
        return LeagueModifierSnapshot(active, triggered)
    }
}
