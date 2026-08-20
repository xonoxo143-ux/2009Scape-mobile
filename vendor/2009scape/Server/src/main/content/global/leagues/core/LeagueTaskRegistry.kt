package content.global.leagues.core

class LeagueTaskRegistry(definitions: Collection<LeagueTaskDefinition>) {
    val tasks: List<LeagueTaskDefinition> = definitions.toList()
    private val byId: Map<String, LeagueTaskDefinition>
    private val byRoute: Map<LeagueSignalKind, Map<String?, List<LeagueTaskDefinition>>>

    init {
        val errors = validate(tasks)
        require(errors.isEmpty()) { "Invalid League task registry:\n${errors.joinToString("\n") { "- $it" }}" }
        byId = tasks.associateBy { it.id }
        byRoute = tasks
            .groupBy { it.trigger.signalKind }
            .mapValues { (_, sameKind) -> sameKind.groupBy { it.trigger.routingKey } }
    }

    fun get(id: String): LeagueTaskDefinition? = byId[id]

    fun candidates(signal: LeagueSignal): List<LeagueTaskDefinition> {
        val routes = byRoute[signal.kind] ?: return emptyList()
        val exact = routes[signal.routingKey()].orEmpty()
        val wildcard = routes[null].orEmpty()
        if (wildcard.isEmpty()) return exact
        if (exact.isEmpty()) return wildcard
        return exact + wildcard
    }

    companion object {
        private val validId = Regex("[a-z0-9][a-z0-9._-]*")

        fun validate(tasks: Collection<LeagueTaskDefinition>): List<String> {
            val errors = mutableListOf<String>()
            val ids = mutableSetOf<String>()
            tasks.forEach { task ->
                if (task.id.isBlank()) errors += "Task has a blank id: ${task.name}"
                if (task.id.isNotBlank() && !validId.matches(task.id)) errors += "Task id '${task.id}' is not canonical lowercase"
                if (!ids.add(task.id)) errors += "Duplicate task id '${task.id}'"
                if (task.name.isBlank()) errors += "Task '${task.id}' has a blank name"
                if (task.description.isBlank()) errors += "Task '${task.id}' has a blank description"
                if (task.points <= 0) errors += "Task '${task.id}' must award positive points"
                if (task.target <= 0) errors += "Task '${task.id}' must have a positive target"
                if (task.region != null && task.region.isBlank()) errors += "Task '${task.id}' has a blank region"
                when (val trigger = task.trigger) {
                    is ProduceItemTrigger -> if (trigger.itemId < 0) errors += "Task '${task.id}' has invalid item id ${trigger.itemId}"
                    is KillNpcTrigger -> if (trigger.npcId < 0) errors += "Task '${task.id}' has invalid npc id ${trigger.npcId}"
                    is GainXpTrigger -> if (trigger.skillId < 0) errors += "Task '${task.id}' has invalid skill id ${trigger.skillId}"
                    is ReachSkillLevelTrigger -> {
                        if (trigger.skillId < 0) errors += "Task '${task.id}' has invalid skill id ${trigger.skillId}"
                        if (trigger.level !in 1..200) errors += "Task '${task.id}' has invalid level ${trigger.level}"
                    }
                    is CompleteQuestTrigger -> if (trigger.questKey.isBlank()) errors += "Task '${task.id}' has a blank quest key"
                    is EquipItemTrigger -> if (trigger.itemId < 0) errors += "Task '${task.id}' has invalid item id ${trigger.itemId}"
                    is ObtainItemTrigger -> if (trigger.itemId < 0) errors += "Task '${task.id}' has invalid item id ${trigger.itemId}"
                    is TeleportToTrigger -> if (trigger.destinationKey.isBlank()) errors += "Task '${task.id}' has a blank destination key"
                    is MetricAtLeastTrigger -> {
                        if (trigger.key.isBlank()) errors += "Task '${task.id}' has a blank metric key"
                        if (trigger.threshold < 0) errors += "Task '${task.id}' has a negative metric threshold ${trigger.threshold}"
                    }
                    is CustomTaskTrigger -> if (trigger.key.isBlank()) errors += "Task '${task.id}' has a blank custom trigger key"
                }
            }
            return errors
        }
    }
}
