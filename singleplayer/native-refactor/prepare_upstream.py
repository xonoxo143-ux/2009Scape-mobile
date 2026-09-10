#!/usr/bin/env python3
"""Apply the smallest possible single-player migration overlay.

The retained 2009Scape source remains authoritative game/content code. This
script currently adds only observability around the existing typed command and
presentation boundaries plus the temporary loopback-only safety constraint.
Direct local commands and shared world scale are driven by the bootstrap, so we
do not fork those engine classes merely to remove networking later.
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


def install_probe(repo_root: Path, server_root: Path) -> None:
    source = (
        repo_root
        / "singleplayer/server-patches/native/core/local/LocalMigrationProbe.kt"
    )
    destination = server_root / "src/main/core/local/LocalMigrationProbe.kt"
    if not source.is_file():
        raise SystemExit(f"Missing migration probe: {source}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)
    print("overlay: local/LocalMigrationProbe.kt")


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

    install_probe(repo_root, server_root)
    patch_command_shadow(server_root)
    patch_presentation_shadow(server_root)
    patch_loopback_only(server_root)
    print("native refactor shadow overlay prepared")


if __name__ == "__main__":
    main()
