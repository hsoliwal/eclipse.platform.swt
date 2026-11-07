#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Viewport performance leaf (docs/m3/VIEWPORT_PERF_20261031.md): build and run its tests. Display phases run on the hidden
# desktop under one C:/m3-superset/tools/ui_lock.sh ticket, one JVM at a time, with a heartbeat every 5 minutes.
#   build   <worktree> <swt-classes>  javac of the leaf's tests, the Tree/Table/List viewport differentials and every
#                                     M3OfficialScene differential (-sourcepath pulls their helpers) against this SWT
#   record                            the official 3.135.0 jar records every differential class (one file per seed)
#   compare <swt-classes> <mode>...   this SWT compares with the recording in each mode (0 mismatches = gate)
#   timing  <swt-classes> <mode>...   Test_M3ViewportPerf in each mode
#   junit   <swt-classes> <mode> <class>...  any test classes of the build in one mode (stock suites, single differentials)
# env: REC (recording dir, default OUT/rec), JOPTS (extra JVM options, e.g. -Dswt.viewport.treePlain=true), DIFFS (classes)
# Windows paths; output under OUT (default H:/m3-scratch/perf). Natives: the oid-verified set (H:/m3-scratch/fidelity/natives).
OUT=${OUT:-H:/m3-scratch/perf}; W () { cygpath -w "$1"; }
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
JUNIT=C:/Users/hsoliwal/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar
OFFICIAL=C:/Users/hsoliwal/.m2/repository/org/eclipse/platform/org.eclipse.swt.win32.win32.x86_64/3.135.0/org.eclipse.swt.win32.win32.x86_64-3.135.0.jar
LIB=C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib
SKIJA="$LIB/skija-shared-0.116.3.jar;$LIB/skija-windows-x64-0.116.3.jar;$LIB/types-0.1.1.jar"
NAT=${M3_NATIVES:-H:/m3-scratch/fidelity/natives}
T=org.eclipse.swt.tests.junit
DIFFS=${DIFFS:-"Test_M3ViewportPerfDifferential Test_org_eclipse_swt_widgets_Tree_ViewportDifferential Test_org_eclipse_swt_widgets_Table_ViewportDifferential
 Test_org_eclipse_swt_widgets_List_ViewportDifferential Test_M3RowMathDifferential Test_M3EmptinessDifferential Test_M3BulkUpdatesDifferential
 Test_M3DiffApplyDifferential Test_M3LazyContentDifferential Test_M3PrefetchDifferential Test_M3ProxyModelDifferential Test_M3ResidencyDifferential
 Test_M3ScrollBarModelDifferential Test_M3ScrollMementoDifferential Test_M3SnapshotDifferential Test_M3VisibilityObserverDifferential
 Test_M3WidthTrackDifferential"}
STOCK="Test_org_eclipse_swt_widgets_Tree Test_org_eclipse_swt_widgets_TreeItem Test_org_eclipse_swt_widgets_TreeColumn Test_org_eclipse_swt_events_TreeEvent
 Test_org_eclipse_swt_widgets_List Test_org_eclipse_swt_widgets_Tree_Viewport Test_org_eclipse_swt_widgets_List_Viewport"
REC=${REC:-$OUT/rec}
select_all () { local s=""; for c in $DIFFS; do s="$s --select-class $T.$c"; done; echo "$s"; }
summary () { echo "$1: rc=$2 $(sed 's/\x1b\[[0-9;]*m//g' "$1" | grep -E 'tests (successful|failed|aborted|skipped)' | sed -E 's/^\[ *//; s/ +\]$//' | tr -s ' ' | tr '\n' ' ')"; }
failures () { sed 's/\x1b\[[0-9;]*m//g' "$1" | grep -E '✘|differs|MethodSource' | cut -c1-220 | head -n 30; }
hidden () { # <budget> <log> <java args...>
  local budget=$1 log=$2; shift 2
  bash C:/m3-superset/tools/hidden_java.sh "$budget" "$log" -Xmx2g -XX:ErrorFile="$(W "$OUT/logs")\\hs_err_%p.log" "$@"
}
case "$1" in
  build)
    WT=$2; SWT=$3; S="$WT/tests/org.eclipse.swt.tests/JUnit Tests"; D="$S/org/eclipse/swt/tests/junit"
    rm -rf "$OUT/tests"; mkdir -p "$OUT/tests" "$OUT/logs"
    { echo "$D/Test_M3ViewportPerf.java"; for c in $DIFFS $STOCK; do echo "$D/$c.java"; done; } | sed 's/.*/"&"/' > "$OUT/tests.src"
    "$JDK/bin/javac" -J-Xmx768m --release 21 -encoding UTF-8 -nowarn -proc:none -g -sourcepath "$S;${M3_TEST_STUBS:-C:/m3-superset/test-stubs-j4}" -cp "$SWT;$SKIJA;$JUNIT" -d "$OUT/tests" @"$OUT/tests.src" > "$OUT/logs/tests-javac.log" 2>&1
    n=$(grep -cE ': error:|^error: ' "$OUT/logs/tests-javac.log"); n=${n:-0}
    echo "tests: sources=$(wc -l < "$OUT/tests.src") classes=$(find "$OUT/tests" -name '*.class' | wc -l) errors=$n"; grep -A2 ': error:' "$OUT/logs/tests-javac.log" | head -20
    [ "$n" -eq 0 ] ;;
  --inside)
    shift; PHASE=$1; shift
    ( while :; do sleep 300; echo "HEARTBEAT $(date -Iseconds) perf_tests $PHASE"; done ) & HB=$!
    case "$PHASE" in
      record)
        rm -rf "$REC"; mkdir -p "$REC"
        hidden 5400 "$OUT/logs/record$TAG.log" $JOPTS "-Dswt.viewport.recording=$(W "$REC")" "-Dswt.viewport.evidence=$(W "$OUT/evidence")" -Djava.io.tmpdir="$(W "$OUT/tmp")" \
          -jar "$JUNIT" execute -cp "$OUT/tests;$OFFICIAL" $(select_all) --disable-banner --disable-ansi-colors --details=tree
        summary "$OUT/logs/record$TAG.log" $?; failures "$OUT/logs/record$TAG.log"
        echo "recordings: $(ls "$REC" | grep -c 'seed[0-9]*\.txt$')" ;;
      compare|timing|junit)
        SWT=$1; shift
        if [ "$PHASE" = junit ]; then MODES=$1; shift; SEL=""; for c in "$@"; do SEL="$SEL --select-class $T.$c"; done; set -- $MODES; fi
        for mode in "$@"; do
          log="$OUT/logs/$PHASE$TAG-$mode.log"
          [ "$PHASE" = compare ] && SEL=$(select_all)
          [ "$PHASE" = timing ] && SEL="--select-class $T.Test_M3ViewportPerf"
          hidden 5400 "$log" $JOPTS -Dswt.widget.mode=$mode "-Djava.library.path=$(W "$NAT")" "-Dswt.library.path=$(W "$NAT")" \
            "-Dswt.viewport.recording=$(W "$REC")" "-Dswt.viewport.evidence=$(W "$OUT/evidence-$mode")" -Djava.io.tmpdir="$(W "$OUT/tmp")" \
            -jar "$JUNIT" execute -cp "$OUT/tests;$SWT;$SKIJA" $SEL --disable-banner --disable-ansi-colors --details=tree
          summary "$log" $?; failures "$log"
        done ;;
    esac
    kill $HB 2>/dev/null ;;
  record|compare|timing|junit)
    mkdir -p "$OUT/logs" "$OUT/tmp"
    exec bash C:/m3-superset/tools/ui_lock.sh claude-swt-perf bash "$0" --inside "$@" ;;
  *) echo "usage: perf_tests.sh build|record|compare|timing|junit ..."; exit 2 ;;
esac
