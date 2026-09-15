# Single-player engine ownership audit

This is the architectural inventory for turning the current combined 2009Scape + RT4 runtime into a native Android single-player game without needlessly rewriting the game itself.

Build 42 (`adb4a3947c94918d2e8d98a67ac407b93833aaa9`) is the presentation checkpoint before this migration. Keep the proven 765x503 startup contract and the 1289x503 post-login Fold viewport unchanged while the ownership boundaries below are introduced.

The governing rule is:

> Rewrite the machinery, not the game.

The old RT4 interface layout is not a compatibility requirement. The 2009-era look may be reused, but Android is allowed to own the UI completely.

## Ownership classes

- **CONTENT** - definitions, maps, scripts, dialogue text, quests, recipes, drops, shops, assets and other authored game content. Preserve.
- **GAME CORE** - authoritative simulation and player/world state. Preserve behavior; refactor only to remove presentation/transport coupling.
- **ANDROID** - application UI, touch/keyboard, lifecycle, save selection, settings and eventually platform audio/presentation.
- **RENDERER** - the retained RT4 3D scene/cache/model/animation pipeline. Keep as a bounded renderer until there is a reason to replace it.
- **LEGACY** - protocol, desktop/AWT/Cacio or RT4-interface machinery that exists only because the original system was a networked desktop client/server game. Remove after its replacement is authoritative.

## Main finding

A blank-slate gameplay rewrite is not justified.

`Player` is already the natural game-state aggregate. It directly owns the inventory, equipment, bank containers, spellbook manager, dialogue interpreter, quest repository, prayer manager, music player and related player state. Skills and combat are likewise world-side systems. The server-side model is therefore much closer to the desired single-player core than the current UI makes it appear.

The expensive coupling is mostly at the edges:

1. authoritative world systems call `PacketDispatch`, `PacketRepository`, `InterfaceManager`, component IDs, varps/varbits and client scripts to present themselves;
2. RT4 interprets those presentation bytes/widgets and sends interface-button actions back;
3. Android currently hosts RT4 and its retained desktop assumptions.

The migration target is therefore:

`Android semantic UI/input -> local game API -> authoritative Player/world state`

and, independently:

`authoritative world scene state -> bounded RT4 renderer -> Android surface`

RT4 should eventually know how to render the world, not how banking, quest journals, skill menus or League configuration work.

## System inventory

### Player aggregate and session

**Current authority:** `core.game.node.entity.player.Player` plus retained login/parser/init logic.

**Keep as GAME CORE:**
- Player entity and properties;
- containers and player links;
- initialization hooks/content registration;
- death/combat/zone state;
- local human and AI entities.

**Replace/peel away:**
- `IoSession` as an identity/lifecycle requirement once retained codecs no longer need it;
- login-success/header ceremony that exists only to satisfy RT4;
- remote account/session concepts.

**Target:** a `LocalPlayerSession`/local game authority creates and owns one human Player directly. Save loading and normal initialization remain reused.

### Skills

**Current authority:** `core.game.node.entity.skill.Skills`.

The skill model already owns static levels, dynamic levels, XP, XP rules, level calculations and semantic XP events. Its UI coupling is primarily `SkillLevel` presentation packets, level-up interfaces and content that opens skill guides.

**Keep as GAME CORE:** all skill state, formulas, XP events and skill-training content.

**ANDROID:** native skill grid, skill details/guide navigation and level-up presentation.

**LEGACY after cutover:** RT4 Stats tab component 320, skill-guide component 499, level-up widgets and skill-value presentation packets used only for UI.

**First-class API needed:** read skill snapshot; subscribe to XP/level changes; request semantic skill-guide data.

### Inventory and items

**Current authority:** `Player.inventory`, `Container`, `Item`, `ItemDefinition`, interaction listeners.

**Keep as GAME CORE/CONTENT:** item ids/definitions, stack rules, container mutation, item options, equip/use/drop semantics, requirements and item content scripts.

**ANDROID:** inventory grid, selection state, long-press/context actions and examine presentation.

**RENDERER:** ground-item world models remain scene presentation.

**LEGACY after cutover:** inventory widgets/container packets whose only consumer is the RT4 inventory interface.

**First-class API needed:** slot/item snapshot and semantic `itemAction(slot, action)` calls. Do not expose component IDs to Android.

### Equipment

**Current authority:** `EquipmentContainer`, `EquipHandler`, equipment plugins and combat properties.

The existing equipment-tab listener is mostly an adapter: it maps widget buttons onto `EquipHandler`, `InteractionListeners`, death-container calculation and equipment state.

**Keep as GAME CORE:** equip/unequip validation, requirements, bonuses, degradation, operate actions, weapon interfaces and worn appearance state.

**ANDROID:** equipment screen, bonus display, items-kept-on-death screen.

**RENDERER:** worn player models/appearance updates.

**LEGACY after cutover:** components 387/667/670 and their container/string/interface-setting packets.

**First-class API needed:** equipment snapshot, calculated bonus snapshot, equip inventory slot, unequip equipment slot, operate slot, items-kept-on-death snapshot.

### Quests and diaries

**Current authority:** `QuestRepository`, `Quest`, `content.data.Quests` and quest plugins/scripts.

Quest progress is already semantic state (`quest index -> stage`, quest points). The largest coupling is journal/reward rendering: many quests imperatively write strings into component 275/277 and update varps/varbits for RT4 coloring.

**Keep as CONTENT/GAME CORE:** quest registration, stage state, prerequisites, rewards, quest scripts and authored journal text.

**ANDROID:** quest list, filters, journal, requirements, completion/reward screen and diaries UI.

**Migration technique:** do not rewrite every quest journal. Introduce a `QuestJournalSink`/presentation collector. Existing `line(...)`, journal-title and reward helpers should populate a semantic journal model; the legacy RT4 adapter can still translate that model to component strings during transition.

**LEGACY after cutover:** quest-tab component mapping and varp/varbit values whose only purpose is coloring the RT4 quest list.

### Dialogue and input prompts

**Current authority:** `DialogueInterpreter`, dialogue plugins/files and their state/actions.

**Keep as CONTENT/GAME CORE:** dialogue trees, current dialogue key/state, NPC/item identity, facial-expression intent, options and callbacks.

**ANDROID:** dialogue panel, continue/options UI, text/number/name prompts and keyboard ownership.

**Migration technique:** add a semantic `DialoguePresentation` model (speaker, portrait/model intent, lines, options, prompt type). Retain the interpreter and content; replace `Component`/packet emission behind its send helpers.

**LEGACY after cutover:** dialogue component IDs and Android keyboard heuristics based on RT4 chatbox widget occupancy.

### Prayer

**Current authority:** `Player.prayer`, `Prayer`, `PrayerType`.

This is already particularly clean: the tab listener mostly maps a button to `player.prayer.toggle(prayer)`.

**Keep as GAME CORE:** prayer points, drain, requirements, effects, protection logic and toggles.

**ANDROID:** prayer grid and active-state feedback.

**First-class API needed:** prayer snapshot and `togglePrayer(id)`.

### Magic and spellbooks

**Current authority:** `SpellBookManager`, `MagicSpell`, spell listeners and magic content.

The current Magic tab primarily maps interface buttons to `SpellListeners` / `MagicSpell.castSpell`.

**Keep as GAME CORE/CONTENT:** spell definitions, requirements, runes, cooldowns, target rules, autocast and effects.

**ANDROID:** spellbook UI, filtering, selection and target-mode presentation.

**RENDERER:** projectiles, animations and graphics produced by spells.

**First-class API needed:** current spellbook/spell availability snapshot and semantic cast/select-target commands.

### Combat

**Current authority:** combat pulse/swing handlers, entity properties, equipment, timers and combat content.

**Keep as GAME CORE:** attack validation, styles, max hit/accuracy, damage, special attacks, death, aggression and combat movement.

**ANDROID:** combat-style controls, special-attack control, autocast selection and status presentation.

**RENDERER:** combat animations, hitsplats, projectiles, graphics and entity appearance.

**LEGACY after cutover:** weapon-tab component IDs and interface-button mappings. Never move combat authority into Android.

### Bank

**Current authority:** `BankContainer` plus the inventory/container model.

Deposit/withdraw/note logic is already world-side. `BankContainer.open()` and listeners are heavily coupled to components 762/763, scripts and interface settings.

**Keep as GAME CORE:** bank contents, deposit/withdraw, note behavior, capacity, bank pin rules if retained, Ironman restrictions and item validation.

**ANDROID:** full-screen/mobile-first bank UI, search, quantity selection and tabs if desired.

**First-class API needed:** bank snapshot, inventory snapshot and semantic deposit/withdraw actions. `open bank` should become a UI-state event, not an RT4 component operation.

### Shops

**Current authority:** `Shop`, shop stock containers and pricing logic.

**Keep as GAME CORE/CONTENT:** stock, restock, currencies, buy/sell prices and transaction validation.

**ANDROID:** shop catalog and buy/sell UI.

**First-class API needed:** shop snapshot plus semantic buy/sell quantity commands.

### Movement and pathfinding

**Current authority:** world movement pulses and `core.game.world.map.path`.

**Keep as GAME CORE:** collision, pathfinding, destination validation, run state and interaction movement.

**ANDROID:** gestures/taps produce semantic world destinations.

**RENDERER:** camera and movement interpolation/visual entity positions.

The existing direct `LocalCommands` walking paths are already the correct direction. Do not rebuild movement in Android.

### NPCs, scenery and world interactions

**Current authority:** world repository, NPC/entity classes, scenery, interaction listeners and content plugins.

**Keep as GAME CORE/CONTENT:** spawns, AI, dialogue/action scripts, drops, object replacement, timers and interaction rules.

**ANDROID:** receives available actions/selection context; sends semantic action selection.

**RENDERER:** models, animations, scene placement and click-pick results for the 3D world.

The existing `npcAction`, `sceneryAction`, `groundItemAction` local commands are transitional semantic seams and should evolve away from protocol option numbers toward named/action identifiers where practical.

### Ground items

**Current authority:** `GroundItemManager`/world state.

**Keep as GAME CORE:** ownership, lifetime, amount and pickup rules.

**RENDERER:** visible ground item scene updates.

**ANDROID:** only action/context presentation where useful.

### World tick, timers and events

**Current authority:** `GameWorld`, pulser/task/timer/event systems.

**Keep as GAME CORE.** This is mature simulation machinery and should not be rebuilt merely to become Android-native.

Android lifecycle should pause/resume the authoritative scheduler through one explicit local runtime boundary; it should not become the scheduler.

### Persistence and saves

**Current authority:** retained Player parser/saver plus the local file account provider.

`PlayerSaver` already serializes core data, skills, containers, quests, spellbook, appearance, settings, familiar/house/music/etc. This is valuable coverage.

**Keep/adapt as GAME CORE:** serialization knowledge and backward compatibility.

**ANDROID:** save-slot/profile selection, backup/export UX and platform file ownership.

**Target:** migrate from hosted-account-shaped persistence to explicit single-player save slots without throwing away the proven field serialization. New saves should be atomic and versioned. The local world remains the authority for when state is safe to serialize.

### Audio/music

**Current authority:** world music/audio intent plus retained client mixer/audio playback.

**Keep as CONTENT/GAME CORE:** music-zone rules, sound ids, positional intent and game-trigger timing.

**ANDROID eventually:** audio focus, lifecycle and final playback/mixing if replacing the retained audio stack proves worthwhile.

Do not recreate assets.

### League/relics

**Current authority:** `core.local.LeagueRuntime` and the Demonic Pacts overlay/content.

**Keep as GAME CORE/CONTENT:** rule hooks, points/tasks/relic state and gameplay modifiers.

**ANDROID:** League board, task browser, reset/selection UX.

The full-screen RT4 League UI is transitional. Its existence is not a reason to preserve RT4 widgets.

Relic-board compilation and publication are separate from proof that every modern relic mechanic has a complete 2009Scape equivalent; feature completeness remains its own ledger.

### Social systems and fake players

**Current authority:** retained local communication models/repository plus AI player entities.

Fake players stay world entities, never network clients.

**Keep selectively as GAME CORE/CONTENT:** local friends/clan semantics only if they benefit simulated inhabitants.

**REMOVE:** global/remote world presence, remote moderation transport and hosted-server assumptions.

### 3D renderer and cache

**Current authority:** RT4 cache/model/terrain/entity/animation renderer.

**Keep as RENDERER for now:**
- cache decoding needed by visuals;
- terrain/scene construction;
- models/textures/animations;
- player/NPC visual state;
- spot animations/projectiles;
- camera and picking where it is tightly coupled to the scene.

Build 42 already lets Android consume completed RT4 software frames directly instead of Cacio compositing.

**Shrink RT4 responsibility over time:** inventory/skills/quests/bank/dialogue/settings/League and other application UI should move out first. Scene presentation codecs may remain longer because replacing them buys much less than replacing the UI boundary.

### Android shell

**ANDROID should own:**
- activity/surface lifecycle;
- touch, keyboard and accessibility;
- mobile UI/navigation;
- save/profile selection;
- settings;
- pause/resume and audio focus;
- update/install UX;
- presentation of semantic game state.

It must not own authoritative XP, item, quest, combat, pathfinding or drop logic.

### AWT/Cacio and desktop compatibility

**LEGACY.** Build 42 bypasses Cacio for active completed-frame presentation but still keeps it for startup/fallback and retained AWT expectations.

Remove it only after the remaining RT4 startup/component/font assumptions no longer need it. Do not change the proven startup geometry merely to accelerate this removal.

### Revision-530 protocol/presentation codecs

The multiplayer audit remains valid, but this ownership audit narrows what we ultimately want to retain.

**Keep temporarily as RENDERER compatibility:** scene rebuilds, entity synchronization, animations/graphics/projectiles and other tightly RT4-bound world visuals.

**Migrate away from codec presentation early:** skills, inventory, equipment, bank, quests, dialogue, shops, prayer, magic selection, settings and League UI.

**REMOVE:** transport/session telemetry and any codec with no remaining renderer consumer.

## New local API boundary

Do not expose RT4 component ids, opcodes, packet classes, varps or client scripts to new Android UI.

Introduce a semantic local-game boundary with two directions.

### State/events to Android

Examples:
- `PlayerSnapshot`
- `SkillSnapshot[]`
- `ItemStackSnapshot[] inventory`
- `EquipmentSnapshot`
- `QuestSnapshot[]`
- `PrayerSnapshot[]`
- `SpellSnapshot[]`
- `BankSnapshot`
- `ShopSnapshot`
- `DialoguePresentation`
- `LeagueSnapshot`
- lightweight events such as XP gain, item/container change, quest-stage change and dialogue change.

Snapshots are views of authoritative world state, not a second state store.

### Commands to GAME CORE

Examples:
- `equipInventorySlot(slot)`
- `unequipSlot(slot)`
- `itemAction(slot, action)`
- `togglePrayer(prayer)`
- `castSpell(spell, target)`
- `deposit(slot, amount)` / `withdraw(slot, amount, noted)`
- `buy(shopSlot, amount)` / `sell(inventorySlot, amount)`
- `chooseDialogueOption(index)` / `continueDialogue()`
- `walk(destination)` / world interaction actions
- `selectRelic(id)` / `resetRelics()`

Commands call existing world logic. They do not reimplement it.

## Presentation-port strategy

Some content currently mixes semantic behavior with RT4 drawing. Rewriting hundreds of scripts would be wasteful. Use ports/adapters instead.

1. Add semantic presentation helpers/ports at the central seams (`DialogueInterpreter`, quest journal/reward helpers, container/state change observers, messages/prompts).
2. Keep a **LegacyRt4Presentation** adapter during migration so current gameplay remains usable.
3. Add an **AndroidPresentation** consumer for the new mobile UI.
4. Move one screen/system at a time to Android.
5. Once Android is authoritative for a system, stop emitting the corresponding RT4 UI packets and delete that legacy adapter path.

This lets old content survive while the machinery underneath it changes.

## Migration order

### Phase 0 - freeze and measure

- Treat Build 42 source as the native-rendering rollback checkpoint.
- Preserve startup/login/display geometry.
- Keep `MULTIPLAYER_BOUNDARY_AUDIT.md` as the transport ledger.
- Add a repeatable ownership/coupling report so UI/protocol dependencies can be measured rather than guessed.

### Phase 1 - player-state API and mobile tabs

Start with **Skills, Inventory, Equipment and Quests** because they are high-value, easy to validate and include the currently troublesome RT4 tabs.

1. expose read-only semantic snapshots from the authoritative Player;
2. add semantic equip/unequip/item commands;
3. build Android-native navigation/screens over those snapshots;
4. leave legacy RT4 tabs available as fallback until parity is proven;
5. then stop opening/emitting those RT4 tab interfaces.

This phase should make broken Skills/Equipment tabs irrelevant rather than spending significant effort rehabilitating them.

### Phase 2 - dialogue, prayer, magic and combat controls

Move dialogue/prompts first because many quests depend on them. Then prayer/magic/combat tabs can become native controls while combat remains entirely world-authoritative.

### Phase 3 - bank and shops

Expose container transactions through semantic commands and replace the most widget/script-heavy economic interfaces with mobile-first screens.

### Phase 4 - persistence and app shell

Create explicit local save slots/profile management around retained parser/saver knowledge. Continue removing hosted-account ceremony.

### Phase 5 - world interaction command cleanup

Replace remaining compatibility packet/opcode routes with semantic local commands. Keep RT4 scene picking/rendering while eliminating protocol-shaped client->world behavior.

### Phase 6 - presentation codec reduction

Stop sending UI-only world->RT4 packets for systems now owned by Android. Keep only the scene/entity/animation codecs RT4 still needs.

### Phase 7 - remove remaining desktop shell

After direct framebuffer and native UI are proven, remove Cacio/AWT startup dependencies in controlled slices. This is deliberately late because it has the highest blast radius.

### Phase 8 - optional renderer replacement

Only evaluate replacing RT4 itself after every gameplay/UI system is isolated behind semantic APIs. At that point a renderer replacement is an implementation choice, not a rewrite of the game.

## Deletion rule

Every migration phase must delete or disable the obsolete path after parity is proven. A "pass" is not complete if it merely adds another compatibility layer forever.

For each subsystem the definition of done is:

1. one authoritative GAME CORE state;
2. one semantic command/state boundary;
3. Android/native presentation works on device;
4. legacy RT4 UI/protocol path is no longer required for that subsystem;
5. regression coverage proves the retained game rules still behave the same.

## Immediate next cut

Implement the read-only **Player UI state boundary** for Skills, Inventory, Equipment and Quest progress without changing any renderer/startup behavior. Then expose semantic equipment/item actions. This creates the data/control plane needed for the first genuinely native Android game screens while keeping the full retained game playable during migration.
