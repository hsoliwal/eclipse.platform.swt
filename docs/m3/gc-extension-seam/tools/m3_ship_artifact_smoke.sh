#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Leaf 18 ARTIFACT_SMOKE (scratch): ControlExample + CustomControlExample through the real-app driver
# (H:/m3-scratch/realapp/driver, reused) from the built jars only: -cp swt.jar;swt-m3.jar;<example classes>;<example
# resources>;<driver>. No Skija jar, no M3 classes dir, no java.library.path: natives come out of swt.jar into a fresh
# per-run swt.library.path, nested Skija jars out of swt-m3.jar into the per-run java.io.tmpdir.
# Usage: run_smoke.sh <stock|m3|gpu> [timeout-seconds]
MODE=${1:?mode}; TIMEOUT=${2:-900}; L=${M3_SHIP_SCRATCH:-H:/m3-scratch/l18}; R=${M3_REALAPP:-H:/m3-scratch/realapp}
g=$(df -k /h | awk 'NR==2{print int($4/1048576)}'); [ "${g:-0}" -ge 1 ] || { echo "DISK_GATE H: ${g}GB < 1"; exit 3; }
D=$L/build/dist; EX=$R/example/classes; E=C:/m3-superset/swt-realapp/examples/org.eclipse.swt.examples/src
CP="$D/swt.jar;$D/swt-m3.jar;$EX;$E;$R/driver/classes"
case "$MODE" in stock) PROP="" ;; *) PROP="-Dswt.widget.mode=$MODE" ;; esac
TMPD=$L/tmp/$MODE; LIBD=$L/swtlib/$MODE; OUT=$L/out
rm -rf "$TMPD" "$LIBD"; mkdir -p "$TMPD" "$LIBD" "$L/logs" "$OUT" "$L/run"
LOG=$L/logs/run_$MODE.log; : > "$LOG"
cd "$L/run" || exit 9
( while :; do sleep 300
    if grep -qs claude-l18 C:/m3-superset/ui-test.lock.*/owner.txt; then s=running; else s=waiting-for-ui-lock; fi
    echo "HEARTBEAT $(date -Iseconds) mode=$MODE $s log_lines=$(wc -l < "$LOG")"; done ) &
HB=$!
echo "START $(date -Iseconds) mode=$MODE cp=$CP"
bash C:/m3-superset/tools/ui_lock.sh claude-l18 bash C:/m3-superset/tools/hidden_java.sh "$TIMEOUT" "$LOG" -Xmx1536m -verbose:class \
  -XX:ErrorFile="$L/logs/hs_err_$MODE.log" $PROP -Djava.io.tmpdir="$(cygpath -w "$TMPD")" -Drealapp.out="$OUT" \
  -Dswt.library.path="$(cygpath -w "$LIBD")" -cp "$CP" org.eclipse.swt.examples.controlexample.RealAppDriver
rc=$?
kill $HB 2>/dev/null
echo "exit=$rc" >> "$LOG"
echo "END $(date -Iseconds) mode=$MODE exit=$rc"
echo "swtlib: $(cd "$LIBD" && sha256sum * 2>/dev/null | cut -c1-16,65- | tr '\n' ' ')"
echo "tmpdir: $(find "$TMPD" -type f | sed "s#$TMPD/##" | tr '\n' ' ')"
grep -E '^REALAPP (SUMMARY|HANG|done)' "$LOG"
