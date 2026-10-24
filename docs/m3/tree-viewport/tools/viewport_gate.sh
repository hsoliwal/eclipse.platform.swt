#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# The viewport gate of a worktree: compile win32, build the jar and the tests (C:/m3-superset/tools), run the virtual
# Tree probe, then every viewport acceptance and differential class of the test build (Tree, Table, List, Combo,
# CoolBar, ExpandBar, ScrolledComposite, CTabFolder, StyledText: 14 classes on 2026-10-24) against the stock recordings
# (survey mode: every divergence listed, none fails the run early). One line per stage, a verdict at the end;
# VIEWPORT_GATE.tsv in OUT gets one row (utc, worktree, commit, probe, tests successful/failed/aborted).
# A merge into the integration branch is not done until this says GATE_GREEN (user: every widget must be a viewport).
# Usage: [OUT=S:/m3-scratch/swt-tv] [RECORDING=K:/m3-work/runs/tree-stock-recording-i] viewport_gate.sh <worktree> [<stamp>]
set -uo pipefail; export LC_ALL=C
W="${1:?worktree}"; STAMP="${2:-gate$(date -u +%Y%m%d%H%M)}"; OUT=${OUT:-S:/m3-scratch/swt-tv}; RECORDING=${RECORDING:-K:/m3-work/runs/tree-stock-recording-i}
D=$(cd "$(dirname "$0")" && pwd); T=org.eclipse.swt.tests.junit; mkdir -p "$OUT"
commit=$(git -C "$W" rev-parse --short HEAD 2>/dev/null || echo "-")
say() { echo "[$(date -u +%H:%M:%S)] $*"; }
say "compile win32 of $W ($commit)"
bash C:/m3-superset/tools/compile_platform.sh "$W" win32 2>&1 | tail -n 1 || { say "GATE_RED: compile"; exit 1; }
say "jar and tests ($STAMP)"
bash C:/m3-superset/tools/build_and_tests.sh "$W" "$OUT" "$STAMP" 2>&1 | grep -E "tests javac|no jar" | head -n 2
JAR="$OUT/dist/org.eclipse.swt.win32.win32.x86_64-m3-$STAMP.jar"; [ -f "$JAR" ] || { say "GATE_RED: no jar"; exit 1; }
# grep -c prints 0 and exits 1 when nothing matches: no "|| echo 0" (that made the count "0 0")
errors=$(grep -c ': error:' "$OUT/tests.log" 2>/dev/null); errors=${errors:-0}; [ "$errors" -eq 0 ] || { say "GATE_RED: $errors test compile errors ($OUT/tests.log)"; exit 1; }
say "probe"
probe=$(bash "$D/viewport_probe.sh" "$JAR" 300); say "$probe"
say "suites (every viewport widget: acceptance and differential classes, survey mode)"
SEL=""
for cls in $(ls "$OUT/tests/org/eclipse/swt/tests/junit/" | grep -E "_Viewport(Differential)?\.class$" | sed 's/\.class$//' | sort); do SEL="$SEL --select-class $T.$cls"; done
say "classes: $(printf '%s' "$SEL" | grep -o "select-class" | wc -l)"
OUT=$OUT RECORDING=$RECORDING JVM_OPTS="-Dswt.viewport.survey=true" bash "$D/tree_viewport_run.sh" "$STAMP" $SEL > /dev/null 2>&1
L="$OUT/run-$STAMP.log"
counts=$(sed 's/\x1b\[[0-9;]*m//g' "$L" | grep -oE "[0-9]+ tests (successful|failed|aborted)" | tr '\n' ';')
sed 's/\x1b\[[0-9;]*m//g' "$L" | grep -E "✘|■" | sed 's/^[│ ├└─]*//' | cut -c1-200 | head -n 20
say "$counts"
failed=$(printf '%s' "$counts" | grep -oE "[0-9]+ tests failed" | grep -oE "^[0-9]+"); aborted=$(printf '%s' "$counts" | grep -oE "[0-9]+ tests aborted" | grep -oE "^[0-9]+")
verdict=GATE_GREEN; case "$probe" in GOOD*) ;; *) verdict=GATE_RED ;; esac; [ "${failed:-0}" -eq 0 ] && [ "${aborted:-0}" -eq 0 ] || verdict=GATE_RED
[ -f "$OUT/VIEWPORT_GATE.tsv" ] || printf 'utc\tworktree\tcommit\tstamp\tprobe\tcounts\tverdict\n' > "$OUT/VIEWPORT_GATE.tsv"
printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$(date -u +%Y-%m-%dT%H:%MZ)" "$W" "$commit" "$STAMP" "$(printf '%s' "$probe" | cut -c1-60)" "$counts" "$verdict" >> "$OUT/VIEWPORT_GATE.tsv"
say "$verdict ($L)"
[ "$verdict" = GATE_GREEN ]
