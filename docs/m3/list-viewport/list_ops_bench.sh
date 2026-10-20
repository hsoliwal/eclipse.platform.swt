#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# Optimization pass (C:/m3-superset/tools/OPTIMIZATION_PASS.md) of the List and Combo track: compiles
# evidence/ListComboOpsBench.java.txt against a build and runs it on the invisible desktop, under the desktop lock, at
# 100,000 and 1,000,000 items in the default (viewport) mode and at 100,000 in the original mode (the stock list box
# takes minutes to fill 1,000,000 strings). Logs: <dest-dir>/<mode>-<size>.log; one JUL line per operation
# (operation size mode wall_ms alloc_KB messages row_ops).
# Usage: list_ops_bench.sh <out-dir> <stamp> <dest-dir> [list|combo|all]
O=$1; STAMP=$2; D=$3; WHICH=${4:-all}
HERE=$(cd "$(dirname "$0")" && pwd)
JAVAC="C:/Program Files/Java/jdk-21/bin/javac.exe"
JAR=$(cygpath -w $O/dist/org.eclipse.swt.win32.win32.x86_64-m3-$STAMP.jar)
H=C:/m3-superset/tools/hidden_java.sh
mkdir -p $D/classes
cp "$HERE/evidence/ListComboOpsBench.java.txt" $D/ListComboOpsBench.java
"$JAVAC" -nowarn -d $(cygpath -w $D/classes) -cp "$JAR" $D/ListComboOpsBench.java || exit 1
CP="$(cygpath -w $D/classes);$JAR"
run () {
  local tag=$1; shift
  bash C:/m3-superset/tools/ui_lock.sh swt-list bash $H 1500 $D/$tag.log -Xmx3g "$@" -cp "$CP" ListComboOpsBench - ${tag##*-} $WHICH
  tr -d '\r' < $D/$tag.log
}
run viewport-100000
run viewport-1000000
run original-100000 -Dswt.widget.mode=original
