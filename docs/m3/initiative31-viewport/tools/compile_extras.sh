#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# Compile the harness, the tests and the examples of this track against a Swing build. Run under build_lock.sh.
# Usage: compile_extras.sh <classes-dir> <build-dir>   (writes <build-dir>/harness-i31, test-<name>, examples)
CLASSES=$1; B=$2
D=$(cd "$(dirname "$0")/.." && pwd -W)
M2=$(cygpath -m "$HOME/.m2/repository")
LIBS="$M2/junit/junit/4.13.2/junit-4.13.2.jar;$M2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar;$M2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"
JDK="C:/Program Files/Java/jdk-21/bin"
T="$B/test-$(basename "$(dirname "$CLASSES")")"
rm -rf "$B/harness-i31" "$B/examples" "$T"; mkdir -p "$B/harness-i31" "$B/examples" "$T"
"$JDK/javac" --release 21 -encoding UTF-8 -nowarn -cp "$CLASSES" -d "$B/harness-i31" $(find "$D/harness" -name '*.java') || exit 2
"$JDK/javac" --release 21 -encoding UTF-8 -nowarn -cp "$CLASSES" -d "$B/examples" $(find "$D/examples" -name '*.java') || exit 2
"$JDK/javac" --release 21 -encoding UTF-8 -nowarn -cp "$CLASSES;$LIBS" -d "$T" $(find "$D/test" -name '*.java') || exit 2
echo "harness, examples and tests compiled into $B ($T)"
