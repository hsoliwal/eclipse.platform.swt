#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Applies SwtPartVisibility (brief rule 25) to SWT.java, TypedListener.java, the Control and Widget files of win32, gtk
# and cocoa, then SwtPartVisibilityShells to the win32 Decorations and the GTK Shell, each twice: the second pass must report UNCHANGED for every file.
# Usage: part_visibility_recipe.sh <worktree>
WT=$1
C=K:/m3-work/syn-recipes/synexia-openrewrite-recipes/crates/m3-viewport-catalogue-recipes
S="$WT/bundles/org.eclipse.swt/Eclipse SWT"
export JAVA_HOME="C:/Program Files/Java/jdk-21"
CPR="$WT/docs/m3/visibility/recipe;$C/target/classes;$(cat $C/target/cp.txt)"
FILES=("$S/common/org/eclipse/swt/SWT.java" "$S/common/org/eclipse/swt/widgets/TypedListener.java"
  "$S/win32/org/eclipse/swt/widgets/Control.java" "$S/gtk/org/eclipse/swt/widgets/Control.java" "$S/cocoa/org/eclipse/swt/widgets/Control.java"
  "$S/win32/org/eclipse/swt/widgets/Widget.java" "$S/gtk/org/eclipse/swt/widgets/Widget.java" "$S/cocoa/org/eclipse/swt/widgets/Widget.java"
  )
for pass in 1 2; do
  echo "part visibility pass $pass: $("$JAVA_HOME/bin/java" -Xmx1g -cp "$CPR" com.synexia.openrewrite.viewport.M3PerFileApply \
    com.synexia.openrewrite.viewport.SwtPartVisibility "${FILES[@]}" 2>&1 | grep -oE '^(CHANGED|UNCHANGED|ERROR)' | tr '\n' ' ')"
done
for pass in 1 2; do
  echo "part visibility shells pass $pass: $("$JAVA_HOME/bin/java" -Xmx1g -cp "$CPR" com.synexia.openrewrite.viewport.M3PerFileApply \
    com.synexia.openrewrite.viewport.SwtPartVisibilityShells "$S/win32/org/eclipse/swt/widgets/Decorations.java" "$S/gtk/org/eclipse/swt/widgets/Shell.java" 2>&1 | grep -oE '^(CHANGED|UNCHANGED|ERROR)' | tr '\n' ' ')"
done
