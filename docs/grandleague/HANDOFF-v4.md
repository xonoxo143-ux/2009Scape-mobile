# HANDOFF — 2009Scape Homebrew / Grand League

**Created:** 2026-08-18 America/New_York  
**Purpose:** This document assumes the next chat has no usable conversation memory. Treat this archive as authoritative project context.

---

## 1. User intent and working rules

The user is building a custom homebrewed 2009Scape, ultimately mixing 2009-era RuneScape with selected OSRS League systems, RS3 systems, modern conveniences, and original systems.

Immediate focus: **Grand League**, a permanent mega-League combining/adapting the useful mechanics of all historical OSRS Leagues.

Working rules that matter:

1. **Local-first development.** Use the unpacked workspace as the primary source of truth.
2. **GitHub is infrastructure, not the editor.** Use it for milestone CI, special workflows, and eventual publication—not every tiny source edit.
3. **Do not make an APK on every edit.** APK creation is deliberate/manual and should replace one latest APK rather than accumulate artifacts/builds.
4. **Do not make the user the regression tester.** Automated unit/integration/simulation/client tests should carry routine validation. The user should mainly judge subjective progression/UI/game feel.
5. **Avoid infrastructure rabbit holes.** If validation machinery starts taking more work than the feature, simplify it and return to game implementation.
6. **Build horizontal/vertical completeness before obsessive polish.** The six-hour Production Prodigy adapter promotion loop was explicitly judged a bad workflow.
7. The current clean Grand League vertical slice **supersedes** the old Production Prodigy GitHub patch stack.

---

## 2. Canonical source state

Canonical workspace root:

```text
2009scape-homebrew/
```

The local Git repository intentionally tracks the Grand League overlay/QA files rather than every upstream 2009Scape file.

Known checkpoint before handoff documentation:

```text
ef8cfcb Implement Grand League vertical slice
```

Run this immediately after unpacking:

```bash
bash qa/grandleague/run.sh
```

Expected output at handoff time:

```text
GRAND LEAGUE VERTICAL SLICE PASS
points=1450 tier=6 tasks=9
regions=[asgarnia, karamja, misthalin, wilderness] fragments=[homewrecker, trailblazer]
masteries=[combat-root, melee-echo] pacts=[demonic-root, echo-pact] echoes={kbd:echo=1}
GRAND LEAGUE SHARED-TRIGGER PASS
GRAND LEAGUE STRESS PASS: 2500 tasks / 250000 signals in ~325ms
GRAND LEAGUE SERVER ADAPTER PASS: points=450 tier=3
GRAND LEAGUE LOCAL GATE PASS
```

Small runtime differences in the stress-test milliseconds are expected.

---

## 3. What is actually implemented now

### Grand League domain/core

Located under:

```text
server/2009scape-master/Server/src/main/content/global/leagues/core/
```

Core files:

- `LeagueModel.kt`
- `LeagueProfile.kt`
- `LeagueProfileCodec.kt`
- `LeagueContent.kt`
- `LeagueEngine.kt`
- `LeagueView.kt`

Currently implemented/proven concepts:

- event-driven task completion
- exact-once task point awards
- indexed task dispatch
- tier progression
- exact-once tier rewards
- traditional relic selection (one selected relic per tier)
- region-token unlocks
- fragment unlock/equip
- fragment-set activation
- combat mastery prerequisite graph and point spending
- Demonic Pact prerequisite graph and point spending
- Echo boss eligibility and kill records
- versioned persistence round-trip
- UI-facing overview/task view models

### 2009Scape bridge

Primary file:

```text
server/2009scape-master/Server/src/main/content/global/leagues/GrandLeagueManager.kt
```

It currently bridges Grand League into central 2009Scape player/event/save seams rather than patching every skill.

It listens to/uses central signals including:

- resource production
- NPC kills
- XP gain
- quest completion
- total-level milestone derivation
- quest-point milestone derivation

League state is persisted under one versioned player attribute:

```text
/save:grand-league:profile
```

### Generic quest-completion signal

The server has a central quest-completion event added so League tasks do not have to patch every quest individually.

Relevant tracked files:

```text
server/2009scape-master/Server/src/main/core/api/Event.kt
server/2009scape-master/Server/src/main/core/game/event/Events.kt
server/2009scape-master/Server/src/main/core/game/node/entity/player/link/quest/Quest.java
```

### Server-side acceptance test

```text
server/2009scape-master/Server/src/test/kotlin/content/global/leagues/GrandLeagueVerticalSliceTests.kt
```

### Dependency-free local QA

```text
qa/grandleague/
```

Important tests:

- `VerticalSliceAcceptance.kt`
- `SharedTriggerAcceptance.kt`
- `StressAcceptance.kt`
- `adapter-stubs/src/AdapterAcceptance.kt`

One command:

```bash
bash qa/grandleague/run.sh
```

---

## 4. Critical bug already found and fixed

The stress test exposed a serious scalability bug:

> If several tasks watched the same trigger key, one incoming signal could increment the shared progress counter once **per candidate task**, causing duplicated progress.

This is fixed.

There is a dedicated shared-trigger regression test proving:

```text
1 signal = 1 progress increment
```

regardless of how many tasks observe that signal.

Do not remove that test.

---

## 5. Current bootstrap progression

The current content is intentionally a tiny vertical-slice catalogue, not the final Grand League content corpus.

At handoff, the acceptance progression reaches:

```text
1450 League points
Tier 6
9 completed bootstrap tasks
```

Regions include:

```text
Misthalin
Karamja
Asgarnia
Wilderness
```

Important geography correction already made:

- Graardor progression uses **Asgarnia**.
- Echo KBD requires **Wilderness**.

Current sample systems exercised:

```text
Relic: Endless Harvest
Fragments: Trailblazer + Homewrecker
Fragment set: Mobility
Masteries: Combat Root + Melee Echo
Pacts: Demonic Root + Echo Pact
Echo: KBD Echo kill record
```

These names/content are bootstrap proof points. They are not a statement that the historical import is complete.

---

## 6. Grand League target architecture/content

The long-term combined progression concept is:

```text
Tasks
↓
League Points
↓
Relic Tiers
↓
Traditional Relics
↓
Region Unlocks
↓
Fragments
↓
Fragment Set Effects
↓
Combat Masteries
↓
Demonic Pact Tree
↓
Echo Bosses
↓
Echo Equipment
↓
Prestige / Endgame Tasks
```

Historical OSRS League sources to cover:

1. Twisted League
2. Trailblazer League
3. Shattered Relics
4. Trailblazer Reloaded
5. Raging Echoes
6. Demonic Pacts

Previously agreed scope direction:

- roughly **1,500–2,500 tasks**
- permanent mode
- early relic choices meaningful, but alternate relics should eventually become obtainable at high cost so a permanent character can ultimately access implemented content
- Shattered Relics fragments remain a modular customization layer
- Raging Echoes Combat Masteries coexist with Demonic Pact progression
- Echoes should be adapted to 2009 bosses such as KBD, KQ, DKS, Chaos Elemental, Barrows, GWD bosses, Corp, Jad, Tormented Demons, etc.
- potential Echo difficulty ladder: Normal -> Echo -> Greater Echo -> Grand Echo
- modern tasks referencing content absent from 2009Scape should generally be **adapted to 2009 equivalents first**, not used as an excuse to port every modern OSRS area immediately

---

## 7. Next implementation priority

Do **not** return to polishing Production Prodigy one crafting method at a time.

The next good work is broad content expansion on top of the now-proven vertical slice:

1. Expand the task catalogue/data format substantially.
2. Expand real tier/relic definitions across historical Leagues.
3. Expand region definitions/unlock costs/prerequisites.
4. Add real fragment pools/set effects.
5. Add Combat Mastery tree content.
6. Add Demonic Pact tree content.
7. Add initial Echo boss catalogue/rewards.
8. Keep persistence/view models compatible as content expands.
9. Run `qa/grandleague/run.sh` frequently.
10. Use one real Maven integration run after a **meaningful batch**, not after every tiny adapter.

A good next milestone is a playable early/midgame Grand League content slice with dozens/hundreds of tasks and several genuine choices—not another infrastructure rewrite.

---

## 8. UI direction already agreed

Eventually Grand League should expose a proper RuneScape-native interface suite:

- League side tab / hub
- Tasks
- Relics
- Regions
- Fragments
- Combat Masteries
- Demonic Pacts
- Echoes
- passive effects
- stats

Task UI should support filters such as:

- completion
- difficulty
- region
- skill
- boss
- source League
- points
- can-complete-now
- nearly-complete

The domain already has UI-facing `overview()` / task view APIs so the client/interface layer should consume a clean view model instead of reaching directly into persistence internals.

Mobile is first-class. Eventually test both desktop fixed/resizable and Android landscape/touch behavior.

---

## 9. Client/mobile source inventory

This handoff archive includes source archives under `supporting-sources/`:

```text
2009Scape-mobile-master.zip
rt4-client-lwjgl-mobile-callbacks.zip
apache-maven-3.9.16-bin.tar.gz
```

The current workspace contains the full 2009Scape server source under:

```text
server/2009scape-master/
```

Important previous client/mobile findings:

- `2009Scape-mobile` is mostly an Android/Pojav/Boardwalk launcher/runtime shell.
- It historically embeds a compiled `rt4.jar`.
- The editable RT4 branch is `lwjgl-mobile-callbacks`.
- RT4 can be source-built locally from its vendored jars without requiring Gradle for the client compile path.
- A rebuilt RT4 client was previously run under Xvfb and could reach the normal JS5/network socket path.
- RT4 supports `-DconfigFile=...`, useful for pointing a client at localhost.
- A no-plugin-directory desktop QA crash was identified/fixed in earlier exploratory work, but **those client changes are not canonical in the current Grand League workspace unless explicitly re-applied later**. Treat the included RT4 ZIP as source material, not as already-integrated current code.
- Android/Pojav offline JRE packaging became an infrastructure rabbit hole and was deliberately parked.

Do not restart Android packaging work until the game/server milestone actually needs an APK.

---

## 10. GitHub repositories and their roles

Known user repos:

### `xonoxo143-ux/2009Scape-mobile`

- initially just a fork of the mobile repo
- user explicitly considers it disposable
- eventual destination for the finished custom/homebrewed project
- may be aggressively replaced/restructured later
- do not treat its current upstream fork history as sacred

### `xonoxo143-ux/apk`

Support/tooling repo.

It contains experiments for:

- offline server/Maven cache bootstrap
- Android toolchain/bootstrap work
- old Grand League patch/CI staging

Important:

> The old Production Prodigy patch promotion chain in `apk` is **not canonical project source**.

It was the source of the six-hour workflow failure. Do not resume from “jewellery candidate”, “stage4b”, or similar old status. The canonical source is this local handoff workspace.

The server-cache bootstrap itself did successfully prove that upstream 2009Scape could build/test online and then rebuild/test offline. That infrastructure can be reused later for milestone validation.

---

## 11. APK policy

Do not generate an APK on every source edit.

Desired eventual behavior:

- normal CI: compile/test only
- manual APK build only when useful
- APK should be pushed to a dedicated `apk` branch/repository location
- keep **one latest development APK**, not hundreds of historical APK commits/artifacts
- no routine `upload-artifact` accumulation for APKs
- intentional releases can later be preserved separately

---

## 12. Historical failed workflow — do not repeat

A previous approach spent hours doing this:

```text
edit tiny production adapter
→ create patch
→ upload patch to apk repo
→ edit workflow
→ run full Maven suite
→ publish status branch
→ promote one tiny adapter
→ repeat
```

It eventually reached a Crafting jewellery compile error involving `ResourceProducedEvent`, but this entire path was intentionally abandoned because the validation loop had become slower than development.

Do not pick that up as unfinished canonical work.

The corrective strategy is:

```text
implement meaningful local batch
→ run local League QA repeatedly
→ checkpoint locally
→ one milestone integration run
```

---

## 13. QA philosophy

Release/acceptance eventually should cover:

```text
compile
existing 2009Scape tests
League unit tests
task database validation
progression validation
relic tests
fragment tests
mastery tests
pact tests
Echo tests
save/relog tests
interface packet tests
regression tests
simulation/fuzz tests
performance/soak tests
visual regression
```

But do not turn this checklist into a reason to stop feature work. Add tests alongside real systems and batch expensive integration validation.

---

## 14. Recovery instructions

After receiving this ZIP in a fresh chat:

1. Unpack it.
2. Read this file completely.
3. Treat `workspace/2009scape-homebrew` as canonical.
4. Run:

```bash
cd workspace/2009scape-homebrew
bash qa/grandleague/run.sh
```

5. Check:

```bash
git log --oneline --decorate -5
git status --short
```

The full upstream server tree appears largely untracked by the small local overlay Git repository; this is expected. The tracked files are the Grand League overlay and QA files.

6. Continue broad Grand League content implementation from the vertical slice.
7. Do not ask the user to reconstruct the six-hour prior conversation; this document is intended to replace that context.

---

## 15. User-facing project philosophy

The project is not trying to preserve 2009Scape architecture for its own sake.

Use 2009Scape as a working foundation/runtime. Rewrite poor architecture when justified. Import/adapt good RuneScape systems based on their merits. Keep major systems modular and persistence versioned. Prefer clean event/modifier seams over thousands of scattered `if (league)` conditionals.

The user wants forward progress, not endless planning, scaffolding, or tiny CI promotions.

---

## 16. Continuation checkpoint — broad content batch

This section supersedes the old statements that production content is only a 9-task catalogue.

Feature commit:

```text
dabc142 Expand Grand League content catalogue
```

The 9-task `GrandLeagueBootstrapContent` still exists, but only as an immutable regression fixture. Production `GrandLeagueSession()` now defaults to `GrandLeagueContent.create()`.

Current production catalogue:

```text
157 tasks
8 tiers (0 through 8, with tier 0 as the starting state)
22 traditional relic choices
7 unlockable regions + Misthalin/Karamja starting regions
24 fragments
10 fragment set effects
19 Combat Mastery nodes
31 Demonic Pact nodes
12 adapted Echo bosses
```

Important implementation changes in this batch:

- task definitions now carry difficulty, historical source League, region, category, and optional reward currencies
- production tasks can award Combat Mastery points and Pact Points exactly once alongside League points
- task views expose this metadata and support domain-level filters for completion, difficulty, source, region, category, points, nearly-complete state, and text search
- fragments can contribute to multiple set effects instead of the old single-set limitation
- traditional relic selection is now locked once a relic is chosen for that tier
- region definitions can express prerequisites
- the production content has seven 2009-era unlockable regions: Asgarnia, Desert, Fremennik, Kandarin, Morytania, Tirannwn, Wilderness
- Combat Masteries now contain three six-node style branches plus the neutral root
- Demonic Pacts now contain a connected 31-node first-pass tree rather than the two-node bootstrap proof
- Echo catalogue now covers KBD, KQ, Dagannoth Kings, Chaos Elemental, Barrows, GWD bosses, Corp, Jad, and Tormented Demons
- `qa/grandleague/ContentCatalogueAcceptance.kt` validates the production-sized catalogue end-to-end
- the QA script no longer recompiles the entire League core a second time for the server adapter test

Current local gate after the batch:

```text
GRAND LEAGUE VERTICAL SLICE PASS
GRAND LEAGUE SHARED-TRIGGER PASS
GRAND LEAGUE STRESS PASS
GRAND LEAGUE CONTENT CATALOGUE PASS
GRAND LEAGUE SERVER ADAPTER PASS
GRAND LEAGUE LOCAL GATE PASS
```

The production catalogue acceptance currently reports:

```text
tasks=157 relics=22 fragments=24 masteries=19 pacts=31 echoes=12
points=12160 tier=8 regions=9
```

A real Maven test run was attempted after this meaningful batch. It could not start because the handoff does not include the Maven dependency cache and this execution environment had no DNS access to Maven Central. The failure was dependency resolution for `org.jetbrains.kotlin:kotlin-maven-plugin:1.8.20`, not a Kotlin/source compile error. The dependency-free domain compilation and server-adapter compilation both pass with `-Werror`.

### Next useful implementation work

Do not go back to infrastructure polishing. Continue from here with game mechanics:

1. Implement the actual modifier/effect layer for traditional relics instead of definitions only.
2. Implement fragment base effects and set-effect modifiers.
3. Implement Combat Mastery effects using shared combat modifier seams.
4. Expand Demonic Pacts toward the full large connected tree and wire their actual combat effects.
5. Add Echo-orb eligibility, actual encounter/reward hooks, and the pact-reset rewards from unique Echo kills.
6. Add login/reconciliation signals for existing player state so total level, quest points, and similar milestone tasks do not require a fresh triggering action after loading an old account.
7. Expand the task corpus in meaningful batches toward the 1,500–2,500 target, adapting unavailable modern OSRS tasks to 2009 equivalents.
8. Only return to Maven/full server integration once dependency access/cache is available or after another meaningful batch.

---

## 17. Continuation checkpoint — shared modifier engine / Batch A

This section supersedes the prior statement that relics/fragments are definitions only.

Feature commit:

```text
38cd08a Add Grand League shared modifier engine
```

Grand League now has a typed, dependency-free effect system in `LeagueEffects.kt`. Content entries carry generic effect definitions; `GrandLeagueSession` resolves equipped/selected relics, fragments, fragment sets, masteries, and pacts into a cached modifier snapshot. Cache invalidation occurs on all relevant loadout changes.

### Live shared seams

The following Batch-A mechanics are wired into actual 2009Scape server paths rather than being catalogue-only data:

- gathering quantity modifiers for Woodcutting, Fishing, and Mining
- automatic banking of generated gathering resources, including Endless Harvest's bonus-only bank semantics
- production bonus output for classified Cooking, Smithing, and Crafting producers
- single-input production material conservation where the event identifies the consumed original material
- shared production-speed acceleration for production activities inheriting `SkillPulse`
- XP scaling through the central `Skills` XP award seam, including Equilibrium's below-account-average curve
- run-energy drain/regeneration modifiers through `Settings`
- Fire Sale main-stock coin price and shop-stock consumption behavior through `Shop`
- farming growth, yield, disease reduction, and disease immunity through `Patch`

The event layer now classifies `ResourceProducedEvent` by activity and skill, allowing generic League logic to operate without scattering relic IDs through individual skill implementations.

### Batch-A content currently carrying real effects

Traditional relics with active/shared modifier definitions:

- Endless Harvest
- Production Prodigy
- Trickster (run-energy portion live; skill-success portions still pending)
- Fairy's Flight (capability defined; teleport action pending)
- Globetrotter (capability defined; teleport action pending)
- Banker's Note (capability defined; note/un-note action pending)
- Fire Sale
- Infernal Gathering (auto-process capability/chance defined; processing action pending)
- Equilibrium
- Farmer's Fortune

Fragments with Batch-A definitions include Trailblazer, Homewrecker, Greedy Gatherer, Personal Banker, Production Prodigy, Rock Solid, Certified Farmer, Chef's Catch, and Smooth Criminal. The mobility/gathering/banking/production fragment-set effects are also defined and resolved dynamically.

### Important behavior/safety fixes included

- Fire Sale is restricted to main NPC shop stock. Player-supplied stock is not made free or infinite.
- fractional farming/resource/output bonuses use probabilistic quantity math instead of unconditional ceiling, avoiding a fake +1 on every single-item action
- glassmaking's resource event now reports one actual product per pulse instead of the remaining batch size, preventing League production bonuses from multiplying an incorrect signal
- the original 9-task bootstrap remains independent and green as a regression fixture

### Automated acceptance coverage

New tests:

- `qa/grandleague/ModifierEngineAcceptance.kt`
- `qa/grandleague/ServerSeamAcceptance.sh`
- expanded server adapter stubs/acceptance covering resource banking and the Java-safe shared modifier accessors

Latest local gate:

```text
GRAND LEAGUE VERTICAL SLICE PASS
points=1450 tier=6 tasks=9
GRAND LEAGUE SHARED-TRIGGER PASS
GRAND LEAGUE STRESS PASS: 2500 tasks / 250000 signals in 830ms
GRAND LEAGUE CONTENT CATALOGUE PASS
tasks=157 relics=22 fragments=24 masteries=19 pacts=31 echoes=12
points=12160 tier=8 regions=9
GRAND LEAGUE MODIFIER ENGINE PASS
GRAND LEAGUE SERVER ADAPTER PASS: points=470 tier=2
GRAND LEAGUE SERVER SEAMS PASS
GRAND LEAGUE LOCAL GATE PASS
```

### Known Batch-A holes — finish these without redesigning the engine

1. Wire Infernal Gathering's defined auto-process modifier to real fish/log/ore conversion semantics and secondary XP.
2. Implement Banker's Note note/un-note actions and UI entry point.
3. Implement Fairy's Flight and Globetrotter teleport menus/destination rules using unlocked-region filtering.
4. Wire Trickster/Smooth Criminal thieving success/auto-repeat, Agility fail reduction, and Hunter success modifiers into central skill seams.
5. Cover production-speed classes that inherit raw `Pulse` rather than `SkillPulse` (notably some silver/glass paths) or migrate them behind a common production pulse seam.
6. Upgrade material conservation from the current single-original return to recipe-aware multi-input conservation.

### Next main milestone after Batch A

Proceed to **Batch B — shared combat modifier engine**, not task-corpus inflation yet. Add central combat primitives for accuracy, damage, attack interval, ammo/rune conservation, extra/repeat hits, lifesteal, Prayer restore/drain, special-energy behavior, incoming-damage reduction/reflection, execute thresholds, death interception, low-HP scaling, and target debuffs. Then bind the combat relics, combat fragments/sets, the 18 style Mastery nodes, and Demonic Pact combat branches to those primitives.

Do not implement these as one-off checks in every weapon/NPC. The same architectural rule applies: content definitions -> resolved modifier snapshot -> a small number of shared combat seams.

---

## 18. Continuation checkpoint — Batch A skill completion + Batch B combat engine

This section supersedes the pending-state details in section 17.

Feature commits:

```text
6c7d72d Finish Grand League Batch A skill effects
a8383bf Wire Grand League core combat modifiers
023a2fc Expand Grand League combat sustain and survival
```

### Batch A changes now live

The non-combat modifier engine remains the same architecture, but several previously inert effects are now wired into real server paths:

- Infernal Gathering now resolves gathered logs/fish/ores centrally and converts them into Firemaking/Cooking/Smithing outcomes with real level checks and secondary XP.
- Endless Harvest + Infernal Gathering interaction is deterministic under adapter acceptance: the base processed resource remains in inventory while the relic-generated bonus follows Endless Harvest's bonus-bank behavior.
- iron ore uses the Grand League adaptation of direct steel-bar processing without coal; other standard ores map to the appropriate 2009Scape bar definitions.
- Trickster now modifies the central Thieving success threshold, Agility fail chance, and the main Hunter success paths (trap setting, trap hooks, butterfly net, and falconry).
- raw-Pulse Cooking/silver/glass production paths now use the common production-delay helper.
- dairy and glassmaking resource signals were corrected so production modifiers operate on the item actually produced per pulse, not the remaining batch count.

Still pending from Batch A:

1. Trickster/Smooth Criminal automatic repeated pickpocketing needs a state-safe repeat pulse; success modifiers are already live.
2. Banker's Note still needs its portable note/un-note service and UI entry point.
3. Fairy's Flight and Globetrotter still need their teleport surfaces/destination filtering.
4. production material conservation still needs recipe-aware multi-input refunds instead of only the current single-original material seam.

### Batch B shared combat primitives now live

`LeagueEffects.kt` now exposes shared combat scopes (`COMBAT`, `MELEE`, `RANGED`, `MAGIC`) and generic combat modifier channels. The server consumes them through central combat paths rather than individual weapon checks.

Live primitives:

- outgoing accuracy multiplier
- outgoing damage multiplier
- attack-interval multiplier
- defence penetration
- ranged ammunition conservation
- magic rune conservation
- repeat/secondary-hit chance and damage fraction
- lifesteal
- Prayer restoration from damage
- incoming damage multiplier
- reflected damage
- low-HP outgoing damage scaling
- special-attack energy cost multiplier
- special-energy restoration multiplier

Live central server seams:

- `CombatSwingHandler.isAccurateImpact` — accuracy + defence penetration
- `CombatSwingHandler.getFormattedHit` — outgoing damage / low-HP scaling
- `CombatSwingHandler.adjustBattleState` — generic repeat-hit creation without replacing weapon-native secondary hits
- `CombatPulse` — attack interval
- `RangeSwingHandler.useAmmo` — ammo conservation
- `MagicSpell.meetsRequirements` — rune conservation after requirements are validated
- `ImpactHandler` — actual incoming damage reduction, post-hit sustain, and non-recursive reflected damage
- `Settings.drainSpecial` — special-attack energy cost
- `SkillRestore` — special-energy restoration

### Combat content carrying real effects at this checkpoint

Traditional relics:

- Archer's Embrace — ranged accuracy, speed, ammo conservation
- Brawler's Resolve — melee accuracy, damage, speed
- Superior Sorcerer — magic accuracy, damage, speed, rune conservation
- Soul Stealer — damage-based Hitpoint and Prayer sustain
- Weapon Master — half special cost + doubled natural special restoration
- Berserker — outgoing damage increases smoothly as HP falls

Fragments / sets:

- Bottomless Quiver — ammo conservation
- Arcane Conduit — rune conservation
- Drakan's Touch — lifesteal
- Knife's Edge — low-HP damage scaling
- Twin Strikes / Double Tap / Chain Magic — style-scoped repeat-hit mechanics
- Absolute Unit — incoming damage reduction + reflection
- Melee/Ranged/Magic fragment sets strengthen repeat-hit chance/damage
- Survival set strengthens reduction/reflection
- Prayer set adds additional sustain

Combat Masteries:

- Tier I: style accuracy
- Tier II: style damage
- Tier III: style attack interval
- Tier IV: melee defence penetration / ranged ammo conservation / magic rune conservation
- Tier V: style repeat-hit mechanic
- Tier VI: style capstone damage multiplier

Demonic Pacts with live effects:

- Hellforged Strength
- Bloodrush
- Demonblade
- Sharpsight
- Endless Volley
- Piercing Shots
- Demonbow
- Arcane Hunger
- Rune Siphon
- Elemental Ruin
- Demonstaff
- Thorns
- Stone Skin

The remaining Pact names still exist in the connected 31-node graph but require their specialized mechanics (rhythm/momentum, shields, stored retaliation, kill streaks, debuffs, potion behavior, etc.).

### Automated QA status

The pure domain suite, 250k-signal stress suite, production catalogue acceptance, modifier-engine acceptance, server adapter, and server-seam contracts are green.

The combined `qa/grandleague/run.sh` can occasionally exceed the command wrapper timeout during the second `kotlinc` startup. When that happens, run the adapter compilation/execution and `ServerSeamAcceptance.sh` separately; at this checkpoint both passed after the domain tests passed. This is compile-process startup latency, not a discovered League failure.

A full Maven server build remains blocked in this execution environment by missing cached dependencies plus unavailable Maven Central DNS. Do not treat that dependency-resolution failure as a source compile result.

### Next Batch B work

Do **not** expand the task corpus yet. Finish the gameplay layer first.

Highest-value remaining combat mechanics:

1. Executioner target thresholds with separate ordinary/boss behavior.
2. Undying Retribution and Immortal Shell lethal-hit interception with cooldowns.
3. Guardian companion behavior.
4. Ruinous Powers prayer set / Prayer drain semantics.
5. remaining conditional fragments (Unholy trio, Slayer targeting, Divine Restoration, Venomaster, Fast Metabolism).
6. remaining conditional Pact mechanics: Brutal Rhythm, Crushing Momentum, Windstep, Deadeye, Overcharge, Soulfire, Life Ward, Reprisal, Unyielding, Culling Spree, Evil Eye, Flask of Fervour, Executioner's Mark, etc.
7. then build the Echo boss runtime and Normal -> Echo -> Greater Echo -> Grand Echo escalation on top of these combat primitives.
