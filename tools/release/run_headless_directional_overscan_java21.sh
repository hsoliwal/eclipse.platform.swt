#!/usr/bin/env bash
# SPDX-License-Identifier: EPL-2.0
# No GUI dependencies or test doubles: compile the actual ViewportRuntime source.
set -euo pipefail
cd "$(dirname "$0")/../.."
out=target/m3-directional-overscan-java21
mkdir -p "$out/classes"
javac_cmd="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
java_cmd="${JAVA_HOME:+$JAVA_HOME/bin/}java"
"$javac_cmd" -version > "$out/javac-version.txt" 2>&1
"$java_cmd" -version > "$out/java-version.txt" 2>&1
sha256sum \
 "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/ViewportRuntime.java" \
 "tools/release/java/org/eclipse/swt/internal/DirectionalOverscanContract.java" \
 > "$out/SOURCE_SHA256SUMS"
"$javac_cmd" --release 21 -Xlint:all -Werror -d "$out/classes" \
 "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/ViewportRuntime.java" \
 "tools/release/java/org/eclipse/swt/internal/DirectionalOverscanContract.java" \
 > "$out/compile.log" 2>&1
"$java_cmd" -ea -cp "$out/classes" org.eclipse.swt.internal.DirectionalOverscanContract \
 | tee "$out/run.log"
sha256sum "$out/SOURCE_SHA256SUMS" "$out/compile.log" "$out/run.log" > "$out/EVIDENCE_SHA256SUMS"
