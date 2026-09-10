package core.local.league

import core.api.StartupListener
import core.game.event.Event
import core.game.node.entity.player.Player
import core.local.LeagueRuntime
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Auto-loaded single-player league entry point.
 *
 * ClassScanner discovers StartupListener implementations from the world-engine
 * classpath. That gives league code a normal 2009Scape content lifecycle while
 * the migration/runtime plumbing stays isolated in core.local.
 */
class LeagueBootstrap : StartupListener {
    override fun startup() {
        if (!java.lang.Boolean.getBoolean("singleplayer")) return
        LeagueRuntime.install(LeagueRules)
        println("SINGLEPLAYER_LEAGUE: RULES_READY")
    }
}

/** One dispatcher owns all league-specific gameplay behavior. */
object LeagueRules : LeagueRuntime.RuleSet {
    override fun onAttach(player: Player) {
        LeagueRelics.onAttach(player)
    }

    override fun onEvent(player: Player, event: Event) {
        LeagueTasks.onEvent(player, event)
        LeagueRelics.onEvent(player, event)
    }
}

/**
 * Declarative task definition. Predicates consume semantic 2009Scape events,
 * so tasks do not need to know anything about RT4 packets or Android input.
 */
data class LeagueTask(
    val id: String,
    val points: Int,
    val matches: (Player, Event) -> Boolean
)

object LeagueTasks {
    private val tasks = CopyOnWriteArrayList<LeagueTask>()

    @JvmStatic
    fun register(task: LeagueTask) {
        require(task.id.isNotBlank()) { "League task id cannot be blank" }
        require(task.points >= 0) { "League task points cannot be negative" }
        require(tasks.none { it.id == task.id }) { "Duplicate league task id: ${task.id}" }
        tasks.add(task)
    }

    @JvmStatic
    fun registeredTaskIds(): List<String> = tasks.map { it.id }

    fun onEvent(player: Player, event: Event) {
        for (task in tasks) {
            if (LeagueRuntime.hasCompletedTask(player, task.id)) continue
            if (task.matches(player, event)) {
                LeagueRuntime.completeTask(player, task.id, task.points)
            }
        }
    }
}

/**
 * Relic effects use the same semantic event stream, with explicit attach hooks
 * for effects that must configure persistent/player state when selected.
 */
interface LeagueRelicEffect {
    val id: String
    fun onAttach(player: Player) {}
    fun onEvent(player: Player, event: Event) {}
}

object LeagueRelics {
    private val effects = LinkedHashMap<String, LeagueRelicEffect>()

    @JvmStatic
    @Synchronized
    fun register(effect: LeagueRelicEffect) {
        require(effect.id.isNotBlank()) { "League relic id cannot be blank" }
        require(!effects.containsKey(effect.id)) { "Duplicate league relic id: ${effect.id}" }
        effects[effect.id] = effect
    }

    @JvmStatic
    @Synchronized
    fun registeredRelicIds(): List<String> = effects.keys.toList()

    @JvmStatic
    fun unlock(player: Player, id: String): Boolean {
        val unlocked = LeagueRuntime.unlockRelic(player, id)
        if (unlocked) {
            effects[id]?.onAttach(player)
        }
        return unlocked
    }

    fun onAttach(player: Player) {
        for ((id, effect) in effects) {
            if (LeagueRuntime.hasRelic(player, id)) {
                effect.onAttach(player)
            }
        }
    }

    fun onEvent(player: Player, event: Event) {
        for ((id, effect) in effects) {
            if (LeagueRuntime.hasRelic(player, id)) {
                effect.onEvent(player, event)
            }
        }
    }
}
