#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# Compile the Initiative 31 SWT-on-Swing source set (binaries/.classpath_swing) of a worktree.
# Usage: compile_swing.sh <worktree> <out-dir> [extra-source-dir...]
# Run it under C:/m3-superset/tools/build_lock.sh. Output: <out-dir>/classes, javac.log, src.
WT=$1; OUT=$2; shift 2
JDK="C:/Program Files/Java/jdk-21"
B="$WT/bundles/org.eclipse.swt"
rm -rf "$OUT"; mkdir -p "$OUT/classes"
grep -o 'kind="src" path="[^"]*"' "$WT/binaries/.classpath_swing" | sed 's/.*path="//; s/"$//' > "$OUT/dirs"
: > "$OUT/src"
while IFS= read -r d; do
	[ -d "$B/$d" ] && find "$B/$d" -name '*.java' -type f | sed 's#.*#"&"#' >> "$OUT/src"
done < "$OUT/dirs"
for d in "$@"; do find "$d" -name '*.java' -type f | sed 's#.*#"&"#' >> "$OUT/src"; done
"$JDK/bin/javac" -J-Xmx1536m --release 21 -encoding UTF-8 -nowarn -proc:none -Xmaxerrs 300 -d "$OUT/classes" @"$OUT/src" > "$OUT/javac.log" 2>&1
RC=$?
while IFS= read -r d; do
	[ -d "$B/$d" ] && (cd "$B/$d" && find . -type f ! -name "*.java" -exec cp --parents {} "$OUT/classes/" \;)
done < "$OUT/dirs"
echo "sources=$(wc -l < "$OUT/src") errors=$(grep -c ': error:' "$OUT/javac.log") rc=$RC"
grep ': error:' "$OUT/javac.log" | sed -E 's#.*bundles/org.eclipse.swt/##' | head -20
