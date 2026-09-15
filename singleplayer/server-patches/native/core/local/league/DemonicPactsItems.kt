package core.local.league

import core.api.openDialogue
import core.cache.def.impl.ItemDefinition
import core.game.dialogue.DialogueFile
import core.game.interaction.InteractionListener
import core.game.interaction.IntType
import core.game.node.entity.player.Player
import core.game.node.entity.player.link.SpellBookManager.SpellBook
import core.game.node.entity.player.link.TeleportManager
import core.game.node.entity.skill.Skills
import core.game.node.item.Item
import core.game.world.GameWorld
import core.game.world.map.Location
import core.local.LeagueRuntime

object DemonicPactsItemIds {
    const val KNAPSACK = 65010
    const val SEARING_BOOTS = 65011
    const val FORAGERS_POUCH = 65012
    const val BANKERS_BRIEFCASE = 65013
    const val EVIL_EYE = 65014
    const val MAP_OF_ALACRITY = 65015
    const val TRANSMUTATION_LEDGER = 65016
    const val BUTLERS_BELL = 65017
    const val FAIRY_MUSHROOM = 65018
    const val SOUL_SHARD = 65019
    const val ARCANE_GRIMOIRE = 65020
    const val SAGES_AXE = 65021
    const val MINION_WHISTLE = 65022
    const val FLASK_OF_FERVOUR = 65023
}

/** Cache-safe custom League items using retained 2009Scape models as visual donors. */
object DemonicPactsItems {
    private data class Definition(
        val id: Int,
        val donor: Int,
        val name: String,
        val examine: String,
        val action: String?,
        val stackable: Boolean = false
    )

    private val definitions = arrayOf(
        Definition(DemonicPactsItemIds.KNAPSACK, 10012, "Knapsack", "A surprisingly capacious gathering bag.", null),
        Definition(DemonicPactsItemIds.SEARING_BOOTS, 6106, "Searing boots", "Boots warmed by impossible League energy.", null),
        Definition(DemonicPactsItemIds.FORAGERS_POUCH, 10012, "Forager's pouch", "It seems to find herbs on its own.", null),
        Definition(DemonicPactsItemIds.BANKERS_BRIEFCASE, 6199, "Banker's briefcase", "Every bank is only a click away.", "activate"),
        Definition(DemonicPactsItemIds.EVIL_EYE, 14534, "Evil eye", "It stares toward dangerous places.", "activate"),
        Definition(DemonicPactsItemIds.MAP_OF_ALACRITY, 981, "Map of Alacrity", "A map folded around shortcuts.", "activate"),
        Definition(DemonicPactsItemIds.TRANSMUTATION_LEDGER, 970, "Transmutation ledger", "A ledger of impossible exchanges.", "activate"),
        Definition(DemonicPactsItemIds.BUTLERS_BELL, 1531, "Butler's bell", "Ring for industrious demonic assistance.", "activate"),
        Definition(DemonicPactsItemIds.FAIRY_MUSHROOM, 6004, "Fairy mushroom", "A portable piece of fairy travel magic.", "activate"),
        Definition(DemonicPactsItemIds.SOUL_SHARD, 4278, "Soul shard", "A stackable fragment of harvested soul energy.", "sacrifice", true),
        Definition(DemonicPactsItemIds.ARCANE_GRIMOIRE, 3842, "Arcane grimoire", "Its pages remember every spellbook.", "activate"),
        Definition(DemonicPactsItemIds.SAGES_AXE, 1351, "Sage's axe", "A League execution weapon.", null),
        Definition(DemonicPactsItemIds.MINION_WHISTLE, 10150, "Minion whistle", "A whistle for an infernal helper.", "activate"),
        Definition(DemonicPactsItemIds.FLASK_OF_FERVOUR, 229, "Flask of fervour", "A flask of concentrated battle fervour.", "activate")
    )

    @JvmStatic
    fun installDefinitions() {
        for (definition in definitions) {
            val donor = ItemDefinition.forId(definition.donor)
            val custom = ItemDefinition()
            custom.id = definition.id
            custom.name = definition.name
            custom.examine = definition.examine
            custom.interfaceModelId = donor.interfaceModelId
            custom.modelZoom = donor.modelZoom
            custom.modelRotationX = donor.modelRotationX
            custom.modelRotationY = donor.modelRotationY
            custom.modelOffset1 = donor.modelOffset1
            custom.modelOffset2 = donor.modelOffset2
            custom.isStackable = definition.stackable
            custom.value = 0
            custom.isMembersOnly = false
            custom.options = arrayOf(definition.action, null, null, null, null)
            ItemDefinition.getDefinitions()[definition.id] = custom
        }
        println("SINGLEPLAYER_LEAGUE: DEMONIC_ITEMS_READY count=${definitions.size}")
    }

    fun ensure(player: Player, itemId: Int) {
        if (itemId == DemonicPactsItemIds.SOUL_SHARD) return
        if (player.inventory.contains(itemId, 1) || player.bank.contains(itemId, 1)) return
        val item = Item(itemId)
        if (!player.inventory.isFull) player.inventory.add(item) else player.bank.add(item)
    }
}

/** Utility-item adaptations for item-driven Demonic Pacts relics. */
class DemonicPactsItemListener : InteractionListener {
    override fun defineListeners() {
        on(DemonicPactsItemIds.BANKERS_BRIEFCASE, IntType.ITEM, "activate") { player, _ ->
            if (LeagueRuntime.hasRelic(player, DemonicPactsRelics.BANK_HEIST)) {
                openDialogue(player, RelicTeleportDialogue("Bank Heist", bankDestinations))
            }
            true
        }
        on(DemonicPactsItemIds.EVIL_EYE, IntType.ITEM, "activate") { player, _ ->
            if (LeagueRuntime.hasRelic(player, DemonicPactsRelics.EVIL_EYE)) {
                openDialogue(player, RelicTeleportDialogue("Evil Eye", bossDestinations))
            }
            true
        }
        on(DemonicPactsItemIds.MAP_OF_ALACRITY, IntType.ITEM, "activate") { player, _ ->
            if (LeagueRuntime.hasRelic(player, DemonicPactsRelics.MAP_OF_ALACRITY)) {
                openDialogue(player, RelicTeleportDialogue("Map of Alacrity", agilityDestinations))
            }
            true
        }
        on(DemonicPactsItemIds.FAIRY_MUSHROOM, IntType.ITEM, "activate") { player, _ ->
            if (LeagueRuntime.hasRelic(player, DemonicPactsRelics.NATURES_ACCORD)) {
                openDialogue(player, RelicTeleportDialogue("Fairy Mushroom", natureDestinations))
            }
            true
        }
        on(DemonicPactsItemIds.ARCANE_GRIMOIRE, IntType.ITEM, "activate") { player, _ ->
            if (LeagueRuntime.hasRelic(player, DemonicPactsRelics.GRIMOIRE)) {
                openDialogue(player, GrimoireDialogue())
            }
            true
        }
        on(DemonicPactsItemIds.SOUL_SHARD, IntType.ITEM, "sacrifice") { player, _ ->
            if (!LeagueRuntime.hasRelic(player, DemonicPactsRelics.SOUL_HARVEST)) return@on true
            if (player.inventory.remove(Item(DemonicPactsItemIds.SOUL_SHARD, 100))) {
                player.skills.addExperience(Skills.PRAYER, 550.0, false)
                player.sendMessage("You sacrifice 100 soul shards.")
            } else {
                player.sendMessage("You need 100 soul shards to make a sacrifice.")
            }
            true
        }
        on(DemonicPactsItemIds.FLASK_OF_FERVOUR, IntType.ITEM, "activate") { player, _ ->
            if (!LeagueRuntime.hasRelic(player, DemonicPactsRelics.FLASK_OF_FERVOUR)) return@on true
            val now = GameWorld.ticks
            val ready = player.getAttribute("league:flask-ready", 0)
            if (now < ready) {
                player.sendMessage("The Flask of Fervour is still recharging.")
                return@on true
            }
            player.skills.setLifepoints(player.skills.maximumLifepoints)
            player.skills.setPrayerPoints(player.skills.getStaticLevel(Skills.PRAYER).toDouble())
            player.settings.setSpecialEnergy(100)
            player.setAttribute("league:flask-ready", now + 300)
            player.sendMessage("The Flask of Fervour restores your combat resources.")
            true
        }
        on(DemonicPactsItemIds.TRANSMUTATION_LEDGER, IntType.ITEM, "activate") { player, _ ->
            player.sendMessage("The ledger's 2009Scape transmutation recipes are not active yet.")
            true
        }
        on(DemonicPactsItemIds.BUTLERS_BELL, IntType.ITEM, "activate") { player, _ ->
            player.sendMessage("The demon butler's offline-job adaptation is not active yet.")
            true
        }
        on(DemonicPactsItemIds.MINION_WHISTLE, IntType.ITEM, "activate") { player, _ ->
            player.sendMessage("The minion combat-helper adaptation is not active yet.")
            true
        }
    }

    companion object {
        private val bankDestinations = arrayOf(
            "Varrock" to Location.create(3185, 3436, 0),
            "Falador" to Location.create(2946, 3368, 0),
            "Edgeville" to Location.create(3093, 3493, 0),
            "Catherby" to Location.create(2809, 3441, 0),
            "Ardougne" to Location.create(2655, 3283, 0)
        )
        private val bossDestinations = arrayOf(
            "Barrows" to Location.create(3565, 3314, 0),
            "God Wars" to Location.create(2882, 5311, 2),
            "Kalphite Queen" to Location.create(3507, 9494, 0),
            "King Black Dragon" to Location.create(3067, 10253, 0),
            "Dagannoth Kings" to Location.create(2900, 4449, 0)
        )
        private val agilityDestinations = arrayOf(
            "Gnome course" to Location.create(2474, 3436, 0),
            "Barbarian course" to Location.create(2552, 3555, 0),
            "Brimhaven arena" to Location.create(2809, 3192, 0),
            "Wilderness course" to Location.create(3004, 3934, 0),
            "Ape Atoll course" to Location.create(2755, 2742, 0)
        )
        private val natureDestinations = arrayOf(
            "Zanaris" to Location.create(2412, 4434, 0),
            "Gnome Village" to Location.create(2542, 3170, 0),
            "Gnome Stronghold" to Location.create(2461, 3444, 0),
            "Grand Exchange" to Location.create(3185, 3508, 0),
            "Mobilising Armies" to Location.create(2419, 2845, 0)
        )
    }
}

class RelicTeleportDialogue(
    private val title: String,
    private val destinations: Array<Pair<String, Location>>
) : DialogueFile() {
    override fun handle(componentID: Int, buttonID: Int) {
        val p = player ?: return
        when (stage) {
            0 -> {
                interpreter!!.sendOptions(title, *destinations.map { it.first }.toTypedArray())
                stage = 1
            }
            1 -> {
                val destination = destinations.getOrNull(buttonID - 1)?.second ?: return end()
                end()
                p.teleporter.send(destination, TeleportManager.TeleportType.NORMAL, -1)
            }
        }
    }
}

class GrimoireDialogue : DialogueFile() {
    override fun handle(componentID: Int, buttonID: Int) {
        val p = player ?: return
        when (stage) {
            0 -> {
                interpreter!!.sendOptions("Arcane grimoire", "Standard", "Ancient Magicks", "Lunar", "Never mind")
                stage = 1
            }
            1 -> {
                val book = when (buttonID) {
                    1 -> SpellBook.MODERN
                    2 -> SpellBook.ANCIENT
                    3 -> SpellBook.LUNAR
                    else -> return end()
                }
                p.spellBookManager.setSpellBook(book)
                p.spellBookManager.update(p)
                p.sendMessage("The grimoire shifts your spellbook.")
                end()
            }
        }
    }
}
