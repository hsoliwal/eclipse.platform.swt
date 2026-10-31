#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Display probe of the theme and language leaf on the hidden desktop (C:/m3-superset/tools/ui_lock.sh + hidden_java.sh):
# compiles examples/.../m3/M3ThemeLanguageExample.java against the classes of compile_platform.sh (m3 and gpu: of
# compile_m3gc.sh, with the Skija jars) and runs it once per mode with --matrix: each theme:language pair is applied live to
# the same Table, Tree, StyledText, CTabFolder and Canvas and printed (Control.print) to <out>/<mode>-<theme>-<tag>.png.
# m3 and gpu keep StyledText off the M3 canvas unless M3_GF01=1 (pre-existing GF01, see test_theme_language.sh).
# Usage: probe_theme_language.sh <viewport|original|custom|m3|gpu> [matrix]
#   default matrix: light:en,dark:en,high-contrast:en,light:ar,dark:he (Table tab), dark:en:1 (Tree), dark:de:2, light:ja:2
#   (StyledText), dark:ar:3, high-contrast:hi:3 (Canvas)
MODE=${1:-viewport}; MATRIX=${2:-light:en,dark:en,high-contrast:en,light:ar,dark:he,dark:en:1,dark:de:2,light:ja:2,dark:ar:3,high-contrast:hi:3}
WT=${WT:-C:/m3-superset/swt-theme-lang}; MAIN=${MAIN:-H:/m3-scratch/theme-lang/platform/win32}; M3MAIN=${M3MAIN:-H:/m3-scratch/theme-lang/m3gc/classes}
OUT=${OUT:-H:/m3-scratch/theme-lang/probes}; JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
LIB=${M3_SKIJA_LIB:-C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib}
NAT=${M3_NATIVES:-H:/m3-scratch/gc-wiring/natives}
free=$(powershell -NoProfile -Command "[int]((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1024)")
[ "${free:-0}" -ge 1536 ] || { echo "MEMORY_GATE free=${free}MB < 1536MB"; exit 2; }
mkdir -p "$OUT/classes-$MODE" "$OUT/res/org/eclipse/swt/m3"
cp "$WT/bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/m3/M3Languages.tsv" "$OUT/res/org/eclipse/swt/m3/"
CP="$MAIN"; GF01=""
case "$MODE" in m3|gpu) CP="$M3MAIN;$LIB/skija-shared-0.116.3.jar;$LIB/skija-windows-x64-0.116.3.jar;$LIB/types-0.1.1.jar"; [ -z "$M3_GF01" ] && GF01="-Dswt.widget.mode.StyledText=original" ;; esac
"$JDK/bin/javac" -J-Xmx512m --release 21 -encoding UTF-8 -nowarn -proc:none -cp "$CP" -d "$OUT/classes-$MODE" \
  "$WT/examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/m3/M3ThemeLanguageExample.java" > "$OUT/javac-$MODE.log" 2>&1 \
  || { echo "PROBE_COMPILE_FAILED"; cat "$OUT/javac-$MODE.log"; exit 3; }
LOG=$OUT/probe-$MODE.log; : > "$LOG"
bash C:/m3-superset/tools/ui_lock.sh claude-theme-lang bash C:/m3-superset/tools/hidden_java.sh 300 "$LOG" -Xmx1024m \
  -Dswt.widget.mode=$MODE $GF01 -Dm3.probe.screen=${M3_SCREEN:-false} -Dswt.library.path="$(cygpath -w "$NAT")" -Djava.library.path="$(cygpath -w "$NAT")" \
  -cp "$OUT/classes-$MODE;$OUT/res;$CP" org.eclipse.swt.snippets.m3.M3ThemeLanguageExample --matrix=$MATRIX --shots="$(cygpath -m "$OUT")" --exit
echo "exit=$?" >> "$LOG"
grep -E 'PROBE|Exception|Error|exit=' "$LOG" | head -20
