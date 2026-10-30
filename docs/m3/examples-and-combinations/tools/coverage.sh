#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# JaCoCo report of the gallery and the generators from every .exec of the headless and display legs: the classes of
# org.eclipse.swt.snippets.m3.gallery (launcher, model, runner, index, examples), the gallery index generator and the
# covering-array generator. Writes the per-class CSV/HTML under H:/m3-scratch/swt-examples/coverage and the per-class
# line and branch percentages to results/COVERAGE.tsv in the worktree. Usage: coverage.sh [worktree]
WT=${1:-C:/m3-superset/swt-examples}; O=${M3_OUT:-H:/m3-scratch/swt-examples}
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
CLI=C:/Users/hsoliwal/.m2/repository/org/jacoco/org.jacoco.cli/0.8.15/org.jacoco.cli-0.8.15-nodeps.jar
C=$O/coverage; rm -rf "$C/classes" "$C/html"; mkdir -p "$C/classes"
cp -r "$O/snippets/org" "$C/classes/" && rm -rf "$C/classes/org/eclipse/swt/snippets/"*.class
find "$C/classes/org/eclipse/swt/snippets" -mindepth 1 -maxdepth 1 ! -name m3 -exec rm -rf {} +
find "$C/classes/org/eclipse/swt/snippets/m3" -maxdepth 1 -name '*.class' -delete
mkdir -p "$C/classes/org/eclipse/swt/tests/m3" && cp -r "$O/tests/org/eclipse/swt/tests/m3/tools" "$C/classes/org/eclipse/swt/tests/m3/"
# the generators sit in tools/ without package directories: the report reads them from a package-shaped copy
rm -rf "$C/src"; mkdir -p "$C/src/org/eclipse/swt/snippets/m3/tools" "$C/src/org/eclipse/swt/tests/m3/tools"
cp "$WT/examples/org.eclipse.swt.snippets/tools/M3GalleryIndexGen.java" "$C/src/org/eclipse/swt/snippets/m3/tools/"
cp "$WT/tests/org.eclipse.swt.tests/tools/M3CoveringArray.java" "$C/src/org/eclipse/swt/tests/m3/tools/"
mapfile -t EXECS < <(ls "$C"/*.exec)
"$JDK/bin/java" -jar "$CLI" merge "${EXECS[@]}" --destfile "$C/merged.exec" > /dev/null
"$JDK/bin/java" -jar "$CLI" report "$C/merged.exec" --classfiles "$C/classes" \
  --sourcefiles "$WT/examples/org.eclipse.swt.snippets/src" --sourcefiles "$C/src" \
  --csv "$C/coverage.csv" --html "$C/html" > /dev/null
# GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,...
awk -F, 'NR > 1 { cls = $3; sub (/\..*/, "", cls); top = $2 "." cls; lm[top] += $8; lc[top] += $9; bm[top] += $6; bc[top] += $7 }
  END {
    print "class\tlines_covered\tlines_total\tline_pct\tbranches_covered\tbranches_total\tbranch_pct"
    for (k in lm) { lt = lm[k] + lc[k]; bt = bm[k] + bc[k]
      printf "%s\t%d\t%d\t%.1f\t%d\t%d\t%s\n", k, lc[k], lt, lt ? 100 * lc[k] / lt : 100, bc[k], bt, bt ? sprintf ("%.1f", 100 * bc[k] / bt) : "-" }
  }' "$C/coverage.csv" | sort > "$WT/docs/m3/examples-and-combinations/results/COVERAGE.tsv"
awk -F'\t' 'NR > 1 { lc += $2; lt += $3; bc += $5; bt += $6 } END { printf "coverage: lines %d/%d (%.1f%%), branches %d/%d (%.1f%%)\n", lc, lt, 100 * lc / lt, bc, bt, 100 * bc / bt }' \
  "$WT/docs/m3/examples-and-combinations/results/COVERAGE.tsv"
