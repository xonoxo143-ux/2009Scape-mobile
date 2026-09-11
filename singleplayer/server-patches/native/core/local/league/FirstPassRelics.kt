package core.local.league

import content.data.EnchantedJewellery
import core.api.Commands
import core.api.addItemOrDrop
import core.api.openDialogue
import core.cache.def.impl.ItemDefinition
import core.game.dialogue.DialogueFile
import core.game.event.Event
import core.game.event.TickEvent
import core.game.interaction.InteractionListener
import core.game.interaction.IntType
import core.game.node.entity.player.Player
import core.game.node.item.Item
import core.game.node.entity.player.link.TeleportManager
import core.game.node.entity.skill.Skills
import core.game.system.command.Privilege
import core.game.world.map.Location
import core.local.LeagueRuntime

/** IDs above the revision-530 cache range; RT4 synthesizes matching ObjTypes locally. */
object LeagueItemIds {
    const val VOIDWALKER = 65000
    const val DISK_OF_MEMORIES = 65001
    const val VOIDWALKER_VISUAL_DONOR = 14534 // Strange teleorb
    const val DISK_VISUAL_DONOR = 981         // Disk of returning
}

/** Server-side logical definitions for the two custom League utility items. */
object LeagueItems {
    @JvmStatic
    fun installDefinitions() {
        install(
            LeagueItemIds.VOIDWALKER,
            LeagueItemIds.VOIDWALKER_VISUAL_DONOR,
            "Voidwalker",
            "A relic that folds familiar paths through the void.",
            "teleport"
        )
        install(
            LeagueItemIds.DISK_OF_MEMORIES,
            LeagueItemIds.DISK_VISUAL_DONOR,
            "Disk of Memories",
            "A relic that remembers where you were before you left.",
            "recall"
        )
        println("SINGLEPLAYER_LEAGUE: CUSTOM_ITEMS_READY ids=${LeagueItemIds.VOIDWALKER},${LeagueItemIds.DISK_OF_MEMORIES}")
    }

    private fun install(id: Int, donorId: Int, name: String, examine: String, action: String) {
        val donor = ItemDefinition.forId(donorId)
        val custom = ItemDefinition()
        custom.id = id
        custom.name = name
        custom.examine = examine
        custom.interfaceModelId = donor.interfaceModelId
        custom.modelZoom = donor.modelZoom
        custom.modelRotationX = donor.modelRotationX
        custom.modelRotationY = donor.modelRotationY
        custom.modelOffset1 = donor.modelOffset1
        custom.modelOffset2 = donor.modelOffset2
        custom.isStackable = false
        custom.value = 0
        custom.isMembersOnly = false
        custom.options = arrayOf(action, null, null, null, null)
        ItemDefinition.getDefinitions()[id] = custom
    }

    fun ensureRelicItem(player: Player, itemId: Int) {
        if (player.inventory.contains(itemId, 1) || player.bank.contains(itemId, 1)) return
        val item = Item(itemId)
        if (!player.inventory.isFull) player.inventory.add(item)
        else player.bank.add(item)
        player.sendMessage("Your ${item.name} has been restored.")
    }

    fun removeRelicItems(player: Player) {
        for (id in intArrayOf(LeagueItemIds.VOIDWALKER, LeagueItemIds.DISK_OF_MEMORIES)) {
            player.inventory.remove(Item(id))
            player.bank.remove(Item(id))
        }
    }
}

/** First playable set: two choices in each of two automatically available tiers. */
object FirstPassRelics {
    const val ENDLESS_HARVEST = "endless_harvest"
    const val VOIDWALKER = "voidwalker"
    const val PRODUCTION_MASTER = "production_master"
    const val LAST_RECALL = "last_recall"

    @Volatile private var registered = false

    @JvmStatic
    @Synchronized
    fun registerAll() {
        if (registered) return
        registered = true
        LeagueRelics.register(SimpleRelic(
            ENDLESS_HARVEST,
            "Endless Harvest",
            "Gather twice the resources and twice the gathering XP from Mining, Fishing and Woodcutting.",
            1
        ))
        LeagueRelics.register(RelicItemEffect(
            VOIDWALKER,
            "Voidwalker",
            "Gain the Voidwalker, combining unlimited existing jewellery teleports into one item.",
            1,
            LeagueItemIds.VOIDWALKER
        ))
        LeagueRelics.register(SimpleRelic(
            PRODUCTION_MASTER,
            "Production Master",
            "Batch-process supported production actions with their normal ingredients, products and XP.",
            2
        ))
        LeagueRelics.register(RelicItemEffect(
            LAST_RECALL,
            "Last Recall",
            "Gain the Disk of Memories, which returns you to the origin of your last eligible teleport.",
            2,
            LeagueItemIds.DISK_OF_MEMORIES
        ))
    }

    private class SimpleRelic(
        override val id: String,
        override val name: String,
        override val description: String,
        override val tier: Int
    ) : LeagueRelicEffect

    private class RelicItemEffect(
        override val id: String,
        override val name: String,
        override val description: String,
        override val tier: Int,
        private val itemId: Int
    ) : LeagueRelicEffect {
        override fun onAttach(player: Player) {
            LeagueItems.ensureRelicItem(player, itemId)
        }

        override fun onEvent(player: Player, event: Event) {
            if (event is TickEvent && event.worldTicks % 50 == 0) {
                LeagueItems.ensureRelicItem(player, itemId)
            }
        }
    }
}

/** Narrow hooks called from retained mechanics; no transport or duplicated game authority. */
object LeagueModifiers {
    const val RECALL_ORIGIN = "league:last-recall-origin"
    const val RECALL_IN_PROGRESS = "league:last-recall-in-progress"

    @JvmStatic
    fun hasEndlessHarvest(player: Player): Boolean =
        LeagueRuntime.hasRelic(player, FirstPassRelics.ENDLESS_HARVEST)

    @JvmStatic
    fun productionMaster(player: Player): Boolean =
        LeagueRuntime.hasRelic(player, FirstPassRelics.PRODUCTION_MASTER)

    /** Called only by Mining/Fishing/Woodcutting reward sites. */
    @JvmStatic
    fun onGather(player: Player, skillId: Int, itemId: Int, amount: Int, experience: Double) {
        if (!hasEndlessHarvest(player) || amount <= 0) return
        if (skillId != Skills.MINING && skillId != Skills.FISHING && skillId != Skills.WOODCUTTING) return
        addItemOrDrop(player, itemId, amount)
        player.skills.addExperience(skillId, experience, true)
        player.sendMessage("Endless Harvest grants an additional ${if (amount == 1) "resource" else "$amount resources"}.")
    }

    /** Production Master batching is restricted to known production SkillPulse packages. */
    @JvmStatic
    fun isProductionPulse(pulse: Any): Boolean {
        val name = pulse.javaClass.name
        return name.startsWith("content.global.skill.smithing.") ||
            name.startsWith("content.global.skill.crafting.") ||
            name.startsWith("content.global.skill.herblore.") ||
            name.startsWith("content.global.skill.cooking.dairy.")
    }

    /** Capture the departure tile before an eligible teleport mutates player.location. */
    @JvmStatic
    fun beforeTeleport(player: Player) {
        if (!LeagueRuntime.hasRelic(player, FirstPassRelics.LAST_RECALL)) return
        if (player.getAttribute(RECALL_IN_PROGRESS, false)) return
        val loc = player.location
        player.setAttribute("/save:$RECALL_ORIGIN", "${loc.x},${loc.y},${loc.z}")
    }

    @JvmStatic
    fun recall(player: Player): Boolean {
        if (!LeagueRuntime.hasRelic(player, FirstPassRelics.LAST_RECALL)) {
            player.sendMessage("You have not selected Last Recall.")
            return false
        }
        val encoded = player.getAttribute(RECALL_ORIGIN, "")
        val parts = encoded.split(',')
        if (parts.size != 3) {
            player.sendMessage("The Disk of Memories has no location stored yet.")
            return false
        }
        val x = parts[0].toIntOrNull()
        val y = parts[1].toIntOrNull()
        val z = parts[2].toIntOrNull()
        if (x == null || y == null || z == null || z !in 0..3 || x !in 0..16383 || y !in 0..16383) {
            player.sendMessage("The Disk of Memories cannot resolve that memory.")
            return false
        }
        if (!player.zoneMonitor.teleport(1, Item(LeagueItemIds.DISK_OF_MEMORIES))) {
            player.sendMessage("The Disk of Memories cannot be used here.")
            return false
        }
        player.setAttribute(RECALL_IN_PROGRESS, true)
        val started = player.teleporter.send(Location.create(x, y, z), TeleportManager.TeleportType.NORMAL, -1)
        player.removeAttribute(RECALL_IN_PROGRESS)
        if (!started) player.sendMessage("The memory slips away before you can follow it.")
        return started
    }
}

/** Inventory behavior for custom League utility items. */
class LeagueRelicItemListener : InteractionListener {
    override fun defineListeners() {
        on(LeagueItemIds.VOIDWALKER, IntType.ITEM, "teleport") { player, _ ->
            if (!LeagueRuntime.hasRelic(player, FirstPassRelics.VOIDWALKER)) {
                player.sendMessage("You have not selected Voidwalker.")
                return@on true
            }
            openDialogue(player, VoidwalkerDialogue())
            true
        }
        on(LeagueItemIds.DISK_OF_MEMORIES, IntType.ITEM, "recall") { player, _ ->
            LeagueModifiers.recall(player)
            true
        }
    }
}

/** Two-stage menu that delegates the actual teleports to existing jewellery code. */
class VoidwalkerDialogue : DialogueFile() {
    private val families = arrayOf(
        EnchantedJewellery.AMULET_OF_GLORY,
        EnchantedJewellery.RING_OF_DUELING,
        EnchantedJewellery.GAMES_NECKLACE,
        EnchantedJewellery.SKILLS_NECKLACE,
        EnchantedJewellery.COMBAT_BRACELET
    )
    private var selected: EnchantedJewellery? = null

    override fun handle(componentID: Int, buttonID: Int) {
        val p = player ?: return
        when (stage) {
            0 -> {
                interpreter!!.sendOptions(
                    "Voidwalker teleport family",
                    "Amulet of glory",
                    "Ring of dueling",
                    "Games necklace",
                    "Skills necklace",
                    "Combat bracelet"
                )
                stage = 1
            }
            1 -> {
                selected = families.getOrNull(buttonID - 1)
                val jewellery = selected ?: return end()
                interpreter!!.sendOptions("Where would you like to teleport to?", *jewellery.options)
                stage = 2
            }
            2 -> {
                val jewellery = selected ?: return end()
                end()
                val destination = buttonID - 1
                if (destination in jewellery.locations.indices) {
                    // A charged synthetic jewellery item lets the retained handler perform
                    // its authentic checks/animations without consuming any charges.
                    jewellery.attemptTeleport(p, Item(jewellery.ids[0]), destination, false, false)
                }
            }
        }
    }
}

/** Temporary sandbox controls until tasks/League points replace free tier access. */
class LeagueSandboxCommands : Commands {
    override fun defineCommands() {
        define("relics", Privilege.STANDARD, "::relics", "View first-pass League relics.") { player, _ ->
            player.sendMessage("--- League relic sandbox ---")
            for (effect in LeagueRelics.definitions()) {
                val selected = if (LeagueRuntime.hasRelic(player, effect.id)) " [SELECTED]" else ""
                player.sendMessage("Tier ${effect.tier}: ${effect.name}$selected - ${effect.description}")
            }
            player.sendMessage("Choose with ::relic endless, ::relic voidwalker, ::relic production, or ::relic recall")
            player.sendMessage("Development reset: ::resetrelics")
        }

        define("relic", Privilege.STANDARD, "::relic <name>", "Select a relic in the sandbox.") { player, args ->
            val raw = args.drop(1).joinToString(" ").trim().lowercase()
            val id = when (raw.replace("_", " ")) {
                "endless", "harvest", "endless harvest" -> FirstPassRelics.ENDLESS_HARVEST
                "void", "voidwalker" -> FirstPassRelics.VOIDWALKER
                "production", "production master" -> FirstPassRelics.PRODUCTION_MASTER
                "recall", "last recall", "disk", "disk of memories" -> FirstPassRelics.LAST_RECALL
                else -> null
            }
            if (id == null) {
                player.sendMessage("Unknown relic. Use ::relics to see the available choices.")
                return@define
            }
            when (LeagueRelics.select(player, id)) {
                LeagueRelics.SelectionResult.SELECTED -> Unit
                LeagueRelics.SelectionResult.ALREADY_SELECTED -> player.sendMessage("That relic is already selected.")
                LeagueRelics.SelectionResult.TIER_LOCKED -> player.sendMessage("You already selected the other relic in that tier. Use ::resetrelics while testing.")
                LeagueRelics.SelectionResult.UNKNOWN -> player.sendMessage("That relic is not registered.")
            }
        }

        define("resetrelics", Privilege.STANDARD, "::resetrelics", "Reset sandbox relic choices.") { player, _ ->
            LeagueRelics.resetSelections(player)
        }
    }
}
