#!/bin/bash
# ── Minecraft Client Update Java Agent Build Script (Linux/macOS) ──
# Usage: ./build.sh
# Output: UpdateAgent.jar (launcher) + UpdateAgent_core.jar (core)
# ──────────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$SCRIPT_DIR/src"
BUILD_DIR="$SCRIPT_DIR/build"
LAUNCHER_JAR="$SCRIPT_DIR/UpdateAgent.jar"
CORE_JAR="$SCRIPT_DIR/UpdateAgent_core.jar"
# Pin the target release so the JARs run on the JVM that ships with Minecraft
# (Java 15+ is required for Ed25519) instead of on whatever JDK built them.
RELEASE="${JAVA_RELEASE:-15}"

echo "[build] Compiling (target Java $RELEASE)..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
find "$SRC_DIR" -type f -name '*.java' -print0 | xargs -0 javac --release "$RELEASE" -d "$BUILD_DIR"

echo "[build] Packaging launcher JAR..."
cd "$BUILD_DIR"
jar cfm "$LAUNCHER_JAR" "$SCRIPT_DIR/META-INF/MANIFEST.MF" Launcher.class

echo "[build] Packaging core JAR..."
# Keep the launcher out of the self-updatable core, but include the default
# package UpdateAgent compatibility facade and all named-package classes.
jar cf "$CORE_JAR" UpdateAgent.class com

echo "[build] Done!"
echo "[build] Launcher: $LAUNCHER_JAR"
echo "[build] Core:     $CORE_JAR"

# Clean up temp class files
cd "$SCRIPT_DIR"
rm -rf "$BUILD_DIR"
