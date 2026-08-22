#!/usr/bin/env python3
"""Verify that ordinary classpath entries in a JAR are Java 8 bytecode."""

from __future__ import annotations

import argparse
import struct
import zipfile
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("jar", type=Path)
    parser.add_argument("report", type=Path)
    args = parser.parse_args()

    baseline: list[tuple[int, str]] = []
    multi_release: list[tuple[int, str]] = []
    highest = (0, "")
    class_count = 0
    with zipfile.ZipFile(args.jar) as archive:
        for name in archive.namelist():
            if not name.endswith(".class"):
                continue
            header = archive.read(name)[:8]
            if len(header) < 8 or header[:4] != b"\xca\xfe\xba\xbe":
                continue
            class_count += 1
            major = struct.unpack(">H", header[6:8])[0]
            if major > highest[0]:
                highest = (major, name)
            if major > 52:
                target = multi_release if name.startswith("META-INF/versions/") else baseline
                target.append((major, name))

    args.report.parent.mkdir(parents=True, exist_ok=True)
    with args.report.open("w") as report:
        report.write(f"artifact={args.jar}\n")
        report.write(f"class_count={class_count}\n")
        report.write(f"highest_major={highest[0]}\n")
        report.write(f"highest_class={highest[1]}\n")
        report.write(f"baseline_offenders={len(baseline)}\n")
        report.write(f"multi_release_offenders={len(multi_release)}\n")
        report.write(f"total_offenders={len(baseline) + len(multi_release)}\n\n")
        report.write("[baseline offenders: normal classpath entries]\n")
        for major, name in sorted(baseline):
            report.write(f"major={major} path={name}\n")
        report.write("\n[multi-release entries ignored by Java 8]\n")
        for major, name in sorted(multi_release):
            report.write(f"major={major} path={name}\n")

    print(args.report.read_text())
    if baseline:
        raise SystemExit(f"Found {len(baseline)} Java 9+ classpath entries")


if __name__ == "__main__":
    main()
