#!/usr/bin/env python3
"""Align shaded LWJGL fingerprints with the exact Android natives we package.

The retained RT4 client identifies Android as Linux/aarch64 from the embedded JVM,
so LWJGL consults its Linux/arm64 SHA-1 resource names. Our launcher deliberately
loads Android-built liblwjgl/libopenal binaries instead of upstream Linux ones.
Without replacing those two fingerprints LWJGL emits an alarming native/Java
version warning even when the intended Android binaries are loaded and working.
"""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

FINGERPRINTS = {
    "liblwjgl.so": "META-INF/linux/arm64/org/lwjgl/liblwjgl.so.sha1",
    "libopenal.so": "META-INF/linux/arm64/org/lwjgl/openal/libopenal.so.sha1",
}


def sha1(path: Path) -> str:
    digest = hashlib.sha1()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--rt4-jar", required=True, type=Path)
    parser.add_argument("--native-dir", required=True, type=Path)
    args = parser.parse_args()

    jar = args.rt4_jar.resolve()
    native_dir = args.native_dir.resolve()
    if not jar.is_file():
        raise SystemExit(f"Missing RT4 JAR: {jar}")

    replacements: dict[str, bytes] = {}
    for native_name, resource_name in FINGERPRINTS.items():
        native = native_dir / native_name
        if not native.is_file():
            raise SystemExit(f"Missing packaged Android native: {native}")
        digest = sha1(native)
        replacements[resource_name] = (digest + "\n").encode("ascii")
        print(f"align: {native_name} -> {resource_name} = {digest}")

    temp = jar.with_suffix(jar.suffix + ".android-fingerprints")
    seen: set[str] = set()
    replaced: set[str] = set()
    duplicates: set[str] = set()

    with ZipFile(jar, "r") as source, ZipFile(temp, "w", allowZip64=True) as target:
        for info in source.infolist():
            name = info.filename
            if name in seen:
                duplicates.add(name)
                continue
            seen.add(name)

            data = source.read(info)
            if name in replacements:
                data = replacements[name]
                replaced.add(name)

            # Preserve entry metadata while allowing ZipFile to recompute size,
            # CRC and compressed payload for the replaced bytes.
            clone = ZipInfo(name, date_time=info.date_time)
            clone.comment = info.comment
            clone.extra = info.extra
            clone.create_system = info.create_system
            clone.create_version = info.create_version
            clone.extract_version = info.extract_version
            clone.flag_bits = info.flag_bits
            clone.internal_attr = info.internal_attr
            clone.external_attr = info.external_attr
            clone.compress_type = info.compress_type if info.compress_type >= 0 else ZIP_DEFLATED
            target.writestr(clone, data)

    missing = set(replacements) - replaced
    if missing:
        temp.unlink(missing_ok=True)
        raise SystemExit("RT4 JAR is missing LWJGL fingerprint resources: " + ", ".join(sorted(missing)))

    os.replace(temp, jar)

    with ZipFile(jar, "r") as check:
        names = check.namelist()
        if len(names) != len(set(names)):
            raise SystemExit("Aligned RT4 JAR still contains duplicate entries")
        if "rt4/client.class" not in names:
            raise SystemExit("Aligned RT4 JAR lost rt4/client.class")
        for resource_name, expected in replacements.items():
            if check.read(resource_name) != expected:
                raise SystemExit(f"Fingerprint verification failed: {resource_name}")

    if duplicates:
        print("align: removed duplicate ZIP entries:", ", ".join(sorted(duplicates)))
    print("align: RT4 Android native fingerprints verified")


if __name__ == "__main__":
    main()
