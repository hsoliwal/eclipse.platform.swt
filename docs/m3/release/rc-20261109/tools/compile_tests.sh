#!/usr/bin/env bash
# Compile the SWT JUnit sources (JUnit Tests + data) with --release 21 against the win32 classes, copy test resources.
# Excluded: */performance/* (needs org.eclipse.test.performance) and */m3gc/* (imports io.github.humbleui, which the
# test bundle manifest does not declare; no Skija jar in the candidate). org.eclipse.test.Screenshots comes from a stub.
# Usage: compile_tests.sh <worktree> <swt-classes-or-jar> <out-dir> <evidence-dir>
set -u
WT=$1; SWT=$2; OUT=$3; EV=$4; JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
CS=$(cygpath -m ~/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar)
STUB=C:/m3-superset/test-stubs/org/eclipse/test/Screenshots.java
T="$WT/tests/org.eclipse.swt.tests"
rm -rf "$OUT"; mkdir -p "$OUT/classes" "$EV"
( cd "$T" && find "JUnit Tests" data -name '*.java' ) | sort > "$OUT/all-src.txt"
grep -E '/(performance|m3gc)/' "$OUT/all-src.txt" > "$OUT/excluded-src.txt"
{ grep -vE '/(performance|m3gc)/' "$OUT/all-src.txt" | sed "s#^#$T/#"; echo "$STUB"; } | sed 's#.*#"&"#' > "$OUT/src.txt"
"$JDK/bin/javac" -J-Xmx1536m --release 21 -encoding UTF-8 -nowarn -proc:none -Xmaxerrs 500 -cp "$SWT;$CS" -d "$OUT/classes" @"$OUT/src.txt" > "$EV/javac-tests.log" 2>&1
rc=$?
for root in "JUnit Tests" data; do
  (cd "$T/$root" && find . -type f ! -name '*.java' | while IFS= read -r f; do mkdir -p "$OUT/classes/$(dirname "$f")"; cp "$f" "$OUT/classes/$f"; done)
done
cp "$OUT/excluded-src.txt" "$EV/tests-excluded-sources.txt"
printf 'sources\texcluded\trc\terrors\tclasses\tsource_commit\n%s\t%s\t%s\t%s\t%s\t%s\n' "$(wc -l < "$OUT/src.txt")" "$(wc -l < "$OUT/excluded-src.txt")" "$rc" \
  "$(grep -c ': error:' "$EV/javac-tests.log")" "$(find "$OUT/classes" -name '*.class' | wc -l)" "$(git -C "$WT" rev-parse HEAD)" | tee "$EV/javac-tests.summary.tsv"
exit $rc
