#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# Recorded differential of one widget: the same seeds in the original mode (reference) and the viewport mode,
# on the hidden desktop, then DifferentialCompare. Output under S:/m3-scratch/initiative31/runs/<stamp>.
# Usage: run_differential.sh <widget> <harness-class> <classes-dir> <stamp> <seeds> <steps> <compared-columns> [laf-class]
WIDGET=$1; CLASS=$2; CLASSES=$3; STAMP=$4; SEEDS=$5; STEPS=$6; COMPARED=$7; LAF=$8
R=S:/m3-scratch/initiative31/runs/$STAMP; mkdir -p "$R"
H=S:/m3-scratch/initiative31/build/harness-i31
CP="$(cygpath -w "$CLASSES");$(cygpath -w "$H")"
LAFARG=; [ -n "$LAF" ] && LAFARG="-Dswt.swing.laf=$LAF"
for mode in original viewport; do
	bash C:/m3-superset/tools/hidden_java.sh 1500 "$R/$WIDGET-$mode.log" -Xmx1g $LAFARG -Dswt.widget.mode.$WIDGET=$mode -cp "$CP" m3.i31.harness.$CLASS "$(cygpath -w "$R/$WIDGET-$mode.tsv")" 0 "$SEEDS" "$STEPS"
done
"C:/Program Files/Java/jdk-21/bin/java" -cp "$(cygpath -w "$H")" m3.i31.harness.DifferentialCompare "$R/$WIDGET-original.tsv" "$R/$WIDGET-viewport.tsv" "$COMPARED" "$R/$WIDGET-compare.txt"
echo "compare exit=$?"; head -12 "$R/$WIDGET-compare.txt"
