#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# JUnit gate of the real-app fidelity leaf (LEAF_FIDELITY_REALAPP.md): compiles tests/.../junit/m3fidelity (plus the
# render tests it guards: RowRenderer, RowPaintKey) against the classes of compile_m3gc.sh and JUnit 5, then
#   headless          the classes without a Display (row renderer atoms, RowPaint layout, GDI ellipsis cut)
#   display <mode>    the Display classes on the hidden desktop (ui_lock.sh + hidden_java.sh), -Dswt.widget.mode=<mode>
# Natives: the staged set of m3gc_natives.sh, verified by oid (NATIVE_STAGE_BY_OID). GATE_POSITIVE_COUNT: green only with
# tests found > 0 and none failed or aborted. Windows paths only; memory gate first; one JVM at a time.
# Usage: test_m3fidelity.sh headless|display <viewport|original|custom|m3|gpu> [worktree] [main-out] [test-out]
KIND=${1:-headless}; MODE=${2:-m3}; WT=${3:-C:/m3-superset/swt-fidelity}; MAIN=${4:-H:/m3-scratch/fidelity/main}
OUT=${5:-H:/m3-scratch/fidelity/tests-fidelity}
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
LIB=${M3_SKIJA_LIB:-C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib}
JUNIT=${M3_JUNIT:-C:/Users/hsoliwal/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar}
NAT=${M3_NATIVES:-H:/m3-scratch/fidelity/natives}
T="$WT/tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit"
PKG=org.eclipse.swt.tests.junit.m3fidelity
free=$(powershell -NoProfile -Command "[int]((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1024)")
[ "${free:-0}" -ge 1536 ] || { echo "MEMORY_GATE free=${free}MB < 1536MB"; exit 2; }
bash "$(dirname "$0")/m3gc_natives.sh" --verify "$WT" "$NAT" > /dev/null || { echo "NATIVES_NOT_GENUINE: run m3gc_natives.sh $WT $NAT"; exit 1; }
rm -rf "$OUT/classes"; mkdir -p "$OUT/classes" "$OUT/tmp" "$OUT/logs"
{ find "$T/m3fidelity" -name '*.java' -type f
  echo "$T/Test_org_eclipse_swt_internal_render_RowRenderer.java"; echo "$T/Test_org_eclipse_swt_internal_render_RowPaintKey.java"; } | sed 's#.*#"&"#' > "$OUT/src"
CP="$OUT/classes;$MAIN/classes;$LIB/skija-shared-0.116.3.jar;$LIB/skija-windows-x64-0.116.3.jar;$LIB/types-0.1.1.jar;$JUNIT"
"$JDK/bin/javac" -J-Xmx1024m --release 21 -encoding UTF-8 -nowarn -proc:none -Xmaxerrs 100 -cp "$CP" -d "$OUT/classes" @"$OUT/src" > "$OUT/javac.log" 2>&1
n=$(grep -cE ': error:|^error: |^Usage: javac' "$OUT/javac.log"); n=${n:-0}
echo "m3fidelity tests: sources=$(wc -l < "$OUT/src") errors=$n"
grep ': error:' "$OUT/javac.log" | sed -E 's#.*tests/org.eclipse.swt.tests/##' | head -20
[ "$n" -eq 0 ] || exit 1
COMMON="-Xmx1024m -Djava.io.tmpdir=$OUT/tmp -Dswt.library.path=$(cygpath -w "$NAT") -Djava.library.path=$(cygpath -w "$NAT")"
if [ "$KIND" = headless ]; then
  LOG="$OUT/logs/junit-headless.log"
  "$JDK/bin/java" $COMMON -Djava.awt.headless=true -jar "$JUNIT" execute --class-path "$CP" --select-class $PKG.AllM3FidelityHeadless \
    --include-engine junit-platform-suite --details=tree --disable-banner --disable-ansi-colors > "$LOG" 2>&1
else
  LOG="$OUT/logs/junit-display-$MODE.log"; : > "$LOG"
  bash C:/m3-superset/tools/ui_lock.sh claude-fidelity bash C:/m3-superset/tools/hidden_java.sh 900 "$LOG" $COMMON -Dswt.widget.mode=$MODE \
    -jar "$JUNIT" execute --class-path "$CP" --select-class $PKG.AllM3FidelityDisplay --include-engine junit-platform-suite \
    --details=tree --disable-banner --disable-ansi-colors
fi
count() { local v; v=$(grep -oE "[0-9]+ tests $1" "$LOG" | head -1 | grep -oE '^[0-9]+'); echo "${v:-0}"; }
found=$(count found); passed=$(count successful); failed=$(count failed); aborted=$(count aborted)
echo "m3fidelity $KIND $MODE: found=$found successful=$passed failed=$failed aborted=$aborted"
grep -E '✘|MethodSource|=> ' "$LOG" | head -20
[ "$found" -gt 0 ] && [ "$failed" -eq 0 ] && [ "$aborted" -eq 0 ]
