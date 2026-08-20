package content.global.leagues.core

enum class LeagueContentType(val key: String) {
    SKILL("skill"),
    BOSS("boss"),
    QUEST("quest"),
    FEATURE("feature")
}

data class LeagueContentUnlockDefinition(
    val id: String,
    val name: String,
    val type: LeagueContentType,
    val currencyId: String = "sage_renown",
    val cost: Long,
    val requirements: List<LeagueRequirement> = emptyList()
) {
    val contentKey: String get() = "${type.key}:$id"

    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9._-]*"))) { "Invalid content unlock id $id" }
        require(name.isNotBlank())
        require(currencyId.matches(Regex("[a-z0-9][a-z0-9._-]*"))) { "Invalid currency id $currencyId" }
        require(cost >= 0)
    }
}

class LeagueContentUnlockRegistry(definitions: Collection<LeagueContentUnlockDefinition>) {
    val definitions = definitions.toList()
    private val byKey = this.definitions.associateBy { it.contentKey }

    init {
        require(byKey.size == this.definitions.size) { "Duplicate League content unlock key" }
    }

    fun get(type: LeagueContentType, id: String): LeagueContentUnlockDefinition? = byKey["${type.key}:$id"]
    fun get(contentKey: String): LeagueContentUnlockDefinition? = byKey[contentKey]
}

class LeagueContentUnlockEngine(
    private val registry: LeagueContentUnlockRegistry,
    private val context: LeagueRequirementContext = LeagueRequirementContext()
) {
    fun unlock(profile: LeagueProfile, type: LeagueContentType, id: String): LeagueUnlockResult {
        val definition = registry.get(type, id) ?: return LeagueUnlockResult.fail(LeagueUnlockFailure.UNKNOWN_CONTENT)
        return unlock(profile, definition)
    }

    fun unlock(profile: LeagueProfile, contentKey: String): LeagueUnlockResult {
        val definition = registry.get(contentKey) ?: return LeagueUnlockResult.fail(LeagueUnlockFailure.UNKNOWN_CONTENT)
        return unlock(profile, definition)
    }

    private fun unlock(profile: LeagueProfile, definition: LeagueContentUnlockDefinition): LeagueUnlockResult {
        if (definition.contentKey in profile.unlockedContent) {
            return LeagueUnlockResult.fail(LeagueUnlockFailure.ALREADY_UNLOCKED)
        }
        val unmet = definition.requirements.unmet(profile, context)
        if (unmet.isNotEmpty()) return LeagueUnlockResult.fail(LeagueUnlockFailure.REQUIREMENTS_NOT_MET, unmet)
        if (!spendCurrency(profile, definition.currencyId, definition.cost)) {
            return LeagueUnlockResult.fail(LeagueUnlockFailure.INSUFFICIENT_CURRENCY)
        }
        profile.unlockedContent += definition.contentKey
        return LeagueUnlockResult.success()
    }
}
