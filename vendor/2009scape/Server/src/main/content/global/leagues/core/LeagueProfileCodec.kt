package content.global.leagues.core

/** Dependency-free deterministic save codec. IDs are validated and never contain separators used here. */
object LeagueProfileCodec {
    const val VERSION = 2

    fun encode(p: LeagueProfile): String = listOf(
        "v=$VERSION",
        "active=${if (p.active) 1 else 0}",
        "points=${p.points}",
        "tier=${p.tier}",
        "tasks=${set(p.completedTasks)}",
        "progress=${longMap(p.progress)}",
        "relics=${intStringMap(p.selectedRelics)}",
        "regions=${set(p.unlockedRegions)}",
        "fragments=${set(p.unlockedFragments)}",
        "equipped=${set(p.equippedFragments)}",
        "masteries=${set(p.unlockedMasteries)}",
        "pacts=${set(p.unlockedPacts)}",
        "echo=${intMap(p.echoKills)}",
        "cooldowns=${longMap(p.cooldowns)}",
        "claimed=${p.claimedTierRewards.sorted().joinToString(",")}",
        "regionTokens=${p.regionTokens}",
        "fragmentTokens=${p.fragmentTokens}",
        "masteryPoints=${p.masteryPoints}",
        "pactPoints=${p.pactPoints}"
    ).joinToString("|")

    fun decode(raw: String): LeagueProfile {
        if (raw.isBlank()) return LeagueProfile()
        val fields = raw.split('|').associate { entry ->
            val i = entry.indexOf('='); require(i > 0) { "Malformed League save field" }
            entry.substring(0, i) to entry.substring(i + 1)
        }
        val version = fields["v"]?.toIntOrNull() ?: -1
        require(version in 1..VERSION) { "Unsupported League save version ${fields["v"]}" }
        return LeagueProfile(
            active = fields["active"] == "1",
            points = fields.int("points"),
            tier = fields.int("tier"),
            completedTasks = parseSet(fields["tasks"]).toMutableSet(),
            progress = parseLongMap(fields["progress"]).toMutableMap(),
            selectedRelics = parseIntStringMap(fields["relics"]).toMutableMap(),
            unlockedRegions = parseSet(fields["regions"]).toMutableSet(),
            unlockedFragments = parseSet(fields["fragments"]).toMutableSet(),
            equippedFragments = parseSet(fields["equipped"]).toMutableSet(),
            unlockedMasteries = parseSet(fields["masteries"]).toMutableSet(),
            unlockedPacts = parseSet(fields["pacts"]).toMutableSet(),
            echoKills = parseIntMap(fields["echo"]).toMutableMap(),
            cooldowns = if (version >= 2) parseLongMap(fields["cooldowns"]).toMutableMap() else linkedMapOf(),
            claimedTierRewards = parseInts(fields["claimed"]).toMutableSet(),
            regionTokens = fields.int("regionTokens"),
            fragmentTokens = fields.int("fragmentTokens"),
            masteryPoints = fields.int("masteryPoints"),
            pactPoints = fields.int("pactPoints")
        )
    }

    private fun set(values: Set<String>) = values.sorted().joinToString(",")
    private fun longMap(values: Map<String, Long>) = values.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" }
    private fun intMap(values: Map<String, Int>) = values.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" }
    private fun intStringMap(values: Map<Int, String>) = values.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" }
    private fun parseSet(raw: String?) = raw.orEmpty().split(',').filter { it.isNotEmpty() }.onEach { requireLeagueId(it) }.toSet()
    private fun parseLongMap(raw: String?) = pairs(raw).associate { (k, v) -> k to v.toLong() }
    private fun parseIntMap(raw: String?) = pairs(raw).associate { (k, v) -> k to v.toInt() }
    private fun parseIntStringMap(raw: String?) = pairs(raw).associate { (k, v) -> k.toInt() to v }
    private fun parseInts(raw: String?) = raw.orEmpty().split(',').filter { it.isNotEmpty() }.map { it.toInt() }.toSet()
    private fun pairs(raw: String?) = raw.orEmpty().split(',').filter { it.isNotEmpty() }.map {
        val i = it.lastIndexOf(':'); require(i > 0); val k = it.substring(0, i); val v = it.substring(i + 1); k to v
    }
    private fun Map<String, String>.int(key: String) = get(key)?.toIntOrNull() ?: 0
}
