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
RT4_RAW_ROOT = (
    "https://raw.githubusercontent.com/DaveRune/rt4-client/" + RT4_COMMIT
)

SIGNLINK_BLOB_SHA = "902d3c8922c96446946caf9f77374328b5146737"
SIGNLINK_URL = RT4_RAW_ROOT + "/signlink/src/main/java/rt4/SignLink.java"

MINIMENU_BLOB_SHA = "519c68b2207ad25c25c0ceffb3d0f51d9920eae2"
MINIMENU_URL = RT4_RAW_ROOT + "/client/src/main/java/rt4/MiniMenu.java"


def git_blob_sha(data: bytes) -> str:
    header = f"blob {len(data)}\0".encode("ascii")
    return hashlib.sha1(header + data).hexdigest()


def fetch_verified(url: str, expected_sha: str, label: str) -> str:
    with urllib.request.urlopen(url, timeout=60) as response:
        raw = response.read()
    actual = git_blob_sha(raw)
    if actual != expected_sha:
        raise SystemExit(
            f"Pinned {label} blob mismatch: {actual} != {expected_sha}"
        )
    return raw.decode("utf-8")


def replace_exact(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise SystemExit(
            f"Pinned RT4 {label} anchor count was {count}, expected 1"
        )
    return source.replace(old, new, 1)


def generate_signlink(output_root: Path) -> Path:
    source = fetch_verified(SIGNLINK_URL, SIGNLINK_BLOB_SHA, "SignLink")
    source = replace_exact(
        source,
        "\t\treturn this.enqueue(1, 0, hostname, port);\n",
        "\t\tPrivilegedRequest localJs5 = LocalSocketPolicy.openJs5IfRequested();\n"
        "\t\tif (localJs5 != null) {\n"
        "\t\t\treturn localJs5;\n"
        "\t\t}\n"
        "\t\treturn this.enqueue(1, 0, hostname, port);\n",
        "SignLink openSocket",
    )

    # SignLink is normally compiled as its own module, where java.awt.Component
    # is unambiguous. Our generated override is compiled beside rt4.jar, which
    # also contains rt4.Component; Java then resolves the same-package class
    # first. Qualify the three retained AWT uses so this source is semantically
    # identical when compiled in the combined single-player classpath.
    source = replace_exact(
        source,
        "int width, @OriginalArg(3) Component component,",
        "int width, @OriginalArg(3) java.awt.Component component,",
        "SignLink setCursor AWT component",
    )
    source = replace_exact(
        source,
        "@Pc(595) Component component = (Component) request.objectArg;",
        "@Pc(595) java.awt.Component component = (java.awt.Component) request.objectArg;",
        "SignLink cursor-manager AWT component",
    )
    source = replace_exact(
        source,
        "request.intArg2, (Component) args[0], request.intArg1,",
        "request.intArg2, (java.awt.Component) args[0], request.intArg1,",
        "SignLink setCursor AWT cast",
    )

    destination = output_root / "rt4/SignLink.java"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(source)
    print(f"generated RT4 override: {destination}")
    return destination


def wrap_npc_action(source: str, option: int, opcode: int, payload_line: str) -> str:
    old = (
        f"\t\t\t\tProtocol.outboundBuffer.p1isaac({opcode});\n"
        f"\t\t\t\t{payload_line}\n"
    )
    new = (
        f"\t\t\t\tif (!LocalClientCommands.npcAction({option}, local36)) {{\n"
        f"\t\t\t\t\tProtocol.outboundBuffer.p1isaac({opcode});\n"
        f"\t\t\t\t\t{payload_line}\n"
        "\t\t\t\t}\n"
    )
    return replace_exact(source, old, new, f"MiniMenu NPC option {option + 1}")


def wrap_scenery_action(
    source: str,
    option: int,
    opcode: int,
    payload_lines: list[str],
) -> str:
    old = f"\t\t\tProtocol.outboundBuffer.p1isaac({opcode});\n" + "".join(
        f"\t\t\t{line}\n" for line in payload_lines
    )
    new = (
        f"\t\t\tif (!LocalClientCommands.sceneryAction({option},\n"
        "\t\t\t\t\t(int) (local31 >>> 32) & Integer.MAX_VALUE,\n"
        "\t\t\t\t\tlocal15 + Camera.originX,\n"
        "\t\t\t\t\tlocal19 + Camera.originZ)) {\n"
        f"\t\t\t\tProtocol.outboundBuffer.p1isaac({opcode});\n"
        + "".join(f"\t\t\t\t{line}\n" for line in payload_lines)
        + "\t\t\t}\n"
    )
    return replace_exact(
        source, old, new, f"MiniMenu scenery option {option + 1}"
    )


def wrap_continue_option(source: str) -> str:
    old = (
        "\tpublic static void method10(@OriginalArg(0) int arg0, @OriginalArg(2) int arg1) {\n"
        "\t\tProtocol.outboundBuffer.p1isaac(132);\n"
        "\t\tProtocol.outboundBuffer.imp4(arg1);\n"
        "\t\tProtocol.outboundBuffer.ip2(arg0);\n"
        "\t}\n"
    )
    new = (
        "\tpublic static void method10(@OriginalArg(0) int arg0, @OriginalArg(2) int arg1) {\n"
        "\t\tif (Boolean.getBoolean(\"singleplayer\")\n"
        "\t\t\t\t&& singleplayer.InProcessBootstrap.LocalCommands.continueOption(\n"
        "\t\t\t\t\t\targ1 >>> 16, arg1 & 0xFFFF, arg0, 132)) {\n"
        "\t\t\tSystem.out.println(\"SINGLEPLAYER_LOCAL_COMMAND: CONTINUE_OPTION_DIRECT iface=\"\n"
        "\t\t\t\t\t+ (arg1 >>> 16) + \" child=\" + (arg1 & 0xFFFF) + \" slot=\" + arg0);\n"
        "\t\t\treturn;\n"
        "\t\t}\n"
        "\t\tProtocol.outboundBuffer.p1isaac(132);\n"
        "\t\tProtocol.outboundBuffer.imp4(arg1);\n"
        "\t\tProtocol.outboundBuffer.ip2(arg0);\n"
        "\t}\n"
    )
    return replace_exact(source, old, new, "MiniMenu continue option")


def generate_minimenu(output_root: Path) -> Path:
    source = fetch_verified(MINIMENU_URL, MINIMENU_BLOB_SHA, "MiniMenu")

    # Preserve RT4 pathfinding, crosshair, menu and local presentation behavior.
    # Only replace the point where each ordinary interaction becomes a 530 wire
    # packet. If a typed local command cannot be accepted, retain the historical
    # packet bytes as a compatibility fallback while the migration is unfinished.
    source = wrap_npc_action(
        source, 0, 78, "Protocol.outboundBuffer.ip2(local36);"
    )
    source = wrap_npc_action(
        source, 1, 3, "Protocol.outboundBuffer.ip2add(local36);"
    )
    source = wrap_npc_action(
        source, 2, 148, "Protocol.outboundBuffer.p2add(local36);"
    )
    source = wrap_npc_action(
        source, 3, 30, "Protocol.outboundBuffer.p2(local36);"
    )
    source = wrap_npc_action(
        source, 4, 218, "Protocol.outboundBuffer.ip2(local36);"
    )

    source = wrap_scenery_action(
        source,
        0,
        254,
        [
            "Protocol.outboundBuffer.ip2(local15 + Camera.originX);",
            "Protocol.outboundBuffer.p2add((int) (local31 >>> 32) & Integer.MAX_VALUE);",
            "Protocol.outboundBuffer.p2(local19 + Camera.originZ);",
        ],
    )
    source = wrap_scenery_action(
        source,
        1,
        194,
        [
            "Protocol.outboundBuffer.ip2add(local19 + Camera.originZ);",
            "Protocol.outboundBuffer.ip2(Camera.originX + local15);",
            "Protocol.outboundBuffer.p2((int) (local31 >>> 32) & Integer.MAX_VALUE);",
        ],
    )
    source = wrap_scenery_action(
        source,
        2,
        84,
        [
            "Protocol.outboundBuffer.ip2add(Integer.MAX_VALUE & (int) (local31 >>> 32));",
            "Protocol.outboundBuffer.ip2add(Camera.originZ + local19);",
            "Protocol.outboundBuffer.ip2(local15 + Camera.originX);",
        ],
    )
    source = wrap_scenery_action(
        source,
        3,
        247,
        [
            "Protocol.outboundBuffer.ip2(Camera.originZ + local19);",
            "Protocol.outboundBuffer.ip2add(local15 + Camera.originX);",
            "Protocol.outboundBuffer.p2(Integer.MAX_VALUE & (int) (local31 >>> 32));",
        ],
    )
    source = wrap_scenery_action(
        source,
        4,
        170,
        [
            "Protocol.outboundBuffer.ip2add(Integer.MAX_VALUE & (int) (local31 >>> 32));",
            "Protocol.outboundBuffer.ip2add(local15 + Camera.originX);",
            "Protocol.outboundBuffer.ip2add(local19 + Camera.originZ);",
        ],
    )
    source = wrap_continue_option(source)

    destination = output_root / "rt4/MiniMenu.java"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(source)
    print(f"generated RT4 override: {destination}")
    return destination


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("output_root", type=Path)
    args = parser.parse_args()
    output_root = args.output_root.resolve()
    generate_signlink(output_root)
    generate_minimenu(output_root)


if __name__ == "__main__":
    main()
