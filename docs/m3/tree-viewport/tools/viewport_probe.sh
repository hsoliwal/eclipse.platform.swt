#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Thirty-second health probe of the viewport-mediated row widgets in a built SWT jar (ViewportProbe.java): per widget
# (Tree, Table, List; "all" runs the three) one line "GOOD ..." or "BAD ..." with the viewport class, parked flag, gate
# verdict, bar maximum and thumb, first row; exit 0 iff every probed widget is GOOD. Run it after every merge into the
# integration branch and before every differential: on 2026-10-24 every Tree viewport test had been aborting on its
# "mediation is active" assumption since 2026-10-20 and nobody had looked (user: every widget must be a viewport).
# Usage: viewport_probe.sh <swt jar> [Tree|Table|List|all] [rows] [extra java options, e.g. -Dswt.visibility.gate=false]
set -uo pipefail; export LC_ALL=C
JAR="${1:?swt jar}"; WIDGET="${2:-all}"; ROWS="${3:-300}"; shift; shift 2>/dev/null; shift 2>/dev/null || true
D=$(cd "$(dirname "$0")" && pwd); OUT=${OUT:-S:/m3-scratch/swt-probe}; mkdir -p "$OUT/classes"
JDK="${M3_JDK:-C:/Program Files/Java/jdk-21}"
if [ ! -f "$OUT/classes/ViewportProbe.class" ] || [ "$D/ViewportProbe.java" -nt "$OUT/classes/ViewportProbe.class" ]; then
	"$JDK/bin/javac" --release 21 -cp "$JAR" -d "$OUT/classes" "$D/ViewportProbe.java" || exit 2
fi
rc=0
for w in $( [ "$WIDGET" = all ] && echo "Tree Table List" || echo "$WIDGET" ); do
	LOG="$OUT/probe-$w-$(date -u +%Y%m%dT%H%M%S).log"
	bash C:/m3-superset/tools/ui_lock.sh viewport-probe bash C:/m3-superset/tools/hidden_java.sh 120 "$LOG" "$@" -cp "$(cygpath -w "$OUT/classes");$(cygpath -w "$JAR")" ViewportProbe "$w" "$ROWS" > /dev/null 2>&1
	line=$(grep -E "^(GOOD|BAD) " "$LOG" | head -n 1)
	if [ -z "$line" ]; then echo "NO_ANSWER widget=$w (see $LOG)"; grep -v "^\s*at " "$LOG" | head -n 2; rc=3; continue; fi
	echo "$line"
	case "$line" in GOOD*) ;; *) rc=1 ;; esac
done
exit $rc
