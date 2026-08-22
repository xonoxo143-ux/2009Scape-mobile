package content.global.leagues.core

data class LeagueContent(
    val tasks: List<LeagueTaskDefinition>,
    val tiers: List<LeagueTierDefinition>,
    val relics: List<LeagueRelicDefinition>,
    val regions: List<LeagueRegionDefinition>,
    val fragments: List<LeagueFragmentDefinition>,
    val fragmentSets: List<LeagueFragmentSetDefinition>,
    val masteries: List<LeagueNodeDefinition>,
    val pacts: List<LeagueNodeDefinition>,
    val echoes: List<LeagueEchoDefinition>
) {
    val tasksById = tasks.associateBy { it.id }
    val relicsById = relics.associateBy { it.id }
    val regionsById = regions.associateBy { it.id }
    val fragmentsById = fragments.associateBy { it.id }
    val fragmentSetsById = fragmentSets.associateBy { it.id }
    val masteriesById = masteries.associateBy { it.id }
    val pactsById = pacts.associateBy { it.id }
    val echoesById = echoes.associateBy { it.id }
    val bossSignalKeys: Set<String> = tasks.asSequence()
        .filter { it.category == "boss" }
        .map { it.trigger.key }
        .toSet()

    init { validate() }

    fun validate() {
        fun <T> unique(label: String, values: List<T>, id: (T) -> String) {
            val ids = values.map(id)
            require(ids.size == ids.toSet().size) { "Duplicate $label ids" }
        }
        unique("task", tasks) { it.id }; unique("relic", relics) { it.id }; unique("region", regions) { it.id }
        unique("fragment", fragments) { it.id }; unique("fragment set", fragmentSets) { it.id }
        unique("mastery", masteries) { it.id }; unique("pact", pacts) { it.id }; unique("echo", echoes) { it.id }

        require(tiers.isNotEmpty() && tiers.first().tier == 0 && tiers.first().minimumPoints == 0)
        tiers.zipWithNext().forEach { (a, b) ->
            require(b.tier == a.tier + 1) { "League tiers must be contiguous" }
            require(b.minimumPoints > a.minimumPoints) { "League tier point thresholds must increase" }
        }
        relics.forEach { require(it.tier in 1..tiers.last().tier) { "Relic ${it.id} references missing tier ${it.tier}" } }
        tasks.forEach { task ->
            task.regionId?.let { id -> require(id in regionsById || id in BASE_REGIONS) { "Task ${task.id} references missing region $id" } }
        }
        regions.forEach { region ->
            region.prerequisites.forEach { id -> require(id in regionsById || id in BASE_REGIONS) { "Region ${region.id} references missing prerequisite $id" } }
        }
        fragments.forEach { fragment ->
            fragment.setIds.forEach { set -> require(set in fragmentSetsById) { "Fragment ${fragment.id} references missing set $set" } }
        }
        echoes.forEach {
            require(it.regionId in regionsById || it.regionId in BASE_REGIONS) { "Echo ${it.id} references missing region ${it.regionId}" }
            require(it.minimumTier <= tiers.last().tier)
            it.requiredMastery?.let { id -> require(id in masteriesById) }
            it.requiredPact?.let { id -> require(id in pactsById) }
        }
        validateDag("mastery", masteries)
        validateDag("pact", pacts)
    }

    private fun validateDag(label: String, nodes: List<LeagueNodeDefinition>) {
        val map = nodes.associateBy { it.id }
        nodes.forEach { node -> node.prerequisites.forEach { require(it in map) { "$label ${node.id} references missing prerequisite $it" } } }
        val visiting = hashSetOf<String>(); val visited = hashSetOf<String>()
        fun visit(id: String) {
            if (id in visited) return
            require(visiting.add(id)) { "Cycle in $label graph at $id" }
            map.getValue(id).prerequisites.forEach(::visit)
            visiting.remove(id); visited.add(id)
        }
        map.keys.forEach(::visit)
    }

    companion object {
        val BASE_REGIONS = setOf("misthalin", "karamja")
    }
}

private fun bootstrapTasks() = listOf(
    LeagueTaskDefinition("first-log", "Chop a log", 40, CountTrigger(LeagueSignalKind.RESOURCE, "log", 1)),
    LeagueTaskDefinition("ten-fish", "Catch 10 fish", 60, CountTrigger(LeagueSignalKind.RESOURCE, "fish", 10)),
    LeagueTaskDefinition("cow-hunter", "Defeat 10 cows", 100, CountTrigger(LeagueSignalKind.NPC_KILL, "cow", 10)),
    LeagueTaskDefinition("quester", "Complete 3 quests", 100, CountTrigger(LeagueSignalKind.QUEST, "complete", 3)),
    LeagueTaskDefinition("total-250", "Reach 250 total level", 150, MetricTrigger(LeagueSignalKind.METRIC, "total-level", 250)),
    LeagueTaskDefinition("graardor", "Defeat General Graardor", 200, CountTrigger(LeagueSignalKind.NPC_KILL, "general-graardor", 1)),
    LeagueTaskDefinition("total-500", "Reach 500 total level", 250, MetricTrigger(LeagueSignalKind.METRIC, "total-level", 500)),
    LeagueTaskDefinition("quest-points-50", "Reach 50 quest points", 250, MetricTrigger(LeagueSignalKind.METRIC, "quest-points", 50)),
    LeagueTaskDefinition("jad", "Defeat TzTok-Jad", 300, CountTrigger(LeagueSignalKind.NPC_KILL, "tztok-jad", 1))
)

/** Small immutable regression fixture retained so the original vertical-slice proof never gets diluted by catalogue growth. */
object GrandLeagueBootstrapContent {
    fun create(): LeagueContent = LeagueContent(
        tasks = bootstrapTasks(),
        tiers = listOf(
            LeagueTierDefinition(0, 0),
            LeagueTierDefinition(1, 100),
            LeagueTierDefinition(2, 250, regionTokens = 1),
            LeagueTierDefinition(3, 450, fragmentTokens = 2),
            LeagueTierDefinition(4, 650, masteryPoints = 2),
            LeagueTierDefinition(5, 900, pactPoints = 2),
            LeagueTierDefinition(6, 1_400, regionTokens = 1, fragmentTokens = 1, masteryPoints = 1, pactPoints = 1)
        ),
        relics = listOf(
            LeagueRelicDefinition("endless-harvest", 1, "Endless Harvest"),
            LeagueRelicDefinition("production-prodigy", 1, "Production Prodigy"),
            LeagueRelicDefinition("fairys-flight", 2, "Fairy's Flight")
        ),
        regions = listOf(
            LeagueRegionDefinition("asgarnia", "Asgarnia"),
            LeagueRegionDefinition("kandarin", "Kandarin"),
            LeagueRegionDefinition("morytania", "Morytania"),
            LeagueRegionDefinition("wilderness", "Wilderness")
        ),
        fragments = listOf(
            LeagueFragmentDefinition("trailblazer", "Trailblazer", "mobility"),
            LeagueFragmentDefinition("homewrecker", "Homewrecker", "mobility"),
            LeagueFragmentDefinition("venomaster", "Venomaster", "combat")
        ),
        fragmentSets = listOf(
            LeagueFragmentSetDefinition("mobility", 2),
            LeagueFragmentSetDefinition("combat", 1)
        ),
        masteries = listOf(
            LeagueNodeDefinition("combat-root", "Combat Mastery I"),
            LeagueNodeDefinition("melee-echo", "Melee Echo", setOf("combat-root"))
        ),
        pacts = listOf(
            LeagueNodeDefinition("demonic-root", "Demonic Pact I"),
            LeagueNodeDefinition("echo-pact", "Echo Pact", setOf("demonic-root"))
        ),
        echoes = listOf(
            LeagueEchoDefinition("kbd", "King Black Dragon", "wilderness", 6, "melee-echo", "echo-pact")
        )
    )
}

/** Production Grand League catalogue. This is intentionally broad and data-driven; individual effect mechanics land in later batches. */
object GrandLeagueContent {
    private data class SkillDef(val id: Int, val key: String, val name: String)
    private data class BossDef(val key: String, val name: String, val region: String, val source: LeagueSource = LeagueSource.GRAND_LEAGUE)

    private fun fx(key: LeagueModifierKey, value: Double, vararg scopes: LeagueEffectScope) =
        LeagueEffectDefinition(key, value, scopes.toSet())

    private val skills = listOf(
        SkillDef(0, "attack", "Attack"), SkillDef(1, "defence", "Defence"), SkillDef(2, "strength", "Strength"),
        SkillDef(3, "hitpoints", "Hitpoints"), SkillDef(4, "ranged", "Ranged"), SkillDef(5, "prayer", "Prayer"),
        SkillDef(6, "magic", "Magic"), SkillDef(7, "cooking", "Cooking"), SkillDef(8, "woodcutting", "Woodcutting"),
        SkillDef(9, "fletching", "Fletching"), SkillDef(10, "fishing", "Fishing"), SkillDef(11, "firemaking", "Firemaking"),
        SkillDef(12, "crafting", "Crafting"), SkillDef(13, "smithing", "Smithing"), SkillDef(14, "mining", "Mining"),
        SkillDef(15, "herblore", "Herblore"), SkillDef(16, "agility", "Agility"), SkillDef(17, "thieving", "Thieving"),
        SkillDef(18, "slayer", "Slayer"), SkillDef(19, "farming", "Farming"), SkillDef(20, "runecrafting", "Runecrafting"),
        SkillDef(21, "hunter", "Hunter"), SkillDef(22, "construction", "Construction"), SkillDef(23, "summoning", "Summoning")
    )

    private val bosses = listOf(
        BossDef("king-black-dragon", "King Black Dragon", "wilderness"),
        BossDef("kalphite-queen", "Kalphite Queen", "desert"),
        BossDef("dagannoth-rex", "Dagannoth Rex", "fremennik"),
        BossDef("dagannoth-prime", "Dagannoth Prime", "fremennik"),
        BossDef("dagannoth-supreme", "Dagannoth Supreme", "fremennik"),
        BossDef("chaos-elemental", "Chaos Elemental", "wilderness"),
        BossDef("general-graardor", "General Graardor", "asgarnia"),
        BossDef("commander-zilyana", "Commander Zilyana", "asgarnia"),
        BossDef("kreearra", "Kree'arra", "asgarnia"),
        BossDef("kril-tsutsaroth", "K'ril Tsutsaroth", "asgarnia"),
        BossDef("corporeal-beast", "Corporeal Beast", "wilderness"),
        BossDef("tztok-jad", "TzTok-Jad", "karamja"),
        BossDef("tormented-demon", "Tormented Demon", "misthalin"),
        BossDef("ahrim-the-blighted", "Ahrim the Blighted", "morytania")
    )

    fun create(): LeagueContent = LeagueContent(
        tasks = createTasks(),
        tiers = createTiers(),
        relics = createRelics(),
        regions = createRegions(),
        fragments = createFragments(),
        fragmentSets = createFragmentSets(),
        masteries = createMasteries(),
        pacts = createPacts(),
        echoes = createEchoes()
    )

    private fun createTasks(): List<LeagueTaskDefinition> {
        val tasks = mutableListOf<LeagueTaskDefinition>()
        tasks += bootstrapTasks().map { task ->
            when (task.id) {
                "graardor" -> task.copy(regionId = "asgarnia", category = "boss", difficulty = LeagueTaskDifficulty.ELITE, source = LeagueSource.TRAILBLAZER)
                "jad" -> task.copy(regionId = "karamja", category = "boss", difficulty = LeagueTaskDifficulty.ELITE, source = LeagueSource.TRAILBLAZER)
                else -> task.copy(category = if (task.trigger.kind == LeagueSignalKind.METRIC) "milestone" else "general")
            }
        }

        fun metric(id: String, name: String, key: String, target: Long, difficulty: LeagueTaskDifficulty, category: String = "milestone") {
            tasks += LeagueTaskDefinition(id, name, difficulty.defaultPoints, MetricTrigger(LeagueSignalKind.METRIC, key, target), difficulty = difficulty, category = category)
        }
        fun count(
            id: String, name: String, kind: LeagueSignalKind, key: String, target: Long, difficulty: LeagueTaskDifficulty,
            region: String? = null, category: String = "general", source: LeagueSource = LeagueSource.GRAND_LEAGUE,
            masteryReward: Int = 0, pactReward: Int = 0
        ) {
            tasks += LeagueTaskDefinition(
                id, name, difficulty.defaultPoints, CountTrigger(kind, key, target), difficulty = difficulty, source = source,
                regionId = region, category = category, masteryPointReward = masteryReward, pactPointReward = pactReward
            )
        }

        listOf(
            Triple(100L, LeagueTaskDifficulty.EASY, "100"), Triple(750L, LeagueTaskDifficulty.MEDIUM, "750"),
            Triple(1_000L, LeagueTaskDifficulty.MEDIUM, "1000"), Triple(1_250L, LeagueTaskDifficulty.HARD, "1250"),
            Triple(1_500L, LeagueTaskDifficulty.HARD, "1500"), Triple(1_750L, LeagueTaskDifficulty.ELITE, "1750"),
            Triple(2_000L, LeagueTaskDifficulty.ELITE, "2000"), Triple(2_200L, LeagueTaskDifficulty.MASTER, "2200")
        ).forEach { (target, diff, suffix) -> metric("total-$suffix", "Reach $target total level", "total-level", target, diff) }

        listOf(
            Triple(10L, LeagueTaskDifficulty.EASY, "10"), Triple(25L, LeagueTaskDifficulty.MEDIUM, "25"),
            Triple(100L, LeagueTaskDifficulty.HARD, "100"), Triple(150L, LeagueTaskDifficulty.HARD, "150"),
            Triple(200L, LeagueTaskDifficulty.ELITE, "200"), Triple(250L, LeagueTaskDifficulty.ELITE, "250"),
            Triple(275L, LeagueTaskDifficulty.MASTER, "275")
        ).forEach { (target, diff, suffix) -> metric("quest-points-$suffix", "Reach $target quest points", "quest-points", target, diff, "quest") }

        listOf(
            Triple(1L, LeagueTaskDifficulty.EASY, "1"), Triple(5L, LeagueTaskDifficulty.EASY, "5"),
            Triple(10L, LeagueTaskDifficulty.MEDIUM, "10"), Triple(25L, LeagueTaskDifficulty.HARD, "25"),
            Triple(50L, LeagueTaskDifficulty.HARD, "50"), Triple(100L, LeagueTaskDifficulty.ELITE, "100")
        ).forEach { (target, diff, suffix) -> count("quests-$suffix", "Complete $target quests", LeagueSignalKind.QUEST, "complete", target, diff, category = "quest") }

        skills.forEach { skill ->
            count("${skill.key}-xp-1k", "Gain 1,000 ${skill.name} XP", LeagueSignalKind.XP, "skill-${skill.id}", 1_000, LeagueTaskDifficulty.EASY, category = "skill")
            count("${skill.key}-xp-10k", "Gain 10,000 ${skill.name} XP", LeagueSignalKind.XP, "skill-${skill.id}", 10_000, LeagueTaskDifficulty.MEDIUM, category = "skill")
            count("${skill.key}-xp-100k", "Gain 100,000 ${skill.name} XP", LeagueSignalKind.XP, "skill-${skill.id}", 100_000, LeagueTaskDifficulty.HARD, category = "skill")
        }

        listOf(25L to LeagueTaskDifficulty.EASY, 100L to LeagueTaskDifficulty.MEDIUM, 250L to LeagueTaskDifficulty.MEDIUM,
            500L to LeagueTaskDifficulty.HARD, 1_000L to LeagueTaskDifficulty.HARD, 5_000L to LeagueTaskDifficulty.ELITE).forEach { (target, diff) ->
            count("logs-$target", "Chop $target logs", LeagueSignalKind.RESOURCE, "log", target, diff, category = "skill", source = LeagueSource.TWISTED)
            count("fish-$target", "Catch $target fish", LeagueSignalKind.RESOURCE, "fish", target, diff, category = "skill", source = LeagueSource.TWISTED)
        }

        listOf(
            Triple("oak-logs", "Oak logs", 100L), Triple("willow-logs", "Willow logs", 250L),
            Triple("maple-logs", "Maple logs", 250L), Triple("yew-logs", "Yew logs", 100L),
            Triple("magic-logs", "Magic logs", 100L), Triple("raw-lobster", "Raw lobster", 250L),
            Triple("raw-shark", "Raw shark", 100L), Triple("raw-monkfish", "Raw monkfish", 100L)
        ).forEachIndexed { index, (key, label, target) ->
            val diff = if (index < 2) LeagueTaskDifficulty.MEDIUM else LeagueTaskDifficulty.HARD
            count("gather-${key}-$target", "Gather $target $label", LeagueSignalKind.RESOURCE, key, target, diff, category = "skill", source = LeagueSource.SHATTERED_RELICS)
        }

        bosses.forEach { boss ->
            count("boss-${boss.key}-1", "Defeat ${boss.name}", LeagueSignalKind.NPC_KILL, boss.key, 1, LeagueTaskDifficulty.HARD,
                boss.region, "boss", boss.source, masteryReward = 1, pactReward = 1)
            count("boss-${boss.key}-25", "Defeat ${boss.name} 25 times", LeagueSignalKind.NPC_KILL, boss.key, 25, LeagueTaskDifficulty.ELITE,
                boss.region, "boss", boss.source, pactReward = 1)
        }

        listOf(
            BossDef("hill-giant", "Hill Giant", "misthalin", LeagueSource.DEMONIC_PACTS),
            BossDef("steel-dragon", "Steel Dragon", "karamja", LeagueSource.DEMONIC_PACTS),
            BossDef("troll", "Troll", "asgarnia", LeagueSource.DEMONIC_PACTS),
            BossDef("cockatrice", "Cockatrice", "fremennik", LeagueSource.DEMONIC_PACTS),
            BossDef("werewolf", "Werewolf", "morytania", LeagueSource.DEMONIC_PACTS),
            BossDef("black-dragon", "Black Dragon", "tirannwn", LeagueSource.DEMONIC_PACTS),
            BossDef("chaos-dwarf", "Chaos Dwarf", "wilderness", LeagueSource.DEMONIC_PACTS)
        ).forEach { boss ->
            count("pact-${boss.key}", "Defeat a ${boss.name}", LeagueSignalKind.NPC_KILL, boss.key, 1, LeagueTaskDifficulty.EASY,
                boss.region, "pact", boss.source, pactReward = 1)
        }

        return tasks
    }

    private fun createTiers() = listOf(
        LeagueTierDefinition(0, 0),
        LeagueTierDefinition(1, 100, fragmentTokens = 1),
        LeagueTierDefinition(2, 300, regionTokens = 1, fragmentTokens = 2, masteryPoints = 1, pactPoints = 1),
        LeagueTierDefinition(3, 700, regionTokens = 1, fragmentTokens = 2, masteryPoints = 1, pactPoints = 1),
        LeagueTierDefinition(4, 1_300, regionTokens = 1, fragmentTokens = 3, masteryPoints = 2, pactPoints = 2),
        LeagueTierDefinition(5, 2_200, regionTokens = 1, fragmentTokens = 3, masteryPoints = 2, pactPoints = 2),
        LeagueTierDefinition(6, 3_400, regionTokens = 1, fragmentTokens = 4, masteryPoints = 3, pactPoints = 2),
        LeagueTierDefinition(7, 4_800, regionTokens = 1, fragmentTokens = 4, masteryPoints = 3, pactPoints = 2),
        LeagueTierDefinition(8, 6_200, regionTokens = 1, fragmentTokens = 5, masteryPoints = 4, pactPoints = 2)
    )

    private fun createRelics() = listOf(
        LeagueRelicDefinition("endless-harvest", 1, "Endless Harvest", LeagueSource.TRAILBLAZER_RELOADED,
            "Gather twice the resources; the relic-generated bonus is sent to the bank.", listOf(
                fx(LeagueModifierKey.RESOURCE_MULTIPLIER, 2.0, LeagueEffectScope.GATHERING),
                fx(LeagueModifierKey.BANK_BONUS_RESOURCES, 1.0, LeagueEffectScope.GATHERING)
            )),
        LeagueRelicDefinition("production-prodigy", 1, "Production Prodigy", LeagueSource.TRAILBLAZER_RELOADED,
            "Process production actions rapidly with bonus output and material conservation.", listOf(
                fx(LeagueModifierKey.PRODUCTION_SPEED_MULTIPLIER, 4.0, LeagueEffectScope.PRODUCTION),
                fx(LeagueModifierKey.PRODUCTION_OUTPUT_MULTIPLIER, 1.25, LeagueEffectScope.PRODUCTION),
                fx(LeagueModifierKey.MATERIAL_SAVE_CHANCE, 0.25, LeagueEffectScope.PRODUCTION)
            )),
        LeagueRelicDefinition("trickster", 1, "Trickster", LeagueSource.TRAILBLAZER_RELOADED,
            "Greatly improve Thieving, Agility, Hunter and run-energy efficiency.", listOf(
                fx(LeagueModifierKey.THIEVING_SUCCESS_MULTIPLIER, 2.0, LeagueEffectScope.THIEVING),
                fx(LeagueModifierKey.AGILITY_FAIL_CHANCE_MULTIPLIER, 0.25, LeagueEffectScope.AGILITY),
                fx(LeagueModifierKey.HUNTER_SUCCESS_MULTIPLIER, 2.0, LeagueEffectScope.HUNTER),
                fx(LeagueModifierKey.THIEVING_AUTO_REPEAT, 1.0, LeagueEffectScope.THIEVING),
                fx(LeagueModifierKey.RUN_REGEN_MULTIPLIER, 4.0, LeagueEffectScope.MOVEMENT),
                fx(LeagueModifierKey.RUN_DRAIN_MULTIPLIER, 0.5, LeagueEffectScope.MOVEMENT)
            )),
        LeagueRelicDefinition("fairys-flight", 2, "Fairy's Flight", LeagueSource.TRAILBLAZER_RELOADED,
            "Unlock the reusable Fairy's Flight teleport network.", listOf(
                fx(LeagueModifierKey.FAIRY_FLIGHT, 1.0, LeagueEffectScope.MOVEMENT)
            )),
        LeagueRelicDefinition("globetrotter", 2, "Globetrotter", LeagueSource.TRAILBLAZER_RELOADED,
            "Unlock reusable teleports throughout unlocked regions.", listOf(
                fx(LeagueModifierKey.GLOBETROTTER, 1.0, LeagueEffectScope.MOVEMENT)
            )),
        LeagueRelicDefinition("bankers-note", 3, "Banker's Note", LeagueSource.TRAILBLAZER_RELOADED,
            "Note and un-note eligible items anywhere.", listOf(
                fx(LeagueModifierKey.PORTABLE_NOTE, 1.0)
            )),
        LeagueRelicDefinition("fire-sale", 3, "Fire Sale", LeagueSource.TRAILBLAZER_RELOADED,
            "Coin-shop purchases are free and do not consume normal shop stock.", listOf(
                fx(LeagueModifierKey.SHOP_PRICE_MULTIPLIER, 0.0, LeagueEffectScope.SHOP),
                fx(LeagueModifierKey.SHOP_STOCK_CONSUMPTION_MULTIPLIER, 0.0, LeagueEffectScope.SHOP)
            )),
        LeagueRelicDefinition("archers-embrace", 4, "Archer's Embrace", LeagueSource.TRAILBLAZER_RELOADED,
            "Attack faster and more accurately with ranged weapons while conserving ammunition.", listOf(
                fx(LeagueModifierKey.COMBAT_ACCURACY_MULTIPLIER, 1.25, LeagueEffectScope.COMBAT, LeagueEffectScope.RANGED),
                fx(LeagueModifierKey.ATTACK_INTERVAL_MULTIPLIER, 0.50, LeagueEffectScope.COMBAT, LeagueEffectScope.RANGED),
                fx(LeagueModifierKey.AMMO_SAVE_CHANCE, 0.90, LeagueEffectScope.COMBAT, LeagueEffectScope.RANGED)
            )),
        LeagueRelicDefinition("brawlers-resolve", 4, "Brawler's Resolve", LeagueSource.TRAILBLAZER_RELOADED,
            "Attack faster and more accurately in melee with increased damage.", listOf(
                fx(LeagueModifierKey.COMBAT_ACCURACY_MULTIPLIER, 1.25, LeagueEffectScope.COMBAT, LeagueEffectScope.MELEE),
                fx(LeagueModifierKey.COMBAT_DAMAGE_MULTIPLIER, 1.10, LeagueEffectScope.COMBAT, LeagueEffectScope.MELEE),
                fx(LeagueModifierKey.ATTACK_INTERVAL_MULTIPLIER, 0.50, LeagueEffectScope.COMBAT, LeagueEffectScope.MELEE)
            )),
        LeagueRelicDefinition("superior-sorcerer", 4, "Superior Sorcerer", LeagueSource.TRAILBLAZER_RELOADED,
            "Cast faster and more accurately with increased magic damage and rune conservation.", listOf(
                fx(LeagueModifierKey.COMBAT_ACCURACY_MULTIPLIER, 1.25, LeagueEffectScope.COMBAT, LeagueEffectScope.MAGIC),
                fx(LeagueModifierKey.COMBAT_DAMAGE_MULTIPLIER, 1.10, LeagueEffectScope.COMBAT, LeagueEffectScope.MAGIC),
                fx(LeagueModifierKey.ATTACK_INTERVAL_MULTIPLIER, 0.50, LeagueEffectScope.COMBAT, LeagueEffectScope.MAGIC),
                fx(LeagueModifierKey.RUNE_SAVE_CHANCE, 0.90, LeagueEffectScope.COMBAT, LeagueEffectScope.MAGIC)
            )),
        LeagueRelicDefinition("treasure-seeker", 5, "Treasure Seeker", LeagueSource.TRAILBLAZER_RELOADED),
        LeagueRelicDefinition("bloodthirsty", 5, "Bloodthirsty", LeagueSource.TRAILBLAZER_RELOADED),
        LeagueRelicDefinition("infernal-gathering", 6, "Infernal Gathering", LeagueSource.TRAILBLAZER_RELOADED,
            "Gathered resources can be automatically processed while awarding their secondary XP.", listOf(
                fx(LeagueModifierKey.AUTO_PROCESS_CHANCE, 1.0, LeagueEffectScope.GATHERING)
            )),
        LeagueRelicDefinition("equilibrium", 6, "Equilibrium", LeagueSource.TRAILBLAZER_RELOADED,
            "Skills below the account's average XP gain a progressively larger XP bonus.", listOf(
                fx(LeagueModifierKey.BELOW_AVERAGE_XP_BONUS, 0.50, LeagueEffectScope.XP)
            )),
        LeagueRelicDefinition("farmers-fortune", 6, "Farmer's Fortune", LeagueSource.TRAILBLAZER_RELOADED,
            "Crops grow five times faster, cannot become diseased and produce twice the yield.", listOf(
                fx(LeagueModifierKey.FARM_GROWTH_MULTIPLIER, 5.0, LeagueEffectScope.FARMING),
                fx(LeagueModifierKey.FARM_YIELD_MULTIPLIER, 2.0, LeagueEffectScope.FARMING),
                fx(LeagueModifierKey.FARM_DISEASE_IMMUNITY, 1.0, LeagueEffectScope.FARMING)
            )),
        LeagueRelicDefinition("ruinous-powers", 6, "Ruinous Powers", LeagueSource.TRAILBLAZER_RELOADED),
        LeagueRelicDefinition("soul-stealer", 7, "Soul Stealer", LeagueSource.TRAILBLAZER_RELOADED,
            "Damage dealt restores Hitpoints and Prayer.", listOf(
                fx(LeagueModifierKey.LIFESTEAL_FRACTION, 0.10, LeagueEffectScope.COMBAT),
                fx(LeagueModifierKey.PRAYER_RESTORE_FRACTION, 0.05, LeagueEffectScope.COMBAT)
            )),
        LeagueRelicDefinition("weapon-master", 7, "Weapon Master", LeagueSource.TRAILBLAZER_RELOADED,
            "Special attacks cost half as much energy and special energy restores twice as fast.", listOf(
                fx(LeagueModifierKey.SPECIAL_ATTACK_COST_MULTIPLIER, 0.50, LeagueEffectScope.COMBAT),
                fx(LeagueModifierKey.SPECIAL_ENERGY_RESTORE_MULTIPLIER, 2.0, LeagueEffectScope.COMBAT)
            )),
        LeagueRelicDefinition("berserker", 7, "Berserker", LeagueSource.TRAILBLAZER_RELOADED,
            "Deal progressively more damage as your Hitpoints fall.", listOf(
                fx(LeagueModifierKey.LOW_HP_MAX_DAMAGE_BONUS, 1.0, LeagueEffectScope.COMBAT)
            )),
        LeagueRelicDefinition("executioner", 8, "Executioner", LeagueSource.TRAILBLAZER_RELOADED,
            "Execute ordinary enemies below 20% health and bosses below 10% health.", listOf(
                fx(LeagueModifierKey.EXECUTION_THRESHOLD_NORMAL, 0.20, LeagueEffectScope.COMBAT),
                fx(LeagueModifierKey.EXECUTION_THRESHOLD_BOSS, 0.10, LeagueEffectScope.COMBAT)
            )),
        LeagueRelicDefinition(
            "undying-retribution", 8, "Undying Retribution", LeagueSource.TRAILBLAZER_RELOADED,
            "Intercept fatal damage, fully recover, and retaliate for twice the damage avoided; three-minute cooldown.",
            triggeredEffects = listOf(
                LeagueTriggeredEffectDefinition(
                    id = "undying-retribution",
                    kind = LeagueTriggeredEffectKind.LETHAL_INTERCEPT,
                    priority = 100,
                    cooldownMillis = 180_000L,
                    healthRestoreFraction = 1.0,
                    prayerRestoreFraction = 1.0,
                    retaliationDamageMultiplier = 2.0,
                    retaliationRadius = 3
                )
            )
        ),
        LeagueRelicDefinition("guardian", 8, "Guardian", LeagueSource.TRAILBLAZER_RELOADED)
    )

    private fun createRegions() = listOf(
        LeagueRegionDefinition("asgarnia", "Asgarnia"),
        LeagueRegionDefinition("desert", "Kharidian Desert"),
        LeagueRegionDefinition("fremennik", "Fremennik Provinces"),
        LeagueRegionDefinition("kandarin", "Kandarin"),
        LeagueRegionDefinition("morytania", "Morytania"),
        LeagueRegionDefinition("tirannwn", "Tirannwn"),
        LeagueRegionDefinition("wilderness", "Wilderness")
    )

    private fun createFragmentSets() = listOf(
        LeagueFragmentSetDefinition("mobility", 2, "Trailblazer", listOf(
            fx(LeagueModifierKey.RUN_REGEN_MULTIPLIER, 4.0, LeagueEffectScope.MOVEMENT),
            fx(LeagueModifierKey.RUN_DRAIN_MULTIPLIER, 0.25, LeagueEffectScope.MOVEMENT)
        )),
        LeagueFragmentSetDefinition("gathering", 2, "Greedy Gatherer", listOf(
            fx(LeagueModifierKey.RESOURCE_MULTIPLIER, 1.5, LeagueEffectScope.GATHERING)
        )),
        LeagueFragmentSetDefinition("banking", 2, "Personal Banker", listOf(
            fx(LeagueModifierKey.AUTO_BANK_CHANCE, 1.0, LeagueEffectScope.GATHERING)
        )),
        LeagueFragmentSetDefinition("production", 2, "Production Prodigy", listOf(
            fx(LeagueModifierKey.PRODUCTION_SPEED_MULTIPLIER, 2.0, LeagueEffectScope.PRODUCTION),
            fx(LeagueModifierKey.PRODUCTION_OUTPUT_MULTIPLIER, 1.25, LeagueEffectScope.PRODUCTION)
        )),
        LeagueFragmentSetDefinition("melee", 2, "Twin Strikes", listOf(
            fx(LeagueModifierKey.EXTRA_HIT_CHANCE, 0.25, LeagueEffectScope.COMBAT, LeagueEffectScope.MELEE),
            fx(LeagueModifierKey.EXTRA_HIT_DAMAGE_FRACTION, 0.75, LeagueEffectScope.COMBAT, LeagueEffectScope.MELEE)
        )),
        LeagueFragmentSetDefinition("ranged", 2, "Double Tap", listOf(
            fx(LeagueModifierKey.EXTRA_HIT_CHANCE, 0.25, LeagueEffectScope.COMBAT, LeagueEffectScope.RANGED),
            fx(LeagueModifierKey.EXTRA_HIT_DAMAGE_FRACTION, 0.75, LeagueEffectScope.COMBAT, LeagueEffectScope.RANGED)
        )),
        LeagueFragmentSetDefinition("magic", 2, "Chain Magic", listOf(
            fx(LeagueModifierKey.EXTRA_HIT_CHANCE, 0.25, LeagueEffectScope.COMBAT, LeagueEffectScope.MAGIC),
            fx(LeagueModifierKey.EXTRA_HIT_DAMAGE_FRACTION, 0.75, LeagueEffectScope.COMBAT, LeagueEffectScope.MAGIC)
        )),
        LeagueFragmentSetDefinition("survival", 2, "Absolute Unit", listOf(
            fx(LeagueModifierKey.INCOMING_DAMAGE_MULTIPLIER, 0.80, LeagueEffectScope.COMBAT),
            fx(LeagueModifierKey.DAMAGE_REFLECT_FRACTION, 0.10, LeagueEffectScope.COMBAT)
        )),
        LeagueFragmentSetDefinition("prayer", 2, "Drakan's Touch", listOf(
            fx(LeagueModifierKey.LIFESTEAL_FRACTION, 0.05, LeagueEffectScope.COMBAT),
            fx(LeagueModifierKey.PRAYER_RESTORE_FRACTION, 0.025, LeagueEffectScope.COMBAT)
        )),
        LeagueFragmentSetDefinition("slayer", 2, "Knife's Edge")
    )

    private fun createFragments() = listOf(
        LeagueFragmentDefinition("trailblazer", "Trailblazer", setOf("mobility", "banking"), effects = listOf(
            fx(LeagueModifierKey.RUN_REGEN_MULTIPLIER, 2.0, LeagueEffectScope.MOVEMENT),
            fx(LeagueModifierKey.RUN_DRAIN_MULTIPLIER, 0.75, LeagueEffectScope.MOVEMENT)
        )),
        LeagueFragmentDefinition("homewrecker", "Homewrecker", setOf("mobility", "production"), effects = listOf(
            fx(LeagueModifierKey.PRODUCTION_SPEED_MULTIPLIER, 2.0, LeagueEffectScope.PRODUCTION, LeagueEffectScope.CONSTRUCTION),
            fx(LeagueModifierKey.MATERIAL_SAVE_CHANCE, 0.20, LeagueEffectScope.PRODUCTION, LeagueEffectScope.CONSTRUCTION)
        )),
        LeagueFragmentDefinition("venomaster", "Venomaster", setOf("melee", "survival")),
        LeagueFragmentDefinition("greedy-gatherer", "Greedy Gatherer", setOf("gathering", "banking"), effects = listOf(
            fx(LeagueModifierKey.RESOURCE_MULTIPLIER, 1.25, LeagueEffectScope.GATHERING)
        )),
        LeagueFragmentDefinition("personal-banker", "Personal Banker", setOf("banking", "production"), effects = listOf(
            fx(LeagueModifierKey.AUTO_BANK_CHANCE, 0.25, LeagueEffectScope.GATHERING)
        )),
        LeagueFragmentDefinition("production-prodigy-fragment", "Production Prodigy", setOf("production", "gathering"), effects = listOf(
            fx(LeagueModifierKey.PRODUCTION_SPEED_MULTIPLIER, 1.5, LeagueEffectScope.PRODUCTION),
            fx(LeagueModifierKey.PRODUCTION_OUTPUT_MULTIPLIER, 1.10, LeagueEffectScope.PRODUCTION),
            fx(LeagueModifierKey.MATERIAL_SAVE_CHANCE, 0.10, LeagueEffectScope.PRODUCTION)
        )),
        LeagueFragmentDefinition("rock-solid", "Rock Solid", setOf("gathering", "survival"), effects = listOf(
            fx(LeagueModifierKey.RESOURCE_MULTIPLIER, 1.25, LeagueEffectScope.GATHERING, LeagueEffectScope.MINING)
        )),
        LeagueFragmentDefinition("certified-farmer", "Certified Farmer", setOf("gathering", "production"), effects = listOf(
            fx(LeagueModifierKey.FARM_GROWTH_MULTIPLIER, 1.5, LeagueEffectScope.FARMING),
            fx(LeagueModifierKey.FARM_YIELD_MULTIPLIER, 1.25, LeagueEffectScope.FARMING),
            fx(LeagueModifierKey.FARM_DISEASE_CHANCE_MULTIPLIER, 0.5, LeagueEffectScope.FARMING)
        )),
        LeagueFragmentDefinition("chefs-catch", "Chef's Catch", setOf("gathering", "production"), effects = listOf(
            fx(LeagueModifierKey.AUTO_PROCESS_CHANCE, 0.35, LeagueEffectScope.GATHERING, LeagueEffectScope.FISHING)
        )),
        LeagueFragmentDefinition("smooth-criminal", "Smooth Criminal", setOf("gathering", "banking"), effects = listOf(
            fx(LeagueModifierKey.THIEVING_SUCCESS_MULTIPLIER, 1.5, LeagueEffectScope.THIEVING),
            fx(LeagueModifierKey.THIEVING_AUTO_REPEAT, 1.0, LeagueEffectScope.THIEVING)
        )),
        LeagueFragmentDefinition("bottomless-quiver", "Bottomless Quiver", setOf("ranged", "survival"), effects = listOf(
            fx(LeagueModifierKey.AMMO_SAVE_CHANCE, 0.80, LeagueEffectScope.COMBAT, LeagueEffectScope.RANGED)
        )),
        LeagueFragmentDefinition("arcane-conduit", "Arcane Conduit", setOf("magic", "prayer"), effects = listOf(
            fx(LeagueModifierKey.RUNE_SAVE_CHANCE, 0.80, LeagueEffectScope.COMBAT, LeagueEffectScope.MAGIC)
        )),
        LeagueFragmentDefinition("divine-restoration", "Divine Restoration", setOf("prayer", "survival")),
        LeagueFragmentDefinition("drakans-touch", "Drakan's Touch", setOf("prayer", "melee"), effects = listOf(
            fx(LeagueModifierKey.LIFESTEAL_FRACTION, 0.05, LeagueEffectScope.COMBAT)
        )),
        LeagueFragmentDefinition("unholy-ranger", "Unholy Ranger", setOf("ranged", "prayer")),
        LeagueFragmentDefinition("unholy-warrior", "Unholy Warrior", setOf("melee", "prayer")),
        LeagueFragmentDefinition("unholy-wizard", "Unholy Wizard", setOf("magic", "prayer")),
        LeagueFragmentDefinition("knifes-edge", "Knife's Edge", setOf("slayer", "melee"), effects = listOf(
            fx(LeagueModifierKey.LOW_HP_MAX_DAMAGE_BONUS, 0.50, LeagueEffectScope.COMBAT)
        )),
        LeagueFragmentDefinition("twin-strikes", "Twin Strikes", setOf("melee", "survival"), effects = listOf(
            fx(LeagueModifierKey.EXTRA_HIT_CHANCE, 0.20, LeagueEffectScope.COMBAT, LeagueEffectScope.MELEE),
            fx(LeagueModifierKey.EXTRA_HIT_DAMAGE_FRACTION, 0.50, LeagueEffectScope.COMBAT, LeagueEffectScope.MELEE)
        )),
        LeagueFragmentDefinition("double-tap", "Double Tap", setOf("ranged", "survival"), effects = listOf(
            fx(LeagueModifierKey.EXTRA_HIT_CHANCE, 0.20, LeagueEffectScope.COMBAT, LeagueEffectScope.RANGED),
            fx(LeagueModifierKey.EXTRA_HIT_DAMAGE_FRACTION, 0.50, LeagueEffectScope.COMBAT, LeagueEffectScope.RANGED)
        )),
        LeagueFragmentDefinition("chain-magic", "Chain Magic", setOf("magic", "survival"), effects = listOf(
            fx(LeagueModifierKey.EXTRA_HIT_CHANCE, 0.20, LeagueEffectScope.COMBAT, LeagueEffectScope.MAGIC),
            fx(LeagueModifierKey.EXTRA_HIT_DAMAGE_FRACTION, 0.50, LeagueEffectScope.COMBAT, LeagueEffectScope.MAGIC)
        )),
        LeagueFragmentDefinition("absolute-unit", "Absolute Unit", setOf("survival", "prayer"), effects = listOf(
            fx(LeagueModifierKey.INCOMING_DAMAGE_MULTIPLIER, 0.90, LeagueEffectScope.COMBAT),
            fx(LeagueModifierKey.DAMAGE_REFLECT_FRACTION, 0.05, LeagueEffectScope.COMBAT)
        )),
        LeagueFragmentDefinition("slay-all-day", "Slay All Day", setOf("slayer", "gathering")),
        LeagueFragmentDefinition("fast-metabolism", "Fast Metabolism", setOf("survival", "production"))
    )

    private fun createMasteries(): List<LeagueNodeDefinition> {
        val nodes = mutableListOf(LeagueNodeDefinition("combat-root", "Combat Mastery", style = "neutral"))
        fun branch(style: String, display: String) {
            var previous = "combat-root"
            val scope = when (style) {
                "melee" -> LeagueEffectScope.MELEE
                "ranged" -> LeagueEffectScope.RANGED
                else -> LeagueEffectScope.MAGIC
            }
            for (tier in 1..6) {
                val id = "$style-$tier"
                val effects = when (tier) {
                    1 -> listOf(fx(LeagueModifierKey.COMBAT_ACCURACY_MULTIPLIER, 1.05, LeagueEffectScope.COMBAT, scope))
                    2 -> listOf(fx(LeagueModifierKey.COMBAT_DAMAGE_MULTIPLIER, 1.05, LeagueEffectScope.COMBAT, scope))
                    3 -> listOf(fx(LeagueModifierKey.ATTACK_INTERVAL_MULTIPLIER, 0.90, LeagueEffectScope.COMBAT, scope))
                    4 -> when (style) {
                        "melee" -> listOf(fx(LeagueModifierKey.DEFENCE_PENETRATION, 0.10, LeagueEffectScope.COMBAT, scope))
                        "ranged" -> listOf(fx(LeagueModifierKey.AMMO_SAVE_CHANCE, 0.50, LeagueEffectScope.COMBAT, scope))
                        else -> listOf(fx(LeagueModifierKey.RUNE_SAVE_CHANCE, 0.50, LeagueEffectScope.COMBAT, scope))
                    }
                    5 -> listOf(
                        fx(LeagueModifierKey.EXTRA_HIT_CHANCE, 0.15, LeagueEffectScope.COMBAT, scope),
                        fx(LeagueModifierKey.EXTRA_HIT_DAMAGE_FRACTION, 0.50, LeagueEffectScope.COMBAT, scope)
                    )
                    6 -> listOf(fx(LeagueModifierKey.COMBAT_DAMAGE_MULTIPLIER, 1.10, LeagueEffectScope.COMBAT, scope))
                    else -> emptyList()
                }
                nodes += LeagueNodeDefinition(id, "$display Mastery $tier", setOf(previous), style = style, effects = effects)
                previous = id
            }
        }
        branch("melee", "Melee"); branch("ranged", "Ranged"); branch("magic", "Magic")
        return nodes
    }

    private fun createPacts(): List<LeagueNodeDefinition> {
        val nodes = mutableListOf(LeagueNodeDefinition("demonic-root", "Regeneration Pact", style = "neutral"))
        val branches = listOf(
            "melee" to listOf("Hellforged Strength", "Bloodrush", "Infernal Reach", "Brutal Rhythm", "Crushing Momentum", "Demonblade"),
            "ranged" to listOf("Sharpsight", "Endless Volley", "Piercing Shots", "Windstep", "Deadeye", "Demonbow"),
            "magic" to listOf("Arcane Hunger", "Rune Siphon", "Elemental Ruin", "Overcharge", "Soulfire", "Demonstaff"),
            "defence" to listOf("Thorns", "Stone Skin", "Life Ward", "Reprisal", "Unyielding", "Immortal Shell"),
            "neutral" to listOf("Echo Pact", "Blindbag", "Culling Spree", "Evil Eye", "Flask of Fervour", "Executioner's Mark")
        )
        branches.forEach { (style, names) ->
            var previous = "demonic-root"
            names.forEachIndexed { index, name ->
                val id = when (name) {
                    "Echo Pact" -> "echo-pact"
                    else -> "pact-$style-${index + 1}"
                }
                val effects = when (name) {
                    "Hellforged Strength" -> listOf(fx(LeagueModifierKey.COMBAT_DAMAGE_MULTIPLIER, 1.10, LeagueEffectScope.COMBAT, LeagueEffectScope.MELEE))
                    "Bloodrush" -> listOf(fx(LeagueModifierKey.LIFESTEAL_FRACTION, 0.05, LeagueEffectScope.COMBAT, LeagueEffectScope.MELEE))
                    "Demonblade" -> listOf(
                        fx(LeagueModifierKey.EXTRA_HIT_CHANCE, 0.25, LeagueEffectScope.COMBAT, LeagueEffectScope.MELEE),
                        fx(LeagueModifierKey.EXTRA_HIT_DAMAGE_FRACTION, 1.00, LeagueEffectScope.COMBAT, LeagueEffectScope.MELEE)
                    )
                    "Sharpsight" -> listOf(fx(LeagueModifierKey.COMBAT_ACCURACY_MULTIPLIER, 1.10, LeagueEffectScope.COMBAT, LeagueEffectScope.RANGED))
                    "Endless Volley" -> listOf(fx(LeagueModifierKey.AMMO_SAVE_CHANCE, 0.50, LeagueEffectScope.COMBAT, LeagueEffectScope.RANGED))
                    "Piercing Shots" -> listOf(fx(LeagueModifierKey.DEFENCE_PENETRATION, 0.10, LeagueEffectScope.COMBAT, LeagueEffectScope.RANGED))
                    "Demonbow" -> listOf(
                        fx(LeagueModifierKey.EXTRA_HIT_CHANCE, 0.25, LeagueEffectScope.COMBAT, LeagueEffectScope.RANGED),
                        fx(LeagueModifierKey.EXTRA_HIT_DAMAGE_FRACTION, 1.00, LeagueEffectScope.COMBAT, LeagueEffectScope.RANGED)
                    )
                    "Arcane Hunger" -> listOf(fx(LeagueModifierKey.PRAYER_RESTORE_FRACTION, 0.05, LeagueEffectScope.COMBAT, LeagueEffectScope.MAGIC))
                    "Rune Siphon" -> listOf(fx(LeagueModifierKey.RUNE_SAVE_CHANCE, 0.50, LeagueEffectScope.COMBAT, LeagueEffectScope.MAGIC))
                    "Elemental Ruin" -> listOf(fx(LeagueModifierKey.DEFENCE_PENETRATION, 0.10, LeagueEffectScope.COMBAT, LeagueEffectScope.MAGIC))
                    "Demonstaff" -> listOf(
                        fx(LeagueModifierKey.EXTRA_HIT_CHANCE, 0.25, LeagueEffectScope.COMBAT, LeagueEffectScope.MAGIC),
                        fx(LeagueModifierKey.EXTRA_HIT_DAMAGE_FRACTION, 1.00, LeagueEffectScope.COMBAT, LeagueEffectScope.MAGIC)
                    )
                    "Thorns" -> listOf(fx(LeagueModifierKey.DAMAGE_REFLECT_FRACTION, 0.10, LeagueEffectScope.COMBAT))
                    "Stone Skin" -> listOf(fx(LeagueModifierKey.INCOMING_DAMAGE_MULTIPLIER, 0.90, LeagueEffectScope.COMBAT))
                    else -> emptyList()
                }
                val triggeredEffects = when (name) {
                    "Immortal Shell" -> listOf(
                        LeagueTriggeredEffectDefinition(
                            id = "immortal-shell",
                            kind = LeagueTriggeredEffectKind.LETHAL_INTERCEPT,
                            priority = 50,
                            cooldownMillis = 300_000L,
                            healthRestoreFraction = 0.25
                        )
                    )
                    else -> emptyList()
                }
                nodes += LeagueNodeDefinition(
                    id, name, setOf(previous), style = style,
                    effects = effects,
                    triggeredEffects = triggeredEffects
                )
                previous = id
            }
        }
        return nodes
    }

    private fun createEchoes() = listOf(
        LeagueEchoDefinition("kbd", "King Black Dragon", "wilderness", 5, rewardIds = listOf("echo-crossbow")),
        LeagueEchoDefinition("kalphite-queen", "Kalphite Queen", "desert", 5, rewardIds = listOf("echo-khopesh")),
        LeagueEchoDefinition("dagannoth-kings", "Dagannoth Kings", "fremennik", 5, rewardIds = listOf("echo-viking-helm")),
        LeagueEchoDefinition("chaos-elemental", "Chaos Elemental", "wilderness", 5, rewardIds = listOf("echo-chaos-core")),
        LeagueEchoDefinition("barrows", "Barrows Brothers", "morytania", 5, rewardIds = listOf("echo-barrows-sigil")),
        LeagueEchoDefinition("graardor", "General Graardor", "asgarnia", 6, rewardIds = listOf("echo-bandos-relic")),
        LeagueEchoDefinition("zilyana", "Commander Zilyana", "asgarnia", 6, rewardIds = listOf("echo-saradomin-relic")),
        LeagueEchoDefinition("kreearra", "Kree'arra", "asgarnia", 6, rewardIds = listOf("echo-armadyl-relic")),
        LeagueEchoDefinition("kril", "K'ril Tsutsaroth", "asgarnia", 6, rewardIds = listOf("echo-zamorak-relic")),
        LeagueEchoDefinition("corp", "Corporeal Beast", "wilderness", 7, rewardIds = listOf("echo-spirit-shield")),
        LeagueEchoDefinition("jad", "TzTok-Jad", "karamja", 6, rewardIds = listOf("echo-fire-cape")),
        LeagueEchoDefinition("tormented-demon", "Tormented Demon", "misthalin", 6, rewardIds = listOf("echo-demon-claw"))
    )
}
