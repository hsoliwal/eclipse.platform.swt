#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Applies the LF6 loader recipes (dependency tracking, FlatLaf colour functions; docs/m3/LOOK_AND_FEEL.md) to
# LookAndFeelLoader.java, and the LF6 test updates to the LF2 suite, each twice: the second pass must report UNCHANGED.
# Usage: lf6_loader_recipe.sh <worktree>
WT=$1
C=K:/m3-work/syn-recipes/synexia-openrewrite-recipes/crates/m3-viewport-catalogue-recipes
R="$WT/docs/m3/look-and-feel/recipe"
F="$WT/bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/laf/LookAndFeelLoader.java"
export JAVA_HOME="C:/Program Files/Java/jdk-21"
CPR="$R;$C/target/classes;$(cat $C/target/cp.txt)"
for recipe in SwtLafLf6LoaderCone SwtLafLf6ColorFunctions; do
for pass in 1 2; do
  echo "$recipe pass $pass: $("$JAVA_HOME/bin/java" -Xmx1g -cp "$CPR" com.synexia.openrewrite.viewport.M3PerFileApply \
    com.synexia.openrewrite.viewport.$recipe "$F" 2>&1 | grep -oE '^(CHANGED|UNCHANGED|ERROR)' | tr '\n' ' ')"
done
done
T="$WT/tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/Test_org_eclipse_swt_internal_laf_LookAndFeel.java"
for pass in 1 2; do
  echo "SwtLafLf6TestUpdates pass $pass: $("$JAVA_HOME/bin/java" -Xmx1g -cp "$CPR" com.synexia.openrewrite.viewport.M3PerFileApply \
    com.synexia.openrewrite.viewport.SwtLafLf6TestUpdates "$T" 2>&1 | grep -oE '^(CHANGED|UNCHANGED|ERROR)' | tr '\n' ' ')"
done
