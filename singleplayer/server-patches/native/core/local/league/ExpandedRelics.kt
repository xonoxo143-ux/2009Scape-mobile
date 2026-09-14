package core.local.league

import core.api.openBankAccount
import core.cache.def.impl.ItemDefinition
import core.game.event.Event
import core.game.event.NPCKillEvent
import core.game.event.TickEvent
import core.game.event.XPGainEvent
import core.game.interaction.InteractionListener
import core.game.interaction.IntType
import core.game.node.entity.player.Player
import core.game.node.entity.skill.Skills
import core.game.node.item.Item
import core.local.LeagueRuntime
import kotlin.math.floor
import kotlin.math.min

/** Extra custom item IDs used by the expanded single-player League relic set. */
object ExpandedLeagueItemIds {
    const val BANKERS_NOTE = 65002
    const val BANKERS_NOTE_VISUAL_DONOR = 970 // Papyrus
}

/** Server-side definition and persistence helper for the portable banker relic item. */
object ExpandedLeagueItems {
    @JvmStatic
    fun installDefinitions() {
        val donor = ItemDefinition.forId(ExpandedLeagueItemIds.BANKERS_NOTE_VISUAL_DONOR)
        val custom = ItemDefinition()
        custom.id = ExpandedLeagueItemIds.BANKERS_NOTE
        custom.name = "Banker's Note"
        custom.examine = "A relic-bound note that opens your bank wherever you are."
        custom.interfaceModelId = donor.interfaceModelId
        custom.modelZoom = donor.modelZoom
        custom.modelRotationX = donor.modelRotationX
        custom.modelRotationY = donor.modelRotationY
        custom.modelOffset1 = donor.modelOffset1
        custom.modelOffset2 = donor.modelOffset2
        custom.isStackable = false
        custom.value = 0
        custom.isMembersOnly = false
        custom.options = arrayOf("bank", null, null, null, null)
        ItemDefinition.getDefinitions()[ExpandedLeagueItemIds.BANKERS_NOTE] = custom
        println("SINGLEPLAYER_LEAGUE: CUSTOM_ITEM_READY id=${ExpandedLeagueItemIds.BANKERS_NOTE}")
    }

    fun ensureBankersNote(player: Player) {
        val id = ExpandedLeagueItemIds.BANKERS_NOTE
        if (player.inventory.contains(id, 1) || player.bank.contains(id, 1)) return
        val item = Item(id)
        if (!player.inventory.isFull) player.inventory.add(item) else player.bank.add(item)
        player.sendMessage("Your ${item.name} has been restored.")
    }

    fun removeBankersNote(player: Player) {
        val id = ExpandedLeagueItemIds.BANKERS_NOTE
        player.inventory.remove(Item(id))
        player.bank.remove(Item(id))
    }
}

/**
 * The original wider relic roadmap resumed after the four-relic framework probe.
 *
 * Tier 1 intentionally has one open slot: the third gathering relic was never
 * settled, so it is not fabricated here. Voidwalker is retained as a legacy
 * utility relic in tier 4 instead of displacing a gathering choice.
 */
object ExpandedRelics {
    const val TRICKSTER = "trickster"
    const val BANKERS_NOTE = "bankers_note"
    const val EQUILIBRIUM = "equilibrium"
    const val SLAYER_MASTER = "slayer_master"
    const val COMBAT_SPECIALIST = "combat_specialist"

    private const val BONUS_XP_GUARD = "league:expanded-bonus-xp-guard"
    private const val ENDLESS_HARVEST_XP_GUARD = "league:endless-harvest-xp-guard"

    @Volatile private var registered = false

    @JvmStatic
    @Synchronized
    fun registerAll() {
        if (registered) return
        registered = true
        ExpandedLeagueItems.installDefinitions()
        LeagueRelics.register(TricksterEffect)
        LeagueRelics.register(BankersNoteEffect)
        LeagueRelics.register(EquilibriumEffect)
        LeagueRelics.register(SlayerMasterEffect)
        LeagueRelics.register(CombatSpecialistEffect)
    }

    private object TricksterEffect : LeagueRelicEffect {
        override val id = TRICKSTER
        override val name = "Trickster"
        override val description = "Gain double Agility, Thieving and Hunter XP."
        override val tier = 1

        override fun onEvent(player: Player, event: Event) {
            if (event !is XPGainEvent || isGeneratedBonusXp(player)) return
            if (event.skillId !in intArrayOf(Skills.AGILITY, Skills.THIEVING, Skills.HUNTER)) return
            addBonusXp(player, event.skillId, event.amount)
        }
    }

    private object BankersNoteEffect : LeagueRelicEffect {
        override val id = BANKERS_NOTE
        override val name = "Banker's Note"
        override val description = "Gain a permanent Banker's Note that opens your bank from almost anywhere."
        override val tier = 2

        override fun onAttach(player: Player) {
            ExpandedLeagueItems.ensureBankersNote(player)
        }

        override fun onEvent(player: Player, event: Event) {
            if (event is TickEvent && event.worldTicks % 50 == 0) {
                ExpandedLeagueItems.ensureBankersNote(player)
            }
        }
    }

    private object EquilibriumEffect : LeagueRelicEffect {
        override val id = EQUILIBRIUM
        override val name = "Equilibrium"
        override val description = "Each XP drop gains bonus XP equal to 10% of total level, or 20% when training a lowest-level skill."
        override val tier = 2

        override fun onEvent(player: Player, event: Event) {
            if (event !is XPGainEvent || event.amount <= 0.0 || isGeneratedBonusXp(player)) return
            if (event.skillId !in 0 until Skills.NUM_SKILLS) return

            var lowest = Int.MAX_VALUE
            for (skill in 0 until Skills.NUM_SKILLS) {
                lowest = min(lowest, player.skills.getStaticLevel(skill))
            }
            val rate = if (player.skills.getStaticLevel(event.skillId) == lowest) 0.20 else 0.10
            val bonus = floor(player.skills.totalLevel * rate)
            if (bonus > 0.0) addBonusXp(player, event.skillId, bonus)
        }
    }

    private object SlayerMasterEffect : LeagueRelicEffect {
        override val id = SLAYER_MASTER
        override val name = "Slayer Master"
        override val description = "Every monster kill grants Slayer XP equal to its maximum lifepoints; assigned-task kills still keep their normal reward."
        override val tier = 3

        override fun onEvent(player: Player, event: Event) {
            if (event !is NPCKillEvent) return
            val xp = event.npc.skills.maximumLifepoints.toDouble()
            if (xp > 0.0) addBonusXp(player, Skills.SLAYER, xp)
        }
    }

    private object CombatSpecialistEffect : LeagueRelicEffect {
        override val id = COMBAT_SPECIALIST
        override val name = "Combat Specialist"
        override val description = "Gain double core combat XP and restore 10% special-attack energy every 10 game ticks."
        override val tier = 3

        private val combatSkills = intArrayOf(
            Skills.ATTACK,
            Skills.DEFENCE,
            Skills.STRENGTH,
            Skills.HITPOINTS,
            Skills.RANGE,
            Skills.MAGIC
        )

        override fun onEvent(player: Player, event: Event) {
            when (event) {
                is XPGainEvent -> {
                    if (event.amount <= 0.0 || isGeneratedBonusXp(player)) return
                    if (event.skillId in combatSkills) addBonusXp(player, event.skillId, event.amount)
                }
                is TickEvent -> {
                    if (event.worldTicks % 10 != 0) return
                    val current = player.settings.specialEnergy
                    if (current < 100) player.settings.setSpecialEnergy(min(100, current + 10))
                }
            }
        }
    }

    private fun isGeneratedBonusXp(player: Player): Boolean =
        player.getAttribute(BONUS_XP_GUARD, false) ||
            player.getAttribute(ENDLESS_HARVEST_XP_GUARD, false)

    /** Add relic-generated XP without allowing the XP relics to recursively amplify one another. */
    private fun addBonusXp(player: Player, skillId: Int, amount: Double) {
        if (amount <= 0.0 || player.getAttribute(BONUS_XP_GUARD, false)) return
        val endlessWasSet = player.getAttribute(ENDLESS_HARVEST_XP_GUARD, false)
        player.setAttribute(BONUS_XP_GUARD, true)
        if (!endlessWasSet) player.setAttribute(ENDLESS_HARVEST_XP_GUARD, true)
        try {
            player.skills.addExperience(skillId, amount, false)
        } finally {
            player.removeAttribute(BONUS_XP_GUARD)
            if (!endlessWasSet) player.removeAttribute(ENDLESS_HARVEST_XP_GUARD)
        }
    }
}

/** Inventory action for the portable banker relic item. */
class ExpandedRelicItemListener : InteractionListener {
    override fun defineListeners() {
        on(ExpandedLeagueItemIds.BANKERS_NOTE, IntType.ITEM, "bank") { player, _ ->
            if (!LeagueRuntime.hasRelic(player, ExpandedRelics.BANKERS_NOTE)) {
                player.sendMessage("You have not selected Banker's Note.")
                return@on true
            }
            openBankAccount(player)
            true
        }
    }
}
