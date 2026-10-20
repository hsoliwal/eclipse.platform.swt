#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Applies SwtSynchronizerDispatch to the common Synchronizer and SwtDisplayKeyedAsync to the win32, GTK and Cocoa
# Display (brief rule 28), each twice: the second pass must report UNCHANGED. Usage: synchronizer_recipe.sh <worktree>
WT=$1
C=K:/m3-work/syn-recipes/synexia-openrewrite-recipes/crates/m3-viewport-catalogue-recipes
S="$WT/bundles/org.eclipse.swt/Eclipse SWT"
export JAVA_HOME="C:/Program Files/Java/jdk-21"
CPR="$WT/docs/m3/synchronizer/recipe;$C/target/classes;$(cat $C/target/cp.txt)"
for pass in 1 2; do
  echo "synchronizer pass $pass: $("$JAVA_HOME/bin/java" -Xmx1g -cp "$CPR" com.synexia.openrewrite.viewport.M3PerFileApply \
    com.synexia.openrewrite.viewport.SwtSynchronizerDispatch "$S/common/org/eclipse/swt/widgets/Synchronizer.java" 2>&1 | grep -oE '^(CHANGED|UNCHANGED|ERROR)' | tr '\n' ' ')"
  echo "display keyed asyncExec pass $pass: $("$JAVA_HOME/bin/java" -Xmx1g -cp "$CPR" com.synexia.openrewrite.viewport.M3PerFileApply \
    com.synexia.openrewrite.viewport.SwtDisplayKeyedAsync "$S/win32/org/eclipse/swt/widgets/Display.java" "$S/gtk/org/eclipse/swt/widgets/Display.java" "$S/cocoa/org/eclipse/swt/widgets/Display.java" 2>&1 | grep -oE '^(CHANGED|UNCHANGED|ERROR)' | tr '\n' ' ')"
done
