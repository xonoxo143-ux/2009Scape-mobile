#!/usr/bin/env python3
"""Apply native single-player migration patches to a pinned 2009Scape checkout.

The upstream game/content tree stays pristine in git. This script makes a small,
auditable overlay at build time so we can compare every migration step against the
known-good multiplayer implementation before deleting it.
"""
from __future__ import annotations

import argparse
import shutil
from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    if old not in text:
        raise SystemExit(f"{label}: expected source anchor not found in {path}")
    path.write_text(text.replace(old, new, 1))


def copy_overlay(repo_root: Path, server_root: Path) -> None:
    overlay = repo_root / "singleplayer/server-patches/native/core"
    target = server_root / "src/main/core"
    if not overlay.is_dir():
        raise SystemExit(f"Missing native server overlay: {overlay}")
    for source in overlay.rglob("*"):
        if source.is_dir():
            continue
        relative = source.relative_to(overlay)
        destination = target / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        print(f"overlay: {relative}")


def patch_command_shadow(server_root: Path) -> None:
    processor = server_root / "src/main/core/net/packet/PacketProcessor.kt"
    replace_once(
        processor,
        "import core.game.container.Container\n",
        "import core.game.container.Container\nimport core.local.LocalMigrationProbe\n",
        "PacketProcessor import",
    )
    replace_once(
        processor,
        "    @JvmStatic fun enqueue(pkt: Packet) {\n        synchronized(queueLock) {\n",
        "    @JvmStatic fun enqueue(pkt: Packet) {\n"
        "        LocalMigrationProbe.observeIncoming(pkt)\n"
        "        synchronized(queueLock) {\n",
        "PacketProcessor enqueue hook",
    )


def patch_presentation_shadow(server_root: Path) -> None:
    repository = server_root / "src/main/core/net/packet/PacketRepository.java"
    replace_once(
        repository,
        "import core.net.packet.out.GrandExchangePacket;\n",
        "import core.net.packet.out.GrandExchangePacket;\n"
        "import core.local.LocalMigrationProbe;\n",
        "PacketRepository import",
    )
    replace_once(
        repository,
        "\tpublic static void send(Class<? extends OutgoingPacket> clazz, Context context) {\n"
        "                if(context.getPlayer() instanceof AIPlayer) return;\n",
        "\tpublic static void send(Class<? extends OutgoingPacket> clazz, Context context) {\n"
        "                LocalMigrationProbe.observeOutgoing(clazz, context);\n"
        "                if(context.getPlayer() instanceof AIPlayer) return;\n",
        "PacketRepository send hook",
    )


def patch_world_scale(server_root: Path) -> None:
    distance = server_root / "src/main/core/game/world/map/MapDistance.java"
    replace_once(
        distance,
        "\tRENDERING(15),",
        "\tRENDERING(Integer.getInteger(\"singleplayer.viewDistance\", 28)),",
        "MapDistance authority",
    )


def patch_loopback_only(server_root: Path) -> None:
    reactor = server_root / "src/main/core/net/NioReactor.java"
    replace_once(
        reactor,
        "channel.bind(new InetSocketAddress(port));",
        "channel.bind(new InetSocketAddress(\"127.0.0.1\", port));",
        "NioReactor loopback bind",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("server_root", type=Path)
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    args = parser.parse_args()

    server_root = args.server_root.resolve()
    repo_root = args.repo_root.resolve()
    if not (server_root / "src/main/core").is_dir():
        raise SystemExit(f"Not a 2009Scape Server checkout: {server_root}")

    copy_overlay(repo_root, server_root)
    patch_command_shadow(server_root)
    patch_presentation_shadow(server_root)
    patch_world_scale(server_root)
    patch_loopback_only(server_root)
    print("native refactor overlay prepared")


if __name__ == "__main__":
    main()
