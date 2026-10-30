#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Compile one SWT platform source set of a worktree (no jar). Copy of C:/m3-superset/tools/compile_platform.sh with the
# output under H:/m3-scratch/swt-examples/platform and, for win32, the M3 render and GPU trees ("Eclipse SWT Render M3/win32",
# "Eclipse SWT GPU M3/win32", loaded by name in the m3 and gpu modes) against the Skija 0.116.3 binding (as compile_m3gc.sh).
# Windows paths only. Usage: compile_platform.sh <worktree> <gtk|cocoa|win32> [out]
WT=$1; P=$2; JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
LIB=${M3_SKIJA_LIB:-C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib}
case "$P" in
  gtk) BP=org.eclipse.swt.gtk.linux.x86_64 ;;
  cocoa) BP=org.eclipse.swt.cocoa.macosx.aarch64 ;;
  win32) BP=org.eclipse.swt.win32.win32.x86_64 ;;
esac
out=${3:-H:/m3-scratch/swt-examples/platform/$P}; rm -rf "$out"; mkdir -p "$out"
{ grep -o 'bundles/org.eclipse.swt/[^,\\]*' "$WT/binaries/$BP/build.properties" | tr -d '\r' | sed 's/ *$//'
  [ "$P" = win32 ] && printf '%s\n' "bundles/org.eclipse.swt/Eclipse SWT Render M3/win32" "bundles/org.eclipse.swt/Eclipse SWT GPU M3/win32"; } | sort -u > "$out.dirs"
: > "$out.src"
while IFS= read -r d; do [ -d "$WT/$d" ] && find "$WT/$d" -name '*.java' -type f | sed 's#.*#"&"#' >> "$out.src"; done < "$out.dirs"
CP="$LIB/skija-shared-0.116.3.jar;$LIB/skija-windows-x64-0.116.3.jar;$LIB/types-0.1.1.jar"
"$JDK/bin/javac" -J-Xmx1536m --release 21 -encoding UTF-8 -Xlint:none -nowarn -proc:none -Xmaxerrs 200 -cp "$CP" -d "$out" @"$out.src" > "$out.log" 2>&1
n=$(grep -c ': error:' "$out.log"); n=${n:-0}
echo "$P: sources=$(wc -l < "$out.src") errors=$n"
grep ': error:' "$out.log" | sed -E 's#.*bundles/org.eclipse.swt/##' | head -10
# the non-Java resources of the source roots (properties, SVG glyphs, images) next to the classes
while IFS= read -r d; do
  [ -d "$WT/$d" ] || continue
  (cd "$WT/$d" && find . -type f \( -name '*.svg' -o -name '*.properties' -o -name '*.png' -o -name '*.ico' -o -name '*.txt' \) -print |
    while IFS= read -r f; do mkdir -p "$out/$(dirname "$f")"; cp "$f" "$out/$f"; done)
done < "$out.dirs"
[ "$n" -eq 0 ]
