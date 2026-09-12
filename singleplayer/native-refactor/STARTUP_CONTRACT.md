# Single-player startup contract

This file records the ordering rules that have repeatedly mattered on Android. Treat these as architecture constraints, not incidental implementation details.

## Required startup order

1. Android launcher configuration is available.
2. Read the Android display/window geometry synchronously.
3. Freeze one logical RT4/Cacio viewport for the lifetime of the game process.
4. Publish that exact viewport to Cacio and to the single-player JVM properties before RT4 initializes.
5. Start the Java runtime immediately on the dedicated runtime thread; TextureView/layout callbacks never gate startup.
6. Initialize local data/SQLite.
7. Initialize the retained world authority/content.
8. Start RT4.
9. RT4 creates/resizes its software framebuffer to the same frozen logical viewport before normal presentation.
10. Complete socketless local login and the initial rebuild.
11. RT4 reaches the playable in-world state (`gameState == 30`).
12. Create `singleplayer-game-ready.flag`.
13. Android removes the loading overlay.

Surface/layout callbacks are consumers of the frozen viewport. They must not start the JVM, delay startup, or recalculate the logical framebuffer after JVM startup.

## Meaning of GAME_READY

`singleplayer-game-ready.flag` means the local player has entered a playable RT4 world. It must not depend on optional systems such as League attachment, plugins, updater state, audio initialization, or UI overlays.

League attachment may retry after GAME_READY.

## Viewport invariant

The historical 765x503 viewport is the minimum logical size. Wider/taller displays receive extra logical pixels rather than a stretched 4:3 image. Once frozen, the dimensions are immutable for the process:

`Android physical geometry -> frozen logical size -> Cacio managed screen -> RT4 framebuffer/canvas -> Android pixel buffer -> touch mapping`

The launcher also publishes `singleplayer.viewportWidth` and `singleplayer.viewportHeight` so the RT4-side software framebuffer can explicitly verify/recreate itself at the frozen dimensions. Every arrow must preserve the same logical width and height.

Do not reintroduce the earlier failure mode where a late TextureView/layout callback changed framebuffer dimensions after world/JVM startup had begun.

## Lifecycle invariant

Android lifecycle pause is recorded immediately but is not allowed to suspend bootstrap. `MobileLifecycleBridge` reports the game as unpaused until the runtime has reached `RUNNING` or `PAUSED`.

Once playable, one lifecycle authority controls:

- world ticks;
- Android framebuffer rendering;
- touch/input cancellation;
- RT4 audio mixer work;
- the shared native OpenAL context.

Backgrounding must therefore stop both newly mixed audio and already-queued native playback. Resuming reactivates the same world/render/audio state rather than creating a new session.

## Startup diagnostics

Android logs deterministic `SINGLEPLAYER_STARTUP:` checkpoints:

- `BOOTSTRAP`
- `VIEWPORT_FROZEN <logical> source=<physical>`
- `RUNTIME_THREAD_CREATED`
- `JVM_STARTING`
- `JVM_ARGUMENTS_READY viewport=<logical>`
- `STAGE <stage text>` as the Java side updates the stage file
- `STALL ...` once if startup remains behind the loading overlay for 45 seconds
- `GAME_VISIBLE` when the ready flag is observed
- `JVM_EXIT code=<n>` if the Java runtime exits

The Java side supplies finer-grained `SINGLEPLAYER_E2E:`, `SINGLEPLAYER_RUNTIME:` and `SINGLEPLAYER_DEBUG` markers for combined-JVM start, SQLite readiness, world readiness, login/rebuild, client attachment, memory and thread state.

Expected first-run empty JS5 cache misses are collapsed into a cold-cache diagnostic instead of flooding the log. CRC/version/parse failures remain explicit because those are real cache-integrity problems.

When debugging a stuck loading screen, find the **last successful checkpoint** first. Do not guess from the visible loading text alone.

## Historical failure rules

- Do not defer `launchCombinedRuntime()` through `View.post`, TextureView availability, draw, pre-draw, or layout callbacks.
- Do not mutate framebuffer dimensions after `Tools.getCacioJavaArgs()` has consumed them.
- Do not gate game visibility on League/runtime-plugin attachment.
- Do not perform world/JVM bootstrap work on the Android UI thread.
- Do not let an Android pause callback suspend `GameWorld.prompt()` or other bootstrap work.
- Do not treat host-JVM CI success as proof that the Android ARM64 JVM route works.
- Do not allow stale extracted `rt4.jar`/bootstrap payloads to survive a version change without version/hash validation.
- Do not rely on accidental Gradle task order for generated assets; express dependencies explicitly.
- Do not migrate only the low-level minimap walk send path: its historical caller appends an extra trailer, so the call site must be migrated atomically.
- Do not turn a completed socketless session returning to login state into automatic relogin; treat it as a presentation failure unless logout was explicit.
- Do not allow in-world gameplay bytes to reach a kernel socket. Typed local commands are preferred; the compatibility decoder is in-process only, and the local `BufferedSocket` is a presentation facade.

## Android 16/native compatibility

The app targets API 36. Predictive-back compatibility now uses the AndroidX back dispatcher rather than the old manifest opt-out.

Native libraries built from this repository are linked with 16 KiB-compatible ELF segment alignment. The bundled legacy Java 17 runtime and several historical prebuilts are older 4 KiB binaries, so the manifest enables Android 16 page-size compatibility for those components. Replacing the embedded runtime with a fully 16 KiB-native build is a separate runtime modernization task, not a startup-order change.
