# Editing and build guide

## Repository layout

The mobile app has three layers:

1. `rt4-client/` is the editable 2009-era game client. It owns game UI, rendering, cache use, networking, and plugin APIs.
2. `app_pojavlauncher/` is the Android shell. It owns touch input, Android activities, permissions, files, lifecycle, and launching the client.
3. `jre_lwjgl3glfw/` and the native libraries bridge the desktop-oriented client to Android.

The game server is a separate project and is not packaged in the APK.

## GitHub build flow

The one-time source import is pinned to the same RT4 revision supplied with the mobile project. After import, normal builds do not clone the client from GitLab.

Each APK build then:

1. Uses Java 8 and the RT4 Gradle wrapper to compile the client and `MobileClientBindings`.
2. Replaces the APK's generated `rt4.jar`, `rt4.version`, and mobile-bindings ZIP.
3. Downloads the known-working official 2.4 APK and verifies its fixed SHA-256 before extracting the embedded Android Java runtime.
4. Builds the LWJGL/GLFW compatibility component.
5. Uses Java 17 and Gradle 7.6.1 to package the Android debug APK.
6. Opens the APK as a ZIP, checks every required component, verifies that the packaged RT4 hash matches the freshly built JAR, and uploads the result.

If any of those checks fails, no APK artifact is published.

## Safe first edits

Good small changes for verifying the edit-build-install loop include:

- Login or status text in `rt4-client/client/src/main/java/rt4/`.
- A key mapping in `rt4-client/plugin-playground/src/main/kotlin/MobileClientBindings/plugin.kt`.
- A touch-control behavior in `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/`.
- A value in `app_pojavlauncher/src/main/assets/config.json`.

Make one focused commit, wait for a green **Editable Mobile APK** run, install it, and verify the change before combining larger rendering or input work.

## Generated files

Do not hand-edit these files:

- `app_pojavlauncher/src/main/assets/rt4.jar`
- `app_pojavlauncher/src/main/assets/rt4.version`
- `app_pojavlauncher/src/main/assets/plugins/MobileClientBindings.zip`
- `app_pojavlauncher/src/main/assets/mobile-build.properties`
- `app_pojavlauncher/src/main/assets/components/jre/`

They are generated or recovered inside GitHub Actions. The workflow artifact includes `mobile-build.properties` and SHA-256 files so a downloaded APK can always be tied back to its mobile and RT4 source revisions.

## Install and update behavior

The debug package ID is `net.kdt.pojavlaunch.debug`. The repository already contains a stable development keystore, so APKs built from this branch share a signature and can update each other. This public development key is intentionally not suitable for a Play Store or production release.

If Android reports a signature conflict, uninstall a copy produced by another project or key, then install this branch's APK. App data is removed when uninstalling, so back up anything important first.

On first launch after a client change, the app compares the packaged RT4 SHA-256 marker with the installed marker. It copies the new client first and writes the marker second, preventing an interrupted update from being accepted as complete.

## Diagnosing a failed workflow

Open the failed job and start with the first red step:

- **Compile editable RT4 client**: a Java/Kotlin compile error in the edited client or bindings.
- **Recover pinned embedded Java runtime**: download failure or upstream release asset changed; the checksum intentionally blocks drift.
- **Build LWJGL Android compatibility JAR**: bridge/Gradle issue.
- **Build debug APK**: Android Java/resource/manifest error.
- **Audit APK and source provenance**: an expected runtime, native library, client, or plugin was omitted from the package.

The last successful artifact remains usable while a later build is being fixed.

## Deliberate next milestones

Keep the builder green while advancing the port in small stages:

1. Improve touch targeting, drag gestures, long-press right click, keyboard/IME behavior, and camera controls.
2. Add mobile UI scaling and safe-area handling independent of render resolution.
3. Make pause/resume, audio focus, rotation, and process restoration predictable.
4. Move to current Android target requirements and rebuild native libraries for 16 KB page alignment.
5. Reduce the Pojav surface and embedded-runtime footprint only after equivalent smoke tests exist.
