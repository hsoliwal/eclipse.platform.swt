#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Applies SwtViewportListener (brief rule 25 sub-rule) to SWT.java, TypedListener.java and the win32, GTK and Cocoa
# Scrollable, twice: the second pass must report UNCHANGED for every file. Usage: viewport_listener_recipe.sh <worktree>
WT=$1
C=K:/m3-work/syn-recipes/synexia-openrewrite-recipes/crates/m3-viewport-catalogue-recipes
S="$WT/bundles/org.eclipse.swt/Eclipse SWT"
export JAVA_HOME="C:/Program Files/Java/jdk-21"
CPR="$WT/docs/m3/visibility/recipe;$C/target/classes;$(cat $C/target/cp.txt)"
FILES=("$S/common/org/eclipse/swt/SWT.java" "$S/common/org/eclipse/swt/widgets/TypedListener.java"
  "$S/win32/org/eclipse/swt/widgets/Scrollable.java" "$S/gtk/org/eclipse/swt/widgets/Scrollable.java" "$S/cocoa/org/eclipse/swt/widgets/Scrollable.java")
for pass in 1 2; do
  echo "viewport listener pass $pass: $("$JAVA_HOME/bin/java" -Xmx1g -cp "$CPR" com.synexia.openrewrite.viewport.M3PerFileApply \
    com.synexia.openrewrite.viewport.SwtViewportListener "${FILES[@]}" 2>&1 | grep -oE '^(CHANGED|UNCHANGED|ERROR)' | tr '\n' ' ')"
done
