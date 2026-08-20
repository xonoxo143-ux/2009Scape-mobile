package content.global.leagues

import content.global.leagues.core.LeagueProfile
import content.global.leagues.core.LeagueProfileSnapshot
import org.json.simple.JSONArray
import org.json.simple.JSONObject

internal object LeagueProfileJson {
    fun write(profile: LeagueProfile): JSONObject = write(profile.snapshot())

    fun write(snapshot: LeagueProfileSnapshot): JSONObject = JSONObject().apply {
        put("schemaVersion", snapshot.schemaVersion)
        put("active", snapshot.active)
        put("seasonKey", snapshot.seasonKey)
        put("points", snapshot.points)
        put("completedTasks", strings(snapshot.completedTasks))
        put("taskProgress", longMap(snapshot.taskProgress))
        put("unlockedRelics", strings(snapshot.unlockedRelics))
        put("primaryRelicsByTier", intStringMap(snapshot.primaryRelicsByTier))
        put("unlockedRegions", strings(snapshot.unlockedRegions))
        put("fragmentLevels", intMap(snapshot.fragmentLevels))
        put("fragmentXp", longMap(snapshot.fragmentXp))
        put("equippedFragments", strings(snapshot.equippedFragments))
        put("masteryRanks", intMap(snapshot.masteryRanks))
        put("unlockedPacts", strings(snapshot.unlockedPacts))
        put("echoKills", longMap(snapshot.echoKills))
        put("unlockedEchoes", strings(snapshot.unlockedEchoes))
        put("currencies", longMap(snapshot.currencies))
        put("unlockedBlessings", strings(snapshot.unlockedBlessings))
        put("unlockedContent", strings(snapshot.unlockedContent))
    }

    fun read(root: JSONObject?): LeagueProfile {
        if (root == null) return LeagueProfile.fresh(active = false)
        val snapshot = LeagueProfileSnapshot(
            schemaVersion = root.int("schemaVersion", 1),
            active = root.bool("active", false),
            seasonKey = root.string("seasonKey", LeagueProfile.DEFAULT_SEASON_KEY),
            points = root.long("points", 0),
            completedTasks = root.stringSet("completedTasks"),
            taskProgress = root.longMap("taskProgress"),
            unlockedRelics = root.stringSet("unlockedRelics"),
            primaryRelicsByTier = root.intStringMap("primaryRelicsByTier"),
            unlockedRegions = root.stringSet("unlockedRegions"),
            fragmentLevels = root.intMap("fragmentLevels"),
            fragmentXp = root.longMap("fragmentXp"),
            equippedFragments = root.stringList("equippedFragments"),
            masteryRanks = root.intMap("masteryRanks"),
            unlockedPacts = root.stringSet("unlockedPacts"),
            echoKills = root.longMap("echoKills"),
            unlockedEchoes = root.stringSet("unlockedEchoes"),
            currencies = root.longMap("currencies"),
            unlockedBlessings = root.stringSet("unlockedBlessings"),
            unlockedContent = root.stringSet("unlockedContent")
        )
        return LeagueProfile.fromSnapshot(snapshot)
    }

    private fun strings(values: Iterable<String>) = JSONArray().apply { values.forEach(::add) }

    private fun longMap(values: Map<String, Long>) = JSONObject().apply {
        values.toSortedMap().forEach { (key, value) -> put(key, value) }
    }

    private fun intMap(values: Map<String, Int>) = JSONObject().apply {
        values.toSortedMap().forEach { (key, value) -> put(key, value) }
    }

    private fun intStringMap(values: Map<Int, String>) = JSONObject().apply {
        values.toSortedMap().forEach { (key, value) -> put(key.toString(), value) }
    }

    private fun JSONObject.string(key: String, default: String) = get(key)?.toString() ?: default
    private fun JSONObject.long(key: String, default: Long) = get(key)?.toString()?.toLongOrNull() ?: default
    private fun JSONObject.int(key: String, default: Int) = get(key)?.toString()?.toIntOrNull() ?: default
    private fun JSONObject.bool(key: String, default: Boolean) = when (val value = get(key)) {
        is Boolean -> value
        null -> default
        else -> value.toString().toBooleanStrictOrNull() ?: default
    }

    private fun JSONObject.stringList(key: String): List<String> =
        (get(key) as? JSONArray)?.map { it.toString() }.orEmpty()

    private fun JSONObject.stringSet(key: String): Set<String> = stringList(key).toSet()

    private fun JSONObject.longMap(key: String): Map<String, Long> =
        (get(key) as? Map<*, *>)?.entries?.mapNotNull { entry ->
            val k = entry.key
            val v = entry.value
            v?.toString()?.toLongOrNull()?.let { k.toString() to it }
        }?.toMap().orEmpty()

    private fun JSONObject.intMap(key: String): Map<String, Int> =
        (get(key) as? Map<*, *>)?.entries?.mapNotNull { entry ->
            val k = entry.key
            val v = entry.value
            v?.toString()?.toIntOrNull()?.let { k.toString() to it }
        }?.toMap().orEmpty()

    private fun JSONObject.intStringMap(key: String): Map<Int, String> =
        (get(key) as? Map<*, *>)?.entries?.mapNotNull { entry ->
            val k = entry.key
            val v = entry.value
            k.toString().toIntOrNull()?.let { it to v.toString() }
        }?.toMap().orEmpty()
}
