#!/usr/bin/env bash
# SPDX-License-Identifier: EPL-2.0
# Only the SWT.error() stub is synthetic; all other Java are original product sources.
set -euo pipefail
cd "$(dirname "$0")/../.."
out=target/m3-swt-donor-row-geometry
mkdir -p "$out/classes"
java_cmd="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac_cmd="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
"$java_cmd" -version > "$out/java-version.txt" 2>&1
"$javac_cmd" -version > "$out/javac-version.txt" 2>&1
sha256sum \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/ItemRange.java" \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/PackedHeapGrowth.java" \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/widgets/VirtualRowExtents.java" \
  "tools/release/headless-stubs/org/eclipse/swt/SWT.java" \
  "tools/release/java/org/eclipse/swt/widgets/VirtualRowExtentsDonorContract.java" > "$out/SOURCE_SHA256SUMS"
"$javac_cmd" --release 21 -Xlint:all -Werror -d "$out/classes" \
  "tools/release/headless-stubs/org/eclipse/swt/SWT.java" \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/ItemRange.java" \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/PackedHeapGrowth.java" \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/widgets/VirtualRowExtents.java" \
  "tools/release/java/org/eclipse/swt/widgets/VirtualRowExtentsDonorContract.java" \
  > "$out/compile.log" 2>&1
"$java_cmd" -ea -cp "$out/classes" org.eclipse.swt.widgets.VirtualRowExtentsDonorContract | tee "$out/run.log"
sha256sum "$out/SOURCE_SHA256SUMS" "$out/compile.log" "$out/run.log" > "$out/EVIDENCE_SHA256SUMS"
