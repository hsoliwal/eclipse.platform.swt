#!/usr/bin/env bash
# SPDX-License-Identifier: EPL-2.0
# Actual source from SWT owner, no library-mimicking implementation.
set -euo pipefail
cd "$(dirname "$0")/../.."
out=target/m3-swt-selection-window
mkdir -p "$out/classes"
java -version > "$out/java-version.txt" 2>&1
javac -version > "$out/javac-version.txt" 2>&1
sha256sum \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/ViewportRuntime.java" \
  "tools/release/java/org/eclipse/swt/internal/ViewportSelectionWindowContract.java" > "$out/SOURCE_SHA256SUMS"
javac --release 21 -Xlint:all -Werror -d "$out/classes" \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/ViewportRuntime.java" \
  "tools/release/java/org/eclipse/swt/internal/ViewportSelectionWindowContract.java" \
  > "$out/compile.log" 2>&1
java -ea -cp "$out/classes" org.eclipse.swt.internal.ViewportSelectionWindowContract | tee "$out/run.log"
sha256sum "$out/SOURCE_SHA256SUMS" "$out/compile.log" "$out/run.log" > "$out/EVIDENCE_SHA256SUMS"
