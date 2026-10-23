#!/usr/bin/env python
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0
"""REQUIREMENTS.md from REQUIREMENTS_ALL.tsv: one section per track, the carrier (PR or branch) that now holds the
work, then every requirement row in time order with its origin (session id or repo#pr). Deterministic.
Usage: render_requirements.py <REQUIREMENTS_ALL.tsv> <out.md>"""
import sys, collections
src, out = sys.argv[1], sys.argv[2]
carriers = {
 'VIEWPORT-SWT': 'SWT #250 (viewport integration, gates green), SWT #251 (render modes), Nebula #273; the owner merged 78 SWT and 71 Nebula PRs in the week before',
 'PLATFORM-UI': 'platform.ui #45 (JFace superset: viewport, Problems view, #27 #35 #37), #47 (anchors), #44 (Java 21); platform #18 (views); PDE #5',
 'M3JDK': 'M3jdk21 #1861 (superset: 38 merged, 120 conflicting), the 9 Java-21 PRs on the Eclipse forks, the JBS scaffold branch',
 'RECIPES-MOP': 'com.synexia crates codex/m3-eclipse-jdk21-compat-20261020 and codex/m3-eclipse-enhance-marks-20261020, superset codex/superset-20261023 (72 merged, 174 conflicting), MOP text M3jdk21 PR #1592',
 'PROCESS': 'done in-session: commits, pushes, scaffolds, forward merges, the consolidation docs',
 'OTHER': 'not mapped mechanically; the final pass reads the row',
}
order = ['VIEWPORT-SWT', 'PLATFORM-UI', 'M3JDK', 'RECIPES-MOP', 'PROCESS', 'OTHER']
rows = collections.defaultdict(list); counts = collections.Counter()
for line in list(open(src, encoding='utf-8'))[1:]:
    ts, source, origin, track, tracks, text = line.rstrip('\n').split('\t', 5)
    rows[track].append((ts, source, origin, text)); counts[source] += 1
with open(out, 'w', encoding='utf-8', newline='\n') as f:
    f.write('# Requirements of the M3 programme, recovered from the session logs and the PR history\n\n')
    f.write('User, 2026-10-23: "you have all the requirements in your session logs"; "the PR history and comments speak of\n'
            'requirements". Generated from `REQUIREMENTS_ALL.tsv` by `render_requirements.py`: every unique user directive\n'
            'from the Claude and Codex session logs, and every requirement sentence of the PR bodies and comments of the\n'
            'programme forks, by track and in time order, with the PR or branch that carries the work now.\n\n')
    f.write('| Source | Rows |\n| --- | --- |\n' + ''.join(f'| {k} | {v} |\n' for k, v in sorted(counts.items())) + '\n')
    for t in order:
        if not rows.get(t): continue
        f.write(f'## {t} ({len(rows[t])} rows)\n\nCarrier now: {carriers[t]}\n\n')
        for ts, source, origin, text in sorted(rows[t]):
            f.write(f'- {ts} `{source}` {origin}: {text}\n')
        f.write('\n')
print(out, sum(len(v) for v in rows.values()), 'rows')
