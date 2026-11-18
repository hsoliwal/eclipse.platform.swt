#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/.." && pwd)"
src="$root/cc/swt"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Platform-free reference pixels and packed-region bounds.
"${CXX:-clang++}" -std=c++17 -Wall -Wextra -Werror -pedantic -O2 \
    -I"$src" "$here/dirty-check.cc" -o "$tmp/dirty-check"
"$tmp/dirty-check"

# Compile the ACTUAL MayaComposition.cc receiver's non-Windows JNI branch,
# not a mocked C++ reproduction. Check all seven old + one new symbols with
# Java 21's native signature resolver. It deliberately cannot test D3D/Win32.
if [[ -z "${JAVA_HOME:-}" ]]; then
    JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
fi
test -f "$JAVA_HOME/include/jni.h"
test -f "$JAVA_HOME/include/linux/jni_md.h"
"${CXX:-clang++}" -std=c++17 -Wall -Wextra -Werror -pedantic -O2 \
    -fPIC -shared -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
    -I"$src" "$src/MayaComposition.cc" -o "$tmp/libmaya_native_abi.so"
mkdir -p "$tmp/classes"
javac --release 21 -Xlint:all -Werror -d "$tmp/classes" "$here/MayaCompositionStubProof.java"
java -Xcheck:jni -cp "$tmp/classes" \
    org.eclipse.swt.internal.maya.MayaCompositionStubProof "$tmp/libmaya_native_abi.so"

# Validate exactly the same independent CPU-side region arithmetic that the
# SWT viewport uses before an optional multi-region JNI upload. No SWT/JUnit
# or graphics display is required for this strict Java 21 proof.
regionSource="$here/../../../Eclipse SWT/common/org/eclipse/swt/internal/render/NativeDirtyRegions.java"
javac --release 21 -Xlint:all -Werror -d "$tmp/classes" \
    "$regionSource" "$here/NativeDirtyRegionsProof.java"
java -cp "$tmp/classes" org.eclipse.swt.internal.render.NativeDirtyRegionsProof
