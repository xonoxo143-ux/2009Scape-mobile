package content.global.leagues

import content.global.leagues.core.*
import core.api.*
import core.api.Event as GameEvent
import core.game.event.*
import core.game.node.entity.Entity
import core.game.node.entity.npc.NPC
import core.game.node.entity.player.Player
import core.game.node.item.Item
import org.json.simple.JSONObject

/** Thin 2009Scape adapter around the dependency-free Grand League domain engine. */
class GrandLeagueManager(private val player: Player? = null) : LoginListener, PersistPlayer {
    private var session: GrandLeagueSession = GrandLeagueSession()

    val profile: LeagueProfile
        get() = session.profile

    fun enable(reset: Boolean = false) = session.enable(reset)
    fun signal(signal: LeagueSignal): LeagueProgressUpdate = session.signal(signal)
    fun selectRelic(id: String) = session.selectRelic(id)
    fun unlockRegion(id: String) = session.unlockRegion(id)
    fun unlockFragment(id: String) = session.unlockFragment(id)
    fun equipFragment(id: String) = session.equipFragment(id)
    fun unequipFragment(id: String) = session.unequipFragment(id)
    fun modifiers(): LeagueModifierSnapshot = session.modifiers()
    fun unlockMastery(id: String) = session.unlockMastery(id)
    fun unlockPact(id: String) = session.unlockPact(id)
    fun recordEchoKill(id: String, difficulty: EchoDifficulty) = session.recordEchoKill(id, difficulty)
    fun activeFragmentSets(): Set<String> = session.activeFragmentSets()
    fun overview(): LeagueOverview = session.overview()
    fun taskViews(): List<LeagueTaskView> = session.taskViews()
    fun taskViews(filter: LeagueTaskFilter): List<LeagueTaskView> = session.taskViews(filter)

    override fun login(player: Player) {
        if (player.isArtificial) return
        val manager = GrandLeagueManager(player)
        setAttribute(player, ATTRIBUTE, manager)
        manager.installHooks(player)
    }

    override fun savePlayer(player: Player, save: JSONObject) {
        val manager = getInstance(player)
        setAttribute(player, SAVE_ATTRIBUTE, LeagueProfileCodec.encode(manager.profile))
    }

    override fun parsePlayer(player: Player, data: JSONObject) {
        val manager = getInstance(player)
        val raw = getAttribute(player, SAVE_KEY, "")
        if (raw.isBlank()) return
        manager.session = GrandLeagueSession(profile = LeagueProfileCodec.decode(raw))
    }

    private fun installHooks(player: Player) {
        hook<ResourceProducedEvent>(player, GameEvent.ResourceProduced) { event ->
            val actualAmount = when (event.activity) {
                ResourceActivity.GATHERING -> applyGatheringModifiers(player, event)
                ResourceActivity.PRODUCTION -> applyProductionModifiers(player, event)
                else -> event.amount
            }
            signal(LeagueSignal(LeagueSignalKind.RESOURCE, normalize(Item(event.itemId).name), actualAmount.toLong()))
            val name = Item(event.itemId).name.lowercase()
            if (name.contains("logs") || name == "logs") {
                signal(LeagueSignal(LeagueSignalKind.RESOURCE, "log", actualAmount.toLong()))
            }
            if (event.skill == ResourceSkill.FISHING || (event.source is NPC && name.startsWith("raw "))) {
                signal(LeagueSignal(LeagueSignalKind.RESOURCE, "fish", actualAmount.toLong()))
            }
        }
        hook<NPCKillEvent>(player, GameEvent.NPCKilled) { event ->
            val key = normalize(event.npc.name)
            signal(LeagueSignal(LeagueSignalKind.NPC_KILL, key))
        }
        hook<XPGainEvent>(player, GameEvent.XpGained) { event ->
            signal(LeagueSignal(LeagueSignalKind.XP, "skill-${event.skillId}", event.amount.toLong().coerceAtLeast(0)))
            signal(LeagueSignal(LeagueSignalKind.METRIC, "total-level", value = player.skills.totalLevel.toLong()))
        }
        hook<QuestCompleteEvent>(player, GameEvent.QuestCompleted) { event ->
            signal(LeagueSignal(LeagueSignalKind.QUEST, "complete"))
            signal(LeagueSignal(LeagueSignalKind.QUEST, event.questId))
            signal(LeagueSignal(LeagueSignalKind.METRIC, "quest-points", value = event.totalQuestPoints.toLong()))
        }
    }

    private fun applyGatheringModifiers(player: Player, event: ResourceProducedEvent): Int {
        if (event.activity != ResourceActivity.GATHERING || event.amount <= 0) return event.amount
        val scope = when (event.skill) {
            ResourceSkill.WOODCUTTING -> LeagueEffectScope.WOODCUTTING
            ResourceSkill.FISHING -> LeagueEffectScope.FISHING
            ResourceSkill.MINING -> LeagueEffectScope.MINING
            ResourceSkill.THIEVING -> LeagueEffectScope.THIEVING
            ResourceSkill.FARMING -> LeagueEffectScope.FARMING
            else -> return event.amount
        }
        val scopes = setOf(LeagueEffectScope.GATHERING, scope)
        val gathering = modifiers().gathering(scopes)
        val total = LeagueEffectMath.scaledQuantity(event.amount, gathering.resourceMultiplier, kotlin.random.Random.nextDouble())
        val bonus = (total - event.amount).coerceAtLeast(0)
        val autoBank = gathering.autoBankChance > 0.0 && kotlin.random.Random.nextDouble() < gathering.autoBankChance
        val autoProcess = gathering.autoProcessChance > 0.0 && kotlin.random.Random.nextDouble() < gathering.autoProcessChance

        if (autoProcess) {
            val recipe = LeagueGatheringProcessor.resolve(player, event)
            if (recipe != null && removeItem(player, Item(event.itemId, event.amount))) {
                player.skills.addExperience(recipe.secondarySkillId, recipe.xpPerItem * total)
                val output = recipe.outputItemId
                if (output != null) {
                    if (autoBank) {
                        bankOrFallback(player, output, total)
                    } else {
                        if (!addItem(player, output, event.amount)) addItemOrDrop(player, output, event.amount)
                        if (bonus > 0) {
                            if (gathering.bankBonusResources) bankOrFallback(player, output, bonus)
                            else if (!addItem(player, output, bonus)) addItemOrDrop(player, output, bonus)
                        }
                    }
                }
                return total
            }
        }

        if (autoBank) {
            // Producers add the base reward before dispatching ResourceProducedEvent. Move that
            // base reward out, then bank the complete post-League quantity as one transaction.
            if (removeItem(player, Item(event.itemId, event.amount))) {
                bankOrFallback(player, event.itemId, total)
                return total
            }
        }

        if (bonus > 0) {
            if (gathering.bankBonusResources) bankOrFallback(player, event.itemId, bonus)
            else if (!addItem(player, event.itemId, bonus)) addItemOrDrop(player, event.itemId, bonus)
        }
        return total
    }

    private fun applyProductionModifiers(player: Player, event: ResourceProducedEvent): Int {
        if (event.activity != ResourceActivity.PRODUCTION || event.amount <= 0) return event.amount
        val scopes = linkedSetOf(LeagueEffectScope.PRODUCTION)
        when (event.skill) {
            ResourceSkill.CONSTRUCTION -> scopes += LeagueEffectScope.CONSTRUCTION
            else -> Unit
        }
        val production = modifiers().production(scopes)
        val total = LeagueEffectMath.scaledQuantity(event.amount, production.outputMultiplier, kotlin.random.Random.nextDouble())
        val bonus = (total - event.amount).coerceAtLeast(0)
        if (bonus > 0 && !addItem(player, event.itemId, bonus)) {
            addItemOrDrop(player, event.itemId, bonus)
        }
        if (event.original > 0 && production.materialSaveChance > 0.0 && kotlin.random.Random.nextDouble() < production.materialSaveChance) {
            if (!addItem(player, event.original, 1)) addItemOrDrop(player, event.original, 1)
        }
        return total
    }

    private fun bankOrFallback(player: Player, itemId: Int, amount: Int) {
        if (amount <= 0) return
        if (!addItem(player, itemId, amount, Container.BANK)) {
            addItemOrDrop(player, itemId, amount)
        }
    }

    private fun <T : core.game.event.Event> hook(player: Player, type: Class<T>, handler: (T) -> Unit) {
        player.hook(type, object : EventHook<T> {
            override fun process(entity: Entity, event: T) {
                if (entity === player) handler(event)
            }
        })
    }

    companion object {
        private const val ATTRIBUTE = "grand-league-manager"
        private const val SAVE_ATTRIBUTE = "/save:grand-league:profile"
        private const val SAVE_KEY = "grand-league:profile"

        @JvmStatic
        fun getInstance(player: Player): GrandLeagueManager =
            getAttribute(player, ATTRIBUTE, GrandLeagueManager(player))

        @JvmStatic
        fun isActive(player: Player): Boolean = getInstance(player).profile.active

        @JvmStatic
        fun xpMultiplier(player: Player, skillId: Int): Double {
            if (player.isArtificial) return 1.0
            val manager = getInstance(player)
            if (!manager.profile.active) return 1.0
            var total = 0.0
            for (id in 0 until 24) total += player.skills.getExperience(id)
            val average = total / 24.0
            return manager.modifiers().xpMultiplier(player.skills.getExperience(skillId), average)
        }

        @JvmStatic
        fun runEnergyMultiplier(player: Player, drain: Double): Double {
            if (player.isArtificial || !isActive(player)) return 1.0
            val movement = getInstance(player).modifiers().movement()
            return if (drain >= 0.0) movement.runDrainMultiplier else movement.runRegenMultiplier
        }

        @JvmStatic
        fun shopPriceMultiplier(player: Player): Double =
            if (player.isArtificial || !isActive(player)) 1.0 else getInstance(player).modifiers().shop().priceMultiplier

        @JvmStatic
        fun shopStockConsumptionMultiplier(player: Player): Double =
            if (player.isArtificial || !isActive(player)) 1.0 else getInstance(player).modifiers().shop().stockConsumptionMultiplier

        @JvmStatic
        fun productionSpeedMultiplier(player: Player, construction: Boolean = false): Double {
            if (player.isArtificial || !isActive(player)) return 1.0
            val scopes = linkedSetOf(LeagueEffectScope.PRODUCTION)
            if (construction) scopes += LeagueEffectScope.CONSTRUCTION
            return getInstance(player).modifiers().production(scopes).speedMultiplier
        }

        @JvmStatic
        fun productionDelay(player: Player, baseTicks: Int, construction: Boolean = false): Int {
            if (baseTicks <= 1 || player.isArtificial || !isActive(player)) return baseTicks.coerceAtLeast(1)
            val multiplier = productionSpeedMultiplier(player, construction).coerceAtLeast(1.0)
            return kotlin.math.ceil(baseTicks / multiplier).toInt().coerceAtLeast(1)
        }

        @JvmStatic
        fun productionOutputMultiplier(player: Player, construction: Boolean = false): Double {
            if (player.isArtificial || !isActive(player)) return 1.0
            val scopes = linkedSetOf(LeagueEffectScope.PRODUCTION)
            if (construction) scopes += LeagueEffectScope.CONSTRUCTION
            return getInstance(player).modifiers().production(scopes).outputMultiplier
        }

        @JvmStatic
        fun productionMaterialSaveChance(player: Player, construction: Boolean = false): Double {
            if (player.isArtificial || !isActive(player)) return 0.0
            val scopes = linkedSetOf(LeagueEffectScope.PRODUCTION)
            if (construction) scopes += LeagueEffectScope.CONSTRUCTION
            return getInstance(player).modifiers().production(scopes).materialSaveChance
        }

        @JvmStatic
        fun thievingSuccessMultiplier(player: Player): Double =
            if (player.isArtificial || !isActive(player)) 1.0 else getInstance(player).modifiers().thieving().successMultiplier

        @JvmStatic
        fun thievingAutoRepeat(player: Player): Boolean =
            !player.isArtificial && isActive(player) && getInstance(player).modifiers().thieving().autoRepeat

        @JvmStatic
        fun agilityFailChanceMultiplier(player: Player): Double =
            if (player.isArtificial || !isActive(player)) 1.0 else getInstance(player).modifiers().agility().failChanceMultiplier

        @JvmStatic
        fun hunterSuccessMultiplier(player: Player): Double =
            if (player.isArtificial || !isActive(player)) 1.0 else getInstance(player).modifiers().hunter().successMultiplier

        private fun combatModifiers(player: Player, styleKey: String): LeagueCombatModifiers {
            if (player.isArtificial || !isActive(player)) return LeagueCombatModifiers()
            val style = LeagueCombatStyle.fromKey(styleKey) ?: return LeagueCombatModifiers()
            return getInstance(player).modifiers().combat(style)
        }

        @JvmStatic
        fun combatAccuracyMultiplier(player: Player, styleKey: String): Double =
            combatModifiers(player, styleKey).accuracyMultiplier

        @JvmStatic
        fun combatDamageMultiplier(player: Player, styleKey: String): Double {
            val modifiers = combatModifiers(player, styleKey)
            val maximum = player.skills.maximumLifepoints.coerceAtLeast(1)
            val healthRatio = player.skills.lifepoints.toDouble() / maximum.toDouble()
            return modifiers.outgoingDamageMultiplier(healthRatio)
        }

        @JvmStatic
        fun combatDefencePenetration(player: Player, styleKey: String): Double =
            combatModifiers(player, styleKey).defencePenetration

        @JvmStatic
        fun combatAttackInterval(player: Player, styleKey: String, baseTicks: Int): Int {
            if (baseTicks <= 1) return baseTicks.coerceAtLeast(1)
            val multiplier = combatModifiers(player, styleKey).attackIntervalMultiplier.coerceAtLeast(0.05)
            return kotlin.math.ceil(baseTicks * multiplier).toInt().coerceAtLeast(1)
        }

        @JvmStatic
        fun rangedAmmoSaveChance(player: Player): Double =
            combatModifiers(player, "ranged").ammoSaveChance

        @JvmStatic
        fun magicRuneSaveChance(player: Player): Double =
            combatModifiers(player, "magic").runeSaveChance

        @JvmStatic
        fun combatExtraHitChance(player: Player, styleKey: String): Double =
            combatModifiers(player, styleKey).extraHitChance

        @JvmStatic
        fun combatExtraHitDamageFraction(player: Player, styleKey: String): Double =
            combatModifiers(player, styleKey).extraHitDamageFraction

        @JvmStatic
        fun combatExecutionDamage(
            player: Player,
            targetName: String,
            currentHealth: Int,
            maximumHealth: Int,
            proposedHit: Int
        ): Int {
            if (proposedHit <= 0 || currentHealth <= 0 || maximumHealth <= 0 || player.isArtificial || !isActive(player)) {
                return proposedHit
            }
            val manager = getInstance(player)
            val boss = normalize(targetName) in manager.session.content.bossSignalKeys
            val combat = manager.modifiers().combat(LeagueCombatStyle.MELEE)
            return if (combat.shouldExecute(currentHealth, maximumHealth, boss)) currentHealth else proposedHit
        }

        @JvmStatic
        fun interceptLethalDamage(player: Player, incomingDamage: Int, maximumPrayer: Int): LeagueLethalHitResult {
            if (incomingDamage <= 0 || player.isArtificial || !isActive(player)) {
                return LeagueLethalHitResult(false, incomingDamage.coerceAtLeast(0))
            }
            return getInstance(player).session.interceptLethalHit(
                currentHealth = player.skills.lifepoints,
                maximumHealth = player.skills.maximumLifepoints,
                maximumPrayer = maximumPrayer.coerceAtLeast(0),
                incomingDamage = incomingDamage,
                nowMillis = System.currentTimeMillis()
            )
        }

        @JvmStatic
        fun incomingCombatDamage(player: Player, styleKey: String, rawHit: Int): Int {
            if (rawHit <= 0) return rawHit
            val multiplier = combatModifiers(player, styleKey).incomingDamageMultiplier
            return kotlin.math.floor(rawHit * multiplier).toInt().coerceAtLeast(0)
        }

        @JvmStatic
        fun reflectedCombatDamage(player: Player, styleKey: String, damageTaken: Int): Int {
            if (damageTaken <= 0) return 0
            val fraction = combatModifiers(player, styleKey).reflectFraction
            return kotlin.math.floor(damageTaken * fraction).toInt().coerceAtLeast(0)
        }

        @JvmStatic
        fun applyCombatSustain(player: Player, styleKey: String, damageDealt: Int) {
            if (damageDealt <= 0 || player.isArtificial || !isActive(player)) return
            val modifiers = combatModifiers(player, styleKey)
            val healing = kotlin.math.floor(damageDealt * modifiers.lifestealFraction).toInt()
            if (healing > 0) player.skills.heal(healing)
            val prayer = damageDealt * modifiers.prayerRestoreFraction
            if (prayer > 0.0) player.skills.incrementPrayerPoints(prayer)
        }

        @JvmStatic
        fun specialAttackCost(player: Player, baseCost: Int): Int {
            if (baseCost <= 0) return 0
            val multiplier = combatModifiers(player, "melee").specialAttackCostMultiplier
            return kotlin.math.ceil(baseCost * multiplier).toInt().coerceAtLeast(1)
        }

        @JvmStatic
        fun specialEnergyRestore(player: Player, baseAmount: Int): Int {
            if (baseAmount <= 0) return 0
            val multiplier = combatModifiers(player, "melee").specialEnergyRestoreMultiplier
            return kotlin.math.floor(baseAmount * multiplier).toInt().coerceAtLeast(0)
        }

        @JvmStatic
        fun farmingGrowthMultiplier(player: Player): Double =
            if (player.isArtificial || !isActive(player)) 1.0 else getInstance(player).modifiers().farming().growthMultiplier

        @JvmStatic
        fun farmingYieldMultiplier(player: Player): Double =
            if (player.isArtificial || !isActive(player)) 1.0 else getInstance(player).modifiers().farming().yieldMultiplier

        @JvmStatic
        fun farmingDiseaseChanceMultiplier(player: Player): Double =
            if (player.isArtificial || !isActive(player)) 1.0 else getInstance(player).modifiers().farming().diseaseChanceMultiplier

        @JvmStatic
        fun farmingDiseaseImmune(player: Player): Boolean =
            !player.isArtificial && isActive(player) && getInstance(player).modifiers().farming().diseaseImmune

        @JvmStatic
        fun gatheringResourceMultiplier(player: Player, scope: LeagueEffectScope): Double {
            if (player.isArtificial || !isActive(player)) return 1.0
            val scopes = setOf(LeagueEffectScope.GATHERING, scope)
            return getInstance(player).modifiers().gathering(scopes).resourceMultiplier
        }

        @JvmStatic
        fun gatheringAutoBankChance(player: Player, scope: LeagueEffectScope): Double {
            if (player.isArtificial || !isActive(player)) return 0.0
            val scopes = setOf(LeagueEffectScope.GATHERING, scope)
            return getInstance(player).modifiers().gathering(scopes).autoBankChance
        }

        @JvmStatic
        fun gatheringAutoProcessChance(player: Player, scope: LeagueEffectScope): Double {
            if (player.isArtificial || !isActive(player)) return 0.0
            val scopes = setOf(LeagueEffectScope.GATHERING, scope)
            return getInstance(player).modifiers().gathering(scopes).autoProcessChance
        }

        @JvmStatic
        fun gatheringBanksBonusResources(player: Player, scope: LeagueEffectScope): Boolean {
            if (player.isArtificial || !isActive(player)) return false
            val scopes = setOf(LeagueEffectScope.GATHERING, scope)
            return getInstance(player).modifiers().gathering(scopes).bankBonusResources
        }

        private fun normalize(value: String): String {
            val normalized = value.lowercase()
                .replace("'", "")
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
            return if (normalized.isBlank()) "unknown" else normalized
        }
    }
}
