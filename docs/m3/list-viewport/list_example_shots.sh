#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# The List and Combo examples (examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/m3/M3List*Example.java,
# M3Combo*Example.java) on the invisible desktop: compiles them with the harness evidence/ListExampleShots.java.txt
# against a build and against the official 3.135.0 jar, then runs the harness in the default mode (saving the shells
# as PNG into <dest-dir>/png), in the original mode (-Dswt.widget.mode=original) and on the official jar.
# Logs: <dest-dir>/<run>.log. Pattern: table-viewport/table_example_shots.sh.
# Run it under the desktop lock:
#   bash C:/m3-superset/tools/ui_lock.sh <agent> bash list_example_shots.sh <out-dir> <stamp> <dest-dir>
# <out-dir> holds dist/ of a build (build_and_tests.sh).
O=$1; STAMP=$2; D=$3
HERE=$(cd "$(dirname "$0")" && pwd)
SRC=$HERE/../../../examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/m3
JAVAC="C:/Program Files/Java/jdk-21/bin/javac.exe"
JAR=$(cygpath -w $O/dist/org.eclipse.swt.win32.win32.x86_64-m3-$STAMP.jar)
OFFICIAL=$(cygpath -w ~/.m2/repository/org/eclipse/platform/org.eclipse.swt.win32.win32.x86_64/3.135.0/org.eclipse.swt.win32.win32.x86_64-3.135.0.jar)
H=C:/m3-superset/tools/hidden_java.sh
mkdir -p $D/classes $D/classes-official $D/png
cp "$HERE/evidence/ListExampleShots.java.txt" $D/ListExampleShots.java
EX=("$SRC"/M3List*Example.java "$SRC"/M3Combo*Example.java)
"$JAVAC" -d $(cygpath -w $D/classes) -cp "$JAR" "${EX[@]}" $D/ListExampleShots.java || exit 1
"$JAVAC" -d $(cygpath -w $D/classes-official) -cp "$OFFICIAL" "${EX[@]}" $D/ListExampleShots.java || exit 1
bash $H 600 $D/default.log -Xmx1g -cp "$(cygpath -w $D/classes);$JAR" ListExampleShots $(cygpath -w $D/png)
bash $H 600 $D/original.log -Xmx1g -Dswt.widget.mode=original -cp "$(cygpath -w $D/classes);$JAR" ListExampleShots -
bash $H 600 $D/official.log -Xmx1g -cp "$(cygpath -w $D/classes-official);$OFFICIAL" ListExampleShots -
for r in default original official; do echo "== $r"; tr -d '\r' < $D/$r.log; done
