#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Compile gate of the GC seam leaf 2: the win32 x86_64 source set of the worktree (build.properties) plus the M3 render
# and GPU trees ("Eclipse SWT Render M3/win32", "Eclipse SWT GPU M3/win32", loaded by name at run time), against the
# fork's Skija 0.116.3 binding. Adapted from C:/m3-superset/tools/compile_i31.sh. One JVM, -Xmx1536m, memory gate first.
# Usage: compile_m3gc.sh [worktree] [out-dir]      (defaults: C:/m3-superset/swt-gcseam, H:/m3-scratch/gc-backend/main)
WT=${1:-C:/m3-superset/swt-gcseam}; OUT=${2:-H:/m3-scratch/gc-backend/main}
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
LIB=${M3_SKIJA_LIB:-C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib}
BP="$WT/binaries/org.eclipse.swt.win32.win32.x86_64"
free=$(powershell -NoProfile -Command "[int]((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1024)")
[ "${free:-0}" -ge 1536 ] || { echo "MEMORY_GATE free=${free}MB < 1536MB"; exit 2; }
rm -rf "$OUT"; mkdir -p "$OUT/classes"
{ grep -o 'bundles/org.eclipse.swt/[^,\]*' "$BP/build.properties" | tr -d '\r' | sed 's/ *$//'
  echo "bundles/org.eclipse.swt/Eclipse SWT Render M3/win32"
  echo "bundles/org.eclipse.swt/Eclipse SWT GPU M3/win32"; } | sort -u > "$OUT/dirs"
: > "$OUT/src"
while IFS= read -r d; do [ -d "$WT/$d" ] && find "$WT/$d" -name '*.java' -type f | sed 's#.*#"&"#' >> "$OUT/src"; done < "$OUT/dirs"
# M3_OVERLAY=<dir> (m3gc_baseline_overlay.py): a repaired scratch copy of a base-branch file with a pre-existing error
# replaces the original in the source list; the worktree stays untouched and PRE_EXISTING.tsv names each one
if [ -n "$M3_OVERLAY" ] && [ -d "$M3_OVERLAY" ]; then
  find "$M3_OVERLAY" -name '*.java' -type f | while IFS= read -r f; do
    rel=${f#"$M3_OVERLAY"/}; echo "overlay: $rel"
    grep -vF "/$rel\"" "$OUT/src" > "$OUT/src.tmp" && mv "$OUT/src.tmp" "$OUT/src"; echo "\"$f\"" >> "$OUT/src"
  done
fi
CP="$LIB/skija-shared-0.116.3.jar;$LIB/skija-windows-x64-0.116.3.jar;$LIB/types-0.1.1.jar"
"$JDK/bin/javac" -J-Xmx1536m --release 21 -encoding UTF-8 -nowarn -proc:none -Xmaxerrs 300 -cp "$CP" -d "$OUT/classes" @"$OUT/src" > "$OUT/javac.log" 2>&1
# a launcher failure (`error: file not found`, MSYS /c/ paths in the argfile: pass C:/ paths) counts as an error too
n=$(grep -cE ': error:|^error: |^Usage: javac' "$OUT/javac.log"); n=${n:-0}
# GATE_POSITIVE_COUNT v1 (census L8, LF-02): a green gate needs positive witnesses, sources listed and classes written, not
# just zero errors (an empty source list or a javac that wrote nothing is a failure)
srcs=$(wc -l < "$OUT/src"); cls=$(find "$OUT/classes" -name '*.class' -type f | wc -l)
echo "win32+m3gc: sources=$srcs classes=$cls errors=$n"
grep ': error:' "$OUT/javac.log" | sed -E 's#.*bundles/org.eclipse.swt/##' | head -"${M3_SHOW:-20}"
[ "$n" -eq 0 ] && [ "$srcs" -gt 0 ] && [ "$cls" -gt 0 ]
