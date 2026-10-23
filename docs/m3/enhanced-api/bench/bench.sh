#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Optimization pass: compiles M3EnhancedApiBench against an SWT jar and runs each group on the hidden desktop
# (one ui_lock ticket per group). Output: S:/m3-scratch/enhanced-api/bench/<run>/<group>.log
# Usage: bench.sh <run-name> <jar> <group:sizes,...>...   e.g. bench.sh b1 swt.jar rowmath:100000,1000000 bulk:100000
here=$(cd "$(dirname "$0")" && pwd -W 2>/dev/null || pwd)
RUN=$1; JAR=$2; shift 2
O=S:/m3-scratch/enhanced-api/bench; C=$O/classes; L=$O/$RUN; mkdir -p $C $L
"C:/Program Files/Java/jdk-21/bin/javac" --release 21 -encoding UTF-8 -cp "$JAR" -d $C "$here/M3EnhancedApiBench.java" || exit 3
for g in "$@"; do
  name=${g%%:*}; sizes=$(echo "${g#*:}" | tr ',' ' ')
  bash C:/m3-superset/tools/ui_lock.sh api-enrichment bash C:/m3-superset/tools/hidden_java.sh 2400 $L/$name.log \
    -Xmx3g -XX:+UseSerialGC -Dswt.widget.mode=viewport "-Djava.util.logging.SimpleFormatter.format=%5\$s%n" \
    -cp "$C;$JAR" org.eclipse.swt.m3.bench.M3EnhancedApiBench $name $sizes
  echo "$(date +%H:%M) $name rc=$? $(grep -c 'ns/op' $L/$name.log 2>/dev/null) rows" >> $L/SUMMARY.txt
done
echo DONE >> $L/SUMMARY.txt
