# Multiplayer -> Single-Player Boundary Audit

This file is the migration ledger for the Android single-player refactor.

The target architecture is:

`RT4/Android input -> semantic local command -> one authoritative world -> local presentation -> RT4 renderer`

A retained revision-530 byte format is acceptable only when it is deliberately classified as a **presentation compatibility codec**. Multiplayer transport, remote-session health, master-server assumptions, and external-network state must not control single-player gameplay.

## Status vocabulary

- **DIRECT** - multiplayer transport has been removed; semantic state/action crosses an in-process API.
- **LOCAL CODEC** - bytes remain intentionally because the retained renderer/world codec is useful, but no kernel/network peer exists.
- **REMOVE** - multiplayer/transport-only behavior with no single-player semantic meaning.
- **FIX** - known single-player correctness problem.
- **VERIFY** - architecture looks appropriate but needs runtime coverage.

## Authority and hosting

| Boundary | Status | Current state | Required action |
| --- | --- | --- | --- |
| Gameplay TCP listener | DIRECT | `core.Server` does not start NIO in single-player | Keep CI assertion: no gameplay listener |
| WebSocket listener | DIRECT | Disabled with network host mode | Keep disabled |
| Server stdin console | DIRECT | Not started in single-player | Keep disabled |
| World selection | DIRECT | RT4 synthesizes one local world | Eventually remove world-selection UI/state entirely |
| External authentication | DIRECT | Local login calls retained authenticator/profile store directly | Keep profile persistence; no remote auth ceremony |
| IP/account policing | DIRECT | Bypassed by local login | Keep absent |
| Master-server presence | DIRECT | Not required for local login | Keep absent |
| Android app lifecycle | DIRECT | renderer/world/audio have local pause hooks | Verify resume/save/explicit exit repeatedly |

## Runtime/session lifecycle

| Boundary | Status | Current state | Required action |
| --- | --- | --- | --- |
| Human player creation | DIRECT | World constructs `Player` directly and runs retained `LoginParser` | Keep |
| Player identity | DIRECT | Local account name is canonicalized before `Player` construction | Add invariant before appearance encoding |
| Initial RT4 login success/header/rebuild | LOCAL CODEC | Synthetic revision-530 success/header/rebuild is consumed from memory | Retain until RT4 login state machine can be removed cleanly |
| Normal world -> RT4 stream | LOCAL CODEC | `LocalPresentationBridge` carries retained encoded presentation bytes in memory | Harden invariants; convert control/session messages before cosmetic/UI codecs |
| Legacy gameplay socket object | LOCAL CODEC | RT4 still receives a local `BufferedSocket` facade after login | Remove after remaining client compatibility packets are gone |
| Unexpected client reset | DIRECT | No longer interpreted as implicit logout/relogin | Keep explicit failure reason |
| Explicit logout | VERIFY | World teardown exists, but full UI/logout path needs runtime test | Test once normal gameplay audit is stable |
| 20-second socket ping timeout | FIX | `MajorUpdateWorker` still uses `session.lastPing` like a remote client | Local sessions must never depend on network keepalive |
| External network reachability | FIX | World worker can choose `tickOffline()` from `Server.networkReachability` | Single-player must always run normal local ticks regardless of internet |
| Connectivity watchdog | REMOVE | Disabled in current config, but retained server code can probe URLs | Hard-disable in single-player, not merely by config |
| Daily hosted-server restart | REMOVE | Disabled by config | Keep impossible/disabled in single-player |

## Client -> world commands

### Already direct

- world-space walking
- interaction walking
- minimap walking
- interface option actions
- close interface
- display/window update
- selected player actions
- friends add/remove
- ignore add/remove
- clan join/leave/rank/kick
- command line
- input prompt responses
- item/NPC/scenery/ground-item/examine typed bridge methods exist

### Still observed through the compatibility decoder in the first stable gameplay run

| Revision-530 signal | Observed | Classification | Action |
| --- | ---: | --- | --- |
| opcode 93 `Ping` | 67 | REMOVE | Remove remote keepalive semantics after local timeout is removed |
| opcode 177 `PacketCountUpdate` | 56 | LOCAL CODEC | Retain while interface presentation packets still use verify counters |
| opcode 75 `TrackingMouseClick` | 54 | REMOVE | Server handler is a TODO/no-op; do not serialize it in single-player |
| opcode 21 `TrackingCameraPos` | 1 | REMOVE | Server handler is a TODO/no-op; camera is local renderer state |
| opcode 137 `TrackFinished` | 1 | DIRECT candidate | Route semantic music-finished event directly |
| opcode 20 `MapRebuildStarted` | 4 | REMOVE | Server decoder returns `NoProcess` |
| opcode 110 `MapRebuildFinished` | 4 | REMOVE | Server decoder returns `NoProcess` |
| opcode 78 `NpcAction` | 2 | DIRECT candidate | Move MiniMenu call site to existing typed local command |
| opcode 254 `SceneryAction` | 1 | DIRECT candidate | Move MiniMenu call site to existing typed local command |
| opcode 132 `ContinueOption` | 6 | DIRECT candidate | Move dialogue/interface call site to typed local command |
| opcode 237 `ChatMessage` | 3 | DIRECT candidate | Route semantic message/effects before WordPack encoding |

`Packet.java -> LocalClientCommands.routeEncodedPacket()` remains a temporary safety net. The end state is **zero unclassified gameplay opcodes** entering that fallback.

## World -> client presentation

Current policy: retain codecs where RT4 is tightly coupled to revision-530 presentation structures; remove transport/session meaning from them.

### Retain for now as local presentation codecs

- player/NPC synchronization and update masks
- map/region rebuild presentation
- interfaces, access masks, strings and client scripts
- inventory/container updates
- skills, run energy, varps/varcs
- animations/models/graphics/projectiles
- ground item presentation
- music and sound presentation
- hint icons and minimap flags

### Required hardening

- assert nonblank/canonical player identity before appearance encoding
- assert valid player/NPC indices and update-mask lengths
- distinguish terrain render distance from revision-530 15-tile entity synchronization radius
- presentation failure must be reported as presentation failure, never inferred as logout
- stale/retired sessions must not emit into a reset presentation bridge

## Social systems

| Boundary | Status | Current state | Required action |
| --- | --- | --- | --- |
| Friends/ignore mutation | DIRECT | Commands call retained local communication model | Verify persistence |
| Online friend state | LOCAL | Falls back to local `Repository` when management communicator is absent | Good for future AI players |
| Private messages | LOCAL | `ManagementEvents` is an in-process queue and resolves repository players | Keep local semantics; no remote world routing |
| Clan state/messages | LOCAL | `ManagementEvents`/`ClanRepository` can operate in-process | Verify with one human + future AI entities |
| Global chat | REMOVE | Disabled in single-player config | Keep disabled unless explicitly redesigned as simulated-local feature |
| Remote `WorldCommunicator` | REMOVE | Not required for current single-player runtime | Add CI assertion that it never connects |
| Abuse reports/moderation transport | REMOVE/VERIFY | Meaningless as remote moderation in single-player | Preserve only if repurposed as local notes; otherwise suppress |

## Fake players / bots

**Correct model:** fake players are world entities, not fake network clients.

Existing `AIPlayer` behavior is aligned with this: outgoing per-player packet dispatch is suppressed for AI players while the human client sees them through normal entity synchronization. Do not create login/socket/session stacks for simulated players.

## JS5/cache

| Boundary | Status | Current state | Required action |
| --- | --- | --- | --- |
| JS5 TCP | LOCAL CODEC | `LocalJs5Socket` preserves RT4 JS5 framing in memory | Stable; leave alone during gameplay migration |
| Legacy async socket writer | DIRECT for JS5 | Local socket bypasses the TCP writer thread | Keep |
| Direct packaged-cache API | Future | RT4 still asks through JS5-shaped requests | Low priority after gameplay architecture is clean |

## Android-specific hosted-server leftovers

- external connectivity must not affect ticks
- remote-client ping timeout must not affect local session
- renderer/audio/world should all pause under one lifecycle authority
- hosted-server watchdog/restart/port assumptions should be impossible in single-player mode
- internal debug output belongs in `singleplayer-debug.log`, not the in-game chatbox

## Debug/UI policy during migration

Keep the normal chatbox until this audit is complete because it can expose real content/UI behavior. However, `Player.debug(...)` output should be redirected to stdout/file logging in single-player rather than injected into chat.

After the migration audit is clean, evaluate removing or replacing the chatbox itself as a separate UI/design decision.

## Completion gates

The multiplayer -> single-player conversion is considered structurally clean when CI/runtime can prove:

1. zero external gameplay sockets/listeners;
2. zero dependency on internet/network reachability for world ticks;
3. zero remote keepalive requirement for the local human session;
4. zero automatic reconnect loops inferred from renderer/protocol state;
5. zero unclassified client gameplay opcodes entering the compatibility decoder;
6. zero remote world-list/login/master-server requirements;
7. all remaining world -> client bytes are explicitly listed presentation codecs;
8. fake players require no network/login client;
9. Android pause/resume controls world, renderer, input and audio coherently;
10. internal debug output does not pollute player-facing chat.
