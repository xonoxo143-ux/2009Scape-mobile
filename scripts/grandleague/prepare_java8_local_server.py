#!/usr/bin/env python3
"""Prepare a clean 2009Scape Server tree for the Android Java 8 runtime.

Every replacement is deliberately anchored to the pinned upstream source. If
upstream moves, this script fails instead of silently producing an unverified
server. The source tree passed to this script should be a disposable checkout.
"""

from __future__ import annotations

import argparse
from pathlib import Path


def replace(root: Path, relative: str, old: str, new: str, count: int = 1) -> None:
    path = root / relative
    text = path.read_text()
    found = text.count(old)
    if found < count:
        raise RuntimeError(
            f"{relative}: expected at least {count} occurrence(s) of {old!r}, found {found}"
        )
    path.write_text(text.replace(old, new, count))


def patch_java8_sources(root: Path) -> None:
    # Compile on JDK 8 itself, not merely to class-file version 52 on a newer JDK.
    replace(root, "pom.xml", "<kotlin.compiler.jvmTarget>11</kotlin.compiler.jvmTarget>",
            "<kotlin.compiler.jvmTarget>1.8</kotlin.compiler.jvmTarget>")
    replace(root, "pom.xml", "<maven.compiler.source>11</maven.compiler.source>",
            "<maven.compiler.source>1.8</maven.compiler.source>")
    replace(root, "pom.xml", "<maven.compiler.target>11</maven.compiler.target>",
            "<maven.compiler.target>1.8</maven.compiler.target>")

    # Java 10 local-variable inference.
    replace(root, "src/main/core/game/node/entity/player/link/quest/QuestRepository.java",
            "var theQuest = QUESTS.get(quest);", "Quest theQuest = QUESTS.get(quest);")
    replace(root, "src/main/content/region/asgarnia/taverley/quest/witchshouse/WitchsHouse.java",
            "var line = 12;", "int line = 12;")
    replace(root, "src/main/content/region/misthalin/varrock/quest/whatliesbelow/WhatLiesBelow.java",
            "var line = 12;", "int line = 12;")
    replace(root, "src/main/content/region/asgarnia/taverley/quest/WolfWhistle.java",
            "var line = 12;", "int line = 12;")

    # Java 9 added Matcher overloads for StringBuilder; Java 8 requires StringBuffer.
    replace(root, "src/main/core/game/dialogue/DialogueBuilder.kt",
            'var graphSb = StringBuilder("digraph {\\n")',
            'var graphSb = StringBuffer("digraph {\\n")')
    replace(root, "src/main/core/game/dialogue/DialogueBuilder.kt",
            "var clauseSb = StringBuilder()", "var clauseSb = StringBuffer()")
    replace(root, "src/main/core/game/dialogue/DialogueBuilder.kt",
            "var tmpSb = StringBuilder()", "var tmpSb = StringBuffer()")
    replace(root, "src/main/core/game/dialogue/DialogueInterpreter.java",
            "StringBuilder sb = new StringBuilder();", "StringBuffer sb = new StringBuffer();")

    # Java 9 collection factories and covariant Buffer returns.
    replace(root, "src/main/core/game/world/map/zone/ZoneMonitor.java",
            "import java.util.ArrayList;\nimport java.util.Iterator;",
            "import java.util.ArrayList;\nimport java.util.Arrays;\nimport java.util.HashSet;\nimport java.util.Iterator;")
    replace(root, "src/main/core/game/world/map/zone/ZoneMonitor.java",
            "static final Set<Integer> MID_WILDY_TELEPORT_JEWELLERY = Set.of(",
            "static final Set<Integer> MID_WILDY_TELEPORT_JEWELLERY = new HashSet<>(Arrays.asList(")
    replace(root, "src/main/core/game/world/map/zone/ZoneMonitor.java",
            "\t\tItems.RING_OF_LIFE_2570\n\t);", "\t\tItems.RING_OF_LIFE_2570\n\t));")
    replace(root, "src/main/core/net/registry/AccountRegister.java",
            "\t\tsession.queue(buf.flip());", "\t\tbuf.flip();\n\t\tsession.queue(buf);")


def patch_loopback_binding(root: Path) -> None:
    reactor = root / "src/main/core/net/NioReactor.java"
    text = reactor.read_text()
    old = (
        "\tpublic static NioReactor configure(int port) throws IOException {\n"
        "\t\treturn configure(port, 1);\n"
        "\t}\n"
    )
    new = (
        "\tpublic static NioReactor configure(int port) throws IOException {\n"
        "\t\treturn configure(null, port, 1);\n"
        "\t}\n\n"
        "\tpublic static NioReactor configure(String address, int port) throws IOException {\n"
        "\t\treturn configure(address, port, 1);\n"
        "\t}\n"
    )
    if old not in text:
        raise RuntimeError("NioReactor.java: one-argument configure anchor moved")
    text = text.replace(old, new, 1)

    old = (
        "\tpublic static NioReactor configure(int port, int poolSize) throws IOException {\n"
        "\t\tNioReactor reactor = new NioReactor(new IoEventHandler(Executors.newFixedThreadPool(poolSize)));\n"
        "\t\tServerSocketChannel channel = ServerSocketChannel.open();\n"
        "\t\tSelector selector = Selector.open();\n"
        "\t\tchannel.bind(new InetSocketAddress(port));\n"
        "\t\tchannel.configureBlocking(false);\n"
        "\t\tchannel.register(selector, SelectionKey.OP_ACCEPT);\n"
        "\t\treactor.channel = new ServerSocketConnection(selector, channel);\n"
        "\t\treturn reactor;\n"
        "\t}\n"
    )
    new = (
        "\tpublic static NioReactor configure(int port, int poolSize) throws IOException {\n"
        "\t\treturn configure(null, port, poolSize);\n"
        "\t}\n\n"
        "\tpublic static NioReactor configure(String address, int port, int poolSize) throws IOException {\n"
        "\t\tNioReactor reactor = new NioReactor(new IoEventHandler(Executors.newFixedThreadPool(poolSize)));\n"
        "\t\tServerSocketChannel channel = ServerSocketChannel.open();\n"
        "\t\tSelector selector = Selector.open();\n"
        "\t\tchannel.bind(address == null ? new InetSocketAddress(port) : new InetSocketAddress(address, port));\n"
        "\t\tchannel.configureBlocking(false);\n"
        "\t\tchannel.register(selector, SelectionKey.OP_ACCEPT);\n"
        "\t\treactor.channel = new ServerSocketConnection(selector, channel);\n"
        "\t\treturn reactor;\n"
        "\t}\n"
    )
    if old not in text:
        raise RuntimeError("NioReactor.java: pooled configure anchor moved")
    reactor.write_text(text.replace(old, new, 1))

    replace(root, "src/main/core/Server.kt",
            "reactor = NioReactor.configure(43594 + GameWorld.settings?.worldId!!)",
            'reactor = NioReactor.configure("127.0.0.1", 43594 + GameWorld.settings?.worldId!!)')


def create_local_config(root: Path) -> None:
    source = root / "worldprops/default.conf"
    destination = root / "worldprops/local-singleplayer.conf"
    text = source.read_text()
    settings = {
        "watchdog_enabled = true": "watchdog_enabled = false",
        "enable_default_clan = true": "enable_default_clan = false",
        "enable_bots = true": "enable_bots = false",
        "max_adv_bots = 100": "max_adv_bots = 0",
    }
    for old, new in settings.items():
        if old not in text:
            raise RuntimeError(f"default.conf: expected setting moved: {old!r}")
        text = text.replace(old, new, 1)
    destination.write_text(text)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("server_root", type=Path)
    args = parser.parse_args()
    root = args.server_root.resolve()
    if not (root / "pom.xml").is_file():
        raise SystemExit(f"Not a 2009Scape Server tree: {root}")
    patch_java8_sources(root)
    patch_loopback_binding(root)
    create_local_config(root)
    print(f"Prepared Java 8 loopback-only server tree: {root}")


if __name__ == "__main__":
    main()
