#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Build of the gallery and the combination matrix (no Display): generator fixed points (--check), then javac of the M3
# snippets with the gallery and its generator, and of the matrix and gallery tests with the covering-array generator and the
# recorded differential harness, against the win32 classes of compile_platform.sh. Windows paths only; output under
# H:/m3-scratch/swt-examples. Usage: build.sh [worktree]
WT=${1:-C:/m3-superset/swt-examples}; O=${M3_OUT:-H:/m3-scratch/swt-examples}
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
JUNIT=C:/Users/hsoliwal/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar
SWT=$O/platform/win32
[ -d "$SWT/org" ] || { echo "no SWT classes: run compile_platform.sh $WT win32 first"; exit 3; }
"$JDK/bin/java" -Xmx512m "$WT/examples/org.eclipse.swt.snippets/tools/M3GalleryIndexGen.java" --check "$WT" | tail -4 || exit 1
"$JDK/bin/java" -Xmx512m "$WT/tests/org.eclipse.swt.tests/tools/M3CoveringArray.java" --check "$WT" | tail -4 || exit 1
compile () { # <out> <classpath> <log> <sources...>
  local out=$1 cp=$2 log=$3; shift 3
  rm -rf "$out"; mkdir -p "$out"
  printf '"%s"\n' "$@" > "$out.src"
  "$JDK/bin/javac" -J-Xmx768m --release 21 -encoding UTF-8 -nowarn -proc:none -g -cp "$cp" -d "$out" @"$out.src" > "$log" 2>&1
  local n; n=$(grep -cE ': error:|^error: ' "$log"); n=${n:-0}
  echo "$(basename "$out"): sources=$# errors=$n"; grep -A2 ': error:' "$log" | head -30; [ "$n" -eq 0 ]
}
S=$WT/examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets
mapfile -t SNIPPETS < <(find "$S/m3" -name '*.java'; ls "$S"/M3*.java; echo "$WT/examples/org.eclipse.swt.snippets/tools/M3GalleryIndexGen.java")
compile "$O/snippets" "$SWT" "$O/snippets.log" "${SNIPPETS[@]}" || exit 1
T="$WT/tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit"
mapfile -t TESTS < <(find "$T/m3combinations" "$T/m3gallery" -name '*.java'; echo "$T/ViewportDifferential.java"; echo "$WT/tests/org.eclipse.swt.tests/tools/M3CoveringArray.java")
compile "$O/tests" "$SWT;$O/snippets;$JUNIT" "$O/tests.log" "${TESTS[@]}" || exit 1
