#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# The Tree examples (examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/m3/M3Tree*.java) on the
# invisible desktop: compiles them with the harness evidence/TreeExampleShots.java.txt against a build and against
# the official 3.135.0 jar, then runs the harness in the default mode (saving the shells as PNG into <dest-dir>/png),
# in the original mode (-Dswt.widget.mode.Tree=original) and on the official jar. Logs: <dest-dir>/<run>.log, one
# JUL line per state, comparable across the three runs. Run it under the desktop lock:
#   bash C:/m3-superset/tools/ui_lock.sh <agent> bash tree_example_shots.sh <out-dir> <stamp> <dest-dir>
# <out-dir> holds dist/ of a build (build_and_tests.sh).
O=$1; STAMP=$2; D=$3
HERE=$(cd "$(dirname "$0")" && pwd)
SRC=$HERE/../../../examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/m3
JAVAC="C:/Program Files/Java/jdk-21/bin/javac.exe"
JAR=$(cygpath -w $O/dist/org.eclipse.swt.win32.win32.x86_64-m3-$STAMP.jar)
OFFICIAL=$(cygpath -w ~/.m2/repository/org/eclipse/platform/org.eclipse.swt.win32.win32.x86_64/3.135.0/org.eclipse.swt.win32.win32.x86_64-3.135.0.jar)
H=C:/m3-superset/tools/hidden_java.sh
mkdir -p $D/classes $D/classes-official $D/png
cp "$HERE/evidence/TreeExampleShots.java.txt" $D/TreeExampleShots.java
"$JAVAC" -J-Xmx384m -nowarn -d $(cygpath -w $D/classes) -cp "$JAR" "$SRC"/M3Tree*.java $D/TreeExampleShots.java || exit 1
"$JAVAC" -J-Xmx384m -nowarn -d $(cygpath -w $D/classes-official) -cp "$OFFICIAL" "$SRC"/M3Tree*.java $D/TreeExampleShots.java || exit 1
bash $H 600 $D/default.log -Xmx768m -cp "$(cygpath -w $D/classes);$JAR" TreeExampleShots $(cygpath -w $D/png)
bash $H 600 $D/original.log -Xmx768m -Dswt.widget.mode.Tree=original -cp "$(cygpath -w $D/classes);$JAR" TreeExampleShots
bash $H 900 $D/official.log -Xmx768m -cp "$(cygpath -w $D/classes-official);$OFFICIAL" TreeExampleShots
for r in default original official; do echo "== $r"; grep -E "^tree-|took" $D/$r.log; done
