package content.global.leagues.core

private val ID_RE = Regex("[a-z0-9][a-z0-9._-]*")

fun requireLeagueId(id: String, label: String = "id") {
    require(ID_RE.matches(id)) { "Invalid League $label '$id'" }
}

enum class LeagueSignalKind { RESOURCE, NPC_KILL, XP, QUEST, ITEM, INTERACTION, METRIC }

enum class LeagueTaskDifficulty(val defaultPoints: Int) {
    EASY(10), MEDIUM(30), HARD(80), ELITE(200), MASTER(400)
}

enum class LeagueSource {
    TWISTED,
    TRAILBLAZER,
    SHATTERED_RELICS,
    TRAILBLAZER_RELOADED,
    RAGING_ECHOES,
    DEMONIC_PACTS,
    GRAND_LEAGUE
}

data class LeagueSignal(
    val kind: LeagueSignalKind,
    val key: String,
    val amount: Long = 1,
    val value: Long? = null
) {
    init {
        requireLeagueId(key, "signal key")
        require(amount >= 0) { "Signal amount cannot be negative" }
        value?.let { require(it >= 0) { "Signal value cannot be negative" } }
    }
}

sealed interface LeagueTaskTrigger {
    val kind: LeagueSignalKind
    val key: String
}

data class CountTrigger(
    override val kind: LeagueSignalKind,
    override val key: String,
    val target: Long
) : LeagueTaskTrigger {
    init { require(target > 0); requireLeagueId(key, "task key") }
}

data class MetricTrigger(
    override val kind: LeagueSignalKind,
    override val key: String,
    val target: Long
) : LeagueTaskTrigger {
    init { require(target >= 0); requireLeagueId(key, "metric key") }
}

data class LeagueTaskDefinition(
    val id: String,
    val name: String,
    val points: Int,
    val trigger: LeagueTaskTrigger,
    val tags: Set<String> = emptySet(),
    val difficulty: LeagueTaskDifficulty = LeagueTaskDifficulty.EASY,
    val source: LeagueSource = LeagueSource.GRAND_LEAGUE,
    val regionId: String? = null,
    val category: String = "general",
    val regionTokenReward: Int = 0,
    val fragmentTokenReward: Int = 0,
    val masteryPointReward: Int = 0,
    val pactPointReward: Int = 0
) {
    init {
        requireLeagueId(id, "task id")
        require(name.isNotBlank())
        require(points > 0)
        regionId?.let { requireLeagueId(it, "task region") }
        requireLeagueId(category, "task category")
        require(regionTokenReward >= 0 && fragmentTokenReward >= 0 && masteryPointReward >= 0 && pactPointReward >= 0)
    }
}

data class LeagueTierDefinition(
    val tier: Int,
    val minimumPoints: Int,
    val regionTokens: Int = 0,
    val fragmentTokens: Int = 0,
    val masteryPoints: Int = 0,
    val pactPoints: Int = 0
) {
    init {
        require(tier >= 0)
        require(minimumPoints >= 0)
        require(regionTokens >= 0 && fragmentTokens >= 0 && masteryPoints >= 0 && pactPoints >= 0)
    }
}

data class LeagueRelicDefinition(
    val id: String,
    val tier: Int,
    val name: String,
    val source: LeagueSource = LeagueSource.GRAND_LEAGUE,
    val description: String = "",
    val effects: List<LeagueEffectDefinition> = emptyList(),
    val triggeredEffects: List<LeagueTriggeredEffectDefinition> = emptyList()
) {
    init { requireLeagueId(id, "relic id"); require(tier > 0); require(name.isNotBlank()) }
}

enum class LeagueTriggeredEffectKind { LETHAL_INTERCEPT }

/** A stateful effect kept separate from passive modifier arithmetic. */
data class LeagueTriggeredEffectDefinition(
    val id: String,
    val kind: LeagueTriggeredEffectKind,
    val priority: Int,
    val cooldownMillis: Long,
    val healthRestoreFraction: Double,
    val prayerRestoreFraction: Double = 0.0,
    val retaliationDamageMultiplier: Double = 0.0,
    val retaliationRadius: Int = 0
) {
    init {
        requireLeagueId(id, "triggered effect id")
        require(priority >= 0)
        require(cooldownMillis >= 0)
        require(healthRestoreFraction in 0.0..1.0)
        require(prayerRestoreFraction in 0.0..1.0)
        require(retaliationDamageMultiplier >= 0.0 && retaliationDamageMultiplier.isFinite())
        require(retaliationRadius >= 0)
    }
}

data class LeagueRegionDefinition(
    val id: String,
    val name: String,
    val tokenCost: Int = 1,
    val prerequisites: Set<String> = emptySet()
) {
    init {
        requireLeagueId(id, "region id")
        prerequisites.forEach { requireLeagueId(it, "region prerequisite") }
        require(name.isNotBlank()); require(tokenCost > 0)
    }
}

data class LeagueFragmentDefinition(
    val id: String,
    val name: String,
    val setIds: Set<String> = emptySet(),
    val tokenCost: Int = 1,
    val source: LeagueSource = LeagueSource.SHATTERED_RELICS,
    val effects: List<LeagueEffectDefinition> = emptyList()
) {
    constructor(id: String, name: String, setId: String?, tokenCost: Int = 1) : this(
        id, name, setId?.let { setOf(it) } ?: emptySet(), tokenCost, LeagueSource.SHATTERED_RELICS, emptyList()
    )

    init {
        requireLeagueId(id, "fragment id")
        setIds.forEach { requireLeagueId(it, "fragment set id") }
        require(name.isNotBlank()); require(tokenCost > 0)
    }
}

data class LeagueFragmentSetDefinition(
    val id: String,
    val requiredEquipped: Int,
    val name: String = id,
    val effects: List<LeagueEffectDefinition> = emptyList()
) {
    init { requireLeagueId(id, "fragment set id"); require(requiredEquipped > 0); require(name.isNotBlank()) }
}

data class LeagueNodeDefinition(
    val id: String,
    val name: String,
    val prerequisites: Set<String> = emptySet(),
    val pointCost: Int = 1,
    val style: String = "neutral",
    val effects: List<LeagueEffectDefinition> = emptyList(),
    val triggeredEffects: List<LeagueTriggeredEffectDefinition> = emptyList()
) {
    init {
        requireLeagueId(id, "node id")
        prerequisites.forEach { requireLeagueId(it, "node prerequisite") }
        requireLeagueId(style, "node style")
        require(name.isNotBlank()); require(pointCost > 0)
    }
}

enum class EchoDifficulty { NORMAL, ECHO, GREATER_ECHO, GRAND_ECHO }

data class LeagueEchoDefinition(
    val id: String,
    val name: String,
    val regionId: String,
    val minimumTier: Int,
    val requiredMastery: String? = null,
    val requiredPact: String? = null,
    val rewardIds: List<String> = emptyList(),
    val source: LeagueSource = LeagueSource.RAGING_ECHOES
) {
    init {
        requireLeagueId(id, "echo id")
        requireLeagueId(regionId, "echo region")
        requiredMastery?.let { requireLeagueId(it, "echo mastery") }
        requiredPact?.let { requireLeagueId(it, "echo pact") }
        rewardIds.forEach { requireLeagueId(it, "echo reward") }
        require(name.isNotBlank()); require(minimumTier >= 0)
    }
}
