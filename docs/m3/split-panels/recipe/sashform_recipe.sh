#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Applies a split-panel recipe (default SwtSashFormLimitsSF1) to SashForm and SashFormLayout, twice: the second pass
# must report UNCHANGED for both files. Usage: sashform_recipe.sh <worktree> [recipe simple name]
WT=$1; NAME=${2:-SwtSashFormLimitsSF1}
C=K:/m3-work/syn-recipes/synexia-openrewrite-recipes/crates/m3-viewport-catalogue-recipes
D="$WT/bundles/org.eclipse.swt/Eclipse SWT Custom Widgets/common/org/eclipse/swt/custom"
export JAVA_HOME="C:/Program Files/Java/jdk-21"
CPR="$WT/docs/m3/split-panels/recipe;$C/target/classes;$(cat $C/target/cp.txt)"
for pass in 1 2; do
  echo "$NAME pass $pass: $("$JAVA_HOME/bin/java" -Xmx1g -cp "$CPR" com.synexia.openrewrite.viewport.M3PerFileApply \
    com.synexia.openrewrite.viewport.$NAME "$D/SashForm.java" "$D/SashFormLayout.java" 2>&1 | grep -oE '^(CHANGED|UNCHANGED|ERROR)' | tr '\n' ' ')"
done
