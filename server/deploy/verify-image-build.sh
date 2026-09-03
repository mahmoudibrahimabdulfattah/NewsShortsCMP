#!/usr/bin/env bash
#
# Stands in for `docker build -f server/Dockerfile .` on a machine with no
# docker, and is worth running even on one that has it because it is far
# faster.
#
# What it checks is the only thing the image build can fail on that a normal
# `./gradlew build` cannot: :server depends on :core:contract, which the app
# needs to have an Android target, and the image has no Android SDK. The
# contract's convention plugin gates that on -Pnewsshorts.contract.targets=jvm.
# If that gating breaks, every local build stays green and the deploy stops
# working — so this script copies exactly the paths server/Dockerfile COPYs
# into a clean tree, hides the SDK, and runs the same Gradle command.
#
# Usage: server/deploy/verify-image-build.sh [output-dir]
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="${1:-${TMPDIR:-/tmp}/newsshorts-image-build}"

rm -rf "$OUT"
mkdir -p "$OUT/gradle"

# Keep this list in step with the COPY lines in server/Dockerfile.
cp "$REPO/gradle/libs.versions.toml" "$OUT/gradle/libs.versions.toml"
cp -R "$REPO/build-logic" "$OUT/build-logic"
mkdir -p "$OUT/core"
cp -R "$REPO/core/contract" "$OUT/core/contract"
cp "$REPO/server/deploy/settings.gradle.kts" "$OUT/settings.gradle.kts"
cp -R "$REPO/server" "$OUT/server"

# The image builds a clean checkout; strip anything the working tree carries.
find "$OUT" -type d \( -name build -o -name .gradle \) -prune -exec rm -rf {} + 2>/dev/null || true
rm -f "$OUT/local.properties"

# The image has gradle on its PATH; the wrapper is the closest equivalent here
# and pins the same version.
cp -R "$REPO/gradle/wrapper" "$OUT/gradle/wrapper"
cp "$REPO/gradlew" "$OUT/gradlew"

cd "$OUT"
env -u ANDROID_HOME -u ANDROID_SDK_ROOT \
    ./gradlew :server:installDist --no-daemon -Pnewsshorts.contract.targets=jvm

echo
echo "Image build simulated successfully in $OUT"
