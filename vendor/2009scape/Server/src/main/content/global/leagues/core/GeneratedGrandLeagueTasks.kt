// GENERATED FILE. Edit data/grand-league/tasks.tsv and run qa/grand-league/generate-tasks.py.
package content.global.leagues.core

object GeneratedGrandLeagueTasks {
    val tasks: List<LeagueTaskDefinition> = listOf(
        LeagueTaskDefinition(id = "bootstrap.cut-oak", name = "A Better Axe", description = "Cut an oak log.", difficulty = LeagueTaskDifficulty.EASY, trigger = ProduceItemTrigger(1521), tags = setOf("woodcutting", "gathering")),
        LeagueTaskDefinition(id = "bootstrap.cut-100-oak", name = "Oak You Doing?", description = "Cut 100 oak logs.", difficulty = LeagueTaskDifficulty.MEDIUM, trigger = ProduceItemTrigger(1521), target = 100L, tags = setOf("woodcutting", "gathering")),
        LeagueTaskDefinition(id = "bootstrap.mine-iron", name = "Iron Working", description = "Mine an iron ore.", difficulty = LeagueTaskDifficulty.EASY, trigger = ProduceItemTrigger(440), tags = setOf("mining", "gathering")),
        LeagueTaskDefinition(id = "bootstrap.mine-100-iron", name = "Iron Stockpile", description = "Mine 100 iron ore.", difficulty = LeagueTaskDifficulty.MEDIUM, trigger = ProduceItemTrigger(440), target = 100L, tags = setOf("mining", "gathering")),
        LeagueTaskDefinition(id = "bootstrap.kill-kbd", name = "King Slayer", description = "Defeat the King Black Dragon.", difficulty = LeagueTaskDifficulty.HARD, trigger = KillNpcTrigger(50), tags = setOf("combat", "boss")),
        LeagueTaskDefinition(id = "bootstrap.kill-25-kbd", name = "Dragon Extinction Event", description = "Defeat the King Black Dragon 25 times.", difficulty = LeagueTaskDifficulty.ELITE, trigger = KillNpcTrigger(50), target = 25L, tags = setOf("combat", "boss")),
        LeagueTaskDefinition(id = "bootstrap.kill-elvarg", name = "Crandor's Problem", description = "Defeat Elvarg.", difficulty = LeagueTaskDifficulty.HARD, trigger = KillNpcTrigger(742), tags = setOf("combat", "quest")),
        LeagueTaskDefinition(id = "bootstrap.level-20-mining", name = "Mining Twenty", description = "Reach level 20 Mining.", difficulty = LeagueTaskDifficulty.EASY, trigger = ReachSkillLevelTrigger(14, 20), tags = setOf("mining", "level")),
        LeagueTaskDefinition(id = "bootstrap.level-40-woodcutting", name = "Woodcutting Forty", description = "Reach level 40 Woodcutting.", difficulty = LeagueTaskDifficulty.MEDIUM, trigger = ReachSkillLevelTrigger(8, 40), tags = setOf("woodcutting", "level")),
        LeagueTaskDefinition(id = "bootstrap.level-60-attack", name = "Attack Sixty", description = "Reach level 60 Attack.", difficulty = LeagueTaskDifficulty.HARD, trigger = ReachSkillLevelTrigger(0, 60), tags = setOf("attack", "combat", "level")),
        LeagueTaskDefinition(id = "bootstrap.gain-1m-mining-xp", name = "A Million Rocks", description = "Gain 1,000,000 Mining XP while in the League.", difficulty = LeagueTaskDifficulty.ELITE, trigger = GainXpTrigger(14), target = 1000000L, tags = setOf("mining", "xp")),
        LeagueTaskDefinition(id = "bootstrap.complete-dragon-slayer", name = "Dragon Slayer", description = "Complete Dragon Slayer.", difficulty = LeagueTaskDifficulty.HARD, trigger = CompleteQuestTrigger("dragon_slayer"), tags = setOf("quest")),
        LeagueTaskDefinition(id = "bootstrap.complete-cooks-assistant", name = "Cook's Assistant", description = "Complete Cook's Assistant.", difficulty = LeagueTaskDifficulty.EASY, trigger = CompleteQuestTrigger("cooks_assistant"), tags = setOf("quest")),
        LeagueTaskDefinition(id = "bootstrap.equip-rune-platebody", name = "Rune Armour", description = "Equip a rune platebody.", difficulty = LeagueTaskDifficulty.MEDIUM, trigger = EquipItemTrigger(1127), tags = setOf("equipment", "combat")),
        LeagueTaskDefinition(id = "bootstrap.equip-dragon-scimitar", name = "Red Curved Sword", description = "Equip a dragon scimitar.", difficulty = LeagueTaskDifficulty.HARD, trigger = EquipItemTrigger(4587), tags = setOf("equipment", "combat")),
        LeagueTaskDefinition(id = "bootstrap.obtain-lobster", name = "Lobster Dinner", description = "Obtain a cooked lobster.", difficulty = LeagueTaskDifficulty.EASY, trigger = ObtainItemTrigger(379), tags = setOf("cooking", "item")),
        LeagueTaskDefinition(id = "bootstrap.teleport-lumbridge-region", name = "Home Again", description = "Teleport into the Lumbridge map region.", difficulty = LeagueTaskDifficulty.EASY, trigger = TeleportToTrigger("region:12850"), region = "misthalin", tags = setOf("travel")),
        LeagueTaskDefinition(id = "bootstrap.total-level-500", name = "Getting Somewhere", description = "Reach total level 500.", difficulty = LeagueTaskDifficulty.MEDIUM, trigger = MetricAtLeastTrigger("total-level", 500L), tags = setOf("total-level")),
        LeagueTaskDefinition(id = "bootstrap.100-quest-points", name = "Questing Habit", description = "Reach 100 quest points.", difficulty = LeagueTaskDifficulty.HARD, trigger = MetricAtLeastTrigger("quest-points", 100L), tags = setOf("quest")),
        LeagueTaskDefinition(id = "bootstrap.first-echo", name = "An Echo Answers", description = "Defeat any Echo boss.", difficulty = LeagueTaskDifficulty.ELITE, trigger = CustomTaskTrigger("echo-kill"), tags = setOf("echo", "boss"))
    )
}
