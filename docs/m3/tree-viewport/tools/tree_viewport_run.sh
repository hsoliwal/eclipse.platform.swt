#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Run the Tree viewport differential + acceptance tests against an already built jar (viewport on).
# Waits (bounded) for UI memory headroom. Usage: tree_viewport_run.sh <stamp> [extra junit selectors...]
# OUT (default C:/m3-superset/tv) is the build_and_tests.sh output tree (dist/, tests/, the run log); RECORDING (default
# K:/m3-work/runs/tree-stock-recording) the stock recordings: tree-stock-recording-i carries all six variants
# (plain, columns, presentation, keys, splice, customdraw), 12 seeds each, reference "official, SWT 4974 win32".
STAMP=$1; shift
export JAVA_HOME="C:/Program Files/Java/jdk-21"
OUT=${OUT:-C:/m3-superset/tv}; RECORDING=${RECORDING:-K:/m3-work/runs/tree-stock-recording}
JAR=$OUT/dist/org.eclipse.swt.win32.win32.x86_64-m3-$STAMP.jar
CS=$(cygpath -m ~/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar)
T=org.eclipse.swt.tests.junit
SEL=${*:-"--select-class $T.Test_org_eclipse_swt_widgets_Tree_ViewportDifferential --select-class $T.Test_org_eclipse_swt_widgets_Tree_Viewport"}
for i in $(seq 1 90); do
  kb=$(bash C:/m3-superset/tools/commit_free_kb.sh)
  [ "$kb" -ge 6291456 ] && break; sleep 20
done
bash C:/m3-superset/tools/ui_lock.sh claude-tree bash C:/m3-superset/tools/hidden_java.sh 900 $OUT/run-$STAMP.log -Xmx1g -Dswt.viewport.evidence=K:/m3-work/runs/tree-evidence -Dswt.viewport.recording=$RECORDING ${JVM_OPTS:-} -jar $CS execute -cp "$OUT/tests;$JAR" $SEL --disable-banner --details=tree
sed 's/\x1b\[[0-9;]*m//g' $OUT/run-$STAMP.log | grep -E "memory gate|✘|tests (successful|failed)|^  plain:|^  viewport:|differs|^seed|^log:" | head -16
