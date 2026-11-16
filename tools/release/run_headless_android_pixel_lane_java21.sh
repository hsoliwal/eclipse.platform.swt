#!/usr/bin/env bash
# SPDX-License-Identifier: EPL-2.0
# Compile the actual Android SWT pixel-copy owner with a test-only Bitmap shim.
set -euo pipefail
cd "$(dirname "$0")/../.."
out=target/m3-swt-android-pixel-lane
mkdir -p "$out/classes"
java -version > "$out/java-version.txt" 2>&1
javac -version > "$out/javac-version.txt" 2>&1
owner='bundles/org.eclipse.swt/Eclipse SWT/android/org/eclipse/swt/internal/android/AndroidPixels.java'
stub='tools/release/headless-stubs/android/graphics/Bitmap.java'
contract='tools/release/java/org/eclipse/swt/internal/android/AndroidPixelLaneContract.java'
sha256sum "$owner" "$stub" "$contract" > "$out/SOURCE_SHA256SUMS"
javac --release 21 -Xlint:all -Werror -d "$out/classes" \
 "$owner" "$stub" "$contract" > "$out/compile.log" 2>&1
java -ea -cp "$out/classes" org.eclipse.swt.internal.android.AndroidPixelLaneContract \
 | tee "$out/run.log"
sha256sum "$out/SOURCE_SHA256SUMS" "$out/compile.log" "$out/run.log" \
 > "$out/EVIDENCE_SHA256SUMS"
