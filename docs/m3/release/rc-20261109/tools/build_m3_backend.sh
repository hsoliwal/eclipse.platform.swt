#!/usr/bin/env bash
# Compile the optional M3 render/GPU tree ("Eclipse SWT Render M3/win32", "Eclipse SWT GPU M3/win32") against the
# superset jar and the Skija binding those sources use: skija-shared/-windows-x64 0.116.3 + types 0.1.1 (Maven Central;
# the same pins bundles/org.eclipse.swt.gpu.m3 packages). SWT loads this tree by name (RowBackends.M3_CANVAS_FACTORY);
# the JUnit gate puts these classes and the Skija jars on the test classpath so the m3gc tests compile and run.
# Usage: build_m3_backend.sh <worktree> <swt-jar> <gatelib-dir> <out-classes> <evidence-dir>
set -u
WT=$1; SWT=$2; LIB=$3; OUT=$4; EV=$5; JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
SK="$LIB/skija-shared-0.116.3.jar;$LIB/skija-windows-x64-0.116.3.jar;$LIB/types-0.1.1.jar;$LIB/annotations-24.1.0.jar"
rm -rf "$OUT"; mkdir -p "$OUT" "$EV"
B="$WT/bundles/org.eclipse.swt"
find "$B/Eclipse SWT Render M3/win32" "$B/Eclipse SWT GPU M3/win32" -name '*.java' | sed 's#.*#"&"#' > "$OUT.src"
"$JDK/bin/javac" -J-Xmx1024m --release 21 -encoding UTF-8 -Xlint:none -proc:none -cp "$SWT;$SK" -d "$OUT" @"$OUT.src" > "$EV/javac-m3-backend.log" 2>&1
rc=$?
printf 'sources\trc\terrors\tclasses\tskija\tsource_commit\n%s\t%s\t%s\t%s\t%s\t%s\n' "$(wc -l < "$OUT.src")" "$rc" \
  "$(grep -c ': error:' "$EV/javac-m3-backend.log")" "$(find "$OUT" -name '*.class' | wc -l)" "0.116.3/types-0.1.1" \
  "$(git -C "$WT" rev-parse HEAD)" | tee "$EV/javac-m3-backend.summary.tsv"
exit $rc
