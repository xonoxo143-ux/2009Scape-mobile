#!/usr/bin/env python3
"""Fail when removed multiplayer assumptions reappear in the local runtime.

Run this after singleplayer/native-refactor/prepare_upstream.py has patched a
pinned 2009Scape checkout. The checks intentionally focus on architectural
properties rather than exact whole-file snapshots.
"""
from __future__ import annotations

import argparse
from pathlib import Path


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise SystemExit(f"AUDIT FAIL: {label}: missing {needle!r}")
    print(f"AUDIT OK: {label}")


def reject(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise SystemExit(f"AUDIT FAIL: {label}: forbidden {needle!r}")
    print(f"AUDIT OK: {label}")


def read(path: Path) -> str:
    if not path.is_file():
        raise SystemExit(f"AUDIT FAIL: missing file {path}")
    return path.read_text()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    parser.add_argument("--server-root", type=Path, required=True)
    args = parser.parse_args()

    repo = args.repo_root.resolve()
    server = args.server_root.resolve()

    server_kt = read(server / "src/main/core/Server.kt")
    worker = read(server / "src/main/core/worker/MajorUpdateWorker.kt")
    session = read(server / "src/main/core/net/IoSession.java")
    player = read(server / "src/main/core/game/node/entity/player/Player.java")
    flags = read(server / "src/main/core/game/world/update/flag/PlayerFlags530.kt")
    world_comm = read(server / "src/main/core/net/amsc/WorldCommunicator.java")
    auth = read(server / "src/main/core/auth/Auth.kt")
    account_store = read(server / "src/main/core/storage/LocalFileStorageProvider.kt")

    bootstrap = read(repo / "singleplayer/inprocess/InProcessBootstrap.java")
    login = read(repo / "singleplayer/client-patches/rt4/LocalLoginBridge.java")
    packet = read(repo / "singleplayer/client-patches/rt4/Packet.java")
    presentation = read(repo / "singleplayer/client-patches/rt4/LocalPresentationBridge.java")
    config = read(repo / "singleplayer/default.conf")

    require(server_kt, "SINGLEPLAYER_WORLD: NETWORK_LISTENER_DISABLED",
            "gameplay network listener disabled")
    require(server_kt,
            "ServerConstants.WATCHDOG_ENABLED &&\n            !java.lang.Boolean.getBoolean(\"singleplayer\")",
            "external connectivity watchdog excluded")

    require(worker,
            "java.lang.Boolean.getBoolean(\"singleplayer\") ||\n                Server.networkReachability == NetworkReachability.Reachable",
            "single-player ticks ignore internet reachability")
    require(worker, "!player.session.isLocalTransport &&",
            "remote-client ping timeout excludes local session")

    require(session, "private volatile boolean localTransport = false;",
            "local session transport state")
    require(session, "SINGLEPLAYER_LOCAL_SESSION: NIO_DETACHED",
            "local session detaches NIO")

    require(player, "SINGLEPLAYER_GAME_DEBUG:",
            "internal debug redirected out of game chat")
    require(flags, "Invalid player identity before revision-530 appearance encode",
            "appearance identity invariant")

    require(world_comm, "SINGLEPLAYER_MANAGEMENT: CONNECT_BLOCKED",
            "management-server connection physically blocked")
    require(world_comm,
            "return !isSinglePlayer() && state == ManagementServerState.AVAILABLE;",
            "management state cannot become active locally")
    require(world_comm, "return isSinglePlayer() ? null : session;",
            "local runtime exposes no management session")

    require(auth, "LocalFileStorageProvider",
            "single-player auth selects local persistent account store")
    require(auth, 'return File(data, "localaccounts")',
            "local account metadata stays outside ServerStore namespace")
    require(auth, "MIGRATED_LEGACY_DIRECTORY",
            "broken ServerStore account directory is self-migrated")
    reject(auth, 'LocalFileStorageProvider(File(base, "accounts"))',
            "account provider cannot recreate ServerStore directory collision")
    require(account_store, "StandardCopyOption.ATOMIC_MOVE",
            "account metadata writes use atomic replacement when available")
    require(account_store, "Repository.getPlayerByName",
            "online social state comes from local repository")

    require(config, "noauth_default_admin = false",
            "single-player account is not implicitly administrator")
    require(config, "debug = false",
            "diagnostics separated from world debug gameplay mode")
    require(config, "dev = false",
            "diagnostics separated from development gameplay rules")

    require(bootstrap, "class LocalCommands", "typed local command authority")
    require(login, "UNEXPECTED_CLIENT_RESET", "client failure is not implicit logout")
    require(login, "autoRelogin=false", "automatic relog loop blocked")
    require(presentation, "SERVER_TO_CLIENT_ACTIVE", "in-memory presentation bridge")

    require(packet, "TRANSPORT_ONLY_REMOVED", "transport-only client signals classified")
    for opcode in (20, 21, 22, 75, 93, 98, 99, 110, 123, 245):
        require(packet, f"case {opcode}:", f"transport-only opcode {opcode} removed")

    # Hosted/server-only paths should not be reintroduced into the local bootstrap.
    reject(bootstrap, "new Socket(", "bootstrap opens no raw gameplay socket")
    reject(bootstrap, "SocketChannel.open(", "bootstrap opens no NIO gameplay socket")

    print("AUDIT PASS: multiplayer boundary invariants satisfied")


if __name__ == "__main__":
    main()
