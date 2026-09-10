#!/usr/bin/env python3
"""Apply the smallest possible single-player migration overlay.

The retained 2009Scape source remains authoritative game/content code. Human
login and gameplay are in-process. The loopback NIO listener is retained only
while RT4 still obtains JS5/cache data through the historical service; JS5 is a
separate migration boundary and will be removed independently.
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
    source = repo_root / "singleplayer/server-patches/native/core/local/LocalMigrationProbe.kt"
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
        "import core.net.packet.out.GrandExchangePacket;\nimport core.local.LocalMigrationProbe;\n",
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


def patch_local_session_transport(server_root: Path) -> None:
    session = server_root / "src/main/core/net/IoSession.java"
    replace_once(
        session,
        "import core.cache.crypto.ISAACPair;\n",
        "import core.cache.crypto.ISAACPair;\nimport core.local.LocalMigrationProbe;\n",
        "IoSession local presentation import",
    )
    replace_once(
        session,
        "\tprivate boolean active = true;\n",
        "\tprivate boolean active = true;\n\n"
        "\t/** True for the human single-player session after network transport is removed. */\n"
        "\tprivate volatile boolean localTransport = false;\n",
        "IoSession local transport state",
    )

    old_queue = """\tpublic void queue(ByteBuffer buffer) {
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
    new_queue = """\tpublic void queue(ByteBuffer buffer) {
\t\tboolean locked = false;
\t\ttry {
\t\t\tlocked = writingLock.tryLock(1000L, TimeUnit.MILLISECONDS);
\t\t\tif (!locked) throw new IllegalStateException(\"Timed out acquiring session write lock\");

\t\t\t// The retained encoder still creates the exact RT4 packet bytes. For the
\t\t\t// human local session, ownership moves directly to RT4's in-memory stream.
\t\t\tif (LocalMigrationProbe.routeOutgoingBytes(this, buffer, writingQueue.isEmpty())) {
\t\t\t\treturn;
\t\t\t}
\t\t\twritingQueue.add(buffer);
\t\t} catch (Exception e) {
\t\t\te.printStackTrace();
\t\t\treturn;
\t\t} finally {
\t\t\tif (locked) writingLock.unlock();
\t\t}
\t\twrite();
\t}
"""
    replace_once(session, old_queue, new_queue, "IoSession local presentation queue")

    old_write = """\tpublic void write() {
\t\tif (!key.isValid()) {
\t\t\tdisconnect();
\t\t\treturn;
\t\t}
\t\ttry {
\t\t\twritingLock.tryLock(1000L, TimeUnit.MILLISECONDS);
\t\t} catch (Exception e){
\t\t\te.printStackTrace();
\t\t\twritingLock.unlock();
\t\t\treturn;
\t\t}
\t\tSocketChannel channel = (SocketChannel) key.channel();
\t\ttry {
\t\t\twhile (!writingQueue.isEmpty()) {
\t\t\t\tByteBuffer buffer = writingQueue.get(0);
\t\t\t\tchannel.write(buffer);
\t\t\t\tif (buffer.hasRemaining()) {
\t\t\t\t\tkey.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
\t\t\t\t\tbreak;
\t\t\t\t}
\t\t\t\twritingQueue.remove(0);
\t\t\t}
\t\t} catch (IOException e) {
\t\t\tdisconnect();
\t\t}
\t\twritingLock.unlock();
\t}
"""
    new_write = """\tpublic void write() {
\t\tif (localTransport) {
\t\t\tboolean locked = false;
\t\t\ttry {
\t\t\t\tlocked = writingLock.tryLock(1000L, TimeUnit.MILLISECONDS);
\t\t\t\tif (!locked) return;
\t\t\t\twhile (!writingQueue.isEmpty()) {
\t\t\t\t\tByteBuffer buffer = writingQueue.get(0);
\t\t\t\t\tif (!LocalMigrationProbe.routeOutgoingBytes(this, buffer, true)) {
\t\t\t\t\t\tSystem.err.println(\"SINGLEPLAYER_LOCAL_PRESENTATION: retained local write could not be routed\");
\t\t\t\t\t\treturn;
\t\t\t\t\t}
\t\t\t\t\twritingQueue.remove(0);
\t\t\t\t}
\t\t\t} catch (Exception e) {
\t\t\t\te.printStackTrace();
\t\t\t} finally {
\t\t\t\tif (locked) writingLock.unlock();
\t\t\t}
\t\t\treturn;
\t\t}

\t\tif (key == null || !key.isValid()) {
\t\t\tdisconnect();
\t\t\treturn;
\t\t}
\t\tboolean locked = false;
\t\ttry {
\t\t\tlocked = writingLock.tryLock(1000L, TimeUnit.MILLISECONDS);
\t\t\tif (!locked) return;
\t\t\tSocketChannel channel = (SocketChannel) key.channel();
\t\t\twhile (!writingQueue.isEmpty()) {
\t\t\t\tByteBuffer buffer = writingQueue.get(0);
\t\t\t\tchannel.write(buffer);
\t\t\t\tif (buffer.hasRemaining()) {
\t\t\t\t\tkey.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
\t\t\t\t\tbreak;
\t\t\t\t}
\t\t\t\twritingQueue.remove(0);
\t\t\t}
\t\t} catch (IOException e) {
\t\t\tdisconnect();
\t\t} catch (InterruptedException e) {
\t\t\tThread.currentThread().interrupt();
\t\t} finally {
\t\t\tif (locked) writingLock.unlock();
\t\t}
\t}
"""
    replace_once(session, old_write, new_write, "IoSession local transport write")

    replace_once(
        session,
        "\tpublic String getRemoteAddress() {\n\t\ttry {\n\t\t\treturn ((SocketChannel) key.channel()).getRemoteAddress().toString();\n",
        "\tpublic String getRemoteAddress() {\n"
        "\t\tif (localTransport || key == null) return address;\n"
        "\t\ttry {\n"
        "\t\t\treturn ((SocketChannel) key.channel()).getRemoteAddress().toString();\n",
        "IoSession local remote address",
    )

    marker = """\t/**
\t * Gets the IP-address (without the port).
\t * @return The address.
\t */
\tpublic String getAddress() {
"""
    local_api = """\t/** Mark this logical player session as in-process; logical logout still uses disconnect(). */
\tpublic synchronized void promoteToLocalTransport() {
\t\tif (localTransport) return;
\t\tlocalTransport = true;
\t\tif (key != null) {
\t\t\tkey.cancel();
\t\t\ttry {
\t\t\t\tif (key.channel() instanceof SocketChannel) ((SocketChannel) key.channel()).close();
\t\t\t} catch (IOException e) {
\t\t\t\te.printStackTrace();
\t\t\t}
\t\t}
\t\tSystem.out.println(\"SINGLEPLAYER_LOCAL_SESSION: NIO_DETACHED\");
\t}

\tpublic boolean isLocalTransport() {
\t\treturn localTransport;
\t}

""" + marker
    replace_once(session, marker, local_api, "IoSession local transport API")


def patch_singleplayer_console(server_root: Path) -> None:
    server = server_root / "src/main/core/Server.kt"
    old_console = """        val scanner = Scanner(System.`in`)

        running = true
        GlobalScope.launch {
            while(scanner.hasNextLine()){
                val command = scanner.nextLine()
                when(command){
                    "stop" -> exitProcess(0)

                    "update" -> SystemManager.flag(SystemState.UPDATING)
                    "help","commands" -> printCommands()
                    "restartworker" -> SystemManager.flag(SystemState.ACTIVE)

                }
            }
        }
"""
    new_console = """        running = true
        if (!java.lang.Boolean.getBoolean("singleplayer")) {
            val scanner = Scanner(System.`in`)
            GlobalScope.launch {
                while(scanner.hasNextLine()){
                    val command = scanner.nextLine()
                    when(command){
                        "stop" -> exitProcess(0)
                        "update" -> SystemManager.flag(SystemState.UPDATING)
                        "help","commands" -> printCommands()
                        "restartworker" -> SystemManager.flag(SystemState.ACTIVE)
                    }
                }
            }
        }
"""
    replace_once(server, old_console, new_console, "Server single-player console")


def patch_loopback_only(server_root: Path) -> None:
    # Until JS5 is migrated, the retained listener must still exist. Restrict it
    # to loopback so it is a cache compatibility service, not a multiplayer port.
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
    patch_local_session_transport(server_root)
    patch_singleplayer_console(server_root)
    patch_loopback_only(server_root)
    print("native refactor migration overlay prepared")


if __name__ == "__main__":
    main()
