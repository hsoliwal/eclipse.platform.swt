# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others
"""m3_graphics_prose_scan.py <worktree> <jdk.m3.graphics jar> <file>...   -> TSV on stdout

M3_GRAPHICS_PROSE scan v1 (review aid and after-scan gate of M3_GRAPHICS_PROSE_EDITS.tsv): lists, per comment or
string line of the given files, the retired donor names CONSUMER_RENAME v1 left behind (it masked comments and
strings): the word Skija in any case, and in comments the simple name T of every jdk.m3.graphics type M3T of the jar
when it stands unprefixed. Columns: file, line, kind (comment|string):(skija|type), token, text. Edits nothing;
a reviewer decides per line (SWT's own Canvas, Region or Paint GC are not donor names)."""
import re
import sys
import zipfile

REGIONS = re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\\n])*"', re.S)
SKIJA = re.compile(r'skija', re.I)
M3_CLASS = re.compile(r'^jdk/m3/graphics/M3([A-Za-z0-9]+)\.class$')
TEXT_WIDTH = 150


def donor_names(jar):
    with zipfile.ZipFile(jar) as z:
        names = sorted({m.group(1) for m in map(M3_CLASS.match, z.namelist()) if m}, key=len, reverse=True)
    return re.compile(r'(?<![\w$])(' + '|'.join(names) + r')(?![\w$])')


def main():
    root, jar, files = sys.argv[1], sys.argv[2], sys.argv[3:]
    tok = donor_names(jar)
    for rel in files:
        with open(f'{root}/{rel}', encoding='utf-8') as f:
            s = f.read()
        for m in REGIONS.finditer(s):
            kind = 'string' if m.group(0).startswith('"') else 'comment'
            base = s.count('\n', 0, m.start()) + 1
            for i, line in enumerate(m.group(0).split('\n')):
                hits = [('skija', h.group(0)) for h in SKIJA.finditer(line)]
                hits += [('type', h.group(1)) for h in tok.finditer(line)] if kind == 'comment' else []
                for k, t in hits:
                    print(f'{rel.rsplit("/", 1)[-1]}\t{base + i}\t{kind}:{k}\t{t}\t{line.strip()[:TEXT_WIDTH]}')


if __name__ == '__main__':
    main()
