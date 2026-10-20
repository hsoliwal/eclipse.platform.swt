#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Applies the JUL logging recipe (brief rule 22) to the scale oracle tests and the M3 snippets of the all-widgets
# branch, twice: the second pass must report UNCHANGED for every file.
# Usage: jul_logging_recipe.sh <worktree>
WT=$1
C=K:/m3-work/syn-recipes/synexia-openrewrite-recipes/crates/m3-viewport-catalogue-recipes
R="$WT/docs/m3/look-and-feel/recipe"
T="$WT/tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit"
export JAVA_HOME="C:/Program Files/Java/jdk-21"
CPR="$R;$C/target/classes;$(cat $C/target/cp.txt)"
FILES=("$T/Test_org_eclipse_swt_custom_CTabFolder_ScaleOracle.java" "$T/Test_org_eclipse_swt_widgets_ExpandBar_ScaleOracle.java"
  "$T/Test_org_eclipse_swt_widgets_Text_ScaleOracle.java" "$T/Test_org_eclipse_swt_widgets_ToolBar_ScaleOracle.java"
  "$WT/examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/m3/M3LookAndFeelSwitchSnippet.java")
for pass in 1 2; do
  echo "jul pass $pass: $("$JAVA_HOME/bin/java" -Xmx1g -cp "$CPR" com.synexia.openrewrite.viewport.M3PerFileApply \
    com.synexia.openrewrite.viewport.SwtAllWidgetsJulLogging "${FILES[@]}" 2>&1 | grep -oE '^(CHANGED|UNCHANGED|ERROR)' | tr '\n' ' ')"
done
