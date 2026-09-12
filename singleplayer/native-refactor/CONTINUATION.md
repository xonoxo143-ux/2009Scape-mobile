# Current continuation checkpoint

This is the continuation record for the most recent chat titled "Review workflow trajectory" and its September 12, 2026 continuation. Revisit prior chat context when it affects a decision; update this record with concrete recovered ideas and verified results. Historical retrieval has been incomplete, so do not invent missing exchanges.

## User-supplied handoff

- Build 20 is the last device-proven gameplay checkpoint.
- Build 22, commit 1c0341c445ff238b4a275ace0163671632023472, is the comparison checkpoint for widescreen work.
- Build 26, commit 9d9b1a6c35c3838f7d9db1282efd949579d4564e, failed in run 34680366895.
- Preserve the intended 765x503 startup contract. Do not blindly roll back or change world loading, login ordering, or startup Cacio sizing to fix a build error.
- After the build passes, test world entry and post-login SINGLEPLAYER_WIDESCREEN expansion separately.
- Resume remaining viewport/mobile/native work only after those results.

## Build 26 diagnosis and narrow correction

The exact error was in :app_pojavlauncher:processDebugResources. AAPT rejected android:pageSizeCompat="true": the attribute is an enum with enabled=32 and disabled=64.

Commit 229c645343c39ddf632fc051a9ff656fc6e9da81 changes that value to "enabled" and advances .github/fast-build-trigger. No startup or gameplay source changed in this commit. Build 27 is run 34682904503:
https://github.com/xonoxo143-ux/2009Scape-mobile/actions/runs/34682904503

This Android manifest change requires installing the APK. The in-app "update from GitHub" payload cannot replace the installed manifest.

## Existing source/handoff discrepancy

Inspection of the source at Build 26 and Build 27 found an existing mismatch with the intended 765x503 startup contract:

- JavaGUILauncherActivity calls AWTCanvasView.freezeLogicalViewport with physical display dimensions before starting the JVM.
- AWTCanvasView derives a logical viewport from the display aspect ratio; 765x503 is only the minimum.
- Tools.getCacioJavaArgs passes those dimensions to cacio.managed.screensize.
- LocalWidescreenBridge still describes a 765x503 bootstrap and a resize after GAME_READY.

The compile-only fix intentionally preserves this existing code. Do not describe Build 27 as device-proven or claim that its startup is guaranteed to be 765x503. Reconcile this discrepancy with device logs and the historical checkpoints before making another viewport change. STARTUP_CONTRACT.md currently describes the source's display-derived model, so it must not be mistaken for confirmation of the newer user-supplied handoff.

## Ideas and constraints to carry forward

- The UI goal remains a smaller League menu, more visible game area, and filling unused black space.
- One local runtime/world authority controls the game. Keep semantic LocalCommands and proper RT4 patch source files.
- Reuse existing game assets; do not recreate audio, textures, or models unnecessarily.
- Fake players are local world entities, not clients with fabricated login/network sessions.
- Retain revision-530 codecs only as classified in-process presentation compatibility where useful. The audit's next main protocol target is zero unclassified gameplay opcodes.
- Terrain render distance is distinct from the retained entity synchronization radius.
- Continue validating coordinated Android pause/resume, input, renderer, mixer, and OpenAL behavior.
- Keep debug output out of player-facing chat.
- MULTIPLAYER_BOUNDARY_AUDIT.md remains the detailed migration ledger.

## Build 27 result

- CI completed successfully on September 12, 2026.
- APK artifact: 10295245498 (2009scape-mobile-fast), from the exact source commit above.
- APK filename delivered: 2009scape-mobile-build27.apk.
- APK size: 217065592 bytes.
- APK SHA-256: c3c774a119ffa80ae9f7dc37bf3bb14c0e5be1fb8c2371c66cb1c92b7e821b14.
- Verification: downloaded artifact digest matches GitHub; extracted APK digest matches the CI checksum; APK ZIP CRCs pass; world engine/data, ARM64 runtime, RT4 bootstrap, League plugin, and Android SQLite native payloads are present.
- No device test has been performed for this build.

## Next device feedback

Install Build 27 over the existing app and check:
1. Does the app enter the playable world?
2. After login, does the viewport expand correctly, with touch positions matching the game?

Keep these as separate outcomes. Build success does not settle the startup-source discrepancy or prove widescreen behavior on the phone.

## September 12: Build 27 phone result and software-layout correction

The user confirmed Build 27 reaches the playable world, but widescreen does not work. It is now the latest device-proven world-entry checkpoint. Their screen recording shows the fixed interface centered between black bars; their debug log reports RT4_SOFTWARE_READY 1289x503, CLIENT_ATTACHED and LOGGED_IN, with no widescreen layout transition.

Root cause: LocalViewportBridge enlarged the software framebuffer but never submitted TrackingDisplayUpdate to the local world. The world retained fixed root interface 548 instead of resizable root 746. Later ClientProt window notifications also derived layout solely from DisplayMode.getWindowMode(), which reports 0 for software rendering.

The correction keeps the existing Android/Cacio startup path and frozen input/render dimensions. LocalViewportBridge requests the retained world layout transition after reaching gameState 30, confirms root 746, and logs SINGLEPLAYER_WIDESCREEN: READY with actual canvas dimensions. It preserves unrelated full-screen interfaces, bounds failed retries, and never makes the layout notification a login readiness gate. ClientProt preserves resizable presentation in later local display notifications without enabling the GL renderer.

Validation: compile against the actual Build 27 RT4/bootstrap JARs. The new regression probe fails on unmodified Build 27 with "Wide software display sent fixed layout" and passes on the corrected classes. It also checks no layout dispatch during map loading, no repeated dispatch after confirmation, preserving unrelated roots and renderer selection, and bounded failed retries. The existing bootstrap-only publisher runs this probe before activating the update.

Delivery: updateable bootstrap only; no Android APK change is required. Publication and phone confirmation remain separate gates. Next phone check: confirm root 746/wider world view and correct touches; inspect LAYOUT_REQUEST / READY / LAYOUT_UNCONFIRMED if it fails.
