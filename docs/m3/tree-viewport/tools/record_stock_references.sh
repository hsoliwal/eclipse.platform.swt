#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Records the stock references of viewport differential classes: the classes run against the official SWT jar, which
# has no widget modes, so ViewportDifferential.records () is true and every seed is written to <recording dir> as
# <name>-seed<n>.txt with the header "# reference official, SWT <version> <platform>". One ui_lock claim.
# viewport_gate.sh compares against one recording directory: copy the Tree recordings next to these.
# A test of fork-only API (for example List.setItemCount) records the original mode instead: OFFICIAL=<M3 jar>
# JVM_OPTS="-Dswt.widget.mode.<Widget>=original -Dswt.viewport.record=true" and the test method as Class#method.
# Usage: [OUT=S:/m3-scratch/swt-tv] [OFFICIAL=<official jar>] [JVM_OPTS=...] record_stock_references.sh <recording dir> <test>...
#   test: simple class name in org.eclipse.swt.tests.junit (Test_org_eclipse_swt_widgets_Table_ViewportDifferential),
#   or Class#method for one test method
set -uo pipefail; export LC_ALL=C
REC="${1:?recording dir}"; shift
[ $# -gt 0 ] || { echo "no test class"; exit 2; }
OUT=${OUT:-S:/m3-scratch/swt-tv}
OFFICIAL=${OFFICIAL:-$(cygpath -m ~/.m2/repository/org/eclipse/platform/org.eclipse.swt.win32.win32.x86_64/3.135.0/org.eclipse.swt.win32.win32.x86_64-3.135.0.jar)}
CS=$(cygpath -m ~/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar)
T=org.eclipse.swt.tests.junit; SEL=""
for c in "$@"; do
  case "$c" in *"#"*) SEL="$SEL --select-method $T.$c" ;; *) SEL="$SEL --select-class $T.$c" ;; esac
done
mkdir -p "$REC"; LOG="$REC/record-$(date -u +%Y%m%dT%H%M%SZ).log"
echo "[$(date -u +%H:%M:%S)] recording $# classes with $OFFICIAL into $REC"
bash C:/m3-superset/tools/ui_lock.sh claude-record bash C:/m3-superset/tools/hidden_java.sh 2400 "$LOG" -Xmx1g \
  -Dswt.viewport.recording="$REC" -Dswt.viewport.evidence="$REC/evidence" ${JVM_OPTS:-} -jar "$CS" execute -cp "$OUT/tests;$OFFICIAL" $SEL \
  --disable-banner --details=tree
sed 's/\x1b\[[0-9;]*m//g' "$LOG" | grep -E "✘|■|tests (successful|failed|aborted)" | sed 's/^[│ ├└─]*//' | cut -c1-200 | head -n 40
echo "[$(date -u +%H:%M:%S)] $(ls "$REC" | grep -c 'seed[0-9]*\.txt$') recordings in $REC ($LOG)"
