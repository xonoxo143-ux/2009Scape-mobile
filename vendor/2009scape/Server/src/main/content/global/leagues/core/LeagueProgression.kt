package content.global.leagues.core

data class LeagueTierDefinition(
    val index: Int,
    val key: String,
    val displayName: String,
    val requiredPoints: Long
)

class LeagueProgression(tiers: List<LeagueTierDefinition>) {
    val tiers: List<LeagueTierDefinition> = tiers.sortedBy { it.requiredPoints }

    init {
        require(this.tiers.isNotEmpty()) { "At least one League tier is required" }
        require(this.tiers.first().requiredPoints == 0L) { "The first League tier must start at 0 points" }
        require(this.tiers.map { it.index }.distinct().size == this.tiers.size) { "League tier indexes must be unique" }
        require(this.tiers.map { it.key }.distinct().size == this.tiers.size) { "League tier keys must be unique" }
        this.tiers.zipWithNext().forEach { (a, b) ->
            require(b.requiredPoints > a.requiredPoints) { "League tier point thresholds must be strictly increasing" }
        }
    }

    fun tierFor(points: Long): LeagueTierDefinition {
        val safePoints = points.coerceAtLeast(0)
        return tiers.last { it.requiredPoints <= safePoints }
    }

    fun nextTier(points: Long): LeagueTierDefinition? {
        val current = tierFor(points)
        return tiers.firstOrNull { it.requiredPoints > current.requiredPoints }
    }

    companion object {
        /** Initial Grand League scale. Content balancing can replace these values without touching the engine. */
        fun grandLeagueDefaults(): LeagueProgression = LeagueProgression(
            listOf(
                LeagueTierDefinition(0, "START", "Start", 0),
                LeagueTierDefinition(1, "TIER_1", "Tier I", 500),
                LeagueTierDefinition(2, "TIER_2", "Tier II", 1_500),
                LeagueTierDefinition(3, "TIER_3", "Tier III", 3_500),
                LeagueTierDefinition(4, "TIER_4", "Tier IV", 7_000),
                LeagueTierDefinition(5, "TIER_5", "Tier V", 12_000),
                LeagueTierDefinition(6, "TIER_6", "Tier VI", 20_000),
                LeagueTierDefinition(7, "TIER_7", "Tier VII", 32_500),
                LeagueTierDefinition(8, "TIER_8", "Tier VIII", 50_000),
                LeagueTierDefinition(9, "TIER_9", "Tier IX", 75_000),
                LeagueTierDefinition(10, "TIER_10", "Tier X", 110_000),
                LeagueTierDefinition(11, "GRANDMASTER", "Grandmaster", 150_000)
            )
        )
    }
}
