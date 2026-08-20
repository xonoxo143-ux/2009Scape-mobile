package content.global.leagues.core

/**
 * Serializable representation of a League character. This intentionally uses
 * only primitive/collection types so adapters can persist it to JSON, SQL, or
 * 2009Scape save attributes without coupling the core to those systems.
 */
data class LeagueProfileSnapshot(
    val schemaVersion: Int = LeagueProfile.CURRENT_SCHEMA_VERSION,
    val active: Boolean = true,
    val seasonKey: String = LeagueProfile.DEFAULT_SEASON_KEY,
    val points: Long = 0,
    val completedTasks: Set<String> = emptySet(),
    val taskProgress: Map<String, Long> = emptyMap(),
    val unlockedRelics: Set<String> = emptySet(),
    val primaryRelicsByTier: Map<Int, String> = emptyMap(),
    val unlockedRegions: Set<String> = emptySet(),
    val fragmentLevels: Map<String, Int> = emptyMap(),
    val fragmentXp: Map<String, Long> = emptyMap(),
    val equippedFragments: List<String> = emptyList(),
    val masteryRanks: Map<String, Int> = emptyMap(),
    val unlockedPacts: Set<String> = emptySet(),
    val echoKills: Map<String, Long> = emptyMap(),
    val unlockedEchoes: Set<String> = emptySet(),
    val currencies: Map<String, Long> = emptyMap(),
    val unlockedBlessings: Set<String> = emptySet(),
    val unlockedContent: Set<String> = emptySet()
)

class LeagueProfile private constructor(
    var active: Boolean,
    var seasonKey: String,
    points: Long,
    completedTasks: Set<String>,
    taskProgress: Map<String, Long>,
    unlockedRelics: Set<String>,
    primaryRelicsByTier: Map<Int, String>,
    unlockedRegions: Set<String>,
    fragmentLevels: Map<String, Int>,
    fragmentXp: Map<String, Long>,
    equippedFragments: List<String>,
    masteryRanks: Map<String, Int>,
    unlockedPacts: Set<String>,
    echoKills: Map<String, Long>,
    unlockedEchoes: Set<String>,
    currencies: Map<String, Long>,
    unlockedBlessings: Set<String>,
    unlockedContent: Set<String>
) {
    var points: Long = points.coerceAtLeast(0)
        internal set

    val completedTasks: MutableSet<String> = completedTasks.toMutableSet()
    val taskProgress: MutableMap<String, Long> = taskProgress
        .filterValues { it > 0 }
        .mapValuesTo(mutableMapOf()) { it.value }

    val unlockedRelics: MutableSet<String> = unlockedRelics.toMutableSet()
    val primaryRelicsByTier: MutableMap<Int, String> = primaryRelicsByTier.toMutableMap()
    val unlockedRegions: MutableSet<String> = unlockedRegions.toMutableSet()
    val fragmentLevels: MutableMap<String, Int> = fragmentLevels
        .filterValues { it > 0 }
        .mapValuesTo(mutableMapOf()) { it.value }
    val fragmentXp: MutableMap<String, Long> = fragmentXp
        .filterValues { it > 0 }
        .mapValuesTo(mutableMapOf()) { it.value }
    val equippedFragments: MutableList<String> = equippedFragments.distinct().toMutableList()
    val masteryRanks: MutableMap<String, Int> = masteryRanks
        .filterValues { it > 0 }
        .mapValuesTo(mutableMapOf()) { it.value }
    val unlockedPacts: MutableSet<String> = unlockedPacts.toMutableSet()
    val echoKills: MutableMap<String, Long> = echoKills
        .filterValues { it > 0 }
        .mapValuesTo(mutableMapOf()) { it.value }
    val unlockedEchoes: MutableSet<String> = unlockedEchoes.toMutableSet()
    val currencies: MutableMap<String, Long> = currencies
        .filterValues { it > 0 }
        .mapValuesTo(mutableMapOf()) { it.value }
    val unlockedBlessings: MutableSet<String> = unlockedBlessings.toMutableSet()
    val unlockedContent: MutableSet<String> = unlockedContent.toMutableSet()

    fun snapshot(): LeagueProfileSnapshot = LeagueProfileSnapshot(
        schemaVersion = CURRENT_SCHEMA_VERSION,
        active = active,
        seasonKey = seasonKey,
        points = points,
        completedTasks = completedTasks.toSet(),
        taskProgress = taskProgress.toMap(),
        unlockedRelics = unlockedRelics.toSet(),
        primaryRelicsByTier = primaryRelicsByTier.toMap(),
        unlockedRegions = unlockedRegions.toSet(),
        fragmentLevels = fragmentLevels.toMap(),
        fragmentXp = fragmentXp.toMap(),
        equippedFragments = equippedFragments.toList(),
        masteryRanks = masteryRanks.toMap(),
        unlockedPacts = unlockedPacts.toSet(),
        echoKills = echoKills.toMap(),
        unlockedEchoes = unlockedEchoes.toSet(),
        currencies = currencies.toMap(),
        unlockedBlessings = unlockedBlessings.toSet(),
        unlockedContent = unlockedContent.toSet()
    )

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
        const val DEFAULT_SEASON_KEY = "grand-league"

        fun fresh(
            active: Boolean = true,
            seasonKey: String = DEFAULT_SEASON_KEY,
            baseRegions: Set<String> = setOf("misthalin")
        ): LeagueProfile = LeagueProfile(
            active = active,
            seasonKey = seasonKey,
            points = 0,
            completedTasks = emptySet(),
            taskProgress = emptyMap(),
            unlockedRelics = emptySet(),
            primaryRelicsByTier = emptyMap(),
            unlockedRegions = baseRegions,
            fragmentLevels = emptyMap(),
            fragmentXp = emptyMap(),
            equippedFragments = emptyList(),
            masteryRanks = emptyMap(),
            unlockedPacts = emptySet(),
            echoKills = emptyMap(),
            unlockedEchoes = emptySet(),
            currencies = emptyMap(),
            unlockedBlessings = emptySet(),
            unlockedContent = emptySet()
        )

        fun fromSnapshot(snapshot: LeagueProfileSnapshot): LeagueProfile {
            require(snapshot.schemaVersion in 1..CURRENT_SCHEMA_VERSION) {
                "Unsupported League profile schema ${snapshot.schemaVersion}; current is $CURRENT_SCHEMA_VERSION"
            }
            return LeagueProfile(
                active = snapshot.active,
                seasonKey = snapshot.seasonKey.ifBlank { DEFAULT_SEASON_KEY },
                points = snapshot.points,
                completedTasks = snapshot.completedTasks,
                taskProgress = snapshot.taskProgress.filterKeys { it !in snapshot.completedTasks },
                unlockedRelics = snapshot.unlockedRelics,
                primaryRelicsByTier = snapshot.primaryRelicsByTier,
                unlockedRegions = snapshot.unlockedRegions,
                fragmentLevels = snapshot.fragmentLevels,
                fragmentXp = snapshot.fragmentXp,
                equippedFragments = snapshot.equippedFragments,
                masteryRanks = snapshot.masteryRanks,
                unlockedPacts = snapshot.unlockedPacts,
                echoKills = snapshot.echoKills,
                unlockedEchoes = snapshot.unlockedEchoes,
                currencies = snapshot.currencies,
                unlockedBlessings = snapshot.unlockedBlessings,
                unlockedContent = snapshot.unlockedContent
            )
        }
    }
}
