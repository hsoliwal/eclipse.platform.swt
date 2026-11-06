#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Real-app smoke of leaf 12 (one Skija, LEAF12_ONE_SKIJA.md) for one Skija pin: ControlExample + CustomControlExample end
# to end through RealAppDriver (H:/m3-scratch/realapp/driver, census L11) in stock, m3 and gpu, one JVM at a time on the
# hidden desktop (C:/m3-superset/tools/ui_lock.sh + hidden_java.sh), heartbeat every 5 minutes. Adapted from
# H:/m3-scratch/fidelity/{build.sh,run_mode.sh,smoke.sh} and H:/m3-scratch/realapp/summarize.sh; the Skija jars come from
# SKIJA_PIN (m3_skija_pin.sh: the fragment pom of <worktree>, or M3_SKIJA_VERSION + M3_SKIJA_LIB), never a fixed name.
# Natives: M3_NATIVES (default the oid-verified H:/m3-scratch/fidelity/natives; verify with m3gc_natives.sh --verify).
# Usage: m3_l12_realapp.sh <worktree> <out-root> [modes...]       (modes default: stock m3 gpu)
#   needs <out-root>/main/classes (compile_m3gc.sh <worktree> <out-root>/main with the same pin); writes <out-root>/{example,
#   driver,out,logs,tmp}; prints per mode rows/PASS/EXCEPTION/BLANK/HANG, the Skija/types/render classes loaded and the
#   extracted skija.dll version; DIFF_<mode>.tsv (H:/m3-scratch/realapp/diffmap.py) against the same build's stock run.
WT=${1:?worktree}; B=${2:?out-root}; shift 2; MODES=${*:-stock m3 gpu}
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
LIB=${M3_SKIJA_LIB:-C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib}
NAT=${M3_NATIVES:-H:/m3-scratch/fidelity/natives}; DRV=${M3_DRIVER:-H:/m3-scratch/realapp/driver}
. "$(dirname "$0")/m3_skija_pin.sh"; m3_skija_pin "$WT" "$LIB" || exit 2
E=$WT/examples/org.eclipse.swt.examples/src; OUT=$B/out
[ -d "$B/main/classes" ] || { echo "no $B/main/classes: run compile_m3gc.sh $WT $B/main first"; exit 2; }
rm -rf "$B/example" "$B/driver"; mkdir -p "$B/example/classes" "$B/driver/classes" "$B/logs" "$OUT" "$B/run"
find "$E/org/eclipse/swt/examples/controlexample" -name '*.java' | sed 's#.*#"&"#' > "$B/example/src"
"$JDK/bin/javac" -J-Xmx1024m --release 21 -encoding UTF-8 -nowarn -proc:none -cp "$B/main/classes;$SKIJA_CP" -d "$B/example/classes" \
  @"$B/example/src" > "$B/example/javac.log" 2>&1
"$JDK/bin/javac" -J-Xmx1024m --release 21 -encoding UTF-8 -nowarn -proc:none -cp "$B/main/classes;$SKIJA_CP;$B/example/classes" \
  -d "$B/driver/classes" "$DRV/org/eclipse/swt/examples/controlexample/RealAppDriver.java" > "$B/driver/javac.log" 2>&1
ne=$(grep -c ': error:' "$B/example/javac.log"); nd=$(grep -c ': error:' "$B/driver/javac.log")
echo "example: sources=$(wc -l < "$B/example/src") errors=${ne:-0}; driver: errors=${nd:-0}"
[ "${ne:-0}" -eq 0 ] && [ "${nd:-0}" -eq 0 ] || exit 1
CP="$B/main/classes;$SKIJA_CP;$B/example/classes;$E;$B/driver/classes"
for MODE in $MODES; do
  case "$MODE" in stock) PROP="" ;; *) PROP="-Dswt.widget.mode=$MODE" ;; esac
  TMPD=$B/tmp/$MODE; rm -rf "$TMPD"; mkdir -p "$TMPD"; LOG=$B/logs/run_$MODE.log; : > "$LOG"
  ( while :; do sleep 300 > /dev/null 2>&1; echo "HEARTBEAT $(date -Iseconds) skija=$SKIJA_VERSION mode=$MODE"; done ) & HB=$!
  echo "START $(date -Iseconds) skija=$SKIJA_VERSION mode=$MODE"
  (cd "$B/run" && bash C:/m3-superset/tools/ui_lock.sh claude-l12 bash C:/m3-superset/tools/hidden_java.sh "${M3_TIMEOUT:-900}" "$LOG" \
    -Xmx1536m -verbose:class -XX:ErrorFile="$B/logs/hs_err_$MODE.log" $PROP -Djava.io.tmpdir="$TMPD" -Drealapp.out="$OUT" \
    -Djava.library.path="$(cygpath -w "$NAT")" -Dswt.library.path="$(cygpath -w "$NAT")" -cp "$CP" org.eclipse.swt.examples.controlexample.RealAppDriver)
  rc=$?; kill $HB 2>/dev/null; echo "exit=$rc" >> "$LOG"
  echo "END $(date -Iseconds) skija=$SKIJA_VERSION mode=$MODE exit=$rc"
done
modes_nostock=$(echo $MODES | tr ' ' '\n' | grep -v '^stock$' | tr '\n' ' ')
[ -d "$OUT/screens/stock" ] && [ -n "$modes_nostock" ] && python -I H:/m3-scratch/realapp/diffmap.py "$OUT" $modes_nostock
for MODE in $MODES; do
  f=$OUT/realapp_$MODE.tsv; l=$B/logs/run_$MODE.log
  [ -f "$f" ] || { echo "$MODE: no TSV (NOT_RUN or crashed; see $l)"; continue; }
  awk -F'\t' -v id="skija=$SKIJA_VERSION $MODE" 'NR>1 && $3>=0 {n++; c[$5]++; h+=($6>0); d+=($8!="")}
    END {printf "%s: rows=%d PASS=%d EXCEPTION=%d BLANK=%d HANG=%d tabs_with_M3CanvasHandler=%d tabs_with_drawn_plane=%d\n", id, n, c["PASS"], c["EXCEPTION"], c["BLANK"], c["HANG"], h, d}' "$f"
  dll=$(find "$B/tmp/$MODE" -name skija.dll -path '*skija_*' 2>/dev/null | head -1)
  printf '  classes loaded: skija=%s types=%s render.m3=%s gpu.m3=%s; skija.dll extracted: %s; %s\n' \
    "$(grep -c 'class,load.* io\.github\.humbleui\.skija\.' "$l")" "$(grep -c 'class,load.* io\.github\.humbleui\.types\.' "$l")" \
    "$(grep -c 'class,load.* org\.eclipse\.swt\.internal\.render\.m3\.' "$l")" "$(grep -c 'class,load.* org\.eclipse\.swt\.internal\.gpu\.m3\.' "$l")" \
    "${dll:+$(basename "$(dirname "$dll")") sha256 $(sha256sum "$dll" | cut -c1-16)}" "$(grep -oE '^exit=[0-9-]+' "$l")"
done
