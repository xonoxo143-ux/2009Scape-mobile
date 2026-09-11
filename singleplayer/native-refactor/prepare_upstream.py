#!/usr/bin/env python3
"""Prepare the retained 2009Scape engine for the Android single-player runtime.

The upstream world remains authoritative for game/content behavior. This overlay
only removes transport/multiplayer plumbing, applies Android lifecycle/runtime
compatibility, installs native single-player extensions, and writes the packaged
single-player configuration.
"""
from __future__ import annotations

import argparse
import hashlib
import re
import shutil
from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    if old not in text:
        raise SystemExit(f"{label}: expected source anchor not found in {path}")
    path.write_text(text.replace(old, new, 1))


def replace_regex_once(path: Path, pattern: str, new: str, label: str) -> None:
    text = path.read_text()
    replaced, count = re.subn(pattern, lambda _: new, text, count=1, flags=re.DOTALL)
    if count != 1:
        raise SystemExit(f"{label}: expected structural source anchor not found in {path}")
    path.write_text(replaced)


def patch_sqlite_dependency(server_root: Path) -> None:
    pom = server_root / "pom.xml"
    old = """    <dependency>
      <groupId>org.xerial</groupId>
      <artifactId>sqlite-jdbc</artifactId>
      <version>3.36.0.3</version>
      <scope>compile</scope>
    </dependency>"""
    new = """    <dependency>
      <groupId>org.xerial</groupId>
      <artifactId>sqlite-jdbc</artifactId>
      <version>3.53.4.0</version>
      <scope>compile</scope>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
      <version>1.7.36</version>
      <scope>compile</scope>
    </dependency>"""
    replace_once(pom, old, new, "sqlite-jdbc dependency")
    print("overlay: Android sqlite-jdbc")


def install_native_overlay(repo_root: Path, server_root: Path) -> None:
    """Copy every source-native single-player extension into Server/src/main."""
    source_root = repo_root / "singleplayer/server-patches/native"
    destination_root = server_root / "src/main"
    if not source_root.is_dir():
        raise SystemExit(f"Missing native overlay tree: {source_root}")

    copied = 0
    for source in sorted(source_root.rglob("*")):
        if not source.is_file():
            continue
        relative = source.relative_to(source_root)
        destination = destination_root / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        print(f"overlay: native/{relative.as_posix()}")
        copied += 1
    if copied == 0:
        raise SystemExit(f"Native overlay tree is empty: {source_root}")


def patch_command_boundary(server_root: Path) -> None:
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


def patch_presentation_boundary(server_root: Path) -> None:
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
        "\t/** True for the human single-player session. */\n"
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

    new_write = """\tpublic void write() {
\t\tif (localTransport) {
\t\t\tboolean locked = false;
\t\t\ttry {
\t\t\t\tlocked = writingLock.tryLock(1000L, TimeUnit.MILLISECONDS);
\t\t\t\tif (!locked) return;
\t\t\t\twhile (!writingQueue.isEmpty()) {
\t\t\t\t\tByteBuffer buffer = writingQueue.get(0);
\t\t\t\t\tif (!LocalMigrationProbe.routeOutgoingBytes(this, buffer, true)) {
\t\t\t\t\t\tthrow new IllegalStateException(\"Local presentation route unavailable\");
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
    # Match the no-argument write() method structurally rather than requiring an
    # exact historical body. This keeps the overlay pinned to the method boundary
    # while tolerating harmless upstream implementation drift.
    replace_regex_once(
        session,
        r"\tpublic void write\(\) \{.*?\n\t\}\n(?=\n\t/\*\*\n\t \* Disconnects the session\.)",
        new_write,
        "IoSession local transport write",
    )

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
    local_api = """\t/** Enter in-process transport mode; logical logout still uses disconnect(). */
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


def patch_mobile_pause(server_root: Path) -> None:
    worker = server_root / "src/main/core/worker/MajorUpdateWorker.kt"
    source = worker.read_text()
    loop_old = "        while (running) {\n            Grafana.startTick()\n"
    loop_new = (
        "        while (running) {\n"
        "            val singlePlayerPaused = isSinglePlayerPaused()\n"
        "            if (singlePlayerPaused) {\n"
        "                if (!singlePlayerWasPaused) {\n"
        "                    singlePlayerWasPaused = true\n"
        "                    println(\"SINGLEPLAYER_WORLD: PAUSED\")\n"
        "                }\n"
        "                Server.heartbeat()\n"
        "                val now = System.currentTimeMillis()\n"
        "                for (player in Repository.players.filter { !it.isArtificial }) {\n"
        "                    player.session.lastPing = now\n"
        "                }\n"
        "                Thread.sleep(500L)\n"
        "                continue\n"
        "            } else if (singlePlayerWasPaused) {\n"
        "                singlePlayerWasPaused = false\n"
        "                println(\"SINGLEPLAYER_WORLD: RESUMED\")\n"
        "            }\n\n"
        "            Grafana.startTick()\n"
    )
    if loop_old not in source:
        raise SystemExit("MajorUpdateWorker loop anchor not found")
    source = source.replace(loop_old, loop_new, 1)

    method_old = "    fun tickOffline()\n    {\n"
    method_new = (
        "    private var singlePlayerWasPaused = false\n\n"
        "    private val singlePlayerPauseProbe by lazy {\n"
        "        try {\n"
        "            Class.forName(\"singleplayer.MobileLifecycleBridge\")\n"
        "                .getMethod(\"isAppPaused\")\n"
        "        } catch (_: Throwable) { null }\n"
        "    }\n\n"
        "    private fun isSinglePlayerPaused(): Boolean {\n"
        "        return try { singlePlayerPauseProbe?.invoke(null) == true }\n"
        "        catch (_: Throwable) { false }\n"
        "    }\n\n"
        "    fun tickOffline()\n    {\n"
    )
    if method_old not in source:
        raise SystemExit("MajorUpdateWorker helper anchor not found")
    worker.write_text(source.replace(method_old, method_new, 1))
    print("overlay: Android lifecycle pause")


def patch_server_host_mode(server_root: Path) -> None:
    server = server_root / "src/main/core/Server.kt"
    source = server.read_text()

    network_old = """        log(this::class.java, Log.INFO, "Starting networking...")
        try {
            reactor = NioReactor.configure(43594 + GameWorld.settings?.worldId!!)
            reactor!!.start()
            if (ServerConstants.WEBSOCKET_ENABLED) {
                val websocketPort = if (ServerConstants.WEBSOCKET_PORT > 0) {
                    ServerConstants.WEBSOCKET_PORT
                } else {
                    53594 + GameWorld.settings?.worldId!!
                }
                webSocketServer = GameWebSocketServer(websocketPort, 1)
                WebSocketTls.configure(webSocketServer!!)
                webSocketServer!!.start()
            }
        } catch (e: BindException) {
            log(this::class.java, Log.ERR, "Port " + (43594 + GameWorld.settings?.worldId!!) + " is already in use!")
            throw e
        }
"""
    network_new = """        if (!java.lang.Boolean.getBoolean("singleplayer")) {
            log(this::class.java, Log.INFO, "Starting networking...")
            try {
                reactor = NioReactor.configure(43594 + GameWorld.settings?.worldId!!)
                reactor!!.start()
                if (ServerConstants.WEBSOCKET_ENABLED) {
                    val websocketPort = if (ServerConstants.WEBSOCKET_PORT > 0) {
                        ServerConstants.WEBSOCKET_PORT
                    } else {
                        53594 + GameWorld.settings?.worldId!!
                    }
                    webSocketServer = GameWebSocketServer(websocketPort, 1)
                    WebSocketTls.configure(webSocketServer!!)
                    webSocketServer!!.start()
                }
            } catch (e: BindException) {
                log(this::class.java, Log.ERR, "Port " + (43594 + GameWorld.settings?.worldId!!) + " is already in use!")
                throw e
            }
        } else {
            reactor = null
            webSocketServer = null
            println("SINGLEPLAYER_WORLD: NETWORK_LISTENER_DISABLED")
        }
"""
    if network_old not in source:
        raise SystemExit("Server networking startup anchor not found")
    source = source.replace(network_old, network_new, 1)

    console_old = """        val scanner = Scanner(System.`in`)

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
    console_new = """        running = true
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
    if console_old not in source:
        raise SystemExit("Server console anchor not found")
    server.write_text(source.replace(console_old, console_new, 1))
    print("overlay: networkless single-player host")


def write_singleplayer_config(repo_root: Path, server_root: Path) -> Path:
    source = repo_root / "singleplayer/default.conf"
    if not source.is_file():
        raise SystemExit(f"Missing single-player config: {source}")
    generated = repo_root / "singleplayer-generated.conf"
    shutil.copy2(source, generated)
    shutil.copy2(source, server_root / "worldprops/local.conf")
    print("overlay: singleplayer-generated.conf")
    return generated


def digest_tree(root: Path) -> str:
    digest = hashlib.sha256()
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(root).as_posix().encode()
        digest.update(relative)
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def write_version_manifest(repo_root: Path, server_root: Path, config: Path) -> None:
    def digest(path: Path) -> str:
        return hashlib.sha256(path.read_bytes()).hexdigest()

    upstream_file = repo_root / "upstream-server-commit.txt"
    upstream = upstream_file.read_text().strip() if upstream_file.is_file() else "source-check"
    entries = [
        "upstream=" + upstream,
        "server=" + digest(server_root / "src/main/core/Server.kt"),
        "io-session=" + digest(server_root / "src/main/core/net/IoSession.java"),
        "pause-worker=" + digest(server_root / "src/main/core/worker/MajorUpdateWorker.kt"),
        "local-probe=" + digest(server_root / "src/main/core/local/LocalMigrationProbe.kt"),
        "native-overlay=" + digest_tree(repo_root / "singleplayer/server-patches/native"),
        "config=" + digest(config),
        "bootstrap=" + digest(repo_root / "singleplayer/inprocess/InProcessBootstrap.java"),
        "lifecycle=" + digest(repo_root / "singleplayer/inprocess/MobileLifecycleBridge.java"),
        "local-login=" + digest(repo_root / "singleplayer/client-patches/rt4/LocalLoginBridge.java"),
        "local-js5=" + digest(repo_root / "singleplayer/client-patches/rt4/LocalJs5Socket.java"),
        "sqlite-jdbc=3.53.4.0",
    ]
    (repo_root / "singleplayer-world-version.txt").write_text("\n".join(entries) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("server_root", type=Path)
    parser.add_argument("--repo-root", type=Path, default=Path.cwd())
    args = parser.parse_args()

    server_root = args.server_root.resolve()
    repo_root = args.repo_root.resolve()
    if not (server_root / "src/main/core").is_dir():
        raise SystemExit(f"Not a 2009Scape Server checkout: {server_root}")

    patch_sqlite_dependency(server_root)
    install_native_overlay(repo_root, server_root)
    patch_command_boundary(server_root)
    patch_presentation_boundary(server_root)
    patch_local_session_transport(server_root)
    patch_mobile_pause(server_root)
    patch_server_host_mode(server_root)
    config = write_singleplayer_config(repo_root, server_root)
    write_version_manifest(repo_root, server_root, config)
    print("single-player Android world overlay prepared")


if __name__ == "__main__":
    main()
