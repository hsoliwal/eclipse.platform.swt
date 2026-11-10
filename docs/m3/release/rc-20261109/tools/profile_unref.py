#!/usr/bin/env python3
"""Profile unreferenced JUnit classes: test count, main(), abstract, platform guards, Display use, package dir.
Usage: profile_unref.py <JUnit Tests root> <unref.txt> <out.tsv>"""
import os, re, sys
root, lst, out = sys.argv[1:4]
index = {}
for d, _, fs in os.walk(root):
    for f in fs:
        if f.endswith(".java"):
            index.setdefault(f[:-5], os.path.join(d, f))
rows = []
for c in [l.strip() for l in open(lst, encoding="utf-8") if l.strip()]:
    p = index.get(c)
    s = open(p, encoding="utf-8", errors="replace").read() if p else ""
    rows.append((c, os.path.relpath(os.path.dirname(p), root).replace("\\", "/") if p else "?",
                 len(re.findall(r"^\s*@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b", s, re.M)),
                 int("static void main" in s), int(bool(re.search(r"^\s*(public\s+)?abstract\s+class", s, re.M))),
                 int("@Suite" in s or "@SelectClasses" in s),
                 len(re.findall(r"assume(True|False|That)", s)),
                 int(bool(re.search(r'isGTK|"gtk"|SWT_GTK4|isCocoa|isLinux|"cocoa"', s))),
                 int(bool(re.search(r"new Display|Display\.getDefault|new Shell", s))),
                 int("io.github.humbleui" in s), int(bool(re.search(r"@Disabled|@Tag\(", s)))))
with open(out, "w", encoding="utf-8", newline="\n") as fh:
    fh.write("class\tdir\ttests\tmain\tabstract\tsuite\tassumes\tplatform_ref\tdisplay\tskija\tdisabled_or_tag\n")
    fh.writelines("\t".join(map(str, r)) + "\n" for r in rows)
