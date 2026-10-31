#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Headless gate of the feature-switch registry (no Display, no Device, no natives): compile the m3features tests against the
# win32 classes of compile_platform.sh and JUnit 5 (junit-platform-console-standalone 1.11.4), run them with the console
# launcher. Windows paths only (an MSYS path makes javac fail before compiling and the script would count 0 errors).
# Memory gate first, one JVM at a time, output under H:/m3-scratch (S: and K: are full).
# Usage: test_m3features.sh [worktree] [main-classes] [out]
#   defaults: C:/m3-superset/swt-features, C:/m3-superset/build-check-win32-swt-features, H:/m3-scratch/web-switches/m3features-tests
WT=${1:-C:/m3-superset/swt-features}; MAIN=${2:-C:/m3-superset/build-check-win32-swt-features}; OUT=${3:-H:/m3-scratch/web-switches/m3features-tests}
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
JUNIT=${M3_JUNIT:-C:/Users/hsoliwal/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar}
SRC="$WT/tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/m3features"
free=$(powershell -NoProfile -Command "[int]((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1024)")
[ "${free:-0}" -ge 1536 ] || { echo "MEMORY_GATE free=${free}MB < 1536MB"; exit 2; }
rm -rf "$OUT"; mkdir -p "$OUT/classes"
find "$SRC" -name '*.java' -type f | sed 's#.*#"&"#' > "$OUT/src"
CP="$MAIN;$JUNIT"
"$JDK/bin/javac" -J-Xmx1024m --release 21 -encoding UTF-8 -nowarn -proc:none -Xmaxerrs 100 -cp "$CP" -d "$OUT/classes" @"$OUT/src" > "$OUT/javac.log" 2>&1
n=$(grep -cE ': error:|^error: |^Usage: javac' "$OUT/javac.log"); n=${n:-0}
echo "m3features tests: sources=$(wc -l < "$OUT/src") errors=$n"
grep ': error:' "$OUT/javac.log" | sed -E 's#.*tests/org.eclipse.swt.tests/##' | head -20
[ "$n" -eq 0 ] || exit 1
# the generated view the table test compares against (worktree path, Windows form)
"$JDK/bin/java" -Xmx1024m -Djava.awt.headless=true "-Dm3.features.tsv=$WT/docs/m3/feature-switches/FEATURES.tsv" \
  -jar "$JUNIT" execute --class-path "$OUT/classes;$CP" --select-package org.eclipse.swt.tests.junit.m3features \
  --include-engine junit-jupiter --details=tree --disable-banner --disable-ansi-colors > "$OUT/junit.log" 2>&1
grep -E '^\[ +[0-9]+ (containers|tests) ' "$OUT/junit.log"
grep -E 'tests (found|successful|failed|skipped|aborted)' "$OUT/junit.log"
grep -qE '^\[ +0 tests failed' "$OUT/junit.log"
