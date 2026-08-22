#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
if [[ -d "$ROOT/vendor/2009scape/Server" ]]; then
  SERVER_ROOT="$ROOT/vendor/2009scape/Server"
elif [[ -d "$ROOT/server/2009scape-master/Server" ]]; then
  SERVER_ROOT="$ROOT/server/2009scape-master/Server"
else
  echo "Unable to locate the canonical 2009Scape Server tree" >&2
  exit 1
fi
CORE="$SERVER_ROOT/src/main/content/global/leagues/core"
MANAGER="$SERVER_ROOT/src/main/content/global/leagues/GrandLeagueManager.kt"
QA="$ROOT/qa/grandleague"
BUILD="$QA/build"
mkdir -p "$BUILD"
rm -f "$BUILD/domain.jar" "$BUILD/adapter.jar"

kotlinc -Werror "$CORE"/*.kt \
  "$QA/VerticalSliceAcceptance.kt" \
  "$QA/SharedTriggerAcceptance.kt" \
  "$QA/StressAcceptance.kt" \
  "$QA/ContentCatalogueAcceptance.kt" \
  "$QA/ModifierEngineAcceptance.kt" \
  "$QA/CombatCapstoneAcceptance.kt" \
  -d "$BUILD/domain.jar"
kotlin -classpath "$BUILD/domain.jar" VerticalSliceAcceptanceKt
kotlin -classpath "$BUILD/domain.jar" SharedTriggerAcceptanceKt
kotlin -classpath "$BUILD/domain.jar" StressAcceptanceKt
kotlin -classpath "$BUILD/domain.jar" ContentCatalogueAcceptanceKt
kotlin -classpath "$BUILD/domain.jar" ModifierEngineAcceptanceKt
kotlin -classpath "$BUILD/domain.jar" CombatCapstoneAcceptanceKt

mapfile -t STUBS < <(find "$QA/adapter-stubs/src" -name '*.kt' ! -name AdapterAcceptance.kt | sort)
kotlinc -Werror -classpath "$BUILD/domain.jar" "$MANAGER" "${STUBS[@]}" "$QA/adapter-stubs/src/AdapterAcceptance.kt" -d "$BUILD/adapter.jar"
kotlin -classpath "$BUILD/domain.jar:$BUILD/adapter.jar" AdapterAcceptanceKt
bash "$QA/ServerSeamAcceptance.sh"

grep -q 'QuestCompleteEvent' "$SERVER_ROOT/src/main/core/game/node/entity/player/link/quest/Quest.java"
grep -q 'QuestCompleted' "$SERVER_ROOT/src/main/core/api/Event.kt"
echo 'GRAND LEAGUE LOCAL GATE PASS'
