# Multiplayer -> Single-Player Boundary Audit

This file is the migration ledger for the Android single-player refactor.

The target architecture is:

`RT4/Android input -> semantic local command -> one authoritative world -> local presentation -> RT4 renderer`

A retained revision-530 byte format is acceptable only when it is deliberately classified as a **presentation compatibility codec**. Multiplayer transport, remote-session health, master-server assumptions, and external-network state must not control single-player gameplay.

## Status vocabulary

- **DIRECT** - multiplayer transport has been removed; semantic state/action crosses an in-process API.
- **LOCAL CODEC** - bytes remain intentionally because the retained renderer/world codec is useful, but no kernel/network peer exists.
- **REMOVE** - multiplayer/transport-only behavior with no single-player semantic meaning.
- **VERIFY** - architecture is in place but still benefits from runtime coverage.

## Authority and hosting

| Boundary | Status | Current state | Remaining action |
| --- | --- | --- | --- |
| Gameplay TCP listener | DIRECT | `core.Server` does not start NIO in single-player | Keep CI assertion |
| WebSocket listener | DIRECT | Disabled with network host mode | Keep disabled |
| Server stdin console | DIRECT | Not started in single-player | Keep disabled |
| External network reachability | DIRECT | Single-player forces normal local ticks independent of internet state | Keep invariant |
| Connectivity watchdog | REMOVE | Hard-disabled in single-player, not merely by config | Keep absent |
| Daily hosted-server restart | REMOVE | Hosted restart path is not part of the local runtime | Keep absent |
| World selection | DIRECT | RT4 synthesizes one local world | Remove dead UI/state later if worthwhile |
| External authentication | DIRECT | Local login calls retained auth/profile logic in-process | Keep local persistence |
| IP/account policing | REMOVE | Not part of local login | Keep absent |
| Master-server presence | REMOVE | Not required for login or ticks | Keep absent |
| Android app lifecycle | DIRECT | One local pause authority controls world/render/input/audio | Runtime regression-test |

## Runtime/session lifecycle

| Boundary | Status | Current state | Remaining action |
| --- | --- | --- | --- |
| Human player creation | DIRECT | World constructs `Player` directly and runs retained initialization | Keep |
| Player identity | DIRECT | Local name is canonicalized and appearance encode has a nonblank invariant | Keep invariant |
| Initial RT4 success/header/rebuild | LOCAL CODEC | Synthetic revision-530 login success/header/rebuild is consumed from memory | Remove only when RT4 login state machine can be replaced cleanly |
| Normal world -> RT4 stream | LOCAL CODEC | `LocalPresentationBridge` carries retained presentation bytes in-process | Continue hardening presentation invariants |
| RT4 `BufferedSocket` after login | LOCAL CODEC | Local stream facade only; it has no kernel gameplay peer | Remove when no retained reader expects the facade |
| 20-second client ping timeout | DIRECT | Local sessions are excluded from remote `lastPing` timeout | Keep invariant |
| Unexpected client reset | DIRECT | Presentation failure is not inferred as logout/relogin | Keep explicit failure reason |
| Explicit logout | VERIFY | World teardown exists | Runtime-test normal UI logout/save |

## Client -> world commands

### Direct semantic paths

The following no longer require gameplay TCP serialization:

- world-space walking;
- interaction walking;
- minimap walking;
- interface option actions and close-interface;
- display/window state;
- supported player actions;
- NPC, scenery, ground-item and inventory actions;
- item/NPC/scenery examine;
- continue/dialogue options;
- friends add/remove;
- ignore add/remove;
- clan join/leave/rank/kick;
- command line;
- input prompt responses.

RT4 still has old packet-writing code at some retained call sites. `Packet.finishLocalPacket()` and `LocalClientCommands.routeEncodedPacket()` are the temporary in-process compatibility decoder for those cases.

### Hard transport invariant

**No in-world gameplay packet is allowed to leave the process.**

Before `BufferedSocket.write()` can do anything with `Protocol.outboundBuffer`, the local packet boundary is finalized. A recognized compatibility packet is decoded/enqueued directly into the world. Transport-only signals are discarded. Any residual single-player bytes reaching the local stream facade are logged and dropped; they are never written to a kernel gameplay socket.

This means the compatibility decoder is technical debt, but it is no longer multiplayer transport.

### Transport-only client signals removed locally

The following revision-530 opcodes are classified as transport/session telemetry and consumed without world networking:

- 20 - map rebuild started acknowledgement;
- 21 - camera telemetry;
- 22 - focus telemetry;
- 75 - mouse click telemetry;
- 93 - remote keepalive/ping;
- 98 - preference telemetry;
- 99 - hosted abuse-report transport;
- 110 - map rebuild finished acknowledgement;
- 123 - mouse movement telemetry;
- 177 - packet-count/verify transport bookkeeping;
- 245 - remote AFK logout.

Earlier stable-run counts for these opcodes are historical evidence only; they are not a reason to preserve remote semantics.

### Remaining compatibility candidates

The main remaining useful compatibility traffic should be treated as semantic actions and migrated when a clean direct seam is available. Examples include chat/message semantics and music-finished notification. They may use the in-process decoder during migration, but may not cross a socket.

The completion target remains **zero unclassified gameplay opcodes** entering the compatibility decoder.

## World -> client presentation

Current policy: retain codecs where RT4 is tightly coupled to revision-530 presentation structures; remove transport/session meaning from them.

### Retain for now as local presentation codecs

- player/NPC synchronization and update masks;
- map/region rebuild presentation;
- interfaces, access masks, strings and client scripts;
- inventory/container updates;
- skills, run energy, varps/varcs;
- animations/models/graphics/projectiles;
- ground-item presentation;
- music and sound presentation;
- hint icons and minimap flags.

These bytes terminate in `LocalPresentationBridge`/RT4. They are renderer compatibility data, not a remote network protocol.

### Presentation invariants

- player identity must be nonblank/canonical before appearance encoding;
- terrain render distance is independent from the retained 15-tile entity synchronization radius;
- presentation failure is reported as presentation failure, never inferred as logout;
- retired/reset sessions must not silently create replacement players;
- stale sessions must not require an external socket to drain final state.

## Social systems

| Boundary | Status | Current state | Remaining action |
| --- | --- | --- | --- |
| Friends/ignore mutation | DIRECT | Commands call retained local communication model | Verify persistence |
| Online friend state | LOCAL | Resolves through local `Repository` when remote communicator is absent | Good basis for AI inhabitants |
| Private messages | LOCAL | In-process management/event path can resolve repository players | Keep local semantics |
| Clan state/messages | LOCAL | In-process clan/event model can operate without remote worlds | Verify with future AI entities |
| Global chat | REMOVE | Disabled in single-player config | Only redesign intentionally as a simulated-local feature |
| Remote `WorldCommunicator` | REMOVE | Not required by the single-player runtime | Keep CI/runtime assertion |
| Abuse-report moderation transport | REMOVE | Hosted moderation has no single-player authority | Keep suppressed unless repurposed locally |

## Fake players / bots

**Correct model:** fake players are world entities, not fake network clients.

Existing `AIPlayer` behavior follows this model: outgoing per-player packet dispatch is suppressed for AI players while the human sees them through normal entity synchronization. Do not give simulated players login/socket/session stacks.

Bot startup timing also no longer creates one Java `Timer` thread per bot. Randomized delayed spawns are retained under a shared daemon scheduler so simulated population does not multiply Android threads.

## JS5/cache

| Boundary | Status | Current state | Remaining action |
| --- | --- | --- | --- |
| JS5 TCP shape | LOCAL CODEC | `LocalJs5Socket` preserves JS5 framing in memory | Stable; low migration priority |
| Async socket writer | DIRECT for JS5 | Local socket bypasses TCP writer thread | Keep |
| Empty first-run cache diagnostics | DIRECT/local | Expected missing cache entries collapse to one cold-cache diagnostic | Keep real CRC/version errors visible |
| Direct packaged-cache API | Future | RT4 still requests data through a JS5-shaped local facade | Optional cleanup later |

## Android-specific runtime boundaries

- external connectivity must not affect ticks;
- local sessions must not depend on remote keepalive;
- background pause controls world, renderer, input, mixer and shared OpenAL playback coherently;
- hosted-server watchdog/restart/port assumptions remain impossible in single-player mode;
- internal debug output belongs in `singleplayer-debug.log`, not player-facing chat;
- target API 36/predictive back is handled by AndroidX rather than a legacy manifest opt-out;
- source-built JNI libraries use 16 KiB-compatible ELF alignment; legacy embedded JRE/prebuilt binaries use Android 16 page-size compatibility until the runtime itself is modernized.

## Build-time native consistency

The retained shaded LWJGL JAR historically contains SHA-1 fingerprints for stock Linux/ARM64 natives. Android intentionally packages Android-built `liblwjgl.so` and `libopenal.so`, so build tooling rewrites those two fingerprint resources to the exact binaries packaged in the APK. This removes the false LWJGL native/Java mismatch warning without disabling validation.

## Completion gates

The multiplayer -> single-player conversion is structurally clean when CI/runtime can prove:

1. zero external gameplay sockets/listeners;
2. zero dependency on internet/network reachability for world ticks;
3. zero remote keepalive requirement for the local human session;
4. zero automatic reconnect loops inferred from renderer/protocol state;
5. zero unclassified client gameplay opcodes entering the compatibility decoder;
6. zero remote world-list/login/master-server requirements;
7. all remaining world -> client bytes are explicitly classified presentation codecs;
8. fake players require no network/login client;
9. Android pause/resume controls world, renderer, input and audio coherently;
10. internal debug output does not pollute player-facing chat.

Items 1-4 and the transport side of item 6 are now enforced by source/build assertions. Item 5 remains the main protocol-cleanup ledger target; items 7-10 remain ongoing runtime verification targets.
