#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Brief rule 24 gate, hidden viewports: Test_org_eclipse_swt_widgets_DrawnHiddenViewport in custom, m3 and gpu (Tree, Table, List: hide,
# 10^4 deltas, show: no paint and no native message while hidden, one paint on show). Run under ui_lock.sh. Environment: STAMP, TESTSET; OUT; MODES.
S=${OUT:-S:/m3-scratch/render-modes}; RUN=$S/hidden-viewport; mkdir -p $RUN
M2=$(cygpath -m ~/.m2/repository); SK=C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib
CS=$M2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar
CP="$S/tests-${TESTSET:?};$S/dist/org.eclipse.swt.win32.win32.x86_64-m3-${STAMP:?}.jar;$S/dist/swt-render-m3-$STAMP.jar;$SK/skija-shared-0.116.3.jar;$SK/skija-windows-x64-0.116.3.jar;$SK/types-0.1.1.jar"
for m in ${MODES:-custom m3 gpu}; do
  log=$RUN/$STAMP-$m.log
  bash C:/m3-superset/tools/hidden_java.sh 600 $log -Xmx512m -Dswt.widget.mode=$m -jar $CS execute -cp "$CP" \
    --select-class org.eclipse.swt.tests.junit.Test_org_eclipse_swt_widgets_DrawnHiddenViewport --disable-banner --details=tree
  echo "$m: $(sed 's/\x1b\[[0-9;]*m//g' $log | grep -aE 'tests (successful|failed|aborted)' | tr -s ' ' | tr '\n' ' ')"
  sed 's/\x1b\[[0-9;]*m//g' $log | grep -aE "INFO: drawn hidden viewport: " | sed 's/^INFO: drawn hidden viewport: /  /'
  sed 's/\x1b\[[0-9;]*m//g' $log | grep -aE "✘" | cut -c1-400 | head -3
done
