package content.global.leagues.core

data class LeagueRequirementContext(
    val progression: LeagueProgression = LeagueProgression.grandLeagueDefaults(),
    val externalFlags: Set<String> = emptySet()
)

sealed interface LeagueRequirement {
    fun isMet(profile: LeagueProfile, context: LeagueRequirementContext): Boolean
    fun describe(): String
}

data class PointsAtLeastRequirement(val points: Long) : LeagueRequirement {
    override fun isMet(profile: LeagueProfile, context: LeagueRequirementContext) = profile.points >= points
    override fun describe() = "Reach $points League points"
}

data class TierAtLeastRequirement(val tierIndex: Int) : LeagueRequirement {
    override fun isMet(profile: LeagueProfile, context: LeagueRequirementContext) =
        context.progression.tierFor(profile.points).index >= tierIndex
    override fun describe() = "Reach League tier $tierIndex"
}

data class TaskCompletedRequirement(val taskId: String) : LeagueRequirement {
    override fun isMet(profile: LeagueProfile, context: LeagueRequirementContext) = taskId in profile.completedTasks
    override fun describe() = "Complete task $taskId"
}

data class RegionUnlockedRequirement(val regionId: String) : LeagueRequirement {
    override fun isMet(profile: LeagueProfile, context: LeagueRequirementContext) = regionId in profile.unlockedRegions
    override fun describe() = "Unlock region $regionId"
}

data class RelicUnlockedRequirement(val relicId: String) : LeagueRequirement {
    override fun isMet(profile: LeagueProfile, context: LeagueRequirementContext) = relicId in profile.unlockedRelics
    override fun describe() = "Unlock relic $relicId"
}

data class FragmentLevelRequirement(val fragmentId: String, val level: Int) : LeagueRequirement {
    override fun isMet(profile: LeagueProfile, context: LeagueRequirementContext) =
        (profile.fragmentLevels[fragmentId] ?: 0) >= level
    override fun describe() = "Reach level $level with fragment $fragmentId"
}

data class MasteryRankRequirement(val masteryId: String, val rank: Int) : LeagueRequirement {
    override fun isMet(profile: LeagueProfile, context: LeagueRequirementContext) =
        (profile.masteryRanks[masteryId] ?: 0) >= rank
    override fun describe() = "Reach rank $rank in mastery $masteryId"
}

data class PactUnlockedRequirement(val pactId: String) : LeagueRequirement {
    override fun isMet(profile: LeagueProfile, context: LeagueRequirementContext) = pactId in profile.unlockedPacts
    override fun describe() = "Unlock pact $pactId"
}

data class EchoKillsRequirement(val echoId: String, val kills: Long) : LeagueRequirement {
    override fun isMet(profile: LeagueProfile, context: LeagueRequirementContext) =
        (profile.echoKills[echoId] ?: 0) >= kills
    override fun describe() = "Defeat $echoId $kills times"
}

data class ContentUnlockedRequirement(val contentKey: String) : LeagueRequirement {
    override fun isMet(profile: LeagueProfile, context: LeagueRequirementContext) = contentKey in profile.unlockedContent
    override fun describe() = "Unlock content $contentKey"
}

data class CurrencyRequirement(val currencyId: String, val amount: Long) : LeagueRequirement {
    override fun isMet(profile: LeagueProfile, context: LeagueRequirementContext) =
        (profile.currencies[currencyId] ?: 0) >= amount
    override fun describe() = "Have $amount $currencyId"
}

data class ExternalFlagRequirement(val flag: String) : LeagueRequirement {
    override fun isMet(profile: LeagueProfile, context: LeagueRequirementContext) = flag in context.externalFlags
    override fun describe() = "Satisfy external condition $flag"
}

data class AllOfRequirement(val requirements: List<LeagueRequirement>) : LeagueRequirement {
    override fun isMet(profile: LeagueProfile, context: LeagueRequirementContext) =
        requirements.all { it.isMet(profile, context) }
    override fun describe() = requirements.joinToString(prefix = "All of: ", separator = "; ") { it.describe() }
}

data class AnyOfRequirement(val requirements: List<LeagueRequirement>) : LeagueRequirement {
    override fun isMet(profile: LeagueProfile, context: LeagueRequirementContext) =
        requirements.any { it.isMet(profile, context) }
    override fun describe() = requirements.joinToString(prefix = "Any of: ", separator = "; ") { it.describe() }
}

fun Iterable<LeagueRequirement>.unmet(profile: LeagueProfile, context: LeagueRequirementContext): List<LeagueRequirement> =
    filterNot { it.isMet(profile, context) }
