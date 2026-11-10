#!/usr/bin/env python3
"""Own-diff presence for PRs merged into non-master bases: merge-base(merge^1, head)..head vs candidate HEAD.
Usage: own_presence.py <ledger-prs.tsv> <merged-prs.tsv>   (run inside the worktree); prints TSV."""
import sys
sys.path.insert(0, __file__.rsplit("/", 1)[0].rsplit("\\", 1)[0])
from ledger import git, Presence
pres = Presence("HEAD")
heads = {f"#{r.split(chr(9))[0]}": r.rstrip("\n").split("\t") for r in open(sys.argv[2], encoding="utf-8")}
for row in open(sys.argv[1], encoding="utf-8"):
    f = row.rstrip("\n").split("\t")
    if len(f) < 4 or f[3] != "NOT_ON_MASTER" or "initiative31/" in f[5]:
        continue
    num, _, merge, head, base, _, title = heads[f[1]]
    p1 = git("rev-parse", merge + "^1")[1]
    mb = git("merge-base", p1, head)[1]
    added, present, top = pres.measure(mb, head)
    print(f"{f[1]}\t{base}\t{git('rev-list', '--count', mb + '..' + head)[1]} commits\t{present}/{added}\t{top}\t{title[:80]}")
