#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# Per-frame allocation profile of the drawn modes (optimization pass, leaf "frame allocations"): RenderModeBenchmark
# runs one operation for REPS repetitions per widget x mode under JFR with every TLAB/outside-TLAB allocation event and
# its stack, then the allocations whose stack passes through the drawn plane's frame (DrawnPlane.draw) are summed by
# allocated class and by the first frame of the render path. Run it under the desktop lock:
#   bash C:/m3-superset/tools/ui_lock.sh <agent> bash alloc_profile.sh <swt-jar> <tests-dir> <out-dir>
# Environment: MODES (default "custom m3 gpu"), WIDGETS (default "Tree Table List"), OP (default paint), REPS (default
# 400), EXTRA_CP (the M3 backend jar and the Skija library jars), JFC (the JFR settings, made with jfr configure).
JAR=$1; TESTS=$2; OUT=$3; mkdir -p "$OUT"
MODES=${MODES:-custom m3 gpu}; WIDGETS=${WIDGETS:-Tree Table List}; OP=${OP:-paint}; REPS=${REPS:-400}
JDK="C:/Program Files/Java/jdk-21/bin"
for widget in $WIDGETS; do
  for mode in $MODES; do
    rec="$OUT/$widget-$mode-$OP.jfr"; log="$OUT/$widget-$mode-$OP.log"
    bash C:/m3-superset/tools/hidden_java.sh 300 "$log" -Xmx512m -Dbench.repetitions=$REPS -Dswt.widget.mode=$mode \
      "-XX:StartFlightRecording=filename=$(cygpath -w "$rec"),settings=$(cygpath -w "$JFC")" \
      -cp "$(cygpath -w "$TESTS");$(cygpath -w "$JAR")${EXTRA_CP:+;$EXTRA_CP}" org.eclipse.swt.tests.junit.RenderModeBenchmark "$widget" "$OP" "$(cygpath -w "$OUT/bench.tsv")"
    "$JDK/jfr" print --events jdk.ObjectAllocationInNewTLAB,jdk.ObjectAllocationOutsideTLAB --stack-depth 64 "$rec" > "$OUT/$widget-$mode-$OP.txt"
    python -I - "$OUT/$widget-$mode-$OP.txt" "$widget" "$mode" "$REPS" <<'EOF' | tee "$OUT/$widget-$mode-$OP.top.tsv"
import re, sys, collections
path, widget, mode, reps = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
text = open(path, encoding='utf-8', errors='replace').read()
by_class, by_site, total = collections.Counter(), collections.Counter(), 0
for event in text.split('jdk.ObjectAllocation')[1:]:
    if 'DrawnPlane.draw' not in event:
        continue
    m = re.search(r'objectClass = ([^\s(]+)', event)
    # an in-new-TLAB event stands for the TLAB it opened (the sampling weight), an outside-TLAB event for itself
    w = re.search(r'tlabSize = ([\d.]+) (bytes|kB|MB)', event) or re.search(r'allocationSize = ([\d.]+) (bytes|kB|MB)', event)
    if not m or not w:
        continue
    n = float(w.group(1)) * {'bytes': 1, 'kB': 1024, 'MB': 1048576}[w.group(2)]
    frames = re.findall(r'^\s+([\w.$<>]+)\(', event, re.M)
    site = next((f for f in frames if not f.startswith(('java.', 'jdk.', 'sun.'))), frames[0] if frames else '?')
    by_class[m.group(1)] += n; by_site[site] += n; total += n
print('%s\t%s\tsampled bytes in frames\t%d\tper repetition\t%.0f' % (widget, mode, total, total / reps))
for k, v in by_class.most_common(8): print('class\t%s\t%d' % (k, v))
for k, v in by_site.most_common(10): print('site\t%s\t%d' % (k, v))
EOF
  done
done
