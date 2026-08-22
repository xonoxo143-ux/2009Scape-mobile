# Grand Leagues canonical baseline

This workspace reconstructs the Grand Leagues gameplay line from the last fully
verified handoff, commit `b323ef3` (2026-08-18), on top of the complete supplied
2009Scape server source. The older implementation embedded in the prior Android
APK is evidence for packaging only; it is not the gameplay source of truth.

## Pinned inputs

| Input | SHA-256 |
| --- | --- |
| `2009scape-master.zip` | `850e636d6bae18fee08751fdc0e5f97f3276c2613123131f3622b3af873765ab` |
| `2009Scape-mobile-master.zip` | `93a0179f759894051a7b0102371be5a4050ca4c8435029f51766a6c021cc1a18` |
| `rt4-client-lwjgl-mobile-callbacks.zip` | `259a659dfdba354955471055017d197f0febb56412825e8086d8af2b1318b27e` |

The complete server tree lives at `server/2009scape-master`. Grand Leagues
acceptance checks live at `qa/grandleague`.

## Proven boundaries

- Keep the vanilla `rt4.jar` client unchanged unless a client-facing feature
  actually requires a client patch.
- Run the game server in-process and bind it to Android loopback only.
- Use the Android shell's existing Java 8 runtime if the server passes a real
  Java 8 cache-load and socket smoke test; class-file version checks alone are
  insufficient.
- Treat the connector delta as unapplied. Its guard expects v5 commit `1934923`,
  while this recovered gameplay line is v4 commit `b323ef3`.
- Produce APKs only at deliberate milestones after automated gameplay and boot
  gates pass.

## Current automated gate

The mobile branch `local-server-spike` compiles the modern server on JDK 8,
loads the cache on JDK 8, and verifies that the listener is restricted to
`127.0.0.1`. This catches both newer bytecode and accidental linkage against
newer JDK APIs.
