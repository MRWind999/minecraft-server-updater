#!/bin/bash
# ── Minecraft Client Update Java Agent self-check (Linux/macOS) ──
# Compiles src/ plus test/ into a temporary directory and runs AgentSelfCheck.
# Usage: ./run-tests.sh
# ──────────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RELEASE="${JAVA_RELEASE:-15}"
BUILD_DIR="$(mktemp -d)"
trap 'rm -rf "$BUILD_DIR"' EXIT

echo "[test] Compiling sources and self-check (target Java $RELEASE)..."
find "$SCRIPT_DIR/src" "$SCRIPT_DIR/test" -type f -name '*.java' -print0 \
    | xargs -0 javac --release "$RELEASE" -d "$BUILD_DIR"

echo "[test] Running AgentSelfCheck..."
java -cp "$BUILD_DIR" AgentSelfCheck
