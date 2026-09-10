#!/usr/bin/env python3
"""Apply the smallest possible single-player migration overlay.

The retained 2009Scape source remains authoritative game/content code. This
script adds observability around the existing typed command/presentation
boundaries, the temporary loopback-only safety constraint, and the narrow
world->RT4 in-process byte-stream cutover used while the packet encoders and RT4
decoders are still retained for parity.
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


def patch_local_presentation_transport(server_root: Path) -> None:
    session = server_root / "src/main/core/net/IoSession.java"
    replace_once(
        session,
        "import core.cache.crypto.ISAACPair;\n",
        "import core.cache.crypto.ISAACPair;\nimport core.local.LocalMigrationProbe;\n",
        "IoSession local presentation import",
    )

    old = """\tpublic void queue(ByteBuffer buffer) {
\t\ttry {
\t\t\twritingLock.tryLock(1000L, TimeUnit.MILLISECONDS);
\t\t} catch (Exception e){
\t\t\te.printStackTrace();
\t\t\twritingLock.unlock();
\t\t}
\t\twritingQueue.add(buffer);
\t\twritingLock.unlock();
\t\twrite();
\t}
"""
    new = """\tpublic void queue(ByteBuffer buffer) {
\t\tboolean locked = false;
\t\ttry {
\t\t\tlocked = writingLock.tryLock(1000L, TimeUnit.MILLISECONDS);
\t\t\tif (!locked) {
\t\t\t\tthrow new IllegalStateException(\"Timed out acquiring session write lock\");
\t\t\t}

\t\t\t// During migration, switch the human player's already-encoded output to
\t\t\t// the shared in-process stream only when no older socket bytes remain
\t\t\t// queued. The current ByteBuffer position is not modified by the probe.
\t\t\tif (LocalMigrationProbe.routeOutgoingBytes(this, buffer, writingQueue.isEmpty())) {
\t\t\t\treturn;
\t\t\t}
\t\t\twritingQueue.add(buffer);
\t\t} catch (Exception e) {
\t\t\te.printStackTrace();
\t\t\treturn;
\t\t} finally {
\t\t\tif (locked) {
\t\t\t\twritingLock.unlock();
\t\t\t}
\t\t}
\t\twrite();
\t}
"""
    replace_once(session, old, new, "IoSession local presentation queue")


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
    patch_local_presentation_transport(server_root)
    patch_loopback_only(server_root)
    print("native refactor migration overlay prepared")


if __name__ == "__main__":
    main()
