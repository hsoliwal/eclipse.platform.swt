#!/usr/bin/env bash
# Run one JUnit selection against the superset jar on the invisible desktop (ui_lock.sh slot), fork natives only.
# The fork DLLs are extracted from the jar into a fresh directory passed as -Dswt.library.path, so no stale
# ~/.swt/lib copy can be loaded. -Xcheck:jni is on (same as the #325 Surefire argLine).
# Usage: run_junit_gate.sh <name> <jar> <test-classes> <evidence-dir> <timeout-s> <junit selectors...>
set -u
NAME=$1; JAR=$2; TC=$3; EV=$4; TO=$5; shift 5
CS=$(cygpath -m ~/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar)
RUN=F:/m3-scratch/rc-20261109/runs/$NAME
rm -rf "$RUN"; mkdir -p "$RUN/natives" "$RUN/reports" "$RUN/home" "$EV"
( cd "$RUN/natives" && unzip -q -o "$JAR" '*.dll' )
sha256sum "$RUN"/natives/*.dll | sed "s#$RUN/natives/##" > "$EV/junit-$NAME.natives.sha256"
LOG=$RUN/run.log
start=$(date -Iseconds)
bash C:/m3-superset/tools/ui_lock.sh "rc-20261109-$NAME" bash C:/m3-superset/tools/hidden_java.sh "$TO" "$LOG" \
  -Xmx1536m -Xcheck:jni -Dswt.library.path="$RUN/natives" -Djava.io.tmpdir=F:/m3-scratch/tmp \
  -jar "$CS" execute -cp "$TC;$JAR" "$@" --disable-banner --details=tree --reports-dir="$RUN/reports"
rc=$?
end=$(date -Iseconds)
sed 's/\x1b\[[0-9;]*m//g' "$LOG" > "$EV/junit-$NAME.log"
for f in "$RUN"/reports/TEST-*.xml; do [ -f "$f" ] && b=$(basename "$f" .xml) && cp "$f" "$EV/$b-$NAME.xml"; done
grep -E '^\[ +[0-9]+ (tests|containers) [a-z]+ +\]' "$EV/junit-$NAME.log" | sed -E 's/^\[ +([0-9]+) ([a-z]+) ([a-z]+) +\]/\2_\3\t\1/' > "$EV/junit-$NAME.counts.tsv"
jni=$(grep -c -E 'WARNING in native method|FATAL ERROR in native method|JNI warning' "$EV/junit-$NAME.log")
printf 'name\tstart\tend\trc\tjni_check_diagnostics\tselectors\n%s\t%s\t%s\t%s\t%s\t%s\n' "$NAME" "$start" "$end" "$rc" "$jni" "$*" > "$EV/junit-$NAME.summary.tsv"
cat "$EV/junit-$NAME.counts.tsv"; echo "rc=$rc jni_diagnostics=$jni"
