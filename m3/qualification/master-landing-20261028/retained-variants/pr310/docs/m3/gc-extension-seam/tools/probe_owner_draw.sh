#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Display probe of the GC seam leaf 3 (owner-draw events with a real GC in the drawn modes), on the hidden desktop
# (C:/m3-superset/tools/ui_lock.sh + hidden_java.sh): compiles OwnerDrawProbe.java against the classes of compile_m3gc.sh
# (run with M3_OVERLAY so the classes dir is complete) and runs it with -Dswt.widget.mode=<mode>. Prints PROBE PASS|FAIL.
# Usage: probe_owner_draw.sh <mode: m3|custom|gpu> [worktree] [main-out]  (defaults: C:/m3-superset/swt-gcseam, H:/m3-scratch/gc-wiring/main)
MODE=${1:-m3}; WT=${2:-C:/m3-superset/swt-gcseam}; MAIN=${3:-H:/m3-scratch/gc-wiring/main}
W=${M3_PROBE_OUT:-H:/m3-scratch/gc-wiring}; P=$W/probe
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
LIB=${M3_SKIJA_LIB:-C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib}
. "$(dirname "$0")/m3_skija_pin.sh"; m3_skija_pin "$WT" "$LIB" || exit 2   # SKIJA_PIN v1 (census L12): skija.version of the fragment pom
CP="$MAIN/classes;$SKIJA_CP"
# natives of the worktree's Library revision (m3gc_natives.sh: the newest built set under that revision's names)
NAT=${M3_NATIVES:-$W/natives}
[ -f "$NAT/swt-win32-"*.dll ] 2>/dev/null || bash "$WT/docs/m3/gc-extension-seam/tools/m3gc_natives.sh" "$WT" "$NAT"
free=$(powershell -NoProfile -Command "[int]((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1024)")
[ "${free:-0}" -ge 1536 ] || { echo "MEMORY_GATE free=${free}MB < 1536MB"; exit 2; }
mkdir -p "$P/classes"
"$JDK/bin/javac" -J-Xmx512m --release 21 -encoding UTF-8 -nowarn -proc:none -cp "$CP" -d "$P/classes" "$WT/docs/m3/gc-extension-seam/tools/OwnerDrawProbe.java" > "$P/javac.log" 2>&1 \
  || { echo "PROBE_COMPILE_FAILED"; cat "$P/javac.log"; exit 3; }
LOG=$P/probe_$MODE.log; : > "$LOG"
bash C:/m3-superset/tools/ui_lock.sh claude-gcwiring bash C:/m3-superset/tools/hidden_java.sh 120 "$LOG" -Xmx1536m \
  -Dswt.widget.mode=$MODE -Djava.library.path="$(cygpath -w "$NAT")" -Dswt.library.path="$(cygpath -w "$NAT")" -cp "$CP;$P/classes" OwnerDrawProbe
echo "exit=$?" >> "$LOG"
grep -E 'PROBE|Exception|Error|exit=' "$LOG" | head -12
