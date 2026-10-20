#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Applies SwtLafRowContractJavadoc (the drawn row delegate names the LF6 contract M3ComponentUI) to a copy of
# render-modes' M3DrawnRowUI.java (and the M3 switch snippet's comment), twice: the second pass must report UNCHANGED.
# Usage: row_contract_recipe.sh <worktree-with-this-recipe> <java file>...
WT=$1; shift
C=K:/m3-work/syn-recipes/synexia-openrewrite-recipes/crates/m3-viewport-catalogue-recipes
export JAVA_HOME="C:/Program Files/Java/jdk-21"
CPR="$WT/docs/m3/look-and-feel/recipe;$C/target/classes;$(cat $C/target/cp.txt)"
for pass in 1 2; do
  echo "row contract pass $pass: $("$JAVA_HOME/bin/java" -Xmx1g -cp "$CPR" com.synexia.openrewrite.viewport.M3PerFileApply \
    com.synexia.openrewrite.viewport.SwtLafRowContractJavadoc "$@" 2>&1 | grep -oE '^(CHANGED|UNCHANGED|ERROR)' | tr '\n' ' ')"
done
