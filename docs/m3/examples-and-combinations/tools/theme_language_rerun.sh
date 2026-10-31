#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Reruns the matrix rows that need M3Theme / M3Language against a local merge of this branch with the theme-language
# branch, without a second worktree and without pushing: git merge-tree --write-tree gives the merged tree, git archive
# exports the SWT sources, the tests, the snippets and the tables to H:/m3-scratch/swt-examples/merge/src, which is then
# compiled (compile_platform.sh, build.sh) and run on the hidden desktop (run_display.sh matrix with the subset of rows whose
# theme or language is not none). Results: H:/m3-scratch/swt-examples/merge/results/matrix-theme-language.tsv.
# Usage: theme_language_rerun.sh <their-commit-or-ref> [worktree]
THEIRS=$1; WT=${2:-C:/m3-superset/swt-examples}; M=H:/m3-scratch/swt-examples/merge; T=$WT/docs/m3/examples-and-combinations/tools
cd "$WT" || exit 2
tree=$(git merge-tree --write-tree HEAD "$THEIRS" | head -1) || { echo "merge conflicts: $(git merge-tree --write-tree --name-only HEAD "$THEIRS" | tail -n +2 | head)"; exit 1; }
echo "merged tree $tree (ours $(git rev-parse --short HEAD), theirs $(git rev-parse --short "$THEIRS"))"
rm -rf "$M/src"; mkdir -p "$M/src"
git archive "$tree" bundles/org.eclipse.swt binaries/org.eclipse.swt.win32.win32.x86_64/build.properties tests/org.eclipse.swt.tests \
  examples/org.eclipse.swt.snippets docs/m3/ENHANCED_API_MAP.tsv docs/m3/feature-switches | tar -x -C "$M/src" || exit 1
bash "$T/compile_platform.sh" "$M/src" win32 "$M/platform/win32" || exit 1
M3_OUT=$M bash "$T/build.sh" "$M/src" || exit 1
{ head -1 "$M/src/tests/org.eclipse.swt.tests/tools/m3-combinations/COMBINATION_MATRIX.tsv"
  awk -F'\t' 'NR > 1 && ($9 != "none" || $10 != "none") && $11 == "display"' "$M/src/tests/org.eclipse.swt.tests/tools/m3-combinations/COMBINATION_MATRIX.tsv"; } > "$M/theme-language-matrix.tsv"
echo "rows needing theme or language: $(($(wc -l < "$M/theme-language-matrix.tsv") - 1))"
M3_OUT=$M MATRIX=$M/theme-language-matrix.tsv TAG=theme-language bash "$T/run_display.sh" matrix "$M/src"
