# 2009Scape Mobile — editable build

[![Editable Mobile APK](https://github.com/xonoxo143-ux/2009Scape-mobile/actions/workflows/android.yml/badge.svg?branch=mobile-port)](https://github.com/xonoxo143-ux/2009Scape-mobile/actions/workflows/android.yml?query=branch%3Amobile-port)

This branch is the clean mobile-port workspace. It builds the normal 2009Scape client and does **not** contain Grand Leagues or a bundled game server.

The important change from the old mobile project is that `rt4.jar` is no longer the source of truth. GitHub compiles the editable RT4 source, packages that exact JAR into the APK, verifies the packaged hash, and uploads the APK as a workflow artifact.

## Edit and rebuild in GitHub

1. Switch to the `mobile-port` branch.
2. Open the file you want to change and use GitHub's pencil button.
3. Commit the change to `mobile-port`.
4. Open **Actions → Editable Mobile APK**.
5. Open the newest green run and download the `2009scape-mobile-editable-*` artifact.
6. Unzip the artifact and install `2009scape-mobile-editable.apk`.

Every relevant commit starts a build automatically. You can also re-run a previous workflow from its Actions page.

## Where to edit

| Goal | Source path |
| --- | --- |
| Game client, UI, rendering, protocol | `rt4-client/client/src/main/java/rt4/` |
| Mobile GLFW/AWT input translation | `rt4-client/client/src/main/java/rt4/GlRenderer.java` |
| Mobile control bindings | `rt4-client/plugin-playground/src/main/kotlin/MobileClientBindings/` |
| Android touch controls and launcher | `app_pojavlauncher/src/main/java/` |
| Server address and client options | `app_pojavlauncher/src/main/assets/config.json` |
| APK resources, icons, and layouts | `app_pojavlauncher/src/main/res/` |

See [docs/EDITING.md](docs/EDITING.md) for the build layout and common failure checks.

## What the builder guarantees

- RT4 source is pinned initially to upstream commit `e8589f36209c34ded7a9a545be498739dabb167b` and then stored in this GitHub branch.
- The embedded Java runtime is recovered from the known-working official 2.4 APK and accepted only when its SHA-256 matches the pinned value.
- GitHub Actions dependencies and Gradle versions are pinned.
- The APK audit checks for the RT4 JAR, RT4 hash marker, ARM64 native libraries, mobile bindings, LWJGL bridge, and embedded runtime.
- The app compares `rt4.version` on launch, so installing a rebuilt APK cannot silently keep an older extracted client.
- `MobileClientBindings` is rebuilt from source and refreshed while preserving whether the user disabled it.
- Debug APKs use the repository's stable development keystore, allowing later builds from this branch to update earlier ones.

## Local client build

GitHub Actions is the canonical APK builder. For quicker RT4-only checks on Linux or WSL, install JDK 8 plus `zip`, then run:

```bash
./scripts/build-rt4-client.sh
```

This regenerates:

- `app_pojavlauncher/src/main/assets/rt4.jar`
- `app_pojavlauncher/src/main/assets/rt4.version`
- `app_pojavlauncher/src/main/assets/plugins/MobileClientBindings.zip`

Those are generated build outputs; edit the files under `rt4-client/`, not the generated archives.

## Current foundation

This first editable milestone intentionally keeps the proven Pojav/OpenJDK/LWJGL bridge so we have a repeatable APK before changing rendering or controls. It is a universal debug APK with ARM64 support. Modern Android target levels, 16 KB native-library alignment, touch UX, scaling, lifecycle handling, and eventual removal of the embedded desktop-style runtime are tracked as later mobile-port work.

The 2009Scape server, Docker bundles, and Maven archive are not required to build this client APK.
