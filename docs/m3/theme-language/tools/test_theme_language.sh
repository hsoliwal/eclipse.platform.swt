#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# JUnit gate of the theme and language leaf (docs/m3/theme-language/README.md). Compiles the m3theme tests against the
# win32 classes of compile_platform.sh (out= under H:/m3-scratch/theme-lang) and JUnit 5 (console standalone 1.11.4), then
#   headless          the four classes without a Display (M3Theme, M3LanguageId, M3Languages, M3Language)
#   display <mode>    the two Display classes on the hidden desktop (ui_lock.sh + hidden_java.sh), -Dswt.widget.mode=<mode>;
#                     m3 and gpu put the classes of compile_m3gc.sh ($M3MAIN) and the Skija jars on the class path
# M3_JACOCO=1 runs under the JaCoCo agent (exec per run in $OUT/jacoco). Windows paths only; memory gate first; one JVM.
# m3 and gpu keep StyledText off the M3 canvas (-Dswt.widget.mode.StyledText=original) unless M3_GF01=1: every StyledText
# paint on an M3 canvas throws in Composite.drawBackgroundInPixels (pre-existing GF01, docs/m3/gc-extension-seam/README.md
# leaf 5, owned by the leaf-6 GC-fidelity agent).
# Usage: test_theme_language.sh headless | display <viewport|original|custom|m3|gpu>
KIND=${1:-headless}; MODE=${2:-viewport}
WT=${WT:-C:/m3-superset/swt-theme-lang}; MAIN=${MAIN:-H:/m3-scratch/theme-lang/platform/win32}; OUT=${OUT:-H:/m3-scratch/theme-lang/tests}
M3MAIN=${M3MAIN:-H:/m3-scratch/theme-lang/m3gc/classes}
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
JUNIT=${M3_JUNIT:-C:/Users/hsoliwal/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar}
AGENT=${M3_JACOCO_AGENT:-C:/Users/hsoliwal/.m2/repository/org/jacoco/org.jacoco.agent/0.8.15/org.jacoco.agent-0.8.15-runtime.jar}
LIB=${M3_SKIJA_LIB:-C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib}
NAT=${M3_NATIVES:-H:/m3-scratch/gc-wiring/natives}
SRC="$WT/tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/m3theme"
PKG=org.eclipse.swt.tests.junit.m3theme
free=$(powershell -NoProfile -Command "[int]((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1024)")
[ "${free:-0}" -ge 1536 ] || { echo "MEMORY_GATE free=${free}MB < 1536MB"; exit 2; }
mkdir -p "$OUT/classes" "$OUT/res/org/eclipse/swt/m3" "$OUT/jacoco" "$OUT/tmp" "$OUT/logs"
cp "$WT/bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/m3/M3Languages.tsv" "$OUT/res/org/eclipse/swt/m3/"
CP="$OUT/classes;$OUT/res;$MAIN;$JUNIT"
GF01=""; case "$MODE" in m3|gpu) [ -z "$M3_GF01" ] && GF01="-Dswt.widget.mode.StyledText=original"; CP="$OUT/classes;$OUT/res;$M3MAIN;$MAIN;$LIB/skija-shared-0.116.3.jar;$LIB/skija-windows-x64-0.116.3.jar;$LIB/types-0.1.1.jar;$JUNIT" ;; esac
find "$SRC" -name '*.java' -type f | sed 's#.*#"&"#' > "$OUT/src"
"$JDK/bin/javac" -J-Xmx1024m --release 21 -encoding UTF-8 -nowarn -proc:none -Xmaxerrs 100 -cp "$CP" -d "$OUT/classes" @"$OUT/src" > "$OUT/javac.log" 2>&1
n=$(grep -cE ': error:|^error: |^Usage: javac' "$OUT/javac.log"); n=${n:-0}
echo "m3theme tests: sources=$(wc -l < "$OUT/src") errors=$n"
grep ': error:' "$OUT/javac.log" | sed -E 's#.*tests/org.eclipse.swt.tests/##' | head -20
[ "$n" -eq 0 ] || exit 1
JAC=""; [ -n "$M3_JACOCO" ] && JAC="-javaagent:$AGENT=destfile=$OUT/jacoco/$KIND-$MODE.exec,includes=org.eclipse.swt.m3.*:org.eclipse.swt.internal.laf.M3ThemeTable:org.eclipse.swt.widgets.M3NativeTheme"
COMMON="-Xmx1024m -Djava.io.tmpdir=$OUT/tmp -Dswt.library.path=$(cygpath -w "$NAT") -Djava.library.path=$(cygpath -w "$NAT")"
if [ "$KIND" = headless ]; then
  SEL="--select-class $PKG.Test_org_eclipse_swt_m3_M3Theme --select-class $PKG.Test_org_eclipse_swt_m3_M3LanguageId --select-class $PKG.Test_org_eclipse_swt_m3_M3Languages --select-class $PKG.Test_org_eclipse_swt_m3_M3Language"
  LOG="$OUT/logs/junit-headless.log"
  "$JDK/bin/java" $JAC $COMMON -Djava.awt.headless=true -jar "$JUNIT" execute --class-path "$CP" $SEL \
    --include-engine junit-jupiter --details=tree --disable-banner --disable-ansi-colors > "$LOG" 2>&1
else
  LOG="$OUT/logs/junit-display-$MODE.log"; : > "$LOG"
  bash C:/m3-superset/tools/ui_lock.sh claude-theme-lang bash C:/m3-superset/tools/hidden_java.sh 900 "$LOG" $JAC $COMMON -Dswt.widget.mode=$MODE $GF01 \
    -jar "$JUNIT" execute --class-path "$CP" --select-class $PKG.Test_org_eclipse_swt_m3_M3ThemesDisplay \
    --select-class $PKG.Test_org_eclipse_swt_m3_M3LanguageDisplay --include-engine junit-jupiter --details=tree --disable-banner --disable-ansi-colors
fi
grep -E 'tests (found|successful|failed)' "$LOG"
grep -E 'damage|native' "$LOG" | sed 's/^ *//' | head -4
grep -qE '^\[ +0 tests failed' "$LOG"
