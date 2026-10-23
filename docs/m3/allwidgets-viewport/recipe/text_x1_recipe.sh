#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Applies SwtTextCrLfX1 (optimization leaf X1) to the win32 Display, twice: the second pass must report UNCHANGED.
# Usage: text_x1_recipe.sh <worktree>
WT=$1
C=K:/m3-work/syn-recipes/synexia-openrewrite-recipes/crates/m3-viewport-catalogue-recipes
export JAVA_HOME="C:/Program Files/Java/jdk-21"
CPR="$WT/docs/m3/allwidgets-viewport/recipe;$C/target/classes;$(cat $C/target/cp.txt)"
for pass in 1 2; do
  echo "text X1 pass $pass: $("$JAVA_HOME/bin/java" -Xmx1g -cp "$CPR" com.synexia.openrewrite.viewport.M3PerFileApply \
    com.synexia.openrewrite.viewport.SwtTextCrLfX1 "$WT/bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/widgets/Display.java" 2>&1 | grep -oE '^(CHANGED|UNCHANGED|ERROR)' | tr '\n' ' ')"
done
