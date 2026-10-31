#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Display legs on the hidden desktop, one JVM at a time, each phase under one C:/m3-superset/tools/ui_lock.sh ticket:
#   matrix    the display rows of the combination matrix: the reference JVM records every row (original), then one JVM per
#             other mode compares its rows (viewport, custom, m3, gpu)
#   gallery   Test_M3ExampleGallery under JaCoCo (window, child JVM, in-process runner)
#   examples  every gallery entry in each of the five modes (M3ExampleRunner --batch, JaCoCo; a blocked or crashed entry is
#             recorded and the batch resumes at the next entry in a fresh JVM)
# Natives: the Library revision's set (H:/m3-scratch/gc-wiring/natives, 4975r19). Output under H:/m3-scratch/swt-examples.
# Usage: run_display.sh <phase> [worktree]      (internal: run_display.sh --inside <phase> <worktree>)
if [ "$1" != --inside ]; then
  exec bash C:/m3-superset/tools/ui_lock.sh claude-swt-examples bash "$0" --inside "$1" "${2:-C:/m3-superset/swt-examples}"
fi
PHASE=$2; WT=$3; O=${M3_OUT:-H:/m3-scratch/swt-examples}
NAT=${M3_NATIVES:-H:/m3-scratch/gc-wiring/natives}; W() { cygpath -w "$1"; }
LIB=C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib
JUNIT=C:/Users/hsoliwal/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar
AGENT=C:/Users/hsoliwal/.m2/repository/org/jacoco/org.jacoco.agent/0.8.15/org.jacoco.agent-0.8.15-runtime.jar
SKIJA="$LIB/skija-shared-0.116.3.jar;$LIB/skija-windows-x64-0.116.3.jar;$LIB/types-0.1.1.jar"
CP="$O/tests;$O/snippets;$O/platform/win32;$SKIJA;$JUNIT"
INCLUDES='org.eclipse.swt.snippets.m3.gallery.*:org.eclipse.swt.snippets.m3.tools.*:org.eclipse.swt.tests.m3.tools.*'
NATIVE_OPTS="-Djava.library.path=$(W "$NAT") -Dswt.library.path=$(W "$NAT")"
mkdir -p "$O/results" "$O/coverage" "$O/examples"
echo "phase $PHASE on desktop $M3_UI_DESKTOP $(date -Iseconds)"
hidden () { # <budget-seconds> <log> <java arguments...>
  local budget=$1 log=$2; shift 2
  bash C:/m3-superset/tools/hidden_java.sh "$budget" "$log" -Xmx2g -XX:ErrorFile="$(W "$O/results")\\hs_err_%p.log" $NATIVE_OPTS "$@"
}
summary () { echo "$(basename "$1" .log): rc=$2 $(sed 's/\x1b\[[0-9;]*m//g' "$1" | grep -E 'tests (successful|failed|aborted|skipped)' | sed -E 's/^\[ *//; s/ +\]$//' | tr -s ' ' | tr '\n' ' ')"; }
matrix () { # env: MATRIX (default: the checked-in TSV), TAG (result name, default display), MODES, RESUME (keep earlier rows), ROW_LIMIT
  local REC=$O/recording/${TAG:-display} RES=$O/results/matrix-${TAG:-display}.tsv record attempt rc log
  local M=${MATRIX:-$WT/tests/org.eclipse.swt.tests/tools/m3-combinations/COMBINATION_MATRIX.tsv}
  [ -n "$RESUME" ] || { rm -rf "$REC"; rm -f "$RES"; rm -rf "$O/results/stacks"; }
  for mode in ${MODES:-original viewport custom m3 gpu}; do
    record=false; [ "$mode" = original ] && record=true
    # a row over the row limit halts its JVM with 3 after recording TIMEOUT; the next JVM resumes after it
    for attempt in 1 2 3 4 5 6 7 8 9 10; do
      log=$O/results/${TAG:-display}-matrix-$mode-$attempt.log
      hidden 3600 "$log" -Dswt.widget.mode=$mode -Dswt.viewport.record=$record -Dm3.combinations.env=display -Dm3.combinations.resume=true \
        -Dm3.combinations.rowLimit=${ROW_LIMIT:-900} "-Dswt.viewport.recording=$(W "$REC")" "-Dm3.combinations.results=$(W "$RES")" "-Dm3.combinations.matrix=$(W "$M")" \
        -cp "$CP" org.junit.platform.console.ConsoleLauncher execute --select-class org.eclipse.swt.tests.junit.m3combinations.Test_M3CombinationMatrix \
        --disable-banner --disable-ansi-colors --details=summary
      rc=$?; summary "$log" $rc
      [ $rc -eq 3 ] || break
    done
  done
}
gallery () {
  hidden 1200 "$O/results/display-gallery.log" "-javaagent:$(W "$AGENT")=destfile=$(W "$O/coverage/display-gallery.exec"),includes=$INCLUDES" -Dm3.runner.delay=400 \
    "-Dm3.repo.root=$(W "$WT")" -cp "$CP" org.junit.platform.console.ConsoleLauncher execute --select-package org.eclipse.swt.tests.junit.m3gallery \
    --disable-banner --disable-ansi-colors --details=tree
  summary "$O/results/display-gallery.log" $?
}
examples () { # env: MODES, RESUME (keep earlier lines, continue each mode after its last entry)
  local RES=$O/results/examples-modes.tsv size first rc last
  size=$(sed -n 's/.*int SIZE = \([0-9]*\);.*/\1/p' "$WT/examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/m3/gallery/M3GalleryIndex.java")
  [ -n "$RESUME" ] || rm -f "$RES"
  # the single-example entry point of the runner (what the gallery's "Run and close" starts), under JaCoCo
  hidden 300 "$O/examples/single.log" "-javaagent:$(W "$AGENT")=destfile=$(W "$O/coverage/examples-single.exec"),includes=$INCLUDES" -Dm3.runner.delay=400 \
    -cp "$CP" org.eclipse.swt.snippets.m3.gallery.M3ExampleRunner org.eclipse.swt.snippets.m3.gallery.widgets.M3ControlsExample Label
  echo "single run: rc=$? $(cut -f5 "$O/examples/single.log" | tail -1)"
  for mode in ${MODES:-viewport original custom m3 gpu}; do
    # with RESUME, a mode continues after its last recorded entry
    first=$([ -f "$RES" ] && awk -F'\t' -v m=$mode '$1==m {l=$2} END {print (l == "" ? 0 : l + 1)}' "$RES" || echo 0)
    while [ "$first" -lt "$size" ]; do
      hidden 3600 "$O/examples/$mode-$first.log" "-javaagent:$(W "$AGENT")=destfile=$(W "$O/coverage/examples-$mode.exec"),includes=$INCLUDES" \
        -Dswt.widget.mode=$mode -Dm3.runner.timeout=45000 -cp "$CP" org.eclipse.swt.snippets.m3.gallery.M3ExampleRunner --batch $mode "$(W "$RES")" "$(W "$O/examples/png")" $first
      rc=$?
      last=$(awk -F'\t' -v m=$mode '$1==m {l=$2} END {print (l == "" ? -1 : l)}' "$RES")
      [ $rc -eq 0 ] && break
      if [ $rc -ne 3 ]; then
        printf '%s\t%d\t-\t-\tCRASH\t-1\t-\tJVM exit %s; log %s\n' $mode $((last + 1)) $rc "$O/examples/$mode-$first.log" >> "$RES"; last=$((last + 1))
      fi
      first=$((last + 1))
    done
    echo "examples $mode: $(awk -F'\t' -v m=$mode '$1==m {c[$5]++} END {for (s in c) printf "%s=%d ", s, c[s]}' "$RES")"
  done
}
case "$PHASE" in
  matrix) matrix ;;
  gallery) gallery ;;
  examples) examples ;;
  *) echo "unknown phase $PHASE"; exit 2 ;;
esac
