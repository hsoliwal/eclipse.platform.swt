#!/usr/bin/env python
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Candidate name pairs between a reference viewport class (TreeViewport) and another viewport class, found
mechanically (NEC: suggestions with evidence, never decisions): for every member of the other class whose name the
reference lacks, the reference members with the same javadoc first sentence (DOC), the same signature shape (SIG:
return type and parameter types) and a body-token overlap (BODY: Jaccard over identifiers) are listed with the
evidence. Usage: name_pairs.py <reference.java> <other.java> [min-jaccard]  -> TSV on stdout
(other_name, kind, reference_name, evidence, jaccard, other_doc, reference_doc)"""
import re, sys

DECL = re.compile(r'^\t((?:public |protected |private |static |final |synchronized |abstract )*)'
                  r'([A-Za-z_][A-Za-z0-9_<>\[\], .?]*?)\s+([a-zA-Z_][A-Za-z0-9_]*)\s*(\(([^)]*)\))?\s*(=|;|\{|throws)')
IDENT = re.compile(r'[A-Za-z_][A-Za-z0-9_]*')
KEYWORDS = set('abstract boolean break byte case catch char class continue default do double else enum extends final '
               'finally float for if implements import instanceof int interface long native new null package private '
               'protected public return short static super switch synchronized this throw throws transient try void '
               'volatile while true false'.split())


def members(path):
    lines = open(path, encoding='utf-8', errors='replace').read().replace('\r', '').split('\n')
    out = []
    doc = []
    in_doc = False
    i = 0
    while i < len(lines):
        line = lines[i]
        s = line.strip()
        if s.startswith('/**'):
            in_doc = True
            doc = []
        if in_doc:
            t = s.lstrip('/*').strip()
            if t and not t.startswith('@') and not t.startswith('*/'):
                doc.append(t.rstrip('*/').strip())
            if '*/' in s:
                in_doc = False
            i += 1
            continue
        m = DECL.match(line)
        if m and m.group(3) not in KEYWORDS and not line.strip().startswith('return') and not line.strip().startswith('else'):
            name = m.group(3)
            is_method = m.group(4) is not None
            ret = m.group(2).strip()
            params = ''
            if is_method:
                params = ','.join(p.strip().split(' ')[0] for p in m.group(5).split(',') if p.strip())
            sentence = ' '.join(doc).split('. ')[0][:140] if doc else ''
            body = set()
            if is_method and m.group(6) == '{' or (is_method and 'throws' == m.group(6)):
                depth = 0
                j = i
                started = False
                while j < len(lines):
                    for ch in lines[j]:
                        if ch == '{':
                            depth += 1
                            started = True
                        elif ch == '}':
                            depth -= 1
                    for w in IDENT.findall(lines[j]):
                        if w not in KEYWORDS and len(w) > 1:
                            body.add(w)
                    if started and depth == 0:
                        break
                    j += 1
            out.append({'name': name, 'kind': 'method' if is_method else 'field', 'ret': ret, 'params': params,
                        'doc': sentence, 'body': body})
            doc = []
        elif s and not s.startswith('//') and not s.startswith('@'):
            doc = [] if not s.startswith('*') else doc
        i += 1
    return out


def main():
    ref_path, other_path = sys.argv[1], sys.argv[2]
    min_j = float(sys.argv[3]) if len(sys.argv) > 3 else 0.45
    ref = members(ref_path)
    other = members(other_path)
    ref_names = {m['name'] for m in ref}
    print('other_name\tkind\treference_name\tevidence\tjaccard\tother_doc\treference_doc')
    for o in other:
        if o['name'] in ref_names:
            continue
        for r in ref:
            if r['kind'] != o['kind']:
                continue
            ev = []
            if o['doc'] and r['doc'] and o['doc'].lower() == r['doc'].lower():
                ev.append('DOC')
            if o['kind'] == 'method' and o['ret'] == r['ret'] and o['params'] == r['params'] and o['params'] != '':
                ev.append('SIG')
            jac = 0.0
            if o['body'] and r['body']:
                jac = len(o['body'] & r['body']) / float(len(o['body'] | r['body']))
                if jac >= min_j:
                    ev.append('BODY')
            if ev:
                print('\t'.join([o['name'], o['kind'], r['name'], '+'.join(ev), '%.2f' % jac, o['doc'], r['doc']]))


main()
