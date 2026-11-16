#!/usr/bin/env bash
# SPDX-License-Identifier: EPL-2.0
# Native source is compiled unmodified except for the approved product fix.
# Test-only AndroidBitmap headers and C stubs never enter the production NDK library.
set -euo pipefail
cd "$(dirname "$0")/../.."
out=target/m3-android-ndk-pixels
mkdir -p "$out"
owner='bundles/org.eclipse.swt/Eclipse SWT PI/android/library/swt_pixels.c'
header='tools/release/c/android/bitmap.h'
contract='tools/release/c/swt_android_pixels_contract.c'
if [ -n "${JAVA_HOME:-}" ]; then
    java_home="$JAVA_HOME"
else
    java_home="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
fi
compiler="${CC:-cc}"
"$compiler" --version > "$out/compiler-version.txt" 2>&1
sha256sum "$owner" "$header" "$contract" > "$out/SOURCE_SHA256SUMS"
"$compiler" -std=c11 -O1 -g -Wall -Wextra -Werror -Wpedantic \
    -fsanitize=address,undefined -fno-omit-frame-pointer \
    -I tools/release/c -I "$java_home/include" -I "$java_home/include/linux" \
    "$owner" "$contract" -o "$out/swt_android_pixels_contract" \
    > "$out/compile.log" 2>&1
ASAN_OPTIONS=detect_leaks=1 "$out/swt_android_pixels_contract" | tee "$out/run.log"
sha256sum "$out/SOURCE_SHA256SUMS" "$out/compile.log" "$out/run.log" > "$out/EVIDENCE_SHA256SUMS"
