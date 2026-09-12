#!/usr/bin/env python3
"""Make LWJGL native verification correct for the custom Android build.

The retained RT4 JAR identifies the embedded Android JVM as Linux/aarch64, so
LWJGL looks for the stock Linux/arm64 SHA-1 resources. The APK deliberately
packages Android-built liblwjgl/libopenal binaries produced by the launcher
native build instead. Those binaries are not the upstream Linux artifacts and
therefore can never legitimately match the stock Linux fingerprints.

Do not disable LWJGL checks globally. Remove only the two inapplicable stock
fingerprint resources. LWJGL's hash verifier explicitly treats an absent bundled
fingerprint as "not applicable", while all of its other safety checks remain on.
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

FINGERPRINTS = {
    "liblwjgl.so": "META-INF/linux/arm64/org/lwjgl/liblwjgl.so.sha1",
    "libopenal.so": "META-INF/linux/arm64/org/lwjgl/openal/libopenal.so.sha1",
}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--rt4-jar", required=True, type=Path)
    parser.add_argument("--native-dir", required=True, type=Path)
    args = parser.parse_args()

    jar = args.rt4_jar.resolve()
    native_dir = args.native_dir.resolve()
    if not jar.is_file():
        raise SystemExit(f"Missing RT4 JAR: {jar}")

    # Keep these as task inputs and sanity checks. Their bytes are custom Android
    # binaries; intentionally do not claim that they are stock Linux natives.
    for native_name in FINGERPRINTS:
        native = native_dir / native_name
        if not native.is_file():
            raise SystemExit(f"Missing packaged Android native source: {native}")

    strip_names = set(FINGERPRINTS.values())
    temp = jar.with_suffix(jar.suffix + ".android-fingerprints")
    seen: set[str] = set()
    stripped: set[str] = set()
    duplicates: set[str] = set()

    with ZipFile(jar, "r") as source, ZipFile(temp, "w", allowZip64=True) as target:
        for info in source.infolist():
            name = info.filename
            if name in seen:
                duplicates.add(name)
                continue
            seen.add(name)

            if name in strip_names:
                stripped.add(name)
                continue

            data = source.read(info)
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

    os.replace(temp, jar)

    with ZipFile(jar, "r") as check:
        names = check.namelist()
        if len(names) != len(set(names)):
            raise SystemExit("RT4 JAR still contains duplicate entries")
        if "rt4/client.class" not in names:
            raise SystemExit("RT4 JAR lost rt4/client.class")
        remaining = strip_names.intersection(names)
        if remaining:
            raise SystemExit(
                "Inapplicable Android LWJGL fingerprints remain: "
                + ", ".join(sorted(remaining))
            )

    for name in sorted(stripped):
        print(f"android-native: removed inapplicable stock fingerprint {name}")
    if not stripped:
        print("android-native: stock Linux fingerprints already absent")
    if duplicates:
        print("android-native: removed duplicate ZIP entries:", ", ".join(sorted(duplicates)))
    print("android-native: LWJGL/OpenAL custom Android verification policy ready")


if __name__ == "__main__":
    main()
