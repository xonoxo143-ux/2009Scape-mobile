package content.global.leagues.core

data class LeagueTaskView(
    val id: String,
    val name: String,
    val points: Int,
    val progress: Long,
    val target: Long,
    val complete: Boolean,
    val difficulty: LeagueTaskDifficulty,
    val source: LeagueSource,
    val regionId: String?,
    val category: String,
    val masteryPointReward: Int,
    val pactPointReward: Int
)


data class LeagueTaskFilter(
    val completed: Boolean? = null,
    val difficulty: LeagueTaskDifficulty? = null,
    val source: LeagueSource? = null,
    val regionId: String? = null,
    val category: String? = null,
    val minPoints: Int? = null,
    val maxPoints: Int? = null,
    val nearlyComplete: Boolean = false,
    val search: String = ""
)

data class LeagueOverview(
    val active: Boolean,
    val points: Int,
    val tier: Int,
    val nextTierPoints: Int?,
    val completedTasks: Int,
    val totalTasks: Int,
    val selectedRelics: Map<Int, String>,
    val unlockedRegions: Set<String>,
    val equippedFragments: Set<String>,
    val activeFragmentSets: Set<String>,
    val unlockedMasteries: Set<String>,
    val unlockedPacts: Set<String>,
    val echoKills: Map<String, Int>,
    val regionTokens: Int,
    val fragmentTokens: Int,
    val masteryPoints: Int,
    val pactPoints: Int
)

fun GrandLeagueSession.overview(): LeagueOverview = LeagueOverview(
    active = profile.active,
    points = profile.points,
    tier = profile.tier,
    nextTierPoints = content.tiers.firstOrNull { it.tier > profile.tier }?.minimumPoints,
    completedTasks = profile.completedTasks.size,
    totalTasks = content.tasks.size,
    selectedRelics = profile.selectedRelics.toSortedMap(),
    unlockedRegions = profile.unlockedRegions.toSortedSet(),
    equippedFragments = profile.equippedFragments.toSortedSet(),
    activeFragmentSets = activeFragmentSets(),
    unlockedMasteries = profile.unlockedMasteries.toSortedSet(),
    unlockedPacts = profile.unlockedPacts.toSortedSet(),
    echoKills = profile.echoKills.toSortedMap(),
    regionTokens = profile.regionTokens,
    fragmentTokens = profile.fragmentTokens,
    masteryPoints = profile.masteryPoints,
    pactPoints = profile.pactPoints
)

fun GrandLeagueSession.taskViews(filter: LeagueTaskFilter = LeagueTaskFilter()): List<LeagueTaskView> = content.tasks.map { task ->
    val progressKey = when (task.trigger) {
        is CountTrigger -> "count:${task.trigger.kind}:${task.trigger.key}"
        is MetricTrigger -> "metric:${task.trigger.kind}:${task.trigger.key}"
    }
    val target = when (val trigger = task.trigger) {
        is CountTrigger -> trigger.target
        is MetricTrigger -> trigger.target
    }
    LeagueTaskView(
        id = task.id,
        name = task.name,
        points = task.points,
        progress = (profile.progress[progressKey] ?: 0L).coerceAtMost(target),
        target = target,
        complete = task.id in profile.completedTasks,
        difficulty = task.difficulty,
        source = task.source,
        regionId = task.regionId,
        category = task.category,
        masteryPointReward = task.masteryPointReward,
        pactPointReward = task.pactPointReward
    )
}.filter { view ->
    (filter.completed == null || view.complete == filter.completed) &&
        (filter.difficulty == null || view.difficulty == filter.difficulty) &&
        (filter.source == null || view.source == filter.source) &&
        (filter.regionId == null || view.regionId == filter.regionId) &&
        (filter.category == null || view.category == filter.category) &&
        (filter.minPoints == null || view.points >= filter.minPoints) &&
        (filter.maxPoints == null || view.points <= filter.maxPoints) &&
        (!filter.nearlyComplete || (!view.complete && view.progress > 0 && view.progress * 4 >= view.target * 3)) &&
        (filter.search.isBlank() || view.name.contains(filter.search, ignoreCase = true) || view.id.contains(filter.search, ignoreCase = true))
}
