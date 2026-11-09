#!/usr/bin/env bash
# Final gate sequence for m3-superset-20261109 at the worktree HEAD (all evidence into docs/m3/release/rc-20261109/gates).
# Usage: final_gates.sh   (prints one heartbeat line per stage)
set -u
R=F:/m3-scratch/rc-20261109; WT=$R/swt; EV=$WT/docs/m3/release/rc-20261109/gates; L=$R/gatelib; T=$R/tools
JAR=$R/dist-final/org.eclipse.swt.win32.win32.x86_64-m3-superset-20261109.jar
P=org.eclipse.swt.tests.junit
hb() { echo "$(date +%T) $*"; }
hb "compile win32/gtk/cocoa"
for p in win32 gtk cocoa; do bash $T/compile_gate.sh $WT $p $EV $R/build/classes-$p > /dev/null 2>&1 & done; wait
tail -qn1 $EV/javac-win32.summary.tsv $EV/javac-gtk.summary.tsv $EV/javac-cocoa.summary.tsv
hb "superset jar"
rm -rf $R/dist-final/stage-work; bash $T/build_superset_jar.sh $WT $R/build/classes-win32 20261109 $WT/docs/m3/release/rc-20261109/LEDGER.tsv $R/dist-final 2>&1 | tail -1
hb "svg fragment + M3 backend"
bash $T/build_svg_fragment.sh $WT $JAR $L/jsvg-2.1.0.jar $L > /dev/null
bash $T/build_m3_backend.sh $WT $JAR $L $R/build/m3-backend $EV | tail -1
hb "tests compile"
TEST_EXTRA_CP="$R/build/m3-backend;$L/skija-shared-0.116.3.jar;$L/types-0.1.1.jar" bash $T/compile_tests.sh $WT $R/build/classes-win32 $R/build/tests $EV | tail -1
export GATE_EXTRA_CP="$L/org.eclipse.swt.svg-3.133.200.m3-superset-20261109.jar;$L/jsvg-2.1.0.jar;$R/build/m3-backend;$L/skija-shared-0.116.3.jar;$L/skija-windows-x64-0.116.3.jar;$L/types-0.1.1.jar"
export GATE_JVM_ARGS="-Dorg.eclipse.swt.internal.enableStrictChecks=true -Dorg.eclipse.swt.internal.gtk.disablePrinting=true"
hb "headless"
bash $T/run_junit_gate.sh headless $JAR $R/build/tests/classes $EV 900 $(cat $R/build/headless-selectors.txt) | tail -1
hb "m3gc"
bash $T/run_junit_gate.sh m3gc $JAR $R/build/tests/classes $EV 900 --select-class $P.m3gc.AllM3GcTests | tail -1
hb "display (AllTests without AllBrowserTests)"
SEL=""; while read c; do SEL="$SEL --select-class $P.$c"; done < $R/build/alltests-direct.txt
bash $T/run_junit_gate.sh display $JAR $R/build/tests/classes $EV 3600 $SEL | tail -1
hb "done"
