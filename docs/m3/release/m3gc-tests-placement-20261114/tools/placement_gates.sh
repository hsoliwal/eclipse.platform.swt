#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# placement_gates.sh: SWT gates of the m3gc test placement (2026-11-14), JDK 21, one heartbeat line per stage.
#   api      tools/api_loss_audit.py v1.1 (rename-aware) <base>..HEAD               -> gates/api-loss.tsv
#   compile  javac --release 21 of the win32, GTK and Cocoa fragment source sets     -> gates/javac-<p>.summary.tsv
#   jar      the win32 x86_64 fragment jar with the fork natives (build_superset_jar.sh), stand-in for the reactor jar
#   tests    javac of org.eclipse.swt.tests, and ECJ under the access rules of the SDK build (ecj_access_gate.sh)
#                                                                                    -> gates/javac-tests.*, ecj-swt-tests.*
#   module   mvn clean verify of tests/org.eclipse.swt.tests.m3gc with -DskipNativeTests=false on an invisible desktop
#            (ui_lock.sh slot); the stage-jar fragment is the org.eclipse.swt.win32.win32.x86_64 3.138.0-SNAPSHOT
#            dependency in a scratch local repository                               -> gates/module.*, TEST-*.xml.gz
# Scratch (classes, jar, local repository, Maven log) stays in <scratch>; only small TSV/XML evidence goes to gates/.
# Reads, does not copy: F:/m3-scratch/green21/tools (compile_gate.sh, build_superset_jar.sh, compile_tests.sh),
# C:/m3-superset/tools (ui_lock.sh, run_hidden_desktop.ps1), docs/m3/release/swt-tests-sdk-compile-20261114/tools.
# Usage: placement_gates.sh <worktree> <base-ref> <scratch-dir> [stages: api compile jar tests module] (default: all)
set -u
WT=$1; BASE=$2; S=$3; shift 3; STAGES=${*:-api compile jar tests module}
D=$WT/docs/m3/release/m3gc-tests-placement-20261114; EV=$D/gates; G=F:/m3-scratch/green21/tools
STAMP=20261114; JAR=$S/dist/org.eclipse.swt.win32.win32.x86_64-m3-superset-$STAMP.jar
MVN=C:/ProgramData/chocolatey/lib/maven/apache-maven-3.9.14/bin/mvn.cmd; SETTINGS=F:/m3-scratch/rel21/tools/settings-central-first.xml
hb() { echo "$(date +%T) [m3gc-placement] $*"; }
has() { case " $STAGES " in *" $1 "*) return 0;; esac; return 1; }
mkdir -p "$EV" "$S/tmp"
if has api; then
  hb "api_loss $BASE..HEAD"
  python -I "$D/tools/api_loss_audit.py" "$WT" "$BASE" HEAD "$EV/api-loss.tsv"
fi
if has compile; then
  for p in win32 gtk cocoa; do
    hb "javac $p"; bash $G/compile_gate.sh "$WT" $p "$S/ev" "$S/classes-$p" > /dev/null 2>&1
    cp "$S/ev/javac-$p.summary.tsv" "$EV/"; tail -n1 "$EV/javac-$p.summary.tsv"
    [ $p = win32 ] || rm -rf "$S/classes-$p"
  done
fi
if has jar; then
  hb "win32 x86_64 fragment jar"
  rm -rf "$S/dist"; bash $G/build_superset_jar.sh "$WT" "$S/classes-win32" $STAMP "$WT/docs/m3/release/m3-superset-20261110/LEDGER.tsv" "$S/dist" 2>&1 | tail -1
  rm -rf "$S/dist/stage-work"
fi
if has tests; then
  hb "javac org.eclipse.swt.tests"
  bash $G/compile_tests.sh "$WT" "$S/classes-win32" "$S/tests" "$S/ev" > /dev/null 2>&1
  cp "$S/ev/javac-tests.summary.tsv" "$S/ev/tests-excluded-sources.txt" "$EV/"; tail -n1 "$EV/javac-tests.summary.tsv"
  hb "ECJ org.eclipse.swt.tests under the SDK access rules"
  bash "$WT/docs/m3/release/swt-tests-sdk-compile-20261114/tools/ecj_access_gate.sh" "$WT" "$S/classes-win32" "$S/ev" swt-tests > /dev/null 2>&1
  cp "$S/ev/ecj-swt-tests.summary.tsv" "$EV/"; tail -n1 "$EV/ecj-swt-tests.summary.tsv"
fi
if has module; then
  hb "mvn clean verify tests/org.eclipse.swt.tests.m3gc -DskipNativeTests=false"
  M2=$S/m2; A=$M2/org/eclipse/platform/org.eclipse.swt.win32.win32.x86_64/3.138.0-SNAPSHOT; MOD=$WT/tests/org.eclipse.swt.tests.m3gc
  rm -rf "$M2"; mkdir -p "$A"; cp "$JAR" "$A/org.eclipse.swt.win32.win32.x86_64-3.138.0-SNAPSHOT.jar"
  printf '<project><modelVersion>4.0.0</modelVersion><groupId>org.eclipse.platform</groupId><artifactId>org.eclipse.swt.win32.win32.x86_64</artifactId><version>3.138.0-SNAPSHOT</version></project>\n' \
    > "$A/org.eclipse.swt.win32.win32.x86_64-3.138.0-SNAPSHOT.pom"
  w() { cygpath -w "$1"; }
  printf '@echo off\r\nset "JAVA_HOME=C:\\Program Files\\Java\\jdk-21"\r\nset "PATH=%%JAVA_HOME%%\\bin;%%PATH%%"\r\nset "MAVEN_OPTS=-Xmx1g -Djava.io.tmpdir=%s"\r\ncall "%s" -B -nsu -f "%s" -s "%s" "-Dmaven.repo.local=%s" "-Dmaven.repo.local.tail=F:/m3-scratch/m2-eclipse" -DskipNativeTests=false clean verify\r\n' \
    "$(w "$S/tmp")" "$(w "$MVN")" "$(w "$MOD/pom.xml")" "$(w "$SETTINGS")" "$(cygpath -m "$M2")" > "$S/module.cmd"
  t0=$(date +%s)
  bash C:/m3-superset/tools/ui_lock.sh m3gc-placement bash -c 'powershell -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w C:/m3-superset/tools/run_hidden_desktop.ps1)" -TimeoutSec 1800 -Log "$1" -CommandLine "\"$2\"" -Desktop "$M3_UI_DESKTOP"' _ "$(w "$S/module.log")" "$(w "$S/module.cmd")"
  rc=$?; secs=$(( $(date +%s) - t0 ))
  R=$MOD/target/surefire-reports
  tests=0; fail=0; err=0; skip=0
  for f in "$R"/TEST-*.xml; do [ -f "$f" ] || continue
    t=$(head -c 2000 "$f" | grep -o -m1 ' tests="[0-9]*"' | grep -o '[0-9]*'); tests=$((tests + ${t:-0}))
    fl=$(head -c 2000 "$f" | grep -o -m1 'failures="[0-9]*"' | grep -o '[0-9]*'); fail=$((fail + ${fl:-0}))
    er=$(head -c 2000 "$f" | grep -o -m1 'errors="[0-9]*"' | grep -o '[0-9]*'); err=$((err + ${er:-0}))
    sk=$(head -c 2000 "$f" | grep -o -m1 'skipped="[0-9]*"' | grep -o '[0-9]*'); skip=$((skip + ${sk:-0}))
    gzip -9 -c "$f" > "$EV/$(basename "$f").gz"
  done
  st=$(tr -d '\r' < "$S/module.log" | grep -a -o -E 'BUILD (SUCCESS|FAILURE)' | tail -1)
  comp=$(tr -d '\r' < "$S/module.log" | grep -a -E 'Compiling [0-9]+ source files' | sed -E 's/.*Compiling ([0-9]+) source files.*/\1/' | tr '\n' '+' | sed 's/+$//')
  cerr=$(tr -d '\r' < "$S/module.log" | grep -a -c -E '^\[ERROR\] .*\.java:\[[0-9]+')
  jni=$(tr -d '\r' < "$S/module.log" | grep -a -c -E 'WARNING in native method|FATAL ERROR in native method')
  printf 'module\tstatus\trc\tseconds\tcompiled_sources\tcompile_errors\ttests\tfailures\terrors\tskipped\tjni_check_diagnostics\tsource_commit\n%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    org.eclipse.swt.tests.m3gc "${st:-NO_RESULT}" "$rc" "$secs" "${comp:-0}" "$cerr" "$tests" "$fail" "$err" "$skip" "$jni" "$(git -C "$WT" rev-parse HEAD)" | tee "$EV/module.summary.tsv"
  tr -d '\r' < "$S/module.log" | grep -a -E '^\[(INFO|WARNING|ERROR)\] (Tests run|BUILD|--- |Compiling|Building|.*FAIL)' | sed -E 's#[A-Z]:\\[^ ]*\\##g' > "$EV/module.steps.txt"
fi
hb "done"
