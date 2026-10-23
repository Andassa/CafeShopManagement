#!/usr/bin/env bash
# Lance les tests unitaires (requiert un JDK 8 avec JavaFX).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/cafeShopManagementSystem"
OUT="$ROOT/out/tests"
CP="$APP/dist/lib/*"

rm -rf "$OUT"
mkdir -p "$OUT"
find "$APP/src" "$ROOT/tests" -name '*.java' > "$OUT/sources.txt"
javac -encoding UTF-8 -source 1.8 -target 1.8 -nowarn -cp "$CP" -d "$OUT" @"$OUT/sources.txt"

STATUT=0
for TEST in AnalyseVentesTest AssistantIATest; do
  java -cp "$OUT:$CP" "cafeshopmanagementsystem.$TEST" || STATUT=1
done
exit $STATUT
