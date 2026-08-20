package content.global.leagues

import content.global.leagues.core.*
import core.api.Event as GameEvent
import core.api.LoginListener
import core.api.PersistPlayer
import core.api.addItemOrDrop
import core.game.event.*
import core.game.node.entity.Entity
import core.game.node.entity.player.Player
import core.game.node.entity.player.link.IronmanMode
import core.game.node.item.Item
import core.tools.RandomFunction
import org.json.simple.JSONObject

/**
 * Thin 2009Scape adapter around the deterministic Grand League core.
 * Gameplay systems emit existing server events; this manager normalizes them
 * into League signals and owns persistence/UI notifications only.
 */
class GrandLeagueManager(private val player: Player? = null) : LoginListener, PersistPlayer {
    var profile: LeagueProfile = LeagueProfile.fresh(active = false)
        private set

    override fun login(player: Player) {
        val instance = GrandLeagueManager(player)
        if (java.lang.Boolean.getBoolean("grandleague.mobile")) instance.profile.active = true
        player.setAttribute(ATTRIBUTE, instance)
        installHooks(player)
    }

    override fun savePlayer(player: Player, save: JSONObject) {
        save[SAVE_KEY] = LeagueProfileJson.write(getInstance(player).profile)
    }

    override fun parsePlayer(player: Player, data: JSONObject) {
        val leagueData = data[SAVE_KEY] as? JSONObject
        val loaded = LeagueProfileJson.read(leagueData)
        if (java.lang.Boolean.getBoolean("grandleague.mobile")) loaded.active = true
        getInstance(player).profile = loaded
    }

    fun enable(reset: Boolean = false) {
        if (reset) profile = LeagueProfile.fresh(active = true)
        else profile.active = true
    }

    fun disable() {
        profile.active = false
    }

    fun process(signal: LeagueSignal): LeagueSignalResult {
        val result = ENGINE.process(profile, signal)
        val rewards = REWARD_ENGINE.apply(profile, result)
        notifyPlayer(result, rewards)
        return result
    }

    fun resolvedEffects(): LeagueResolvedEffects = CONTENT.effects.resolve(profile)

    fun unlockContent(type: LeagueContentType, id: String): LeagueUnlockResult =
        LeagueContentUnlockEngine(CONTENT.contentUnlocks).unlock(profile, type, id)

    fun unlockRelic(relicId: String): LeagueUnlockResult = RelicEngine(CONTENT.relics).unlock(profile, relicId)

    fun selectRelic(relicId: String): LeagueUnlockResult = RelicEngine(CONTENT.relics).selectPrimary(profile, relicId)

    fun unlockRegion(regionId: String): LeagueUnlockResult = RegionEngine(CONTENT.regions).unlock(profile, regionId)

    fun equipFragment(fragmentId: String, slots: Int): LeagueUnlockResult =
        FragmentEngine(CONTENT.fragments).equip(profile, fragmentId, slots)

    fun rankMastery(masteryId: String): LeagueUnlockResult = MasteryEngine(CONTENT.masteries).rankUp(profile, masteryId)

    fun unlockPact(pactId: String): LeagueUnlockResult = PactEngine(CONTENT.pacts).unlock(profile, pactId)

    fun unlockEcho(echoId: String): LeagueUnlockResult = EchoEngine(CONTENT.echoes).unlock(profile, echoId)

    fun unlockBlessing(blessingId: String): LeagueUnlockResult = BlessingEngine(CONTENT.blessings).unlock(profile, blessingId)

    private fun notifyPlayer(result: LeagueSignalResult, rewards: List<LeagueCurrencyGrant>) {
        val player = player ?: return
        result.completions.forEach { completion ->
            player.sendMessage("<col=ff981f>League task complete: ${completion.task.name} (+${completion.pointsAwarded} points)</col>")
        }
        if (result.tierChanged) {
            player.sendMessage("<col=ff981f>Grand League: ${result.newTier.displayName} unlocked.</col>")
        }
        rewards.groupBy { it.currencyId }.forEach { (currency, grants) ->
            val total = grants.sumOf { it.amount }
            player.sendMessage("<col=ff981f>League reward: +$total ${currency.replace('_', ' ')}.</col>")
        }
    }

    companion object {
        const val ATTRIBUTE = "grand-league-manager"
        const val SAVE_KEY = "grandLeague"

        @JvmField
        val TASK_REGISTRY = LeagueTaskRegistry(GeneratedGrandLeagueTasks.tasks)

        @JvmField
        val ENGINE = LeagueEngine(TASK_REGISTRY)

        @JvmField
        val REWARD_ENGINE = LeagueRewardEngine(GrandLeagueRewardPolicy.policy, ENGINE.progression)

        @JvmField
        val CONTENT = GrandLeagueBootstrapContent

        @JvmStatic
        fun getInstance(player: Player): GrandLeagueManager =
            player.getAttribute(ATTRIBUTE, GrandLeagueManager(player))

        @JvmStatic
        fun experienceMultiplier(player: Player): Double {
            val manager = getInstance(player)
            if (!manager.profile.active) return 1.0
            return manager.resolvedEffects().numeric(LeagueModifierKey.XP_MULTIPLIER)
        }

        @JvmStatic
        fun modifier(player: Player, key: LeagueModifierKey): Double {
            val manager = getInstance(player)
            if (!manager.profile.active) return 1.0
            return manager.resolvedEffects().numeric(key)
        }

        @JvmStatic
        fun prayerDrainMultiplier(player: Player): Double = modifier(player, LeagueModifierKey.PRAYER_DRAIN_MULTIPLIER)

        @JvmStatic
        fun runEnergyDrainMultiplier(player: Player): Double = modifier(player, LeagueModifierKey.RUN_ENERGY_DRAIN_MULTIPLIER)

        @JvmStatic
        fun accuracyMultiplier(player: Player): Double = modifier(player, LeagueModifierKey.ACCURACY_MULTIPLIER)

        @JvmStatic
        fun damageMultiplier(player: Player): Double = modifier(player, LeagueModifierKey.DAMAGE_MULTIPLIER)

        @JvmStatic
        fun attackSpeedMultiplier(player: Player): Double = modifier(player, LeagueModifierKey.ATTACK_SPEED_MULTIPLIER)

        @JvmStatic
        fun skillLevelBoost(player: Player, skillId: Int): Int {
            val manager = getInstance(player)
            if (!manager.profile.active) return 0
            return manager.resolvedEffects().skillBoost(skillId)
        }

        @JvmStatic
        fun outputPlan(player: Player, baseAmount: Int, kind: LeagueOutputKind): LeagueOutputPlan {
            val manager = getInstance(player)
            if (!manager.profile.active) {
                return LeagueOutputPlan(
                    baseAmount = baseAmount,
                    guaranteedCopiesPerUnit = 1,
                    fractionalBonusChancePerUnit = 0.0
                )
            }
            return LeagueOutputPlanner.plan(baseAmount, kind, manager.resolvedEffects())
        }

        @JvmStatic
        fun resolveOutput(player: Player, baseAmount: Int, kind: LeagueOutputKind): LeagueResolvedOutput =
            outputPlan(player, baseAmount, kind).resolve { RandomFunction.randomDouble(1.0) }

        @JvmStatic
        fun autoBanksResources(player: Player): Boolean =
            outputPlan(player, 1, LeagueOutputKind.RESOURCE).autoBank

        /**
         * Deliver a resolved League output without duplicating reward logic in every skill.
         * Auto-bank respects Ultimate Ironman restrictions and falls back to inventory/ground
         * if both bank containers are full.
         *
         * @return true when the complete output was sent directly to a bank.
         */
        @JvmStatic
        fun deliverOutput(player: Player, itemId: Int, output: LeagueResolvedOutput): Boolean {
            if (output.amount <= 0) return true
            if (output.autoBank && player.ironmanManager.mode != IronmanMode.ULTIMATE) {
                val item = Item(itemId, output.amount)
                if (player.bankPrimary.add(item) || player.bankSecondary.add(item)) {
                    return true
                }
            }
            addItemOrDrop(player, itemId, output.amount)
            return false
        }

        /**
         * Deliver only League-created production bonus output. Ordinary products remain
         * under the production pulse's control so source-specific destinations and side
         * effects are preserved.
         */
        @JvmStatic
        fun deliverBonusOutput(player: Player, itemId: Int, output: LeagueResolvedOutput): Boolean =
            deliverBonusOutput(player, itemId, output, 1)

        @JvmStatic
        fun deliverBonusOutput(
            player: Player,
            itemId: Int,
            output: LeagueResolvedOutput,
            itemUnitsPerOutput: Int
        ): Boolean {
            require(itemUnitsPerOutput > 0) { "Item units per output must be positive" }
            val bonusItems = output.bonusAmount * itemUnitsPerOutput
            if (bonusItems <= 0) return true
            if (output.bonusAutoBank && player.ironmanManager.mode != IronmanMode.ULTIMATE) {
                val item = Item(itemId, bonusItems)
                if (player.bankPrimary.add(item) || player.bankSecondary.add(item)) {
                    return true
                }
            }
            addItemOrDrop(player, itemId, bonusItems)
            return false
        }

        private fun installHooks(player: Player) {
            hookMany<ResourceProducedEvent>(player, GameEvent.ResourceProduced) { event ->
                listOf(
                    ResourceProducedSignal(event.itemId, event.amount),
                    ItemObtainedSignal(event.itemId, event.amount)
                )
            }
            hook<NPCKillEvent>(player, GameEvent.NPCKilled) { event ->
                NpcKilledSignal(event.npc.id)
            }
            hook<XPGainEvent>(player, GameEvent.XpGained) { event ->
                XpGainedSignal(event.skillId, event.amount)
            }
            hookMany<StaticSkillLevelUpEvent>(player, GameEvent.StaticSkillLevelUp) { event ->
                listOf(
                    SkillLevelReachedSignal(event.skillId, event.newValue),
                    MetricValueSignal("total-level", player.skills.totalLevel.toLong())
                )
            }
            hookMany<QuestCompleteEvent>(player, GameEvent.QuestCompleted) { event ->
                listOf(
                    QuestCompletedSignal(event.quest.name.lowercase()),
                    MetricValueSignal("quest-points", player.questRepository.points.toLong())
                )
            }
            hook<ItemEquipEvent>(player, GameEvent.ItemEquipped) { event ->
                ItemEquippedSignal(event.itemId, event.slotId)
            }
            hook<PickUpEvent>(player, GameEvent.PickedUp) { event ->
                ItemObtainedSignal(event.itemId)
            }
            hook<ItemShopPurchaseEvent>(player, GameEvent.ItemPurchased) { event ->
                ItemObtainedSignal(event.itemId, event.amount)
            }
            hook<TeleportEvent>(player, GameEvent.Teleported) { event ->
                TeleportSignal("region:${event.location.regionId}", event.method.name.lowercase())
            }
        }

        private fun <T : Event> hook(player: Player, eventClass: Class<T>, signal: (T) -> LeagueSignal?) {
            player.hook(eventClass, object : EventHook<T> {
                override fun process(entity: Entity, event: T) {
                    if (entity !is Player) return
                    val normalized = signal(event) ?: return
                    getInstance(entity).process(normalized)
                }
            })
        }

        private fun <T : Event> hookMany(player: Player, eventClass: Class<T>, signals: (T) -> Iterable<LeagueSignal>) {
            player.hook(eventClass, object : EventHook<T> {
                override fun process(entity: Entity, event: T) {
                    if (entity !is Player) return
                    val manager = getInstance(entity)
                    signals(event).forEach(manager::process)
                }
            })
        }
    }
}
