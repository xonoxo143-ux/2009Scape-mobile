import content.global.leagues.GrandLeagueManager
import content.global.leagues.core.*
import core.game.event.*
import core.game.node.entity.npc.NPC
import core.game.node.entity.player.Player
import core.game.node.item.Item
import org.json.simple.JSONObject

fun main() {
    Item.names[1511] = "Logs"
    Item.names[317] = "Raw shrimps"
    val player = Player("adapter")
    val plugin = GrandLeagueManager()
    plugin.login(player)
    val manager = GrandLeagueManager.getInstance(player)
    manager.enable(reset = true)

    player.dispatch(ResourceProducedEvent(1511,1,NPC(0,"Tree")))
    player.dispatch(ResourceProducedEvent(317,10,NPC(316,"Fishing spot")))
    repeat(10) { player.dispatch(NPCKillEvent(NPC(81,"Cow"))) }
    repeat(3) { player.dispatch(QuestCompleteEvent("quest-$it",1,it+1)) }
    player.skills.totalLevel = 250
    player.dispatch(XPGainEvent(0,100.0))

    check(manager.profile.completedTasks.containsAll(setOf("first-log","ten-fish","cow-hunter","quester","total-250")))
    check(manager.profile.tier >= 2)
    check(manager.selectRelic("endless-harvest").success)
    check(manager.unlockRegion("asgarnia").success)

    // Live resource seam: the producer has already placed the base log in inventory.
    // Endless Harvest adds one bonus log and banks only that relic-generated bonus.
    player.inventoryItems[1511] = 1
    player.dispatch(ResourceProducedEvent(1511, 1, NPC(0, "Tree"), -1, ResourceActivity.GATHERING, ResourceSkill.WOODCUTTING))
    check(player.inventoryItems[1511] == 1)
    check(player.bankItems[1511] == 1)
    check(GrandLeagueManager.gatheringResourceMultiplier(player, LeagueEffectScope.WOODCUTTING) == 2.0)


    // Endless Harvest + Infernal Gathering: the gathered raw fish is consumed,
    // the base cooked fish stays in inventory, and the doubled bonus is banked.
    val infernalPlayer = Player("infernal")
    plugin.login(infernalPlayer)
    val infernal = GrandLeagueManager.getInstance(infernalPlayer)
    infernal.enable(reset = true)
    infernal.profile.tier = 8
    check(infernal.selectRelic("endless-harvest").success)
    check(infernal.selectRelic("infernal-gathering").success)
    infernalPlayer.inventoryItems[317] = 1
    infernalPlayer.dispatch(ResourceProducedEvent(317, 1, NPC(316, "Fishing spot"), -1, ResourceActivity.GATHERING, ResourceSkill.FISHING))
    check((infernalPlayer.inventoryItems[317] ?: 0) == 0)
    check(infernalPlayer.inventoryItems[315] == 1)
    check(infernalPlayer.bankItems[315] == 1)
    check(infernalPlayer.skills.getExperience(7) == 60.0)

    val tricksterPlayer = Player("trickster")
    plugin.login(tricksterPlayer)
    val trickster = GrandLeagueManager.getInstance(tricksterPlayer)
    trickster.enable(reset = true)
    trickster.profile.tier = 8
    check(trickster.selectRelic("trickster").success)
    check(GrandLeagueManager.runEnergyMultiplier(tricksterPlayer, 1.0) == 0.5)
    check(GrandLeagueManager.runEnergyMultiplier(tricksterPlayer, -1.0) == 4.0)
    check(GrandLeagueManager.thievingSuccessMultiplier(tricksterPlayer) == 2.0)
    check(GrandLeagueManager.thievingAutoRepeat(tricksterPlayer))
    check(GrandLeagueManager.agilityFailChanceMultiplier(tricksterPlayer) == 0.25)
    check(GrandLeagueManager.hunterSuccessMultiplier(tricksterPlayer) == 2.0)

    val productionPlayer = Player("production")
    plugin.login(productionPlayer)
    val production = GrandLeagueManager.getInstance(productionPlayer)
    production.enable(reset = true)
    production.profile.tier = 8
    check(production.selectRelic("production-prodigy").success)
    check(GrandLeagueManager.productionSpeedMultiplier(productionPlayer) == 4.0)
    check(GrandLeagueManager.productionDelay(productionPlayer, 5) == 2)
    check(GrandLeagueManager.productionOutputMultiplier(productionPlayer) == 1.25)
    check(GrandLeagueManager.productionMaterialSaveChance(productionPlayer) == 0.25)

    val rangedPlayer = Player("ranged")
    plugin.login(rangedPlayer)
    val ranged = GrandLeagueManager.getInstance(rangedPlayer)
    ranged.enable(reset = true)
    ranged.profile.tier = 8
    check(ranged.selectRelic("archers-embrace").success)
    check(GrandLeagueManager.combatAccuracyMultiplier(rangedPlayer, "ranged") == 1.25)
    check(GrandLeagueManager.combatAccuracyMultiplier(rangedPlayer, "melee") == 1.0)
    check(GrandLeagueManager.combatAttackInterval(rangedPlayer, "ranged", 5) == 3)
    check(GrandLeagueManager.rangedAmmoSaveChance(rangedPlayer) == 0.90)

    val magicPlayer = Player("magic")
    plugin.login(magicPlayer)
    val magic = GrandLeagueManager.getInstance(magicPlayer)
    magic.enable(reset = true)
    magic.profile.tier = 8
    check(magic.selectRelic("superior-sorcerer").success)
    check(GrandLeagueManager.combatDamageMultiplier(magicPlayer, "magic") == 1.10)
    check(GrandLeagueManager.combatAttackInterval(magicPlayer, "magic", 5) == 3)
    check(GrandLeagueManager.magicRuneSaveChance(magicPlayer) == 0.90)

    val berserkerPlayer = Player("berserker")
    plugin.login(berserkerPlayer)
    val berserker = GrandLeagueManager.getInstance(berserkerPlayer)
    berserker.enable(reset = true)
    berserker.profile.tier = 8
    check(berserker.selectRelic("berserker").success)
    berserkerPlayer.skills.lifepoints = 50
    berserkerPlayer.skills.maximumLifepoints = 100
    check(GrandLeagueManager.combatDamageMultiplier(berserkerPlayer, "melee") == 1.5)

    val sustainPlayer = Player("sustain")
    plugin.login(sustainPlayer)
    val sustain = GrandLeagueManager.getInstance(sustainPlayer)
    sustain.enable(reset = true)
    sustain.profile.tier = 8
    check(sustain.selectRelic("soul-stealer").success)
    sustainPlayer.skills.lifepoints = 50
    sustainPlayer.skills.prayerPoints = 0.0
    GrandLeagueManager.applyCombatSustain(sustainPlayer, "melee", 20)
    check(sustainPlayer.skills.lifepoints == 52)
    check(sustainPlayer.skills.prayerPoints == 1.0)

    val weaponMasterPlayer = Player("weapon-master")
    plugin.login(weaponMasterPlayer)
    val weaponMaster = GrandLeagueManager.getInstance(weaponMasterPlayer)
    weaponMaster.enable(reset = true)
    weaponMaster.profile.tier = 8
    check(weaponMaster.selectRelic("weapon-master").success)
    check(GrandLeagueManager.specialAttackCost(weaponMasterPlayer, 50) == 25)
    check(GrandLeagueManager.specialEnergyRestore(weaponMasterPlayer, 10) == 20)

    val unitPlayer = Player("absolute-unit")
    plugin.login(unitPlayer)
    val unit = GrandLeagueManager.getInstance(unitPlayer)
    unit.enable(reset = true)
    unit.profile.tier = 8
    unit.profile.fragmentTokens = 5
    listOf("absolute-unit", "twin-strikes", "venomaster").forEach {
        check(unit.unlockFragment(it).success)
        check(unit.equipFragment(it).success)
    }
    check(GrandLeagueManager.incomingCombatDamage(unitPlayer, "melee", 100) == 72)
    check(GrandLeagueManager.reflectedCombatDamage(unitPlayer, "melee", 100) == 15)
    check(GrandLeagueManager.combatExtraHitChance(unitPlayer, "melee") == 0.45)
    check(GrandLeagueManager.combatExtraHitDamageFraction(unitPlayer, "melee") == 0.75)

    val executionerPlayer = Player("executioner")
    plugin.login(executionerPlayer)
    val executioner = GrandLeagueManager.getInstance(executionerPlayer)
    executioner.enable(reset = true)
    executioner.profile.tier = 8
    check(executioner.selectRelic("executioner").success)
    check(GrandLeagueManager.combatExecutionDamage(executionerPlayer, "Cow", 19, 100, 1) == 19)
    check(GrandLeagueManager.combatExecutionDamage(executionerPlayer, "General Graardor", 10, 100, 1) == 1)
    check(GrandLeagueManager.combatExecutionDamage(executionerPlayer, "General Graardor", 9, 100, 1) == 9)

    val undyingPlayer = Player("undying")
    plugin.login(undyingPlayer)
    val undying = GrandLeagueManager.getInstance(undyingPlayer)
    undying.enable(reset = true)
    undying.profile.tier = 8
    check(undying.selectRelic("undying-retribution").success)
    undyingPlayer.skills.lifepoints = 30
    undyingPlayer.skills.maximumLifepoints = 100
    val interception = GrandLeagueManager.interceptLethalDamage(undyingPlayer, 30, 70)
    check(interception.intercepted)
    check(interception.restoreHealth == 100)
    check(interception.restorePrayer == 70)
    check(interception.retaliationDamage == 60)

    val fireSalePlayer = Player("fire-sale")
    plugin.login(fireSalePlayer)
    val fireSale = GrandLeagueManager.getInstance(fireSalePlayer)
    fireSale.enable(reset = true)
    fireSale.profile.tier = 8
    check(fireSale.selectRelic("fire-sale").success)
    check(GrandLeagueManager.shopPriceMultiplier(fireSalePlayer) == 0.0)
    check(GrandLeagueManager.shopStockConsumptionMultiplier(fireSalePlayer) == 0.0)

    val farmerPlayer = Player("farmer")
    plugin.login(farmerPlayer)
    val farmer = GrandLeagueManager.getInstance(farmerPlayer)
    farmer.enable(reset = true)
    farmer.profile.tier = 8
    check(farmer.selectRelic("farmers-fortune").success)
    check(GrandLeagueManager.farmingGrowthMultiplier(farmerPlayer) == 5.0)
    check(GrandLeagueManager.farmingYieldMultiplier(farmerPlayer) == 2.0)
    check(GrandLeagueManager.farmingDiseaseImmune(farmerPlayer))

    val equilibriumPlayer = Player("equilibrium")
    plugin.login(equilibriumPlayer)
    val equilibrium = GrandLeagueManager.getInstance(equilibriumPlayer)
    equilibrium.enable(reset = true)
    equilibrium.profile.tier = 8
    check(equilibrium.selectRelic("equilibrium").success)
    for (skill in 1 until 24) equilibriumPlayer.skills.setExperience(skill, 1_000.0)
    check(GrandLeagueManager.xpMultiplier(equilibriumPlayer, 0) > 1.45)
    check(GrandLeagueManager.xpMultiplier(equilibriumPlayer, 1) == 1.0)

    val save = JSONObject()
    plugin.savePlayer(player, save)
    val persisted = player.attrs["grand-league:profile"] as String

    val relog = Player("adapter")
    relog.attrs["grand-league:profile"] = persisted
    plugin.login(relog)
    plugin.parsePlayer(relog, JSONObject())
    val restored = GrandLeagueManager.getInstance(relog)
    check(restored.profile.points == manager.profile.points)
    check(restored.profile.completedTasks == manager.profile.completedTasks)
    check("asgarnia" in restored.profile.unlockedRegions)
    check(restored.profile.selectedRelics[1] == "endless-harvest")
    println("GRAND LEAGUE SERVER ADAPTER PASS: points=${restored.profile.points} tier=${restored.profile.tier}")
}
