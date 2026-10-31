#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Off == stock gate of a feature switch (FEATURE_SWITCH_RULES.md rule 6) for a drawn-plane switch: the recorded Tree differential
# (official SWT 3.135.0 recording, render-modes/tools/tree_differential_modes.sh) in a drawn mode, with the switch property set.
# Builds a class directory of this worktree (the win32 classes of compile_platform.sh plus the non-Java resources of the win32
# source roots), compiles the differential and its harness, then runs the modes under ui_lock.sh on the hidden desktop with the
# staged natives of the Library revision (H:/m3-scratch/gc-wiring/natives, m3gc_natives.sh). Output under H:/m3-scratch.
# Usage: differential_off.sh <feature-id> <true|false> <modes...>
#   env: WT (default C:/m3-superset/swt-features), RUN (default H:/m3-scratch/web-switches/differential), CLASS (default
#   Test_org_eclipse_swt_widgets_Tree_ViewportDifferential), NATIVES, BUDGET (seconds per JVM, default 900)
FEATURE=$1; VALUE=$2; shift 2
WT=${WT:-C:/m3-superset/swt-features}; RUN=${RUN:-H:/m3-scratch/web-switches/differential}
CLASS=${CLASS:-Test_org_eclipse_swt_widgets_Tree_ViewportDifferential}
NATIVES=${NATIVES:-H:/m3-scratch/gc-wiring/natives}; MAIN=C:/m3-superset/build-check-win32-$(basename "$WT")
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
JUNIT=C:/Users/hsoliwal/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar
SWT="$RUN/swt-classes"; TESTS="$RUN/tests"; T="$WT/tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit"
[ -d "$MAIN/org" ] || { echo "no classes: run compile_platform.sh $WT win32 first"; exit 3; }
rm -rf "$SWT" "$TESTS"; mkdir -p "$SWT" "$TESTS"; cp -r "$MAIN/." "$SWT/"
grep -o 'bundles/org.eclipse.swt/[^,\\]*' "$WT/binaries/org.eclipse.swt.win32.win32.x86_64/build.properties" | tr -d '\r' | sed 's/ *$//' | sort -u |
  while IFS= read -r d; do
    [ -d "$WT/$d" ] || continue
    (cd "$WT/$d" && find . -type f \( -name '*.svg' -o -name '*.properties' -o -name '*.png' -o -name '*.ico' -o -name '*.txt' \) -print |
      while IFS= read -r f; do mkdir -p "$SWT/$(dirname "$f")"; cp "$f" "$SWT/$f"; done)
  done
echo "swt classes: $(find "$SWT" -name '*.class' | wc -l) classes, $(find "$SWT" -type f ! -name '*.class' | wc -l) resources"
"$JDK/bin/javac" -J-Xmx768m --release 21 -encoding UTF-8 -nowarn -proc:none -cp "$SWT;$JUNIT" -d "$TESTS" \
  "$T/$CLASS.java" "$T/ViewportDifferential.java" > "$RUN/tests-javac.log" 2>&1
n=$(grep -cE ': error:|^error: ' "$RUN/tests-javac.log"); n=${n:-0}; echo "differential tests javac errors=$n"; [ "$n" -eq 0 ] || { head -20 "$RUN/tests-javac.log"; exit 1; }
W() { cygpath -w "$1"; }
export BUDGET=${BUDGET:-900}
export JVM_OPTS="-Dswt.widget.feature.$FEATURE=$VALUE -Djava.library.path=$(W "$NATIVES") -Dswt.library.path=$(W "$NATIVES")"
OUT="$RUN/$FEATURE-$VALUE"; mkdir -p "$OUT"
# the official recording is made once per run directory and reused by every mode and every value
# with the reference captures (*-official.png) the pixel similarity of every mode is measured against the same stock pixels
[ -d "$RUN/recording" ] && [ ! -d "$OUT/recording" ] && cp -r "$RUN/recording" "$OUT/recording"
[ -d "$RUN/official-evidence" ] && mkdir -p "$OUT/evidence" && cp "$RUN/official-evidence/"*-official.png "$OUT/evidence/"
bash C:/m3-superset/tools/ui_lock.sh claude-feature-switches bash "$WT/docs/m3/render-modes/tools/tree_differential_modes.sh" "$SWT" "$TESTS" "$OUT" "$CLASS" "$@"
rc=$?
[ -d "$RUN/recording" ] || { [ -d "$OUT/recording" ] && cp -r "$OUT/recording" "$RUN/recording"; }
[ -d "$RUN/official-evidence" ] || { mkdir -p "$RUN/official-evidence" && cp "$OUT/evidence/"*-official.png "$RUN/official-evidence/" 2>/dev/null; }
exit $rc
