#!/usr/bin/env python3
"""Generate tiny RT4 source overrides from the exact pinned client revision.

Large retained RT4 classes stay upstream-authoritative. When a migration needs a
small seam in one of them, fetch the exact source blob, verify its Git blob SHA,
and apply only the documented replacement into a generated build directory.
"""
from __future__ import annotations

import argparse
import hashlib
import urllib.request
from pathlib import Path

RT4_COMMIT = "e8589f36209c34ded7a9a545be498739dabb167b"
SIGNLINK_BLOB_SHA = "902d3c8922c96446946caf9f77374328b5146737"
SIGNLINK_URL = (
    "https://raw.githubusercontent.com/DaveRune/rt4-client/"
    + RT4_COMMIT
    + "/signlink/src/main/java/rt4/SignLink.java"
)


def git_blob_sha(data: bytes) -> str:
    header = f"blob {len(data)}\0".encode("ascii")
    return hashlib.sha1(header + data).hexdigest()


def generate_signlink(output_root: Path) -> Path:
    with urllib.request.urlopen(SIGNLINK_URL, timeout=60) as response:
        raw = response.read()
    actual = git_blob_sha(raw)
    if actual != SIGNLINK_BLOB_SHA:
        raise SystemExit(
            f"Pinned SignLink blob mismatch: {actual} != {SIGNLINK_BLOB_SHA}"
        )

    source = raw.decode("utf-8")
    old = "\t\treturn this.enqueue(1, 0, hostname, port);\n"
    new = (
        "\t\tPrivilegedRequest localJs5 = LocalSocketPolicy.openJs5IfRequested();\n"
        "\t\tif (localJs5 != null) {\n"
        "\t\t\treturn localJs5;\n"
        "\t\t}\n"
        "\t\treturn this.enqueue(1, 0, hostname, port);\n"
    )
    if source.count(old) != 1:
        raise SystemExit("Pinned SignLink openSocket anchor was not unique")
    source = source.replace(old, new, 1)

    destination = output_root / "rt4/SignLink.java"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(source)
    print(f"generated RT4 override: {destination}")
    return destination


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("output_root", type=Path)
    args = parser.parse_args()
    generate_signlink(args.output_root.resolve())


if __name__ == "__main__":
    main()
