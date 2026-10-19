#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# The vector glyph leg (SV3): Test_org_eclipse_swt_widgets_Tree_DrawnGlyphZoom in custom, m3 and gpu at 100, 125, 150 and
# 200 % (-Dswt.autoScale) under the swing and flatdark look and feels, then the captures compared across the modes:
# gpu against m3 pixel for pixel (both the M3 renderer), custom against m3 by the share of equal pixels and the largest
# channel difference (GDI text and blending). Needs the SVG fragment jar and JSVG (SVG_CP). Run under ui_lock.sh.
# Environment: STAMP, TESTSET; OUT (default S:/m3-scratch/render-modes); SVG_CP; ZOOMS, LAFS, MODES.
S=${OUT:-S:/m3-scratch/render-modes}; RUN=$S/glyph-zoom; mkdir -p $RUN
M2=$(cygpath -m ~/.m2/repository); SK=C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib
CS=$M2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar
SVG=${SVG_CP:-$S/svg/org.eclipse.swt.svg.jar;$S/jsvg-2.1.0.jar}
CP="$S/tests-${TESTSET:?};$S/dist/org.eclipse.swt.win32.win32.x86_64-m3-${STAMP:?}.jar;$S/dist/swt-render-m3-$STAMP.jar;$SK/skija-shared-0.116.3.jar;$SK/skija-windows-x64-0.116.3.jar;$SK/types-0.1.1.jar;$SVG"
: > $RUN/results.txt
for zoom in ${ZOOMS:-100 125 150 200}; do
  for laf in ${LAFS:-swing flatdark}; do
    for m in ${MODES:-custom m3 gpu}; do
      log=$RUN/$zoom-$laf-$m.log
      bash C:/m3-superset/tools/hidden_java.sh 300 $log -Xmx512m -Dswt.autoScale=$zoom -Dswt.widget.theme=$laf -Dswt.widget.mode=$m -Dswt.viewport.evidence=$RUN \
        -jar $CS execute -cp "$CP" --select-class org.eclipse.swt.tests.junit.Test_org_eclipse_swt_widgets_Tree_DrawnGlyphZoom --disable-banner --details=tree
      r="$zoom $laf $m: $(sed 's/\x1b\[[0-9;]*m//g' $log | grep -aE 'tests (successful|failed|aborted)' | tr -s ' ' | tr '\n' ' ')"
      echo "$r" | tee -a $RUN/results.txt; sed 's/\x1b\[[0-9;]*m//g' $log | grep -aE "✘" | head -2 | tee -a $RUN/results.txt
    done
  done
done
python -I - "$RUN" ${ZOOMS:-100 125 150 200} <<'EOF' | tee -a $RUN/results.txt
import os, sys
from PIL import Image
run, zooms = sys.argv[1], sys.argv[2:]
print('zoom\tlaf\tgpu=m3\tcustom=m3 equal\tcustom-m3 max channel diff')
for zoom in zooms:
    for laf in ('swing', 'flatdark'):
        files = {m: os.path.join(run, 'glyphzoom-%s-%s-%s.png' % (zoom, laf, m)) for m in ('custom', 'm3', 'gpu')}
        if not all(os.path.exists(f) for f in files.values()):
            print('%s\t%s\tmissing capture' % (zoom, laf)); continue
        px = {m: list(Image.open(f).convert('RGB').getdata()) for m, f in files.items()}
        gpu = 'EQUAL' if px['gpu'] == px['m3'] else 'DIFFERENT (%d px)' % sum(1 for a, b in zip(px['gpu'], px['m3']) if a != b)
        equal = sum(1 for a, b in zip(px['custom'], px['m3']) if a == b)
        diff = max(max(abs(a[i] - b[i]) for i in range(3)) for a, b in zip(px['custom'], px['m3']))
        print('%s\t%s\t%s\t%.2f%%\t%d' % (zoom, laf, gpu, 100.0 * equal / len(px['m3']), diff))
EOF
