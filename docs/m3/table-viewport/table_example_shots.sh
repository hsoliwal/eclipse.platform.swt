#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# The Table examples (examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/m3/M3Table*.java) on the
# invisible desktop: compiles them with the harness evidence/TableExampleShots.java.txt against a build and against
# the official 3.135.0 jar, then runs the harness in the default mode (saving the shells as PNG into <dest-dir>/png),
# in the original mode (-Dswt.widget.mode.Table=original) and on the official jar; the stock runs skip the plain
# 1,000,000-row Table (a native item and a TableItem per row: it did not finish within 300 s). Logs: <dest-dir>/<run>.log.
# Run it under the desktop lock:
#   bash C:/m3-superset/tools/ui_lock.sh <agent> bash table_example_shots.sh <out-dir> <stamp> <dest-dir>
# <out-dir> holds dist/ of a build (build_and_tests.sh).
O=$1; STAMP=$2; D=$3
HERE=$(cd "$(dirname "$0")" && pwd)
SRC=$HERE/../../../examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/m3
JAVAC="C:/Program Files/Java/jdk-21/bin/javac.exe"
JAR=$(cygpath -w $O/dist/org.eclipse.swt.win32.win32.x86_64-m3-$STAMP.jar)
OFFICIAL=$(cygpath -w ~/.m2/repository/org/eclipse/platform/org.eclipse.swt.win32.win32.x86_64/3.135.0/org.eclipse.swt.win32.win32.x86_64-3.135.0.jar)
H=C:/m3-superset/tools/hidden_java.sh
mkdir -p $D/classes $D/classes-official $D/png
cp "$HERE/evidence/TableExampleShots.java.txt" $D/TableExampleShots.java
"$JAVAC" -d $(cygpath -w $D/classes) -cp "$JAR" "$SRC"/M3Table*.java $D/TableExampleShots.java || exit 1
"$JAVAC" -d $(cygpath -w $D/classes-official) -cp "$OFFICIAL" "$SRC"/M3Table*.java $D/TableExampleShots.java || exit 1
bash $H 300 $D/default.log -Xmx512m -cp "$(cygpath -w $D/classes);$JAR" TableExampleShots $(cygpath -w $D/png)
bash $H 300 $D/original.log -Xmx512m -Dswt.widget.mode.Table=original -cp "$(cygpath -w $D/classes);$JAR" TableExampleShots - noplain
bash $H 300 $D/official.log -Xmx512m -cp "$(cygpath -w $D/classes-official);$OFFICIAL" TableExampleShots - noplain
for r in default original official; do echo "== $r"; cat $D/$r.log; done
