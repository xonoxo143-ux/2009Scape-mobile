#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 /path/to/client-boomps.jar" >&2
  exit 2
fi

input=$(realpath "$1")
if [[ ! -f "$input" ]]; then
  echo "input jar not found: $input" >&2
  exit 2
fi

repo_root=$(cd "$(dirname "$0")/.." && pwd)
props="$repo_root/boom-local/runelite-local.properties"
out="${input%.jar}-local.jar"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

cp "$input" "$out"
mkdir -p "$work/net/runelite/client"
cp "$props" "$work/net/runelite/client/runelite.properties"

(
  cd "$work"
  jar uf "$out" net/runelite/client/runelite.properties
)

printf 'patched client: %s\n' "$out"
printf 'embedded config:\n'
unzip -p "$out" net/runelite/client/runelite.properties | sed -n '1,40p'
