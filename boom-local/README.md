# Boom local-singleplayer migration

This branch is an experiment to use the Boom/RuneLite client architecture as the front end for a local single-player runtime.

## Proven client facts

The inspected Boom client is revision-line 239 and exposes its runtime endpoints through `net/runelite/client/runelite.properties`.

Relevant properties in the inspected client:

- `runelite.js5_ip=world1.boom-ps.com`
- `runelite.js5_port=43594`
- `runelite.jav_config=https://boom-ps.com/integrations/jav_config.ws`
- `runelite.jav_config_local=https://client.blurite.io/jav_local_239.ws`
- `runelite.enable_local=false`

The migration therefore starts by redirecting configuration and JS5/login traffic rather than rewriting the obfuscated game client.

## Milestone 1

1. Patch a locally supplied Boom client jar so its configuration points at localhost.
2. Serve a local `jav_config.ws` compatible with revision 239.
3. Stand up a local JS5/login probe and record the first client handshake.
4. Implement only enough cache/login protocol to reach the title/game bootstrap.
5. Do not add Boom proprietary binaries or Boom custom assets to this repository.

## Local client preparation

Place a legally obtained `client-boomps.jar` outside git and run:

```bash
./boom-local/patch-client-local.sh /path/to/client-boomps.jar
```

The script writes `client-boomps-local.jar` next to the input jar and replaces only RuneLite endpoint configuration. The original jar is not modified.

## Current architecture decision

The previous RT4/Cacio branch remains the working reference implementation for local persistence, pause/lifecycle behavior, direct single-player authority, fake-player ideas, and gameplay behavior. This branch tests whether those concepts can be migrated underneath a substantially more modern RuneLite/LWJGL client.
