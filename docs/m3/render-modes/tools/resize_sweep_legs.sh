#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Brief rule 24 gate, viewport deltas: Test_org_eclipse_swt_widgets_DrawnResizeSweep in custom, m3 and gpu (Tree, Table, List: 50 sizes;
# no full repaint after the first step; back buffer re-creations, frame p50/p99). Run under ui_lock.sh. Environment: STAMP, TESTSET; OUT; MODES; JVM_OPTS (e.g. -Dswt.m3.threadCheck=true), BUDGET seconds per JVM.
S=${OUT:-S:/m3-scratch/render-modes}; RUN=$S/resize-sweep; mkdir -p $RUN
M2=$(cygpath -m ~/.m2/repository); SK=C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib
CS=$M2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar
CP="$S/tests-${TESTSET:?};$S/dist/org.eclipse.swt.win32.win32.x86_64-m3-${STAMP:?}.jar;$S/dist/swt-render-m3-$STAMP.jar;$SK/skija-shared-0.116.3.jar;$SK/skija-windows-x64-0.116.3.jar;$SK/types-0.1.1.jar"
for m in ${MODES:-custom m3 gpu}; do
  log=$RUN/$STAMP-$m${LOGTAG}.log
  bash C:/m3-superset/tools/hidden_java.sh ${BUDGET:-1500} $log -Xmx512m ${JVM_OPTS} -Dswt.widget.mode=$m -Dswt.drawn.resize=$RUN/resize.tsv -jar $CS execute -cp "$CP" \
    --select-class org.eclipse.swt.tests.junit.Test_org_eclipse_swt_widgets_DrawnResizeSweep --disable-banner --details=tree
  echo "$m: $(sed 's/\x1b\[[0-9;]*m//g' $log | grep -aE 'tests (successful|failed|aborted)' | tr -s ' ' | tr '\n' ' ')"
  sed 's/\x1b\[[0-9;]*m//g' $log | grep -aE "INFO: drawn resize sweep: " | sed 's/^INFO: drawn resize sweep: /  /'
  sed 's/\x1b\[[0-9;]*m//g' $log | grep -aE "✘" | cut -c1-400 | head -3
done
