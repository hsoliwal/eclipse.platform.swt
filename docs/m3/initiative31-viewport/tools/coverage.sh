#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# JaCoCo report of the classes this track adds (M3*, VirtualItemStorage) from one or more exec files.
# Usage: coverage.sh <classes-dir> <out-dir> <exec>...
CLASSES=$1; OUT=$2; shift 2
M2=$(cygpath -m "$HOME/.m2/repository")
CLI=$M2/org/jacoco/org.jacoco.cli/0.8.15/org.jacoco.cli-0.8.15-nodeps.jar
WT=$(cd "$(dirname "$0")/../../../.." && pwd -W)
S="$WT/bundles/org.eclipse.swt"
rm -rf "$OUT"; mkdir -p "$OUT/classes"
for p in org/eclipse/swt/internal/m3 org/eclipse/swt/internal/swing org/eclipse/swt/widgets; do
	mkdir -p "$OUT/classes/$p"
	cp "$CLASSES/$p"/M3*.class "$OUT/classes/$p/" 2>/dev/null
done
cp "$CLASSES"/org/eclipse/swt/widgets/VirtualItemStorage*.class "$OUT/classes/org/eclipse/swt/widgets/"
EXECS=; for e in "$@"; do EXECS="$EXECS $e"; done
"C:/Program Files/Java/jdk-21/bin/java" -jar "$CLI" report $EXECS --classfiles "$OUT/classes" \
	--sourcefiles "$S/Eclipse SWT/common" --sourcefiles "$S/Eclipse SWT/swing" --sourcefiles "$S/Eclipse SWT PI/swing" \
	--csv "$OUT/coverage.csv" --xml "$OUT/coverage.xml" --html "$OUT/html" --quiet
awk -F, 'NR>1 {ml+=$8; cl+=$9; mb+=$6; cb+=$7; printf "%-40s line %d/%d branch %d/%d\n", $3, $9, $8+$9, $7, $6+$7} END {printf "TOTAL line %.2f%% (%d/%d) branch %.2f%% (%d/%d)\n", 100*cl/(ml+cl), cl, ml+cl, (mb+cb)?100*cb/(mb+cb):100, cb, mb+cb}' "$OUT/coverage.csv"
