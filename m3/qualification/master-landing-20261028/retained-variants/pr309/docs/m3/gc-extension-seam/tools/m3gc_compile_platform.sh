#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Compile one SWT platform source set of a worktree (no jar): C:/m3-superset/tools/compile_platform.sh with the output dir
# as an argument (scratch, never C:) and GATE_POSITIVE_COUNT v1 (census L8, LF-02): green only with sources > 0,
# classes written > 0, 0 errors and 0 launcher failures (`error: file not found` from MSYS /c/ paths counts). Pass C:/ paths.
# Usage: m3gc_compile_platform.sh <worktree> <gtk|cocoa|win32> <out-root>
WT=${1:?worktree}; P=${2:?platform}; ROOT=${3:?out-root}; JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
case "$P" in
  gtk) BP=org.eclipse.swt.gtk.linux.x86_64 ;;
  cocoa) BP=org.eclipse.swt.cocoa.macosx.aarch64 ;;
  win32) BP=org.eclipse.swt.win32.win32.x86_64 ;;
  *) echo "unknown platform $P"; exit 9 ;;
esac
out=$ROOT/build-check-$P-$(basename "$WT"); rm -rf "$out"; mkdir -p "$out"
grep -o 'bundles/org.eclipse.swt/[^,\\]*' "$WT/binaries/$BP/build.properties" | tr -d '\r' | sed 's/ *$//' | sort -u > "$out.dirs"
: > "$out.src"
while IFS= read -r d; do [ -d "$WT/$d" ] && find "$WT/$d" -name '*.java' -type f | sed 's#.*#"&"#' >> "$out.src"; done < "$out.dirs"
"$JDK/bin/javac" -J-Xmx1536m --release 21 -encoding UTF-8 -Xlint:none -nowarn -proc:none -Xmaxerrs 200 -d "$out" @"$out.src" > "$out.log" 2>&1
srcs=$(wc -l < "$out.src"); cls=$(find "$out" -name '*.class' -type f | wc -l)
n=$(grep -cE ': error:|^error: |^Usage: javac' "$out.log"); n=${n:-0}
echo "$P: sources=$srcs classes=$cls errors=$n"
grep ': error:' "$out.log" | sed -E 's#.*bundles/org.eclipse.swt/##' | head -10
[ "$n" -eq 0 ] && [ "$srcs" -gt 0 ] && [ "$cls" -gt 0 ]
