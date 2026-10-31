#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Matrix legs of a set of rows against one build, on the hidden desktop (one ui_lock.sh ticket, one JVM at a time): the
# reference JVM records every row (original), then one JVM per other mode compares its rows; a row over the row limit is
# recorded as TIMEOUT and the leg resumes in a fresh JVM. Used for the before/after reruns against local merges.
# Usage: run_subset.sh <tag> <matrix.tsv> <build-dir> [swt-classes] [env: PIXELS=true, MODES, ENV=display|headless, ROW_LIMIT]
#   build-dir holds tests/ and snippets/ (build.sh with M3_OUT); swt-classes defaults to <build-dir>/platform/win32
if [ "$1" != --inside ]; then
  exec bash C:/m3-superset/tools/ui_lock.sh claude-swt-examples bash "$0" --inside "$@"
fi
shift; TAG=$1; MATRIX=$2; B=$3; SWT=${4:-$3/platform/win32}
NAT=${M3_NATIVES:-H:/m3-scratch/gc-wiring/natives}; W() { cygpath -w "$1"; }
LIB=C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib
JUNIT=C:/Users/hsoliwal/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar
CP="$B/tests;$B/snippets;$SWT;$LIB/skija-shared-0.116.3.jar;$LIB/skija-windows-x64-0.116.3.jar;$LIB/types-0.1.1.jar;$JUNIT"
OUT=$B/runs/$TAG; REC=$OUT/recording; RES=$OUT/matrix.tsv; mkdir -p "$OUT/evidence"; rm -rf "$REC"; rm -f "$RES" "$OUT/similarity.tsv"
echo "subset $TAG on desktop $M3_UI_DESKTOP $(date -Iseconds): $(($(wc -l < "$MATRIX") - 1)) rows, classes $SWT"
for mode in ${MODES:-original viewport custom m3 gpu}; do
  record=false; [ "$mode" = original ] && record=true
  for attempt in 1 2 3 4 5 6 7 8 9 10; do
    log=$OUT/$mode-$attempt.log
    bash C:/m3-superset/tools/hidden_java.sh 3600 "$log" -Xmx2g -XX:ErrorFile="$(W "$OUT")\\hs_err_%p.log" "-Djava.library.path=$(W "$NAT")" "-Dswt.library.path=$(W "$NAT")" \
      -Dswt.widget.mode=$mode -Dswt.viewport.record=$record -Dm3.combinations.env=${ENV:-display} -Dm3.combinations.resume=true -Dm3.combinations.rowLimit=${ROW_LIMIT:-900} \
      -Dm3.combinations.pixels=${PIXELS:-false} "-Dswt.viewport.evidence=$(W "$OUT/evidence")" "-Dswt.viewport.similarity=$(W "$OUT/similarity.tsv")" \
      "-Dswt.viewport.recording=$(W "$REC")" "-Dm3.combinations.results=$(W "$RES")" "-Dm3.combinations.matrix=$(W "$MATRIX")" \
      -cp "$CP" org.junit.platform.console.ConsoleLauncher execute --select-class org.eclipse.swt.tests.junit.m3combinations.Test_M3CombinationMatrix \
      --disable-banner --disable-ansi-colors --details=summary
    rc=$?
    echo "$TAG $mode-$attempt: rc=$rc $(sed 's/\x1b\[[0-9;]*m//g' "$log" | grep -E 'tests (successful|failed|aborted)' | sed -E 's/^\[ *//; s/ +\]$//' | tr -s ' ' | tr '\n' ' ')"
    [ $rc -eq 3 ] || break
  done
done
echo "statuses: $(awk -F'\t' 'NR > 1 {c[$12 " " $14]++} END {for (k in c) printf "%s=%d ", k, c[k]}' "$RES")"
