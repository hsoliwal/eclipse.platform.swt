#!/usr/bin/env bash
# SPDX-License-Identifier: EPL-2.0
# Standalone Java 21 sparse-state contract (no Maven, native library or GUI).
set -euo pipefail
cd "$(dirname "$0")/../.."
javac_cmd="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
java_cmd="${JAVA_HOME:+$JAVA_HOME/bin/}java"
out=target/m3-swt-headless
mkdir -p "$out/classes"

# Source identities must refer to exact checked-out product and test bytes.
git rev-parse HEAD > "$out/COMMIT"
git rev-parse 'HEAD^{tree}' > "$out/TREE"
git status --porcelain --untracked-files=no > "$out/TRACKED_WORKTREE"
test ! -s "$out/TRACKED_WORKTREE"
"$java_cmd" -version > "$out/java-version.txt" 2>&1
"$javac_cmd" -version > "$out/javac-version.txt" 2>&1
sha256sum \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/PackedHeapGrowth.java" \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/residency/ItemBitSet.java" \
  "tools/release/java/org/eclipse/swt/internal/residency/M3SwtHeadlessContract.java" \
  > "$out/SOURCE_SHA256SUMS"

# These are the product Java sources. No extracted or fixture implementation.
"$javac_cmd" --release 21 -Xlint:all -Werror -d "$out/classes" \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/PackedHeapGrowth.java" \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/residency/ItemBitSet.java" \
  "tools/release/java/org/eclipse/swt/internal/residency/M3SwtHeadlessContract.java" \
  > "$out/compile.log" 2>&1

"$java_cmd" -ea -cp "$out/classes" \
  org.eclipse.swt.internal.residency.M3SwtHeadlessContract \
  | tee "$out/run.log"

# Keep the toolchain and run bytes separate from original source SHA-256.
# Published artifacts are evidence, not a full SWT native/GUI release pass.
sha256sum "$out/COMMIT" "$out/TREE" "$out/SOURCE_SHA256SUMS" \
  "$out/java-version.txt" "$out/javac-version.txt" \
  "$out/compile.log" "$out/run.log" > "$out/SHA256SUMS"
