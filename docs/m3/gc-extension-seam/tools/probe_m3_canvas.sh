#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Display probe of the GC seam leaf 4 (SWT.M3 canvas activation behind swt.widget.mode), on the hidden desktop
# (C:/m3-superset/tools/ui_lock.sh + hidden_java.sh): compiles M3CanvasProbe.java against the classes of compile_m3gc.sh
# and runs it with -Dswt.widget.mode=<mode>, or without the property (stock). Prints PROBE PASS|FAIL and the pixels.
# Usage: probe_m3_canvas.sh <m3|gpu|stock> [worktree] [main-out]  (defaults: C:/m3-superset/swt-gcseam, H:/m3-scratch/gc-leaf4/main)
MODE=${1:-m3}; WT=${2:-C:/m3-superset/swt-gcseam}; MAIN=${3:-H:/m3-scratch/gc-leaf4/main}
W=${M3_PROBE_OUT:-H:/m3-scratch/gc-leaf4}; P=$W/probe
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
LIB=${M3_SKIJA_LIB:-C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib}
CP="$MAIN/classes;$LIB/skija-shared-0.116.3.jar;$LIB/skija-windows-x64-0.116.3.jar;$LIB/types-0.1.1.jar"
# natives of the worktree's Library revision (m3gc_natives.sh: the newest built set under that revision's names)
NAT=${M3_NATIVES:-H:/m3-scratch/gc-wiring/natives}
[ -f "$NAT"/swt-win32-*.dll ] 2>/dev/null || bash "$WT/docs/m3/gc-extension-seam/tools/m3gc_natives.sh" "$WT" "$NAT"
free=$(powershell -NoProfile -Command "[int]((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1024)")
[ "${free:-0}" -ge 1536 ] || { echo "MEMORY_GATE free=${free}MB < 1536MB"; exit 2; }
mkdir -p "$P/classes"
"$JDK/bin/javac" -J-Xmx512m --release 21 -encoding UTF-8 -nowarn -proc:none -cp "$CP" -d "$P/classes" "$WT/docs/m3/gc-extension-seam/tools/M3CanvasProbe.java" > "$P/javac.log" 2>&1 \
  || { echo "PROBE_COMPILE_FAILED"; cat "$P/javac.log"; exit 3; }
case "$MODE" in stock) PROP="" ;; *) PROP="-Dswt.widget.mode=$MODE" ;; esac
LOG=$P/probe_canvas_$MODE.log; : > "$LOG"
bash C:/m3-superset/tools/ui_lock.sh claude-gcleaf4 bash C:/m3-superset/tools/hidden_java.sh 120 "$LOG" -Xmx1536m \
  $PROP -Djava.library.path="$(cygpath -w "$NAT")" -Dswt.library.path="$(cygpath -w "$NAT")" -cp "$CP;$P/classes" M3CanvasProbe
echo "exit=$?" >> "$LOG"
grep -E 'PROBE|Exception|Error|exit=' "$LOG" | head -12
