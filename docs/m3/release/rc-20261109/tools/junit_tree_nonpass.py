#!/usr/bin/env python3
"""Extract non-passing nodes from a JUnit console launcher --details=tree log.
Usage: junit_tree_nonpass.py <log> <out.tsv>; marks: FAIL (x), ABORT (assumption), SKIP."""
import collections, re, sys
MARK = {"✘": "FAIL", "■": "ABORT", "↷": "SKIP"}
pat = re.compile(r"^((?:[│ ]  |   )*)[├└]─ (.*?) ([✔✘■↷])(?: (.*))?$")
stack, out = [], []
for line in open(sys.argv[1], encoding="utf-8", errors="replace").read().split("\n"):
    m = pat.match(line)
    if not m:
        continue
    depth = len(m.group(1)) // 3
    name, mark, msg = m.group(2), m.group(3), (m.group(4) or "")
    stack = stack[:depth] + [name]
    if mark in MARK:
        out.append((MARK[mark], "leaf" if "(" in name else "container", "/".join(stack[:-1][-2:]), name, msg[:220]))
with open(sys.argv[2], "w", encoding="utf-8", newline="\n") as fh:
    fh.write("mark\tnode\tparent\ttest\tmessage\n")
    fh.writelines("\t".join(o) + "\n" for o in out)
print(dict(collections.Counter((o[0], o[1]) for o in out)))
