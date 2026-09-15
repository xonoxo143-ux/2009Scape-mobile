#!/usr/bin/env python3
"""Report presentation/network coupling in retained 2009Scape game systems.

This is a migration metric, not a semantic correctness test. It intentionally
counts concrete dependency markers so native-UI work can prove old RT4/protocol
coupling is shrinking rather than accumulating another permanent adapter layer.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class SystemSpec:
    name: str
    paths: tuple[str, ...]
    owner: str


SYSTEMS = (
    SystemSpec("skills", ("core/game/node/entity/skill", "content/global/handlers/iface/tabs/StatsTabInterface.kt"), "GAME_CORE"),
    SystemSpec("inventory", ("core/game/container", "content/global/handlers/item"), "GAME_CORE"),
    SystemSpec("equipment", ("core/game/container/impl/EquipmentContainer.java", "content/global/handlers/iface/tabs/EquipmentTabInterface.kt"), "GAME_CORE"),
    SystemSpec("quests", ("core/game/node/entity/player/link/quest", "content/data/Quests.kt", "content/global/handlers/iface/tabs/QuestTabInterface.kt"), "CONTENT+GAME_CORE"),
    SystemSpec("dialogue", ("core/game/dialogue",), "CONTENT+GAME_CORE"),
    SystemSpec("prayer", ("core/game/node/entity/player/link/prayer", "content/global/handlers/iface/tabs/PrayerTabInterface.kt"), "GAME_CORE"),
    SystemSpec("magic", ("core/game/node/entity/combat/spell", "content/global/skill/magic", "content/global/handlers/iface/tabs/MagicTabInterface.kt"), "CONTENT+GAME_CORE"),
    SystemSpec("combat", ("core/game/node/entity/combat", "content/global/handlers/iface/tabs/CombatTabInterface.java"), "GAME_CORE"),
    SystemSpec("bank", ("core/game/container/impl/BankContainer.java", "content/global/handlers/iface/bank"), "GAME_CORE"),
    SystemSpec("shops", ("core/game/shops", "content/global/handlers/iface/ShoppingPlugin.java"), "CONTENT+GAME_CORE"),
    SystemSpec("movement", ("core/game/interaction/MovementPulse.java", "core/game/world/map/path"), "GAME_CORE"),
    SystemSpec("npcs", ("core/game/node/entity/npc",), "GAME_CORE"),
    SystemSpec("scenery", ("core/game/node/scenery",), "GAME_CORE"),
    SystemSpec("ground_items", ("core/game/node/item",), "GAME_CORE"),
    SystemSpec("persistence", ("core/game/node/entity/player/info/login",), "GAME_CORE"),
    SystemSpec("audio_music", ("core/game/node/entity/player/link/audio", "core/game/node/entity/player/link/music"), "GAME_CORE"),
)

ANCHORS = (
    "core/game/node/entity/player/Player.java",
    "core/game/node/entity/skill/Skills.java",
    "core/game/container/impl/EquipmentContainer.java",
    "core/game/container/impl/BankContainer.java",
    "core/game/node/entity/player/link/quest/QuestRepository.java",
    "core/game/dialogue/DialogueInterpreter.java",
    "core/game/node/entity/player/link/prayer/Prayer.java",
    "core/game/shops/Shop.kt",
    "core/game/node/entity/combat/CombatPulse.kt",
    "core/game/world/map/path/Pathfinder.java",
    "core/game/node/entity/player/info/login/PlayerSaver.kt",
)

MARKERS = {
    "packet": ("core.net.packet", "PacketRepository", "getPacketDispatch("),
    "interface": ("core.game.component", "getInterfaceManager(", "openInterface(", "openComponent("),
    "client_state": ("setVarp(", "setVarbit(", "sendRunScript(", "sendString("),
    "transport": ("java.net.", "new Socket(", "SocketChannel.open(", "IoSession"),
}


def collect(base: Path, entries: tuple[str, ...]) -> list[Path]:
    out: list[Path] = []
    seen: set[Path] = set()
    for raw in entries:
        path = base / raw
        candidates = [path] if path.is_file() else list(path.rglob("*")) if path.is_dir() else []
        for candidate in candidates:
            if candidate.suffix not in {".java", ".kt"} or candidate in seen:
                continue
            seen.add(candidate)
            out.append(candidate)
    return sorted(out)


def count_markers(files: list[Path]) -> dict[str, int]:
    counts = {name: 0 for name in MARKERS}
    for path in files:
        text = path.read_text(encoding="utf-8", errors="ignore")
        for name, needles in MARKERS.items():
            counts[name] += sum(text.count(needle) for needle in needles)
    return counts


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--server-root", type=Path, required=True,
                        help="Path to Server (the directory containing src/main)")
    args = parser.parse_args()
    source = args.server_root.resolve() / "src/main"
    if not source.is_dir():
        raise SystemExit(f"AUDIT FAIL: missing source root {source}")

    for anchor in ANCHORS:
        if not (source / anchor).is_file():
            raise SystemExit(f"AUDIT FAIL: missing architecture anchor {anchor}")

    print("system\towner\tfiles\tpacket\tinterface\tclient_state\ttransport")
    for spec in SYSTEMS:
        files = collect(source, spec.paths)
        if not files:
            raise SystemExit(f"AUDIT FAIL: no files found for {spec.name}")
        counts = count_markers(files)
        print(
            f"{spec.name}\t{spec.owner}\t{len(files)}\t"
            f"{counts['packet']}\t{counts['interface']}\t"
            f"{counts['client_state']}\t{counts['transport']}"
        )

    print("AUDIT PASS: engine ownership anchors present; coupling inventory emitted")


if __name__ == "__main__":
    main()
