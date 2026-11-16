#!/usr/bin/env bash
# SPDX-License-Identifier: EPL-2.0
# Compile actual SWT product sources; never substitute a widget/model fixture.
set -euo pipefail
cd "$(dirname "$0")/../.."
out=target/m3-swt-tree-physical-index
mkdir -p "$out/classes"
java_cmd="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac_cmd="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
"$javac_cmd" -version > "$out/javac-version.txt" 2>&1
"$java_cmd" -version > "$out/java-version.txt" 2>&1
src="bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/widgets"
sha256sum \
 "$src/VirtualItemState.java" \
 "$src/VirtualTreeSiblingIndex.java" \
 "$src/VirtualTreeTopology.java" \
 "$src/VirtualTreeVisibleProjection.java" \
 "$src/VirtualLongIntTable.java" \
 "$src/VirtualTreeResidentPlan.java" \
 "tools/release/java/org/eclipse/swt/widgets/VirtualTreeResidencyIndexContract.java" \
 > "$out/SOURCE_SHA256SUMS"
"$javac_cmd" --release 21 -Xlint:all -Werror -d "$out/classes" \
 "$src/VirtualItemState.java" \
 "$src/VirtualTreeSiblingIndex.java" \
 "$src/VirtualTreeTopology.java" \
 "$src/VirtualTreeVisibleProjection.java" \
 "$src/VirtualLongIntTable.java" \
 "$src/VirtualTreeResidentPlan.java" \
 "tools/release/java/org/eclipse/swt/widgets/VirtualTreeResidencyIndexContract.java" \
 > "$out/compile.log" 2>&1
"$java_cmd" -ea -cp "$out/classes" org.eclipse.swt.widgets.VirtualTreeResidencyIndexContract \
 | tee "$out/run.log"
sha256sum "$out/SOURCE_SHA256SUMS" "$out/compile.log" "$out/run.log" \
 > "$out/EVIDENCE_SHA256SUMS"
