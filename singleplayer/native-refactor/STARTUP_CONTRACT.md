# Single-player startup contract

This file records the ordering rules that have repeatedly mattered on Android. Treat these as architecture constraints, not incidental implementation details.

## Required startup order

1. Android launcher configuration is available.
2. Read the Android display/window geometry synchronously.
3. Freeze one logical RT4/Cacio viewport for the lifetime of the game process.
4. Start the Java runtime immediately on the dedicated runtime thread.
5. Cacio, RT4/GLFW, Android rendering and touch translation consume the same frozen viewport.
6. Initialize local data/SQLite.
7. Initialize the retained world authority/content.
8. Start RT4.
9. Complete socketless local login and the initial rebuild.
10. RT4 reaches the playable in-world state (`gameState == 30`).
11. Create `singleplayer-game-ready.flag`.
12. Android removes the loading overlay.

Surface/layout callbacks are consumers of the frozen viewport. They must not start the JVM, delay startup, or recalculate the logical framebuffer after JVM startup.

## Meaning of GAME_READY

`singleplayer-game-ready.flag` means the local player has entered a playable RT4 world. It must not depend on optional systems such as League attachment, plugins, updater state, audio initialization, or UI overlays.

League attachment may retry after GAME_READY.

## Startup diagnostics

Android logs deterministic `SINGLEPLAYER_STARTUP:` checkpoints:

- `BOOTSTRAP`
- `VIEWPORT_FROZEN <logical> source=<physical>`
- `RUNTIME_THREAD_CREATED`
- `JVM_STARTING`
- `JVM_ARGUMENTS_READY`
- `STAGE <stage text>` as the Java side updates the stage file
- `STALL ...` once if startup remains behind the loading overlay for 45 seconds
- `GAME_VISIBLE` when the ready flag is observed
- `JVM_EXIT code=<n>` if the Java runtime exits

The Java side already supplies finer-grained `SINGLEPLAYER_E2E:` and `SINGLEPLAYER_RUNTIME:` markers for combined-JVM start, SQLite readiness, world readiness, login/rebuild, and client attachment.

When debugging a stuck loading screen, find the **last successful checkpoint** first. Do not guess from the visible loading text alone.

## Historical failure rules

- Do not defer `launchCombinedRuntime()` through `View.post`, TextureView availability, draw, pre-draw, or layout callbacks.
- Do not mutate framebuffer dimensions after `Tools.getCacioJavaArgs()` has consumed them.
- Do not gate game visibility on League/runtime-plugin attachment.
- Do not perform world/JVM bootstrap work on the Android UI thread.
- Do not treat host-JVM CI success as proof that the Android ARM64 JVM route works.
- Do not allow stale extracted `rt4.jar`/bootstrap payloads to survive a version change without version/hash validation.
- Do not rely on accidental Gradle task order for generated assets; express dependencies explicitly.
- Do not migrate only the low-level minimap walk send path: its historical caller appends an extra trailer, so the call site must be migrated atomically.
- Do not turn a completed socketless session returning to login state into automatic relogin; treat it as a presentation failure unless logout was explicit.

## Viewport invariant

The viewport may be wider or taller than historical 765x503, but it is immutable after freeze:

`Android source geometry -> frozen logical size -> Cacio managed screen -> glfwWidth/glfwHeight -> RT4 canvas -> Android pixel buffer -> touch mapping`

Every arrow must preserve the same logical width and height.
