#!/usr/bin/env bash
# SPDX-License-Identifier: EPL-2.0
# Compiles real SWT owner sources; only the error-constant SWT stub is test-only.
set -euo pipefail
cd "$(dirname "$0")/../.."
out=target/m3-swt-bit-lane-prefix
mkdir -p "$out/classes"
java_cmd="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac_cmd="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
"$java_cmd" -version > "$out/java-version.txt" 2>&1
"$javac_cmd" -version > "$out/javac-version.txt" 2>&1
sha256sum \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/PackedHeapGrowth.java" \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/widgets/VirtualGrowth.java" \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/widgets/VirtualBitLanes.java" \
  "tools/release/headless-stubs/org/eclipse/swt/SWT.java" \
  "tools/release/java/org/eclipse/swt/widgets/VirtualBitLanesLogicalPrefixContract.java" \
  > "$out/SOURCE_SHA256SUMS"
"$javac_cmd" --release 21 -Xlint:all -Werror -d "$out/classes" \
  "tools/release/headless-stubs/org/eclipse/swt/SWT.java" \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/PackedHeapGrowth.java" \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/widgets/VirtualGrowth.java" \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/widgets/VirtualBitLanes.java" \
  "tools/release/java/org/eclipse/swt/widgets/VirtualBitLanesLogicalPrefixContract.java" \
  > "$out/compile.log" 2>&1
"$java_cmd" -ea -cp "$out/classes" org.eclipse.swt.widgets.VirtualBitLanesLogicalPrefixContract \
  | tee "$out/run.log"
sha256sum "$out/SOURCE_SHA256SUMS" "$out/compile.log" "$out/run.log" > "$out/EVIDENCE_SHA256SUMS"
