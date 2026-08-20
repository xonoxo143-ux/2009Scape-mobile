package content.global.leagues.core

/**
 * Integration content used to prove all major Grand League subsystems together.
 * It is deliberately small; historical League content is layered into these
 * registries after the engine/server bridge is green under the real build.
 */
object GrandLeagueBootstrapContent {
    val contentUnlocks = LeagueContentUnlockRegistry(
        listOf(
            LeagueContentUnlockDefinition("agility", "Agility", LeagueContentType.SKILL, cost = 10),
            LeagueContentUnlockDefinition("king-black-dragon", "King Black Dragon", LeagueContentType.BOSS, cost = 20),
            LeagueContentUnlockDefinition("dragon-slayer", "Dragon Slayer", LeagueContentType.QUEST, cost = 7)
        )
    )

    val relics = RelicRegistry(
        listOf(
            RelicDefinition(
                id = "endless-harvest",
                name = "Endless Harvest",
                tier = 1,
                source = LeagueSource.TRAILBLAZER,
                effects = setOf(
                    NumericLeagueEffect(LeagueModifierKey.RESOURCE_MULTIPLIER, LeagueModifierOperation.MAX, 2.0),
                    FlagLeagueEffect("resource.auto-bank"),
                    FlagLeagueEffect("resource.extra-xp")
                )
            ),
            RelicDefinition(
                id = "last-recall",
                name = "Last Recall",
                tier = 2,
                source = LeagueSource.TRAILBLAZER,
                effects = setOf(FlagLeagueEffect("teleport.last-recall"))
            ),
            RelicDefinition(
                id = "production-prodigy",
                name = "Production Prodigy",
                tier = 3,
                source = LeagueSource.TRAILBLAZER_RELOADED,
                effects = setOf(
                    NumericLeagueEffect(LeagueModifierKey.PRODUCTION_MULTIPLIER, LeagueModifierOperation.MAX, 1.25),
                    FlagLeagueEffect("production.instant-batch"),
                    FlagLeagueEffect("production.extra-xp"),
                    FlagLeagueEffect("production.bonus-auto-bank"),
                    SkillBoostLeagueEffect(7, 12),   // Cooking
                    SkillBoostLeagueEffect(9, 12),   // Fletching
                    SkillBoostLeagueEffect(12, 12),  // Crafting
                    SkillBoostLeagueEffect(13, 12),  // Smithing
                    SkillBoostLeagueEffect(15, 12)   // Herblore
                )
            )
        )
    )

    val regions = RegionRegistry(
        listOf(
            RegionDefinition("asgarnia", "Asgarnia"),
            RegionDefinition("kandarin", "Kandarin"),
            RegionDefinition("desert", "Kharidian Desert"),
            RegionDefinition("fremennik", "Fremennik Provinces"),
            RegionDefinition("morytania", "Morytania"),
            RegionDefinition("tirannwn", "Tirannwn"),
            RegionDefinition("wilderness", "Wilderness")
        )
    )

    val fragments = FragmentRegistry(
        fragments = listOf(
            FragmentDefinition(
                id = "greedy-gatherer-a",
                name = "Greedy Gatherer A",
                setTags = setOf("greedy-gatherer"),
                effectsByLevel = mapOf(
                    1 to setOf(NumericLeagueEffect(LeagueModifierKey.RESOURCE_MULTIPLIER, LeagueModifierOperation.MULTIPLY, 1.10)),
                    2 to setOf(NumericLeagueEffect(LeagueModifierKey.RESOURCE_MULTIPLIER, LeagueModifierOperation.MULTIPLY, 1.10)),
                    3 to setOf(NumericLeagueEffect(LeagueModifierKey.RESOURCE_MULTIPLIER, LeagueModifierOperation.MULTIPLY, 1.10))
                )
            ),
            FragmentDefinition(
                id = "greedy-gatherer-b",
                name = "Greedy Gatherer B",
                setTags = setOf("greedy-gatherer")
            )
        ),
        sets = listOf(
            FragmentSetDefinition(
                id = "greedy-gatherer",
                name = "Greedy Gatherer",
                tag = "greedy-gatherer",
                requiredFragments = 2,
                effects = setOf(NumericLeagueEffect(LeagueModifierKey.RESOURCE_MULTIPLIER, LeagueModifierOperation.MAX, 3.0))
            )
        )
    )

    val masteries = MasteryRegistry(
        listOf(
            MasteryDefinition(
                id = "melee",
                name = "Melee",
                effectsByRank = mapOf(
                    1 to setOf(NumericLeagueEffect(LeagueModifierKey.ACCURACY_MULTIPLIER, LeagueModifierOperation.MULTIPLY, 1.10)),
                    2 to setOf(NumericLeagueEffect(LeagueModifierKey.DAMAGE_MULTIPLIER, LeagueModifierOperation.MULTIPLY, 1.10))
                )
            ),
            MasteryDefinition(
                id = "ranged",
                name = "Ranged",
                effectsByRank = mapOf(
                    1 to setOf(NumericLeagueEffect(LeagueModifierKey.ACCURACY_MULTIPLIER, LeagueModifierOperation.MULTIPLY, 1.10)),
                    2 to setOf(NumericLeagueEffect(LeagueModifierKey.DAMAGE_MULTIPLIER, LeagueModifierOperation.MULTIPLY, 1.10))
                )
            ),
            MasteryDefinition(
                id = "magic",
                name = "Magic",
                effectsByRank = mapOf(
                    1 to setOf(NumericLeagueEffect(LeagueModifierKey.ACCURACY_MULTIPLIER, LeagueModifierOperation.MULTIPLY, 1.10)),
                    2 to setOf(NumericLeagueEffect(LeagueModifierKey.DAMAGE_MULTIPLIER, LeagueModifierOperation.MULTIPLY, 1.10))
                )
            )
        )
    )

    val pacts = PactRegistry(
        listOf(
            PactNodeDefinition("pact-root", "Pact Root", pointCost = 1),
            PactNodeDefinition(
                id = "pact-blood-price",
                name = "Blood Price",
                prerequisites = setOf("pact-root"),
                pointCost = 1,
                effects = setOf(NumericLeagueEffect(LeagueModifierKey.DAMAGE_MULTIPLIER, LeagueModifierOperation.MULTIPLY, 1.10))
            )
        )
    )

    val echoes = EchoRegistry(
        listOf(
            EchoDefinition("kbd:echo", "Echo King Black Dragon", "kbd", tier = 1),
            EchoDefinition(
                "kbd:greater",
                "Greater Echo King Black Dragon",
                "kbd",
                tier = 2,
                requirements = listOf(EchoKillsRequirement("kbd:echo", 10))
            )
        )
    )

    val blessings = BlessingRegistry(
        listOf(
            BlessingDefinition(
                id = "equilibrium-seed",
                name = "Equilibrium Seed",
                effects = setOf(FlagLeagueEffect("blessing.equilibrium-seed"))
            )
        )
    )

    /**
     * MAX makes these tier breakpoints declarative absolute floors rather than
     * compounding multipliers. This is intentionally provisional balancing.
     */
    val tierEffects: Map<Int, Set<LeagueEffect>> = mapOf(
        0 to setOf(
            NumericLeagueEffect(LeagueModifierKey.XP_MULTIPLIER, LeagueModifierOperation.MAX, 5.0),
            NumericLeagueEffect(LeagueModifierKey.RUN_ENERGY_DRAIN_MULTIPLIER, LeagueModifierOperation.MULTIPLY, 0.0)
        ),
        3 to setOf(NumericLeagueEffect(LeagueModifierKey.XP_MULTIPLIER, LeagueModifierOperation.MAX, 8.0)),
        5 to setOf(NumericLeagueEffect(LeagueModifierKey.XP_MULTIPLIER, LeagueModifierOperation.MAX, 12.0)),
        8 to setOf(NumericLeagueEffect(LeagueModifierKey.XP_MULTIPLIER, LeagueModifierOperation.MAX, 16.0))
    )

    val effects = LeagueActiveEffectResolver(
        relics = relics,
        fragments = fragments,
        masteries = masteries,
        pacts = pacts,
        blessings = blessings,
        tierEffects = tierEffects
    )
}
