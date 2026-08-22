package content.global.leagues.core

data class LeagueProfile(
    var active: Boolean = false,
    var points: Int = 0,
    var tier: Int = 0,
    val completedTasks: MutableSet<String> = linkedSetOf(),
    val progress: MutableMap<String, Long> = linkedMapOf(),
    val selectedRelics: MutableMap<Int, String> = linkedMapOf(),
    val unlockedRegions: MutableSet<String> = linkedSetOf("misthalin", "karamja"),
    val unlockedFragments: MutableSet<String> = linkedSetOf(),
    val equippedFragments: MutableSet<String> = linkedSetOf(),
    val unlockedMasteries: MutableSet<String> = linkedSetOf(),
    val unlockedPacts: MutableSet<String> = linkedSetOf(),
    val echoKills: MutableMap<String, Int> = linkedMapOf(),
    val cooldowns: MutableMap<String, Long> = linkedMapOf(),
    val claimedTierRewards: MutableSet<Int> = linkedSetOf(0),
    var regionTokens: Int = 0,
    var fragmentTokens: Int = 0,
    var masteryPoints: Int = 0,
    var pactPoints: Int = 0
) {
    fun copyDeep(): LeagueProfile = copy(
        completedTasks = completedTasks.toMutableSet(),
        progress = progress.toMutableMap(),
        selectedRelics = selectedRelics.toMutableMap(),
        unlockedRegions = unlockedRegions.toMutableSet(),
        unlockedFragments = unlockedFragments.toMutableSet(),
        equippedFragments = equippedFragments.toMutableSet(),
        unlockedMasteries = unlockedMasteries.toMutableSet(),
        unlockedPacts = unlockedPacts.toMutableSet(),
        echoKills = echoKills.toMutableMap(),
        cooldowns = cooldowns.toMutableMap(),
        claimedTierRewards = claimedTierRewards.toMutableSet()
    )
}
