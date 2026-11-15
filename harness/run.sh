#!/usr/bin/env bash
# Harnais hors-Android : compile SyncApi / MediaSyncQueue réels contre des stubs (org.json, Context, ApiClient).
# Usage : ./run.sh <dossier-contenant-app/src/main/java/com/unitepaintball/app>
set -euo pipefail
APP="${1:?chemin vers app/src/main/java/com/unitepaintball/app}"
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$(mktemp -d)"
javac -d "$OUT" $(find "$HERE/src" -name "*.java") "$APP/SyncApi.java" "$APP/MediaSyncQueue.java" \
  "$HERE/test/com/unitepaintball/app/SyncRaceTest.java" "$HERE/test/com/unitepaintball/app/SyncEdgeTest.java" "$HERE/test/com/unitepaintball/app/MediaRaceTest.java"
java -cp "$OUT" com.unitepaintball.app.SyncRaceTest
java -cp "$OUT" com.unitepaintball.app.SyncEdgeTest
java -cp "$OUT" com.unitepaintball.app.MediaRaceTest
