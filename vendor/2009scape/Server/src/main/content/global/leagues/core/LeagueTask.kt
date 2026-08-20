package content.global.leagues.core

/** Stable source labels let the combined League preserve where content came from. */
enum class LeagueSource {
    TWISTED,
    TRAILBLAZER,
    SHATTERED_RELICS,
    TRAILBLAZER_RELOADED,
    RAGING_ECHOES,
    DEMONIC_PACTS,
    RS3_CATALYST,
    RS3_EQUILIBRIUM,
    GRAND_LEAGUE
}

enum class LeagueTaskDifficulty(val defaultPoints: Int) {
    EASY(10),
    MEDIUM(40),
    HARD(80),
    ELITE(200),
    MASTER(400),
    GRANDMASTER(800)
}

/**
 * Declarative task matchers. They contain no lambdas so the task catalogue can
 * eventually be loaded/generated from data files and fully validated.
 */
sealed interface LeagueTaskTrigger {
    val signalKind: LeagueSignalKind
    val routingKey: String?

    /** Returns progress added by this signal, or 0 when it does not match. */
    fun progressDelta(signal: LeagueSignal): Long
}

data class ProduceItemTrigger(val itemId: Int) : LeagueTaskTrigger {
    override val signalKind = LeagueSignalKind.RESOURCE_PRODUCED
    override val routingKey = itemId.toString()
    override fun progressDelta(signal: LeagueSignal): Long {
        val event = signal as? ResourceProducedSignal ?: return 0
        return if (event.itemId == itemId) event.amount.coerceAtLeast(0).toLong() else 0
    }
}

data class KillNpcTrigger(val npcId: Int) : LeagueTaskTrigger {
    override val signalKind = LeagueSignalKind.NPC_KILLED
    override val routingKey = npcId.toString()
    override fun progressDelta(signal: LeagueSignal): Long {
        val event = signal as? NpcKilledSignal ?: return 0
        return if (event.npcId == npcId) event.amount.coerceAtLeast(0).toLong() else 0
    }
}

data class GainXpTrigger(val skillId: Int) : LeagueTaskTrigger {
    override val signalKind = LeagueSignalKind.XP_GAINED
    override val routingKey = skillId.toString()
    override fun progressDelta(signal: LeagueSignal): Long {
        val event = signal as? XpGainedSignal ?: return 0
        if (event.skillId != skillId || event.amount <= 0.0) return 0
        return event.amount.toLong().coerceAtLeast(1)
    }
}

data class ReachSkillLevelTrigger(
    val skillId: Int,
    val level: Int
) : LeagueTaskTrigger {
    override val signalKind = LeagueSignalKind.SKILL_LEVEL_REACHED
    override val routingKey = skillId.toString()
    override fun progressDelta(signal: LeagueSignal): Long {
        val event = signal as? SkillLevelReachedSignal ?: return 0
        return if (event.skillId == skillId && event.level >= level) 1 else 0
    }
}

data class CompleteQuestTrigger(val questKey: String) : LeagueTaskTrigger {
    override val signalKind = LeagueSignalKind.QUEST_COMPLETED
    override val routingKey = questKey
    override fun progressDelta(signal: LeagueSignal): Long {
        val event = signal as? QuestCompletedSignal ?: return 0
        return if (event.questKey == questKey) 1 else 0
    }
}

data class EquipItemTrigger(val itemId: Int) : LeagueTaskTrigger {
    override val signalKind = LeagueSignalKind.ITEM_EQUIPPED
    override val routingKey = itemId.toString()
    override fun progressDelta(signal: LeagueSignal): Long {
        val event = signal as? ItemEquippedSignal ?: return 0
        return if (event.itemId == itemId) 1 else 0
    }
}

data class ObtainItemTrigger(val itemId: Int) : LeagueTaskTrigger {
    override val signalKind = LeagueSignalKind.ITEM_OBTAINED
    override val routingKey = itemId.toString()
    override fun progressDelta(signal: LeagueSignal): Long {
        val event = signal as? ItemObtainedSignal ?: return 0
        return if (event.itemId == itemId) event.amount.coerceAtLeast(0).toLong() else 0
    }
}

data class TeleportToTrigger(val destinationKey: String) : LeagueTaskTrigger {
    override val signalKind = LeagueSignalKind.TELEPORT
    override val routingKey = destinationKey
    override fun progressDelta(signal: LeagueSignal): Long {
        val event = signal as? TeleportSignal ?: return 0
        return if (event.destinationKey == destinationKey) 1 else 0
    }
}

data class MetricAtLeastTrigger(
    val key: String,
    val threshold: Long
) : LeagueTaskTrigger {
    override val signalKind = LeagueSignalKind.METRIC_VALUE
    override val routingKey = key
    override fun progressDelta(signal: LeagueSignal): Long {
        val event = signal as? MetricValueSignal ?: return 0
        return if (event.key == key && event.value >= threshold) 1 else 0
    }
}

data class CustomTaskTrigger(val key: String) : LeagueTaskTrigger {
    override val signalKind = LeagueSignalKind.CUSTOM
    override val routingKey = key
    override fun progressDelta(signal: LeagueSignal): Long {
        val event = signal as? CustomLeagueSignal ?: return 0
        return if (event.key == key) event.amount.coerceAtLeast(0) else 0
    }
}

data class LeagueTaskDefinition(
    val id: String,
    val name: String,
    val description: String,
    val difficulty: LeagueTaskDifficulty,
    val trigger: LeagueTaskTrigger,
    val target: Long = 1,
    val points: Int = difficulty.defaultPoints,
    val source: LeagueSource = LeagueSource.GRAND_LEAGUE,
    val region: String? = null,
    val tags: Set<String> = emptySet()
)

data class LeagueTaskCompletion(
    val task: LeagueTaskDefinition,
    val pointsAwarded: Int,
    val previousPoints: Long,
    val newPoints: Long
)
