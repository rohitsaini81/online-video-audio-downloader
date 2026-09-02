#!/usr/bin/env bash

set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

cd "$PROJECT_DIR"

echo "Building signed debug APK..."
./gradlew :app:assembleDebug

if [[ ! -f "$APK_PATH" ]]; then
    echo "Build completed, but APK was not found at: $APK_PATH" >&2
    exit 1
fi

echo
echo "Debug APK created successfully:"
echo "$APK_PATH"
ls -lh "$APK_PATH"
sha256sum "$APK_PATH"
