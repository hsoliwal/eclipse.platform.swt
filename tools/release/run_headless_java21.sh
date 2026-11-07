#!/usr/bin/env bash
# SPDX-License-Identifier: EPL-2.0
# Standalone Java 21 sparse-state contract (no Maven, native library or GUI).
set -euo pipefail
cd "$(dirname "$0")/../.."
javac_cmd="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
java_cmd="${JAVA_HOME:+$JAVA_HOME/bin/}java"
out=target/m3-swt-headless
mkdir -p "$out/classes"

# These are the product Java sources. No extracted or fixture implementation.
"$javac_cmd" --release 21 -Xlint:all -Werror -d "$out/classes" \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/PackedHeapGrowth.java" \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/residency/ItemBitSet.java" \
  "tools/release/java/org/eclipse/swt/internal/residency/M3SwtHeadlessContract.java" \
  > "$out/compile.log" 2>&1

"$java_cmd" -ea -cp "$out/classes" \
  org.eclipse.swt.internal.residency.M3SwtHeadlessContract \
  | tee "$out/run.log"

# A complete release receipt also needs the exact Git tree and runner identity;
# these local hashes describe only the produced outputs.
sha256sum "$out/compile.log" "$out/run.log" > "$out/SHA256SUMS"
