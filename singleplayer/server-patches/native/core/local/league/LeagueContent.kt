package core.local.league

import core.api.StartupListener
import core.game.event.Event
import core.game.node.entity.player.Player
import core.local.LeagueRuntime
import java.util.concurrent.CopyOnWriteArrayList

/** Auto-loaded single-player league entry point. */
class LeagueBootstrap : StartupListener {
    override fun startup() {
        if (!java.lang.Boolean.getBoolean("singleplayer")) return
        LeagueItems.installDefinitions()
        FirstPassRelics.registerAll()
        LeagueRuntime.install(LeagueRules)
        println("SINGLEPLAYER_LEAGUE: RULES_READY relics=${LeagueRelics.registeredRelicIds()}")
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

/** One self-contained relic implementation. */
interface LeagueRelicEffect {
    val id: String
    val name: String
    val description: String
    val tier: Int
    fun onAttach(player: Player) {}
    fun onEvent(player: Player, event: Event) {}
}

object LeagueRelics {
    private const val RELICS_ATTRIBUTE = "league:relics"
    private val effects = LinkedHashMap<String, LeagueRelicEffect>()

    @JvmStatic
    @Synchronized
    fun register(effect: LeagueRelicEffect) {
        require(effect.id.isNotBlank()) { "League relic id cannot be blank" }
        require(effect.tier > 0) { "League relic tier must be positive" }
        require(!effects.containsKey(effect.id)) { "Duplicate league relic id: ${effect.id}" }
        effects[effect.id] = effect
    }

    @JvmStatic
    @Synchronized
    fun registeredRelicIds(): List<String> = effects.keys.toList()

    @JvmStatic
    @Synchronized
    fun definitions(): List<LeagueRelicEffect> = effects.values.toList()

    @JvmStatic
    fun definition(id: String): LeagueRelicEffect? = effects[id]

    /** Temporary sandbox selection: exactly one relic may be chosen per tier. */
    @JvmStatic
    @Synchronized
    fun select(player: Player, id: String): SelectionResult {
        val chosen = effects[id] ?: return SelectionResult.UNKNOWN
        if (LeagueRuntime.hasRelic(player, id)) return SelectionResult.ALREADY_SELECTED
        val conflict = effects.values.firstOrNull {
            it.tier == chosen.tier && LeagueRuntime.hasRelic(player, it.id)
        }
        if (conflict != null) return SelectionResult.TIER_LOCKED

        if (!LeagueRuntime.unlockRelic(player, id)) return SelectionResult.ALREADY_SELECTED
        chosen.onAttach(player)
        player.sendMessage("League relic selected: ${chosen.name}.")
        println("SINGLEPLAYER_LEAGUE: RELIC_SELECTED tier=${chosen.tier} id=${chosen.id}")
        return SelectionResult.SELECTED
    }

    /** Development-only reset until League points/tier unlocks replace sandbox mode. */
    @JvmStatic
    @Synchronized
    fun resetSelections(player: Player) {
        player.setAttribute("/save:$RELICS_ATTRIBUTE", "")
        LeagueItems.removeRelicItems(player)
        player.removeAttribute(LeagueModifiers.RECALL_ORIGIN)
        player.removeAttribute(LeagueModifiers.RECALL_IN_PROGRESS)
        player.sendMessage("League relic selections reset.")
        println("SINGLEPLAYER_LEAGUE: RELICS_RESET username=${player.username}")
    }

    fun onAttach(player: Player) {
        for ((id, effect) in effects) {
            if (LeagueRuntime.hasRelic(player, id)) effect.onAttach(player)
        }
        if (effects.values.none { LeagueRuntime.hasRelic(player, it.id) }) {
            player.sendMessage("League relic sandbox is ready. Use ::relics to view choices.")
        }
    }

    fun onEvent(player: Player, event: Event) {
        for ((id, effect) in effects) {
            if (LeagueRuntime.hasRelic(player, id)) effect.onEvent(player, event)
        }
    }

    enum class SelectionResult {
        SELECTED,
        ALREADY_SELECTED,
        TIER_LOCKED,
        UNKNOWN
    }
}
