#!/usr/bin/env python3
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Leaf 18, API_FILTERS v1: merge the API-tools filters of the sealed GC / FontMetrics classes from the I31 donor
(swt-initiative31/skija-canvas, EPL-2.0) into binaries/*/.settings/.api_filters.

Text-level, format-preserving: only <resource> blocks whose path ends in graphics/GC.java or graphics/FontMetrics.java
are taken from the donor files; a filter (id + message arguments) already present is kept, a missing one is appended
to our resource, a missing resource is inserted at its sorted position (API tools orders resources by path bytes).
A fragment whose donor file has no such resource takes the resource of the template fragment of the same windowing
system (--template ws=fragment), recorded as ADAPT. Writes a receipt TSV; a second run changes nothing (fixed point).

Usage: m3_api_filters_merge.py <binaries-dir> <receipt.tsv> <donor-dir>[=label] ... [--template gtk=org.eclipse.swt.gtk.linux.x86_64]
Each donor dir holds <fragment>.api_filters files fetched at one donor commit.
"""
import os
import re
import sys

RESOURCE = re.compile(r'(    <resource path="([^"]*)" type="([^"]*)">\n(.*?)    </resource>\n)', re.S)
FILTER = re.compile(r'(        <filter (?:comment="[^"]*" )?id="(\d+)">\n(.*?)        </filter>\n)', re.S)
ARG = re.compile(r'<message_argument value="([^"]*)"/>')
SEALED = re.compile(r'/graphics/(GC|FontMetrics)\.java$')


def read(path):
    with open(path, encoding='utf-8', newline='') as f:
        return f.read().replace('\r\n', '\n')


def resources(text):
    return [(m.group(2), m.group(3), m.group(1), m.group(4)) for m in RESOURCE.finditer(text)]


def filters(body):
    return [(m.group(2), tuple(ARG.findall(m.group(3))), m.group(1)) for m in FILTER.finditer(body)]


def donor_resources(donors, fragment):
    """(path, type) -> list of (filter key, filter text, donor label), donor order, deduplicated by key."""
    out = {}
    for d, label in donors:
        f = os.path.join(d, fragment + '.api_filters')
        if not os.path.isfile(f):
            continue
        for path, typ, _, body in resources(read(f)):
            if not SEALED.search(path):
                continue
            seen = out.setdefault((path, typ), [])
            for fid, args, text in filters(body):
                if all((fid, args) != k for k, _, _ in seen):
                    seen.append(((fid, args), text, label))
    return out


def merge(text, wanted, fragment, receipt, how):
    for (path, typ), items in sorted(wanted.items()):
        ours = {(p, t): (block, body) for p, t, block, body in resources(text)}
        if (path, typ) in ours:
            block, body = ours[(path, typ)]
            have = {(fid, args) for fid, args, _ in filters(body)}
            add = [(k, t, lab) for k, t, lab in items if k not in have]
            for k, _, lab in items:
                receipt.append((fragment, path, k[0], '|'.join(k[1]), lab, how, 'PRESENT' if k in have else 'ADDED'))
            if add:
                text = text.replace(block, block[:-len('    </resource>\n')] + ''.join(t for _, t, _ in add) + '    </resource>\n', 1)
            continue
        new = '    <resource path="%s" type="%s">\n%s    </resource>\n' % (path, typ, ''.join(t for _, t, _ in items))
        for k, _, lab in items:
            receipt.append((fragment, path, k[0], '|'.join(k[1]), lab, how, 'ADDED'))
        after = [block for p, t, block, _ in resources(text) if p.encode() > path.encode()]
        text = text.replace(after[0], new + after[0], 1) if after else text.replace('</component>', new + '</component>', 1)
    return text


def main(argv):
    templates = dict(a.split('=', 1) for a in (x[len('--template='):] for x in argv if x.startswith('--template=')))
    args = [x for x in argv if not x.startswith('--template=')]
    bins, receipt_path, donors = args[0], args[1], [(d.split('=')[0], d.split('=')[-1] if '=' in d else os.path.basename(d)) for d in args[2:]]
    receipt, changed = [], 0
    for fragment in sorted(os.listdir(bins)):
        target = os.path.join(bins, fragment, '.settings', '.api_filters')
        if not os.path.isfile(target):
            continue
        wanted, how = donor_resources(donors, fragment), 'DONOR'
        ws = fragment.split('.')[3] if fragment.count('.') >= 3 else ''
        if not wanted and ws in templates:
            wanted, how = donor_resources(donors, templates[ws]), 'ADAPT:' + templates[ws]
        before = read(target)
        after = merge(before, wanted, fragment, receipt, how)
        if after != before:
            changed += 1
            with open(target, 'w', encoding='utf-8', newline='') as f:
                f.write(after)
    with open(receipt_path, 'w', encoding='utf-8', newline='') as f:
        f.write('fragment\tresource\tfilter_id\targuments\tdonor\tsource\taction\n')
        f.writelines('\t'.join(r) + '\n' for r in receipt)
    added = sum(1 for r in receipt if r[-1] == 'ADDED')
    print('api_filters: fragments_changed=%d filters_added=%d filters_present=%d' % (changed, added, len(receipt) - added))


if __name__ == '__main__':
    main(sys.argv[1:])
