# Self-contained Grand Leagues Android build

This branch builds one APK containing both the RT4 Android client and a private 2009Scape Grand League server.

Runtime flow:

1. Android launcher starts `GrandLeagueServerService` in the isolated `:grandleague_server` process.
2. The service installs the bundled Android Java 17 runtime and unpacks `assets/grandleague/server.zip` into app-private storage.
3. Existing mutable world state (`data/players`, `data/eco`, `data/serverstore`) is carried forward when the embedded server payload changes.
4. The server starts from `server.jar` using `worldprops/mobile.conf` and listens on world 1 / port 43595.
5. The launcher waits until `127.0.0.1:43595` accepts connections, then launches the existing RT4 client.
6. The client config is forcibly restored to localhost on launcher startup so this APK cannot silently fall back to the public 2009Scape server.
7. The server JVM is started with `-Dgrandleague.mobile=true`; the vendored Grand League server overlay uses that flag to activate League mode on login and loaded saves.

Build flow:

- `.github/workflows/android.yml` is the only APK builder for this branch.
- `server-overlay/` is a checksummed source overlay exported from the previously tested Grand League promotion chain.
- CI reconstructs the pinned upstream 2009Scape server, applies the overlay, runs the full Maven test suite, packages the server/cache/data, embeds Android JRE8 + JRE17, builds the debug APK, verifies its signature and required assets, then uploads one APK artifact.
- `build.once` is the explicit one-shot push trigger. `workflow_dispatch` remains available for intentional rebuilds.

The resulting APK does not require a PC, Termux, LAN server, or hosted 2009Scape instance for normal local play.
