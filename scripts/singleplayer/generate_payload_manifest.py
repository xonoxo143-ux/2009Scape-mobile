#!/usr/bin/env python3
"""Build the content-addressed manifest used by the Android single-player updater."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path

FILES = {
    "rt4.jar": "rt4.jar",
    "singleplayer-bootstrap.jar": "singleplayer-bootstrap.jar",
    "engine.jar": "singleplayer/engine.jar",
    "world-default.conf": "singleplayer/world-default.conf",
    "world-data.zip": "singleplayer/world-data.zip",
    "runtime-jre17.tar.xz": "singleplayer/runtime-jre17.tar.xz",
    "runtime-jre17-version.txt": "singleplayer/runtime-jre17-version.txt",
    "LocalSinglePlayerLogin.zip": "plugins/LocalSinglePlayerLogin.zip",
    "MobileTouchControls.zip": "plugins/MobileTouchControls.zip",
    "LoginTimer.zip": "plugins/LoginTimer.zip",
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--assets-root", required=True, type=Path)
    parser.add_argument("--version", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--publish-dir", type=Path)
    parser.add_argument("--release-base")
    args = parser.parse_args()

    version = args.version.strip()
    if not version:
        raise SystemExit("payload version must not be empty")
    if (args.publish_dir is None) != (args.release_base is None):
        raise SystemExit("--publish-dir and --release-base must be supplied together")

    publish_dir = args.publish_dir
    if publish_dir is not None:
        publish_dir.mkdir(parents=True, exist_ok=True)
    release_base = args.release_base.rstrip("/") if args.release_base else None

    entries = []
    for logical_name, relative_path in FILES.items():
        source = args.assets_root / relative_path
        if not source.is_file() or source.stat().st_size <= 0:
            raise SystemExit(f"missing payload input: {source}")
        digest = sha256(source)
        size = source.stat().st_size
        entry = {
            "name": logical_name,
            "sha256": digest,
            "size": size,
        }
        if publish_dir is not None and release_base is not None:
            asset_name = f"{digest}-{logical_name}"
            destination = publish_dir / asset_name
            if not destination.exists() or destination.stat().st_size != size:
                shutil.copy2(source, destination)
            entry["url"] = f"{release_base}/{asset_name}"
        entries.append(entry)

    manifest = {
        "schema": 1,
        "version": version,
        "files": entries,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
