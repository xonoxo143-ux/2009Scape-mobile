#!/usr/bin/env python3
"""Materialize the exact retained RT4 PathFinder with the minimap cutover gate.

The production rt4.jar was built from the lwjgl-mobile-callbacks source line.
Rather than vendoring a second 25 KB copy of PathFinder, fetch the exact known
source blob, verify it byte-for-byte by Git blob SHA, then apply three tiny
return-path edits. The edits suppress MiniMenu's historical 14-byte minimap
trailer only when ClientProt already routed that walk directly to the local
world authority.
"""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
from urllib.request import urlopen

RT4_SOURCE_COMMIT = "6597f30de490ea60c2a7425ecf9a9f7599501343"
PATHFINDER_BLOB_SHA = "1099042a32b9a704a2dcd27f874b46ce9e546046"
PATHFINDER_URL = (
    "https://raw.githubusercontent.com/DaveRune/rt4-client/"
    + RT4_SOURCE_COMMIT
    + "/client/src/main/java/rt4/PathFinder.java"
)


def git_blob_sha(data: bytes) -> str:
    header = b"blob " + str(len(data)).encode("ascii") + b"\0"
    return hashlib.sha1(header + data).hexdigest()


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if text.count(old) != 1:
        raise SystemExit(
            f"{label}: expected exactly one source anchor, found {text.count(old)}"
        )
    return text.replace(old, new, 1)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    with urlopen(PATHFINDER_URL, timeout=30) as response:
        source_bytes = response.read()

    actual_sha = git_blob_sha(source_bytes)
    if actual_sha != PATHFINDER_BLOB_SHA:
        raise SystemExit(
            "RT4 PathFinder source drift: "
            f"{actual_sha} != {PATHFINDER_BLOB_SHA}"
        )

    text = source_bytes.decode("utf-8")
    edits = (
        (
            "\t\tif (local61 > 0) {\n"
            "\t\t\tClientProt.method3502(local61, arg9);\n"
            "\t\t\treturn true;\n"
            "\t\t} else return arg9 != 1;",
            "\t\tif (local61 > 0) {\n"
            "\t\t\tClientProt.method3502(local61, arg9);\n"
            "\t\t\treturn !ClientProt.consumeDirectMinimapWalk();\n"
            "\t\t} else return arg9 != 1;",
            "size-2 path",
        ),
        (
            "\t\tif (local64 > 0) {\n"
            "\t\t\tClientProt.method3502(local64, arg4);\n"
            "\t\t\treturn true;\n"
            "\t\t} else return arg4 != 1;",
            "\t\tif (local64 > 0) {\n"
            "\t\t\tClientProt.method3502(local64, arg4);\n"
            "\t\t\treturn !ClientProt.consumeDirectMinimapWalk();\n"
            "\t\t} else return arg4 != 1;",
            "size-1 path",
        ),
        (
            "\t\tif (local69 > 0) {\n"
            "\t\t\tClientProt.method3502(local69, arg2);\n"
            "\t\t\treturn true;\n"
            "\t\t} else return arg2 != 1;",
            "\t\tif (local69 > 0) {\n"
            "\t\t\tClientProt.method3502(local69, arg2);\n"
            "\t\t\treturn !ClientProt.consumeDirectMinimapWalk();\n"
            "\t\t} else return arg2 != 1;",
            "size-N path",
        ),
    )

    for old, new, label in edits:
        text = replace_once(text, old, new, label)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(text, encoding="utf-8")
    print(
        "RT4 movement source prepared: "
        f"{args.output} source={RT4_SOURCE_COMMIT} blob={PATHFINDER_BLOB_SHA}"
    )


if __name__ == "__main__":
    main()
