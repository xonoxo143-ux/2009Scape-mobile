#!/usr/bin/env python3
import argparse
import io
import zipfile

RESOURCE = "net/runelite/client/runelite.properties"


def patch_properties(raw: bytes, host: str, port: int) -> bytes:
    text = raw.decode("utf-8")
    out = []
    seen = set()
    replacements = {
        "runelite.enable_local": "true",
        "runelite.js5_ip": host,
        "runelite.js5_port": str(port),
    }
    for line in text.splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key = line.split("=", 1)[0].strip()
            if key in replacements:
                out.append(f"{key}={replacements[key]}")
                seen.add(key)
                continue
        out.append(line)
    for key, value in replacements.items():
        if key not in seen:
            out.append(f"{key}={value}")
    return ("\n".join(out) + "\n").encode("utf-8")


def main() -> None:
    ap = argparse.ArgumentParser(description="Patch BoomPS client jar for localhost probing without modifying game classes")
    ap.add_argument("input")
    ap.add_argument("output")
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--port", type=int, default=43594)
    args = ap.parse_args()

    with zipfile.ZipFile(args.input, "r") as zin:
        props = zin.read(RESOURCE)
        patched = patch_properties(props, args.host, args.port)
        with zipfile.ZipFile(args.output, "w") as zout:
            for info in zin.infolist():
                data = patched if info.filename == RESOURCE else zin.read(info.filename)
                zout.writestr(info, data)

    with zipfile.ZipFile(args.output, "r") as check:
        print(check.read(RESOURCE).decode("utf-8"))


if __name__ == "__main__":
    main()
