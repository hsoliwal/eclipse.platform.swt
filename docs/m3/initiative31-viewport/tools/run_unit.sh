#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# Compile and run the headless unit tests of this track (docs/m3/initiative31-viewport/test, JUnit 4 and 5) on the
# JUnit Platform console launcher, with the JaCoCo agent. Usage: run_unit.sh <classes-dir> <out-dir> [class...]
# Without classes, every test class in the package of the headless tests runs (the UI acceptance test excluded).
CLASSES=$1; OUT=$2; shift 2
M2=$(cygpath -m "$HOME/.m2/repository")
LIBS="$M2/junit/junit/4.13.2/junit-4.13.2.jar;$M2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"
CONSOLE=$M2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar
AGENT=$M2/org/jacoco/org.jacoco.agent/0.8.15/org.jacoco.agent-0.8.15-runtime.jar
T=$(cd "$(dirname "$0")/../test" && pwd -W)
JDK="C:/Program Files/Java/jdk-21/bin"
mkdir -p "$OUT/test-classes"
"$JDK/javac" --release 21 -encoding UTF-8 -nowarn -cp "$CLASSES;$LIBS;$CONSOLE" -d "$OUT/test-classes" $(find "$T" -name '*.java') || exit 2
SELECT=; for c in "$@"; do SELECT="$SELECT --select-class $c"; done
[ -n "$SELECT" ] || SELECT="--scan-class-path --exclude-classname .*AcceptanceTest"
"$JDK/java" -Djava.awt.headless=true "-javaagent:$AGENT=destfile=$OUT/unit.exec,append=true" -jar "$CONSOLE" execute \
	-cp "$OUT/test-classes;$CLASSES;$LIBS" $SELECT --disable-banner --details=summary 2>&1
