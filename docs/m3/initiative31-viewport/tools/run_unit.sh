#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# Compile and run the headless unit tests of this track (docs/m3/initiative31-viewport/test) with the JaCoCo
# agent. Usage: run_unit.sh <classes-dir> <out-dir> <test-class>...
CLASSES=$1; OUT=$2; shift 2
M2=$(cygpath -m "$HOME/.m2/repository")
JUNIT="$M2/junit/junit/4.13.2/junit-4.13.2.jar;$M2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"
AGENT=$M2/org/jacoco/org.jacoco.agent/0.8.15/org.jacoco.agent-0.8.15-runtime.jar
T=$(cd "$(dirname "$0")/../test" && pwd)
JDK="C:/Program Files/Java/jdk-21/bin"
mkdir -p "$OUT/test-classes"
"$JDK/javac" --release 21 -encoding UTF-8 -nowarn -cp "$CLASSES;$JUNIT" -d "$OUT/test-classes" $(find "$T" -name '*.java') || exit 2
"$JDK/java" -Djava.awt.headless=true "-javaagent:$(cygpath -w "$AGENT")=destfile=$(cygpath -w "$OUT/unit.exec"),append=true" -cp "$OUT/test-classes;$CLASSES;$JUNIT" org.junit.runner.JUnitCore "$@"
