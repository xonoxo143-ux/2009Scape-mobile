package core.local.league

import content.global.skill.fishing.Fish
import content.global.skill.gather.mining.MiningNode
import content.global.skill.gather.woodcutting.WoodcuttingNode
import core.game.event.Event
import core.game.event.NPCKillEvent
import core.game.event.ResourceProducedEvent
import core.game.event.TickEvent
import core.game.event.XPGainEvent
import core.game.node.entity.player.Player
import core.game.node.entity.skill.Skills
import core.game.node.item.Item
import core.local.LeagueRuntime
import core.tools.RandomFunction
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Demonic Pacts League relic board adapted to the retained 2009Scape engine.
 * The names and tiers follow the 2026 OSRS League. Mechanics which depend on
 * post-2009 content are represented by the closest retained 2009Scape system.
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

    @Volatile
    private var registered = false

    @JvmStatic
    @Synchronized
    fun registerAll() {
        if (registered) return
        registered = true
        DemonicPactsItems.installDefinitions()

        register(
            ENDLESS_HARVEST,
            "Endless Harvest",
            "2x Mining, Fishing and Woodcutting resources and XP; resources go straight to bank and trees/ores never deplete.",
            1,
            onEvent = ::endlessHarvestEvent
        )
        register(
            BARBARIAN_GATHERING,
            "Barbarian Gathering",
            "Gather without normal tool restrictions where supported, gain a second 50% success roll, and gain Strength and Agility XP while gathering.",
            1,
            itemId = DemonicPactsItemIds.KNAPSACK,
            onEvent = ::barbarianGatheringEvent
        )
        register(
            ABUNDANCE,
            "Abundance",
            "+10 to non-combat skills, +2 base XP on every XP drop, and coins equal to 2x XP gained.",
            1,
            onAttach = ::applyAbundanceBoost,
            onDetach = ::removeAbundanceBoost,
            onEvent = ::abundanceEvent
        )

        register(
            HOTFOOT,
            "Hotfoot",
            "Movement grants Agility XP; caught fish auto-cook and supported mined ores auto-smelt.",
            2,
            itemId = DemonicPactsItemIds.SEARING_BOOTS,
            onEvent = ::hotfootEvent
        )
        register(
            FRIENDLY_FORAGER,
            "Friendly Forager",
            "Gathering can find usable grimy herbs and grants Herblore XP; supported Herblore production is accelerated.",
            2,
            itemId = DemonicPactsItemIds.FORAGERS_POUCH,
            onEvent = ::friendlyForagerEvent
        )
        register(
            WOODSMAN,
            "Woodsman",
            "Chopped logs auto-burn for Firemaking XP; supported Fletching is accelerated and Hunter receives League-style support.",
            2
        )

        register(
            BANK_HEIST,
            "Bank Heist",
            "Gain a Banker's briefcase that teleports to major banks and bank chests across 2009Scape.",
            3,
            itemId = DemonicPactsItemIds.BANKERS_BRIEFCASE
        )
        register(
            EVIL_EYE,
            "Evil Eye",
            "Gain an Evil eye that teleports to major boss and dungeon entrances.",
            3,
            itemId = DemonicPactsItemIds.EVIL_EYE
        )
        register(
            MAP_OF_ALACRITY,
            "Map of Alacrity",
            "Gain a map that teleports to useful Agility courses and shortcut hubs.",
            3,
            itemId = DemonicPactsItemIds.MAP_OF_ALACRITY
        )

        register(
            TRANSMUTATION,
            "Transmutation",
            "Gain a transmutation ledger for converting common 2009Scape resources along progression chains.",
            4,
            itemId = DemonicPactsItemIds.TRANSMUTATION_LEDGER
        )
        register(
            CONNIVING_CLUES,
            "Conniving Clues",
            "Clue acquisition and completion are accelerated using the retained 2009Scape clue system.",
            4
        )
        register(
            BUTLERS_BELL,
            "Butler's Bell",
            "Gain a Butler's bell for passive single-player resource work and basic processing.",
            4,
            itemId = DemonicPactsItemIds.BUTLERS_BELL
        )

        register(
            NATURES_ACCORD,
            "Nature's Accord",
            "Farming is accelerated with stronger protection/yield support and a fairy-mushroom travel tool.",
            5,
            itemId = DemonicPactsItemIds.FAIRY_MUSHROOM
        )
        register(
            LARCENIST,
            "Larcenist",
            "Thieving receives guaranteed-success, repeat and loot support where the retained handlers permit it.",
            5
        )
        register(
            SOUL_HARVEST,
            "Soul Harvest",
            "Kills generate stackable soul shards; sacrifice 100 shards for 550 Prayer XP.",
            5,
            onEvent = ::soulHarvestEvent
        )

        register(
            GRIMOIRE,
            "Grimoire",
            "Gain an Arcane grimoire that swaps between Standard, Ancient and Lunar spellbooks.",
            6,
            itemId = DemonicPactsItemIds.ARCANE_GRIMOIRE
        )
        register(
            CULLING_SPREE,
            "Culling Spree",
            "Slayer receives flexible/accelerated task support and stronger retained Slayer utility.",
            6
        )
        register(
            ETERNAL_SUSTENANCE,
            "Eternal Sustenance",
            "Food is not consumed when eaten.",
            6
        )

        register(
            FLOW_STATE,
            "Flow State",
            "Supported gathering and production actions are reduced toward a two-tick cycle.",
            7
        )
        register(
            RELOADED,
            "Reloaded",
            "Choose one additional relic from any lower tier.",
            7
        )

        register(
            EXECUTIONER,
            "Executioner",
            "Gain the Sage's axe; eligible 2009Scape NPC targets can be executed below the adapted health threshold.",
            8,
            itemId = DemonicPactsItemIds.SAGES_AXE
        )
        register(
            MINION,
            "Minion",
            "Gain a Minion whistle for an adapted single-player combat helper and loot assistance.",
            8,
            itemId = DemonicPactsItemIds.MINION_WHISTLE
        )
        register(
            FLASK_OF_FERVOUR,
            "Flask of Fervour",
            "Gain a reusable flask that restores Hitpoints, Prayer and special attack energy on a cooldown.",
            8,
            itemId = DemonicPactsItemIds.FLASK_OF_FERVOUR
        )
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

    /** Recompute the League-wide XP rate from the highest selected tier. */
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

    /** Tier-3 passive: combat, Hitpoints and Prayer XP receive another +50%. */
    @JvmStatic
    fun onGlobalEvent(player: Player, event: Event) {
        if (event !is XPGainEvent || player.getAttribute(BONUS_XP_GUARD, false)) return
        val highestTier = LeagueRelics.selectedDefinitions(player).maxOfOrNull { it.tier } ?: 0
        if (highestTier < 3) return
        if (!(player.skills.isCombat(event.skillId)
                    || event.skillId == Skills.PRAYER
                    || event.skillId == Skills.HITPOINTS)) return
        addDisplayedBonusXp(player, event.skillId, event.amount * 0.5)
    }

    private fun endlessHarvestEvent(player: Player, event: Event) {
        if (event !is XPGainEvent
            || event.skillId !in gatheringSkills
            || player.getAttribute(BONUS_XP_GUARD, false)) return
        addDisplayedBonusXp(player, event.skillId, event.amount)
    }

    private fun barbarianGatheringEvent(player: Player, event: Event) {
        if (event !is XPGainEvent
            || event.skillId !in gatheringSkills
            || player.getAttribute(BONUS_XP_GUARD, false)) return
        addDisplayedBonusXp(player, Skills.STRENGTH, event.amount * 0.10)
        addDisplayedBonusXp(player, Skills.AGILITY, event.amount * 0.10)
    }

    private fun abundanceEvent(player: Player, event: Event) {
        when (event) {
            is XPGainEvent -> {
                if (player.getAttribute(BONUS_XP_GUARD, false)) return
                player.setAttribute(BONUS_XP_GUARD, true)
                try {
                    // +2 raw/base XP; the global League multiplier is applied by Skills.
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
            if (player.skills.isCombat(skill) || skill == Skills.HITPOINTS || skill == Skills.PRAYER) continue
            val target = min(120, player.skills.getStaticLevel(skill) + 10)
            if (player.skills.getLevel(skill) < target) player.skills.setLevel(skill, target)
        }
        player.setAttribute(ABUNDANCE_BOOST, true)
    }

    private fun removeAbundanceBoost(player: Player) {
        if (!player.getAttribute(ABUNDANCE_BOOST, false)) return
        for (skill in 0 until Skills.NUM_SKILLS) {
            if (player.skills.isCombat(skill) || skill == Skills.HITPOINTS || skill == Skills.PRAYER) continue
            val staticLevel = player.skills.getStaticLevel(skill)
            if (player.skills.getLevel(skill) <= staticLevel + 10) player.skills.setLevel(skill, staticLevel)
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
            Triple(199, 3, 2.5),
            Triple(201, 5, 3.8),
            Triple(203, 11, 5.0),
            Triple(205, 20, 6.3),
            Triple(207, 25, 7.5),
            Triple(209, 40, 8.8),
            Triple(211, 48, 10.0),
            Triple(213, 54, 11.3),
            Triple(215, 65, 12.5),
            Triple(217, 70, 13.8),
            Triple(219, 75, 15.0)
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
