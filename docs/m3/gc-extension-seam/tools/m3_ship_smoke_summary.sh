#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Leaf 18 artifact smoke counts per mode (CPU only): driver TSV, -verbose:class sources, extracted natives/jars.
L=H:/m3-scratch/l18; EXPECT=31
for m in stock m3 gpu; do f=$L/out/realapp_$m.tsv; l=$L/logs/run_$m.log
  [ -f "$f" ] || { echo "$m: no TSV"; continue; }
  awk -F'\t' -v id="$m" -v want=$EXPECT 'NR>1 && $3>=0 {n++; c[$5]++; h+=($6>0); d+=($8!="")} END {printf "%s: rows=%d PASS=%d EXCEPTION=%d BLANK=%d HANG=%d not_reached=%d tabs_with_M3CanvasHandler=%d tabs_with_drawn_plane=%d\n", id, n, c["PASS"], c["EXCEPTION"], c["BLANK"], c["HANG"], want-n, h, d}' "$f"
  printf '  loads: skija=%s (from nested jar %s) types=%s render.m3=%s (from swt-m3.jar %s, loader org.eclipse.swt.m3 %s) gpu.m3=%s M3Fragment=%s; fragment log: %s\n' \
    "$(grep -c 'class,load.* io\.github\.humbleui\.skija\.' "$l")" "$(grep -c 'class,load.* io\.github\.humbleui\.skija\..*source: .*swt-m3-[0-9a-f]*/skija-shared.jar' "$l")" \
    "$(grep -c 'class,load.* io\.github\.humbleui\.types\.' "$l")" "$(grep -c 'class,load.* org\.eclipse\.swt\.internal\.render\.m3\.' "$l")" \
    "$(grep -c 'class,load.* org\.eclipse\.swt\.internal\.render\.m3\..*source: .*swt-m3.jar' "$l")" \
    "$(grep -c 'class,load.* org\.eclipse\.swt\.internal\.render\.m3\..*org.eclipse.swt.m3' "$l")" \
    "$(grep -c 'class,load.* org\.eclipse\.swt\.internal\.gpu\.m3\.' "$l")" "$(grep -c 'class,load.* org\.eclipse\.swt\.internal\.M3Fragment ' "$l")" \
    "$(grep -m1 -o 'M3 fragment: .*' "$l" | cut -c1-160)"
done
