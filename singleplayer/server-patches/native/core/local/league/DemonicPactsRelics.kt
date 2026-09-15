package core.local.league

import content.global.skill.fishing.Fish
import content.global.skill.gather.mining.MiningNode
import content.global.skill.gather.woodcutting.WoodcuttingNode
import core.cache.def.impl.ItemDefinition
import core.game.dialogue.DialogueFile
import core.game.event.Event
import core.game.event.NPCKillEvent
import core.game.event.ResourceProducedEvent
import core.game.event.TickEvent
import core.game.event.XPGainEvent
import core.game.interaction.InteractionListener
import core.game.interaction.IntType
import core.game.node.entity.player.Player
import core.game.node.entity.player.link.SpellBookManager.SpellBook
import core.game.node.entity.player.link.TeleportManager
import core.game.node.entity.skill.Skills
import core.game.node.item.Item
import core.game.world.map.Location
import core.local.LeagueRuntime
import core.tools.RandomFunction
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Demonic Pacts League relic board adapted to the retained 2009Scape engine.
 * Names/tiering follow the 2026 OSRS League; effects that rely on content which
 * does not exist in revision 530 are mapped to the closest retained mechanic.
 */
object DemonicPactsRelics {
    const val ENDLESS_HARVEST = "endless_harvest"
    const val BARBARIAN_GATHERING = "barbarian_gathering"
    const val ABUNDANCE = "abundance"

    const val HOTFOOT = "hotfoot"
    const val FRIENDLY_FORAGER = "friendly_forager"
    const val WOODSMAN = "woodsman"

    const val BANK_HEIST = "bank_heist"
    const val EVIL_EYE = "evil_eye"
    const val MAP_OF_ALACRITY = "map_of_alacrity"

    const val TRANSMUTATION = "transmutation"
    const val CONNIVING_CLUES = "conniving_clues"
    const val BUTLERS_BELL = "butlers_bell"

    const val NATURES_ACCORD = "natures_accord"
    const val LARCENIST = "larcenist"
    const val SOUL_HARVEST = "soul_harvest"

    const val GRIMOIRE = "grimoire"
    const val CULLING_SPREE = "culling_spree"
    const val ETERNAL_SUSTENANCE = "eternal_sustenance"

    const val FLOW_STATE = "flow_state"
    const val RELOADED = "reloaded"

    const val EXECUTIONER = "executioner"
    const val MINION = "minion"
    const val FLASK_OF_FERVOUR = "flask_of_fervour"

    private const val BONUS_XP_GUARD = "league:demonic-bonus-xp"
    private const val ABUNDANCE_BOOST = "league:abundance-boosted"
    private const val HOTFOOT_LAST_TILE = "league:hotfoot-last-tile"

    private val gatheringSkills = intArrayOf(Skills.MINING, Skills.FISHING, Skills.WOODCUTTING)
    private val gatheringResources: Set<Int> by lazy {
        buildSet {
            WoodcuttingNode.values().mapTo(this) { it.reward }
            MiningNode.values().mapTo(this) { it.reward }
            Fish.values().mapTo(this) { it.id }
            removeIf { it <= 0 }
        }
    }

    @Volatile private var registered = false

    @JvmStatic
    @Synchronized
    fun registerAll() {
        if (registered) return
        registered = true
        DemonicPactsItems.installDefinitions()

        register(ENDLESS_HARVEST, "Endless Harvest",
            "2x Mining, Fishing and Woodcutting resources and XP; resources can go straight to bank and nodes never deplete.", 1,
            onEvent = ::endlessHarvestEvent)
        register(BARBARIAN_GATHERING, "Barbarian Gathering",
            "Gather without tools or bait, gain a second 50% success roll, and gain 10% Strength and Agility XP while gathering.", 1,
            onEvent = ::barbarianGatheringEvent)
        register(ABUNDANCE, "Abundance",
            "+10 to non-combat skills, +2 base XP on every XP drop, and coins equal to 2x XP gained.", 1,
            onAttach = ::applyAbundanceBoost,
            onDetach = ::removeAbundanceBoost,
            onEvent = ::abundanceEvent)

        register(HOTFOOT, "Hotfoot",
            "Searing-boots effect: movement grants Agility XP; caught fish auto-cook, mined ores auto-smelt, and Cooking cannot fail.", 2,
            itemId = DemonicPactsItemIds.SEARING_BOOTS,
            onEvent = ::hotfootEvent)
        register(FRIENDLY_FORAGER, "Friendly Forager",
            "Gathering finds usable grimy herbs and grants Herblore XP; Herblore production is accelerated.", 2,
            itemId = DemonicPactsItemIds.FORAGERS_POUCH,
            onEvent = ::friendlyForagerEvent)
        register(WOODSMAN, "Woodsman",
            "Chopped logs auto-burn for Firemaking XP, Fletching is accelerated, and Hunter receives the League-style success/loot boost.", 2)

        register(BANK_HEIST, "Bank Heist",
            "Gain a banker's briefcase that teleports to major banks and bank chests across 2009Scape.", 3,
            itemId = DemonicPactsItemIds.BANKERS_BRIEFCASE)
        register(EVIL_EYE, "Evil Eye",
            "Gain an evil eye that teleports to major boss and dungeon entrances.", 3,
            itemId = DemonicPactsItemIds.EVIL_EYE)
        register(MAP_OF_ALACRITY, "Map of Alacrity",
            "Gain a map that teleports to useful Agility courses and shortcut hubs.", 3,
            itemId = DemonicPactsItemIds.MAP_OF_ALACRITY)

        register(TRANSMUTATION, "Transmutation",
            "Gain a transmutation ledger for converting common resources up or down their progression chains.", 4,
            itemId = DemonicPactsItemIds.TRANSMUTATION_LEDGER)
        register(CONNIVING_CLUES, "Conniving Clues",
            "Clue acquisition and completion are heavily accelerated; clue rewards favour the shortest retained clue path.", 4)
        register(BUTLERS_BELL, "Butler's Bell",
            "Gain a Butler's bell for passive/offline resource work and basic processing.", 4,
            itemId = DemonicPactsItemIds.BUTLERS_BELL)

        register(NATURES_ACCORD, "Nature's Accord",
            "Farming is massively accelerated with protected crops, boosted yield, seed saving and a fairy-mushroom travel tool.", 5,
            itemId = DemonicPactsItemIds.FAIRY_MUSHROOM)
        register(LARCENIST, "Larcenist",
            "Thieving succeeds reliably, repeats automatically where supported, and produces greatly increased loot.", 5)
        register(SOUL_HARVEST, "Soul Harvest",
            "Kills and harvesting generate stackable soul shards that can be sacrificed for Prayer XP.", 5,
            itemId = DemonicPactsItemIds.SOUL_SHARD,
            onEvent = ::soulHarvestEvent)

        register(GRIMOIRE, "Grimoire",
            "Gain an arcane grimoire that freely swaps between the standard, Ancient and Lunar spellbooks.", 6,
            itemId = DemonicPactsItemIds.ARCANE_GRIMOIRE)
        register(CULLING_SPREE, "Culling Spree",
            "Slayer receives selectable/accelerated task support, stronger superior behaviour and passive slayer-helmet utility where supported.", 6)
        register(ETERNAL_SUSTENANCE, "Eternal Sustenance",
            "Food is no longer consumed when eaten.", 6)

        register(FLOW_STATE, "Flow State",
            "Core gathering and supported production actions are reduced toward a 2-tick cycle.", 7)
        register(RELOADED, "Reloaded",
            "Choose one additional relic from any lower tier.", 7)

        register(EXECUTIONER, "Executioner",
            "Gain the Sage's axe; its 2009Scape adaptation executes eligible NPC targets below 20% life when supported.", 8,
            itemId = DemonicPactsItemIds.SAGES_AXE)
        register(MINION, "Minion",
            "Gain a minion whistle for a temporary combat helper and automatic loot assistance.", 8,
            itemId = DemonicPactsItemIds.MINION_WHISTLE)
        register(FLASK_OF_FERVOUR, "Flask of Fervour",
            "Gain a reusable flask that restores Hitpoints, Prayer and special attack energy, with a cooldown.", 8,
            itemId = DemonicPactsItemIds.FLASK_OF_FERVOUR)
    }

    private fun register(
        id: String,
        name: String,
        description: String,
        tier: Int,
        itemId: Int? = null,
        onAttach: ((Player) -> Unit)? = null,
        onDetach: ((Player) -> Unit)? = null,
        onEvent: ((Player, Event) -> Unit)? = null
    ) {
        LeagueRelics.register(object : LeagueRelicEffect {
            override val id = id
            override val name = name
            override val description = description
            override val tier = tier
            override fun onAttach(player: Player) {
                if (itemId != null) DemonicPactsItems.ensure(player, itemId)
                onAttach?.invoke(player)
            }
            override fun onDetach(player: Player) {
                onDetach?.invoke(player)
            }
            override fun onEvent(player: Player, event: Event) {
                if (itemId != null && event is TickEvent && event.worldTicks % 50 == 0) {
                    DemonicPactsItems.ensure(player, itemId)
                }
                onEvent?.invoke(player, event)
            }
        })
    }

    /** Recompute the League-wide XP rate from the highest selected relic tier. */
    @JvmStatic
    fun refreshPassives(player: Player) {
        val highestTier = LeagueRelics.selectedDefinitions(player).maxOfOrNull { it.tier } ?: 1
        player.skills.experienceMultiplier = when {
            highestTier >= 6 -> 16.0
            highestTier >= 4 -> 12.0
            highestTier >= 2 -> 8.0
            else -> 5.0
        }
        if (LeagueRuntime.hasRelic(player, ABUNDANCE)) applyAbundanceBoost(player)
    }

    /** Tier-3 passive: combat/HP/Prayer XP is multiplied by a further 1.5x. */
    @JvmStatic
    fun onGlobalEvent(player: Player, event: Event) {
        if (event !is XPGainEvent || player.getAttribute(BONUS_XP_GUARD, false)) return
        val highestTier = LeagueRelics.selectedDefinitions(player).maxOfOrNull { it.tier } ?: 0
        if (highestTier < 3) return
        if (!(Skills.isCombat(event.skillId) || event.skillId == Skills.PRAYER || event.skillId == Skills.HITPOINTS)) return
        addDisplayedBonusXp(player, event.skillId, event.amount * 0.5)
    }

    private fun endlessHarvestEvent(player: Player, event: Event) {
        if (event !is XPGainEvent || event.skillId !in gatheringSkills || player.getAttribute(BONUS_XP_GUARD, false)) return
        addDisplayedBonusXp(player, event.skillId, event.amount)
    }

    private fun barbarianGatheringEvent(player: Player, event: Event) {
        if (event !is XPGainEvent || event.skillId !in gatheringSkills || player.getAttribute(BONUS_XP_GUARD, false)) return
        addDisplayedBonusXp(player, Skills.STRENGTH, event.amount * 0.10)
        addDisplayedBonusXp(player, Skills.AGILITY, event.amount * 0.10)
    }

    private fun abundanceEvent(player: Player, event: Event) {
        when (event) {
            is XPGainEvent -> {
                if (player.getAttribute(BONUS_XP_GUARD, false)) return
                // +2 base XP, then the normal League multiplier applies.
                player.setAttribute(BONUS_XP_GUARD, true)
                try {
                    player.skills.addExperience(event.skillId, 2.0, false)
                } finally {
                    player.removeAttribute(BONUS_XP_GUARD)
                }
                val coins = min(Int.MAX_VALUE.toDouble(), floor(event.amount * 2.0)).toInt()
                if (coins > 0) player.bank.add(Item(995, coins))
            }
            is TickEvent -> if (event.worldTicks % 25 == 0) applyAbundanceBoost(player)
        }
    }

    private fun applyAbundanceBoost(player: Player) {
        for (skill in 0 until Skills.NUM_SKILLS) {
            if (Skills.isCombat(skill) || skill == Skills.HITPOINTS || skill == Skills.PRAYER) continue
            val target = min(120, player.skills.getStaticLevel(skill) + 10)
            if (player.skills.getLevel(skill) < target) player.skills.setLevel(skill, target)
        }
        player.setAttribute(ABUNDANCE_BOOST, true)
    }

    private fun removeAbundanceBoost(player: Player) {
        if (!player.getAttribute(ABUNDANCE_BOOST, false)) return
        for (skill in 0 until Skills.NUM_SKILLS) {
            if (Skills.isCombat(skill) || skill == Skills.HITPOINTS || skill == Skills.PRAYER) continue
            val static = player.skills.getStaticLevel(skill)
            if (player.skills.getLevel(skill) <= static + 10) player.skills.setLevel(skill, static)
        }
        player.removeAttribute(ABUNDANCE_BOOST)
    }

    private fun hotfootEvent(player: Player, event: Event) {
        if (event !is TickEvent) return
        val loc = player.location
        val encoded = "${loc.x},${loc.y},${loc.z}"
        val previous = player.getAttribute(HOTFOOT_LAST_TILE, "")
        player.setAttribute(HOTFOOT_LAST_TILE, encoded)
        if (previous.isBlank() || previous == encoded) return
        val level = player.skills.getStaticLevel(Skills.AGILITY)
        addRawBonusXp(player, Skills.AGILITY, max(1.0, level / 12.0))
    }

    private fun friendlyForagerEvent(player: Player, event: Event) {
        if (event !is ResourceProducedEvent || event.itemId !in gatheringResources) return
        // Keep the League feel without requiring OSRS's silk-lined pouch cache data.
        if (!RandomFunction.roll(3)) return
        val herb = randomUsableHerb(player.skills.getStaticLevel(Skills.HERBLORE) + 25) ?: return
        player.bank.add(Item(herb.first, 1))
        addRawBonusXp(player, Skills.HERBLORE, herb.second / 4.0)
    }

    private fun soulHarvestEvent(player: Player, event: Event) {
        if (event !is NPCKillEvent) return
        val shards = max(1, event.npc.skills.maximumLifepoints / 40)
        player.inventory.add(Item(DemonicPactsItemIds.SOUL_SHARD, shards))
    }

    private fun randomUsableHerb(level: Int): Pair<Int, Double>? {
        val herbs = arrayOf(
            Triple(199, 3, 2.5), Triple(201, 5, 3.8), Triple(203, 11, 5.0),
            Triple(205, 20, 6.3), Triple(207, 25, 7.5), Triple(209, 40, 8.8),
            Triple(211, 48, 10.0), Triple(213, 54, 11.3), Triple(215, 65, 12.5),
            Triple(217, 70, 13.8), Triple(219, 75, 15.0)
        ).filter { level >= it.second }
        if (herbs.isEmpty()) return null
        val selected = herbs[RandomFunction.random(herbs.size)]
        return selected.first to selected.third
    }

    private fun addDisplayedBonusXp(player: Player, skill: Int, displayedAmount: Double) {
        val multiplier = max(1.0, player.skills.experienceMultiplier)
        addRawBonusXp(player, skill, displayedAmount / multiplier)
    }

    private fun addRawBonusXp(player: Player, skill: Int, rawAmount: Double) {
        if (rawAmount <= 0.0 || player.getAttribute(BONUS_XP_GUARD, false)) return
        player.setAttribute(BONUS_XP_GUARD, true)
        try {
            player.skills.addExperience(skill, rawAmount, false)
        } finally {
            player.removeAttribute(BONUS_XP_GUARD)
        }
    }
}

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

    fun removeAll(player: Player) {
        for (definition in definitions) {
            player.inventory.remove(Item(definition.id, Int.MAX_VALUE))
            player.bank.remove(Item(definition.id, Int.MAX_VALUE))
        }
    }
}

/** Active utility-item adaptations for relics whose original effect is item-driven. */
class DemonicPactsItemListener : InteractionListener {
    override fun defineListeners() {
        on(DemonicPactsItemIds.BANKERS_BRIEFCASE, IntType.ITEM, "activate") { player, _ ->
            if (!LeagueRuntime.hasRelic(player, DemonicPactsRelics.BANK_HEIST)) return@on true
            core.api.openDialogue(player, RelicTeleportDialogue("Bank Heist", bankDestinations))
            true
        }
        on(DemonicPactsItemIds.EVIL_EYE, IntType.ITEM, "activate") { player, _ ->
            if (!LeagueRuntime.hasRelic(player, DemonicPactsRelics.EVIL_EYE)) return@on true
            core.api.openDialogue(player, RelicTeleportDialogue("Evil Eye", bossDestinations))
            true
        }
        on(DemonicPactsItemIds.MAP_OF_ALACRITY, IntType.ITEM, "activate") { player, _ ->
            if (!LeagueRuntime.hasRelic(player, DemonicPactsRelics.MAP_OF_ALACRITY)) return@on true
            core.api.openDialogue(player, RelicTeleportDialogue("Map of Alacrity", agilityDestinations))
            true
        }
        on(DemonicPactsItemIds.FAIRY_MUSHROOM, IntType.ITEM, "activate") { player, _ ->
            if (!LeagueRuntime.hasRelic(player, DemonicPactsRelics.NATURES_ACCORD)) return@on true
            core.api.openDialogue(player, RelicTeleportDialogue("Fairy Mushroom", natureDestinations))
            true
        }
        on(DemonicPactsItemIds.ARCANE_GRIMOIRE, IntType.ITEM, "activate") { player, _ ->
            if (!LeagueRuntime.hasRelic(player, DemonicPactsRelics.GRIMOIRE)) return@on true
            core.api.openDialogue(player, GrimoireDialogue())
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
            val now = core.game.world.GameWorld.ticks
            val ready = player.getAttribute("league:flask-ready", 0)
            if (now < ready) {
                player.sendMessage("The Flask of Fervour is still recharging.")
                return@on true
            }
            player.skills.lifepoints = player.skills.maximumLifepoints
            player.skills.prayerPoints = player.skills.getStaticLevel(Skills.PRAYER).toDouble()
            player.settings.setSpecialEnergy(100)
            player.setAttribute("league:flask-ready", now + 300) // 3 minutes at 600ms/tick.
            player.sendMessage("The Flask of Fervour restores your combat resources.")
            true
        }
        on(DemonicPactsItemIds.TRANSMUTATION_LEDGER, IntType.ITEM, "activate") { player, _ ->
            player.sendMessage("Use the ledger from the League menu while transmutation recipes are being expanded.")
            true
        }
        on(DemonicPactsItemIds.BUTLERS_BELL, IntType.ITEM, "activate") { player, _ ->
            player.sendMessage("Your demon butler is ready; offline job selection is being adapted to 2009Scape resources.")
            true
        }
        on(DemonicPactsItemIds.MINION_WHISTLE, IntType.ITEM, "activate") { player, _ ->
            player.sendMessage("The minion whistle hums; the combat-helper adaptation is not active yet.")
            true
        }
    }

    companion object {
        val bankDestinations = arrayOf(
            "Varrock" to Location.create(3185, 3436, 0),
            "Falador" to Location.create(2946, 3368, 0),
            "Edgeville" to Location.create(3093, 3493, 0),
            "Catherby" to Location.create(2809, 3441, 0),
            "Ardougne" to Location.create(2655, 3283, 0)
        )
        val bossDestinations = arrayOf(
            "Barrows" to Location.create(3565, 3314, 0),
            "God Wars" to Location.create(2882, 5311, 2),
            "Kalphite Queen" to Location.create(3507, 9494, 0),
            "King Black Dragon" to Location.create(3067, 10253, 0),
            "Dagannoth Kings" to Location.create(2900, 4449, 0)
        )
        val agilityDestinations = arrayOf(
            "Gnome course" to Location.create(2474, 3436, 0),
            "Barbarian course" to Location.create(2552, 3555, 0),
            "Brimhaven arena" to Location.create(2809, 3192, 0),
            "Wilderness course" to Location.create(3004, 3934, 0),
            "Ape Atoll course" to Location.create(2755, 2742, 0)
        )
        val natureDestinations = arrayOf(
            "Zanaris" to Location.create(2412, 4434, 0),
            "Tree Gnome Village" to Location.create(2542, 3170, 0),
            "Tree Gnome Stronghold" to Location.create(2461, 3444, 0),
            "Grand Exchange tree" to Location.create(3185, 3508, 0),
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
