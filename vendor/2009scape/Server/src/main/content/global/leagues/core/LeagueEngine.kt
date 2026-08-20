package content.global.leagues.core

data class LeagueSignalResult(
    val completions: List<LeagueTaskCompletion>,
    val oldTier: LeagueTierDefinition,
    val newTier: LeagueTierDefinition
) {
    val tierChanged: Boolean get() = oldTier.index != newTier.index
}

/** Deterministic task/progression engine. All mutation of task state goes through here. */
class LeagueEngine(
    val taskRegistry: LeagueTaskRegistry,
    val progression: LeagueProgression = LeagueProgression.grandLeagueDefaults()
) {
    fun process(profile: LeagueProfile, signal: LeagueSignal): LeagueSignalResult {
        val oldTier = progression.tierFor(profile.points)
        if (!profile.active) return LeagueSignalResult(emptyList(), oldTier, oldTier)

        val completions = mutableListOf<LeagueTaskCompletion>()
        for (task in taskRegistry.candidates(signal)) {
            if (task.id in profile.completedTasks) continue

            val delta = task.trigger.progressDelta(signal)
            if (delta <= 0) continue

            val oldProgress = profile.taskProgress[task.id] ?: 0L
            val newProgress = saturatingAdd(oldProgress, delta).coerceAtMost(task.target)
            if (newProgress >= task.target) {
                profile.taskProgress.remove(task.id)
                profile.completedTasks += task.id
                val previousPoints = profile.points
                profile.points = saturatingAdd(profile.points, task.points.toLong())
                completions += LeagueTaskCompletion(task, task.points, previousPoints, profile.points)
            } else {
                profile.taskProgress[task.id] = newProgress
            }
        }

        val newTier = progression.tierFor(profile.points)
        return LeagueSignalResult(completions, oldTier, newTier)
    }

    fun taskProgress(profile: LeagueProfile, taskId: String): Long {
        val task = taskRegistry.get(taskId) ?: return 0
        return if (taskId in profile.completedTasks) task.target else profile.taskProgress[taskId] ?: 0
    }

    fun completionRatio(profile: LeagueProfile): Double {
        if (taskRegistry.tasks.isEmpty()) return 1.0
        return profile.completedTasks.count { taskRegistry.get(it) != null }.toDouble() / taskRegistry.tasks.size.toDouble()
    }

    private fun saturatingAdd(a: Long, b: Long): Long {
        if (b > 0 && a > Long.MAX_VALUE - b) return Long.MAX_VALUE
        if (b < 0 && a < Long.MIN_VALUE - b) return Long.MIN_VALUE
        return a + b
    }
}
