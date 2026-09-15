#!/usr/bin/env python3
"""Patch retained Mining/Woodcutting depletion at narrow League seams."""
from __future__ import annotations

import argparse
from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 source anchor in {path}, found {count}")
    path.write_text(text.replace(old, new, 1))
    print(f"overlay: {label}")


def patch_mining(server_root: Path) -> None:
    path = server_root / "src/main/content/global/skill/gather/mining/MiningListener.kt"
    old = """            // Transform ore to depleted version
            if (!isEssence && resource!!.respawnRate != 0) {
"""
    new = """            // Endless Harvest keeps ordinary ore nodes active and immediately
            // resumes the retained mining loop after a successful reward.
            if (!isEssence && resource!!.respawnRate != 0 &&
                core.local.league.EndlessHarvestHooks.preventDepletion(player)) {
                return delayScript(player, getDelay(resource, tool))
            }

            // Transform ore to depleted version
            if (!isEssence && resource!!.respawnRate != 0) {
"""
    replace_once(path, old, new, "Endless Harvest mining persistence")


def patch_woodcutting(server_root: Path) -> None:
    path = server_root / "src/main/content/global/skill/gather/woodcutting/WoodcuttingListener.kt"
    old = """    private fun rollDepletion(player: Player, node: Scenery, resource: WoodcuttingNode): Boolean {
        //transform to depleted version
"""
    new = """    private fun rollDepletion(player: Player, node: Scenery, resource: WoodcuttingNode): Boolean {
        // Endless Harvest prevents the player's successful chop from felling
        // the resource node. Reward/XP logic remains entirely retained.
        if (core.local.league.EndlessHarvestHooks.preventDepletion(player)) return false

        //transform to depleted version
"""
    replace_once(path, old, new, "Endless Harvest woodcutting persistence")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("server_root", type=Path)
    args = parser.parse_args()
    server_root = args.server_root.resolve()
    if not (server_root / "src/main/content/global/skill/gather").is_dir():
        raise SystemExit(f"Not a prepared 2009Scape Server source tree: {server_root}")
    patch_mining(server_root)
    patch_woodcutting(server_root)


if __name__ == "__main__":
    main()
