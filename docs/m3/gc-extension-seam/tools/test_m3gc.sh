#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Headless test gate of the GC seam leaf 2 (no Display): compile the m3gc doubles, the 12 draw tests and the converter
# tests against the classes of compile_m3gc.sh and JUnit 5 (junit-platform-console-standalone 1.11.4: jupiter API, engine
# and suite API in one jar), then run them with the console launcher (jupiter engine only: the @Suite class would run
# every test a second time). One JVM at a time, memory gate first.
# Usage: test_m3gc.sh [worktree] [main-out] [test-out]   (defaults: C:/m3-superset/swt-gcseam, H:/m3-scratch/gc-backend/main|tests)
WT=${1:-C:/m3-superset/swt-gcseam}; MAIN=${2:-H:/m3-scratch/gc-backend/main}; OUT=${3:-H:/m3-scratch/gc-backend/tests}
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
LIB=${M3_SKIJA_LIB:-C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib}
JUNIT=${M3_JUNIT:-C:/Users/hsoliwal/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar}
SRC="$WT/tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/m3gc"
free=$(powershell -NoProfile -Command "[int]((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1024)")
[ "${free:-0}" -ge 1536 ] || { echo "MEMORY_GATE free=${free}MB < 1536MB"; exit 2; }
rm -rf "$OUT"; mkdir -p "$OUT/classes"
find "$SRC" -name '*.java' -type f | sed 's#.*#"&"#' > "$OUT/src"
CP="$MAIN/classes;$LIB/skija-shared-0.116.3.jar;$LIB/skija-windows-x64-0.116.3.jar;$LIB/types-0.1.1.jar;$JUNIT"
"$JDK/bin/javac" -J-Xmx1024m --release 21 -encoding UTF-8 -nowarn -proc:none -Xmaxerrs 300 -cp "$CP" -d "$OUT/classes" @"$OUT/src" > "$OUT/javac.log" 2>&1
# a launcher failure (`error: file not found`, MSYS /c/ paths in the argfile: pass C:/ paths) counts as an error too
n=$(grep -cE ': error:|^error: |^Usage: javac' "$OUT/javac.log"); n=${n:-0}
echo "m3gc tests: sources=$(wc -l < "$OUT/src") errors=$n"
grep ': error:' "$OUT/javac.log" | sed -E 's#.*tests/org.eclipse.swt.tests/##' | head -"${M3_SHOW:-20}"
[ "$n" -eq 0 ] || exit 1
cd "$OUT" || exit 1
# I31's own order (AllM3GcTests, @Suite): drawText_NoCache sets the system property M3TextDrawing.NO_TEXT_CACHE and never
# clears it, so any order that runs it before drawText/drawString fails those 6 tests (PRE_EXISTING.tsv P04); M3_SCAN=1
# runs the classpath scan instead and shows exactly that
if [ -n "$M3_SCAN" ]; then SELECT="--scan-classpath $OUT/classes --include-engine junit-jupiter"
else SELECT="--select-class org.eclipse.swt.tests.junit.m3gc.AllM3GcTests --include-engine junit-platform-suite"; fi
"$JDK/bin/java" -Xmx1024m -Djava.awt.headless=true -jar "$JUNIT" execute --class-path "$OUT/classes;$CP" $SELECT \
  --details=tree --disable-banner --disable-ansi-colors > "$OUT/junit.log" 2>&1
grep -E '^\[ +[0-9]+ (containers|tests) ' "$OUT/junit.log"
grep -E 'tests (found|successful|failed|skipped|aborted)' "$OUT/junit.log"
