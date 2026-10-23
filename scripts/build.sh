#!/usr/bin/env bash
# Compile l'application sans NetBeans (requiert un JDK 8 avec JavaFX).
# Résultat : out/package/CafeShopManagement.jar + out/package/lib/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/cafeShopManagementSystem"
LIB="$APP/dist/lib"
OUT="$ROOT/out"
MAIN_CLASS="cafeshopmanagementsystem.CafeShopManagementSystem"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/package/lib"

echo "> Compilation des sources"
find "$APP/src" -name '*.java' > "$OUT/sources.txt"
javac -encoding UTF-8 -source 1.8 -target 1.8 \
  -cp "$LIB/*" -d "$OUT/classes" @"$OUT/sources.txt"

echo "> Copie des ressources (FXML, CSS, rapports)"
rsync -a --exclude='*.java' "$APP/src/" "$OUT/classes/"

echo "> Création du manifest"
CLASS_PATH="$(cd "$LIB" && ls *.jar | sed 's|^|lib/|' | tr '\n' ' ')"
{
  echo "Main-Class: $MAIN_CLASS"
  # Les lignes d'un manifest sont limitées à 72 octets : on coupe avec des lignes de continuation.
  echo "Class-Path: $CLASS_PATH" | fold -w 70 | sed '2,$s/^/ /'
} > "$OUT/MANIFEST.MF"

echo "> Packaging"
jar cfm "$OUT/package/CafeShopManagement.jar" "$OUT/MANIFEST.MF" -C "$OUT/classes" .
cp "$LIB"/*.jar "$OUT/package/lib/"

echo "OK : $OUT/package/CafeShopManagement.jar"
