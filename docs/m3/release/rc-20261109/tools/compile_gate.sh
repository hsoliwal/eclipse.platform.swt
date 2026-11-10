#!/usr/bin/env bash
# Gate: javac --release 21 over one SWT platform fragment's source.. dirs (binaries/<fragment>/build.properties).
# Usage: compile_gate.sh <worktree> <gtk|cocoa|win32> <evidence-dir> <classes-out-dir>
# Emits <evidence>/javac-<p>.log, javac-<p>.records.tsv (BLLSE: file line severity message), javac-<p>.summary.tsv.
set -u
WT=$1; P=$2; EV=$3; OUT=$4; JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
case "$P" in
  gtk) BP=org.eclipse.swt.gtk.linux.x86_64 ;;
  cocoa) BP=org.eclipse.swt.cocoa.macosx.aarch64 ;;
  win32) BP=org.eclipse.swt.win32.win32.x86_64 ;;
  *) echo "unknown platform $P" >&2; exit 2 ;;
esac
mkdir -p "$EV"; rm -rf "$OUT"; mkdir -p "$OUT"
src=$OUT.src
grep -o 'bundles/org.eclipse.swt/[^,\\]*' "$WT/binaries/$BP/build.properties" | tr -d '\r' | sed 's/ *$//' | sort -u > "$OUT.dirs"
: > "$src"
while IFS= read -r d; do [ -d "$WT/$d" ] && find "$WT/$d" -name '*.java' -type f | sed 's#.*#"&"#' >> "$src"; done < "$OUT.dirs"
t0=$(date +%s)
"$JDK/bin/javac" -J-Xmx1536m --release 21 -encoding UTF-8 -Xlint:none -Xmaxerrs 1000 -Xmaxwarns 10000 -proc:none -g \
  -d "$OUT" @"$src" > "$EV/javac-$P.log" 2>&1
rc=$?
tr '\\' '/' < "$EV/javac-$P.log" | sed -E -n "s#^.*bundles/org\.eclipse\.swt/(.*\.java):([0-9]+): (error|warning): (.*)\$#\1\t\2\t\3\t\4#p" > "$EV/javac-$P.records.tsv"
err=$(awk -F'\t' '$3=="error"' "$EV/javac-$P.records.tsv" | wc -l)
warn=$(awk -F'\t' '$3=="warning"' "$EV/javac-$P.records.tsv" | wc -l)
classes=$(find "$OUT" -name '*.class' | wc -l)
{
  printf 'platform\tfragment\tjdk\trelease\tsource_dirs\tsources\tclasses\trc\terrors\twarnings\tseconds\tsource_commit\n'
  printf '%s\t%s\t%s\t21\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$P" "$BP" "$("$JDK/bin/javac" -version 2>&1)" \
    "$(wc -l < "$OUT.dirs")" "$(wc -l < "$src")" "$classes" "$rc" "$err" "$warn" "$(( $(date +%s) - t0 ))" "$(git -C "$WT" rev-parse HEAD)"
} > "$EV/javac-$P.summary.tsv"
cat "$EV/javac-$P.summary.tsv" | tail -1
exit $rc
