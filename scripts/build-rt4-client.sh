#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLIENT_DIR="$ROOT_DIR/rt4-client"
ASSET_DIR="$ROOT_DIR/app_pojavlauncher/src/main/assets"

if [[ ! -f "$CLIENT_DIR/UPSTREAM_COMMIT" || ! -x "$CLIENT_DIR/gradlew" ]]; then
  echo "RT4 source is missing. Let the GitHub source-import workflow finish first." >&2
  exit 1
fi

"$CLIENT_DIR/gradlew" \
  --project-dir "$CLIENT_DIR" \
  --no-daemon \
  :client:clean \
  :client:jar \
  :plugin-playground:clean \
  :plugin-playground:classes

mapfile -t CLIENT_JARS < <(find "$CLIENT_DIR/client/build/libs" -maxdepth 1 -type f -name 'client-*.jar' | sort)
if [[ ${#CLIENT_JARS[@]} -ne 1 ]]; then
  echo "Expected one RT4 client JAR; found ${#CLIENT_JARS[@]}." >&2
  printf '%s\n' "${CLIENT_JARS[@]}" >&2
  exit 1
fi

install -m 0644 "${CLIENT_JARS[0]}" "$ASSET_DIR/rt4.jar"
sha256sum "$ASSET_DIR/rt4.jar" | cut -d ' ' -f 1 > "$ASSET_DIR/rt4.version"

PLUGIN_CLASSES="$CLIENT_DIR/plugin-playground/build/classes/kotlin/main"
PLUGIN_ZIP="$ASSET_DIR/plugins/MobileClientBindings.zip"
if [[ ! -d "$PLUGIN_CLASSES/MobileClientBindings" ]]; then
  echo "MobileClientBindings classes were not produced." >&2
  exit 1
fi

rm -f "$PLUGIN_ZIP"
(
  cd "$PLUGIN_CLASSES"
  zip -q -r "$PLUGIN_ZIP" MobileClientBindings
)

unzip -tq "$PLUGIN_ZIP"
test -s "$ASSET_DIR/rt4.jar"
test -s "$ASSET_DIR/rt4.version"

echo "RT4 source: $(tr -d '\r\n' < "$CLIENT_DIR/UPSTREAM_COMMIT")"
echo "RT4 JAR:    $(tr -d '\r\n' < "$ASSET_DIR/rt4.version")"
echo "Bindings:   $(sha256sum "$PLUGIN_ZIP" | cut -d ' ' -f 1)"
