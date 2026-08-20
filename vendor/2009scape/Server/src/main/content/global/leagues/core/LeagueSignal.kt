package content.global.leagues.core

/**
 * Engine-neutral events consumed by the Grand League task engine.
 *
 * The 2009Scape integration layer is responsible for translating game events
 * (XPGainEvent, NPCKillEvent, ResourceProducedEvent, etc.) into these signals.
 * Keeping this layer free of server types makes task logic deterministic and
 * independently testable.
 */
sealed interface LeagueSignal {
    val kind: LeagueSignalKind
}

enum class LeagueSignalKind {
    RESOURCE_PRODUCED,
    NPC_KILLED,
    XP_GAINED,
    SKILL_LEVEL_REACHED,
    QUEST_COMPLETED,
    ITEM_EQUIPPED,
    ITEM_OBTAINED,
    TELEPORT,
    METRIC_VALUE,
    CUSTOM
}

data class ResourceProducedSignal(
    val itemId: Int,
    val amount: Int = 1,
    val sourceId: Int? = null
) : LeagueSignal {
    override val kind = LeagueSignalKind.RESOURCE_PRODUCED
}

data class NpcKilledSignal(
    val npcId: Int,
    val amount: Int = 1
) : LeagueSignal {
    override val kind = LeagueSignalKind.NPC_KILLED
}

data class XpGainedSignal(
    val skillId: Int,
    val amount: Double
) : LeagueSignal {
    override val kind = LeagueSignalKind.XP_GAINED
}

data class SkillLevelReachedSignal(
    val skillId: Int,
    val level: Int
) : LeagueSignal {
    override val kind = LeagueSignalKind.SKILL_LEVEL_REACHED
}

data class QuestCompletedSignal(
    val questKey: String
) : LeagueSignal {
    override val kind = LeagueSignalKind.QUEST_COMPLETED
}

data class ItemEquippedSignal(
    val itemId: Int,
    val slotId: Int? = null
) : LeagueSignal {
    override val kind = LeagueSignalKind.ITEM_EQUIPPED
}

data class ItemObtainedSignal(
    val itemId: Int,
    val amount: Int = 1
) : LeagueSignal {
    override val kind = LeagueSignalKind.ITEM_OBTAINED
}

data class TeleportSignal(
    val destinationKey: String,
    val methodKey: String? = null
) : LeagueSignal {
    override val kind = LeagueSignalKind.TELEPORT
}

data class MetricValueSignal(
    val key: String,
    val value: Long
) : LeagueSignal {
    override val kind = LeagueSignalKind.METRIC_VALUE
}

data class CustomLeagueSignal(
    val key: String,
    val amount: Long = 1,
    val value: String? = null
) : LeagueSignal {
    override val kind = LeagueSignalKind.CUSTOM
}

fun LeagueSignal.routingKey(): String = when (this) {
    is ResourceProducedSignal -> itemId.toString()
    is NpcKilledSignal -> npcId.toString()
    is XpGainedSignal -> skillId.toString()
    is SkillLevelReachedSignal -> skillId.toString()
    is QuestCompletedSignal -> questKey
    is ItemEquippedSignal -> itemId.toString()
    is ItemObtainedSignal -> itemId.toString()
    is TeleportSignal -> destinationKey
    is MetricValueSignal -> key
    is CustomLeagueSignal -> key
}
