#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Headless legs (no Display, no hidden desktop): the generator and gallery-model tests under JaCoCo, then the headless
# rows of the combination matrix: the reference JVM records them (-Dswt.widget.mode=original -Dswt.viewport.record=true),
# one JVM per other mode compares. One JVM at a time; output under H:/m3-scratch/swt-examples. Run build.sh first.
# Usage: run_headless.sh [worktree]
WT=${1:-C:/m3-superset/swt-examples}; O=${M3_OUT:-H:/m3-scratch/swt-examples}
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
JUNIT=C:/Users/hsoliwal/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar
AGENT=C:/Users/hsoliwal/.m2/repository/org/jacoco/org.jacoco.agent/0.8.15/org.jacoco.agent-0.8.15-runtime.jar
CP="$O/tests;$O/snippets;$O/platform/win32;$JUNIT"
INCLUDES='org.eclipse.swt.snippets.m3.gallery.*:org.eclipse.swt.snippets.m3.tools.*:org.eclipse.swt.tests.m3.tools.*'
mkdir -p "$O/results" "$O/coverage"
junit () { # <log> <jvm options and console arguments...>
  local log=$1; shift
  "$JDK/bin/java" -Xmx1g -Djava.awt.headless=true "-Dm3.repo.root=$WT" "-Dm3.combinations.matrix=$WT/tests/org.eclipse.swt.tests/tools/m3-combinations/COMBINATION_MATRIX.tsv" \
    "$@" --disable-banner --disable-ansi-colors --details=summary > "$log" 2>&1
  echo "$(basename "$log" .log): $(grep -E 'tests (successful|failed|aborted|skipped)' "$log" | sed -E 's/^\[ *//; s/ +\]$//' | tr -s ' ' | tr '\n' ' ')"
}
junit "$O/results/headless-tools.log" "-javaagent:$AGENT=destfile=$O/coverage/headless-tools.exec,includes=$INCLUDES" -cp "$CP" org.junit.platform.console.ConsoleLauncher execute \
  --select-class org.eclipse.swt.tests.junit.m3combinations.Test_M3CoveringArray --select-class org.eclipse.swt.tests.junit.m3gallery.Test_M3GalleryIndexGen \
  --select-class org.eclipse.swt.tests.junit.m3gallery.Test_M3GalleryModel
REC=$O/recording; RES=$O/results/matrix-headless.tsv; rm -rf "$REC/headless"; rm -f "$RES"
for mode in original viewport custom m3 gpu; do
  record=false; [ "$mode" = original ] && record=true
  junit "$O/results/headless-matrix-$mode.log" -Dswt.widget.mode=$mode -Dswt.viewport.record=$record -Dm3.combinations.env=headless \
    "-Dswt.viewport.recording=$REC/headless" "-Dm3.combinations.results=$RES" -cp "$CP" org.junit.platform.console.ConsoleLauncher execute \
    --select-class org.eclipse.swt.tests.junit.m3combinations.Test_M3CombinationMatrix
done
