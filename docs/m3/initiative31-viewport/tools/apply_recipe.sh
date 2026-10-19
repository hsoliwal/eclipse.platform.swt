#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# Apply a named recipe of this track to files, twice; the second pass must print UNCHANGED for every file.
# Usage: apply_recipe.sh <recipe-name> <file>...
C=K:/m3-work/syn-recipes/synexia-openrewrite-recipes/crates/m3-viewport-catalogue-recipes
R=$(cd "$(dirname "$0")/../recipe" && pwd -W)
CPR="$R;$C/target/classes;$(cat $C/target/cp.txt)"
NAME=$1; shift
for pass in 1 2; do
	echo "pass $pass: $("C:/Program Files/Java/jdk-21/bin/java" -Xmx1g -cp "$CPR" com.synexia.openrewrite.viewport.M3PerFileApply "$NAME" "$@" 2>&1 | grep -oE '^(CHANGED|UNCHANGED|ERROR).*' | sed 's#\t.*/# #' | tr '\n' ' ')"
done
