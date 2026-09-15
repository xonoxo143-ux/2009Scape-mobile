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
        LeagueItems.installDefinitions() // legacy cleanup/compat definitions
        DemonicPactsRelics.registerAll()
        LeagueRuntime.install(LeagueRules)
        println("SINGLEPLAYER_LEAGUE: DEMONIC_PACTS_READY relics=${LeagueRelics.registeredRelicIds()}")
    }
}

/** One dispatcher owns all league-specific gameplay behavior. */
object LeagueRules : LeagueRuntime.RuleSet {
    override fun onAttach(player: Player) {
        LeagueRelics.onAttach(player)
    }

    override fun onEvent(player: Player, event: Event) {
        LeagueTasks.onEvent(player, event)
        DemonicPactsRelics.onGlobalEvent(player, event)
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
    fun onDetach(player: Player) {}
    fun onEvent(player: Player, event: Event) {}
}

object LeagueRelics {
    private const val RELICS_ATTRIBUTE = "league:relics"
    private const val RELOADED_EXTRA_ATTRIBUTE = "league:reloaded-extra"
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

    @JvmStatic
    fun selectedDefinitions(player: Player): List<LeagueRelicEffect> =
        effects.values.filter { LeagueRuntime.hasRelic(player, it.id) }

    /** Filter saved legacy IDs out of the UI/counts without destroying the save. */
    @JvmStatic
    fun selectedIds(player: Player): List<String> = selectedDefinitions(player).map { it.id }

    @JvmStatic
    fun canSelect(player: Player, id: String): Boolean {
        val chosen = effects[id] ?: return false
        if (LeagueRuntime.hasRelic(player, id)) return false

        val conflict = effects.values.firstOrNull {
            it.tier == chosen.tier && LeagueRuntime.hasRelic(player, it.id)
        }
        if (conflict == null) return true

        return chosen.tier < 7 &&
            LeagueRuntime.hasRelic(player, DemonicPactsRelics.RELOADED) &&
            player.getAttribute(RELOADED_EXTRA_ATTRIBUTE, "").isBlank()
    }

    /** One relic per tier, plus one lower-tier extra when Reloaded has been chosen. */
    @JvmStatic
    @Synchronized
    fun select(player: Player, id: String): SelectionResult {
        val chosen = effects[id] ?: return SelectionResult.UNKNOWN
        if (LeagueRuntime.hasRelic(player, id)) return SelectionResult.ALREADY_SELECTED

        val conflict = effects.values.firstOrNull {
            it.tier == chosen.tier && LeagueRuntime.hasRelic(player, it.id)
        }
        var consumesReloaded = false
        if (conflict != null) {
            val extraAvailable = chosen.tier < 7 &&
                LeagueRuntime.hasRelic(player, DemonicPactsRelics.RELOADED) &&
                player.getAttribute(RELOADED_EXTRA_ATTRIBUTE, "").isBlank()
            if (!extraAvailable) return SelectionResult.TIER_LOCKED
            consumesReloaded = true
        }

        if (!LeagueRuntime.unlockRelic(player, id)) return SelectionResult.ALREADY_SELECTED
        if (consumesReloaded) {
            player.setAttribute("/save:$RELOADED_EXTRA_ATTRIBUTE", id)
            player.sendMessage("Reloaded grants an additional lower-tier relic.")
        }
        chosen.onAttach(player)
        DemonicPactsRelics.refreshPassives(player)
        player.sendMessage("League relic selected: ${chosen.name}.")
        println("SINGLEPLAYER_LEAGUE: RELIC_SELECTED tier=${chosen.tier} id=${chosen.id} reloadedExtra=$consumesReloaded")
        return SelectionResult.SELECTED
    }

    /** Single-player sandbox reset, exposed through the League UI. */
    @JvmStatic
    @Synchronized
    fun resetSelections(player: Player) {
        for (effect in selectedDefinitions(player)) {
            try {
                effect.onDetach(player)
            } catch (failure: Throwable) {
                System.err.println("SINGLEPLAYER_LEAGUE: DETACH_FAILED id=${effect.id} failure=$failure")
            }
        }

        player.setAttribute("/save:$RELICS_ATTRIBUTE", "")
        player.setAttribute("/save:$RELOADED_EXTRA_ATTRIBUTE", "")
        LeagueItems.removeRelicItems(player)
        ExpandedLeagueItems.removeBankersNote(player)
        DemonicPactsReset.removeItems(player)
        player.removeAttribute(LeagueModifiers.RECALL_ORIGIN)
        player.removeAttribute(LeagueModifiers.RECALL_IN_PROGRESS)
        player.removeAttribute(RELOADED_EXTRA_ATTRIBUTE)
        DemonicPactsRelics.refreshPassives(player)
        player.sendMessage("League relic selections reset.")
        println("SINGLEPLAYER_LEAGUE: RELICS_RESET username=${player.username}")
    }

    fun onAttach(player: Player) {
        for ((id, effect) in effects) {
            if (LeagueRuntime.hasRelic(player, id)) effect.onAttach(player)
        }
        DemonicPactsRelics.refreshPassives(player)
        if (selectedDefinitions(player).isEmpty()) {
            player.sendMessage("Demonic Pacts relic board is ready. Open the League menu to choose.")
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
