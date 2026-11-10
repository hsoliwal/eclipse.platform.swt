#!/usr/bin/env python3
"""LEDGER.tsv for a release candidate: every PR merged since a date, classified against the candidate HEAD.

Usage: ledger.py <merged-prs.tsv> <candidate-ref> <out.tsv>   (run inside the worktree)
merged-prs.tsv columns: number, mergedAt, mergeCommit, headOid, baseRef, headRef, title.
Classification (CPU only, git plumbing):
  MERGED            merge commit is an ancestor of the candidate and changes the first-parent tree
  MERGED_NOOP       merge commit on candidate, tree == first parent; PR head is also an ancestor (content already there)
  ANCESTRY_ONLY     merge commit on candidate, tree == first parent, PR head NOT otherwise contained -> presence measured
  CONTAINED         merge commit not on candidate, PR head is an ancestor
  NOT_ON_MASTER     neither; base branch recorded; added-line presence measured unless base is initiative31/*
presence = share of the PR's own added lines (>= 25 chars, stripped) found in the candidate at the same path or basename.
"""
import collections
import subprocess
import sys


def git(*args, check=False):
    r = subprocess.run(("git",) + args, capture_output=True)
    return r.returncode, r.stdout.decode("utf-8", "replace").strip()


def ancestor(a, b):
    return git("merge-base", "--is-ancestor", a, b)[0] == 0


def tree(c):
    return git("rev-parse", c + "^{tree}")[1]


def shortstat(a, b):
    out = git("diff", "--shortstat", a, b)[1]
    import re
    nums = {k: int(m.group(1)) if (m := re.search(rf"(\d+) {k}", out)) else 0 for k in ("file", "insertion", "deletion")}
    return f"{nums['file']} files +{nums['insertion']}/-{nums['deletion']}" if out else "0 files"


class Presence:
    def __init__(self, ref):
        self.ref = ref
        self.cache = {}
        paths = git("ls-tree", "-r", "--name-only", ref)[1].split("\n")
        self.by_base = collections.defaultdict(list)
        for p in paths:
            self.by_base[p.rsplit("/", 1)[-1]].append(p)

    def lines(self, path):
        if path not in self.cache:
            self.cache[path] = {l.strip() for l in git("show", f"{self.ref}:{path}")[1].replace("\r\n", "\n").split("\n")}
        return self.cache[path]

    def measure(self, base, tip):
        diff = git("diff", "--no-renames", "-U0", base, tip)[1].split("\n")
        path, added, present, missing = None, 0, 0, collections.Counter()
        for line in diff:
            if line.startswith("+++ "):
                path = line[6:].rstrip("	") if line.startswith("+++ b/") else None
            elif line.startswith("+") and path and not line.startswith("+++"):
                text = line[1:].strip()
                if len(text) < 25:
                    continue
                added += 1
                cands = [path] + [p for p in self.by_base.get(path.rsplit("/", 1)[-1], []) if p != path]
                if any(text in self.lines(p) for p in cands[:8]):
                    present += 1
                else:
                    missing[path] += 1
        top = ";".join(f"{p}({n})" for p, n in missing.most_common(3))
        return added, present, top


def main():
    rows, ref, out = sys.argv[1], sys.argv[2], sys.argv[3]
    pres = Presence(ref)
    recs = []
    for raw in open(rows, encoding="utf-8"):
        num, merged_at, merge, head, base, headref, title = raw.rstrip("\n").split("\t")
        parents = git("rev-list", "--parents", "-n1", merge)[1].split()[1:] if merge != "-" else []
        on = merge != "-" and ancestor(merge, ref)
        head_on = ancestor(head, ref)
        measure = None
        if on:
            p1 = parents[0] if parents else merge + "^"
            if tree(merge) != tree(p1):
                action, effect = "MERGED", shortstat(p1, merge)
            elif head_on:
                action, effect = "MERGED_NOOP", "content already on first parent"
            else:
                action, effect = "ANCESTRY_ONLY", "merge tree == first parent"
                measure = (git("merge-base", p1, head)[1], head)
        elif head_on:
            action, effect = "CONTAINED", "PR head is an ancestor of the candidate"
        else:
            action, effect = "NOT_ON_MASTER", f"merged into {base}"
            if not base.startswith("initiative31/"):
                measure = (git("merge-base", head, ref)[1], head)
        note = ""
        if measure and measure[0]:
            added, present, top = pres.measure(*measure)
            pct = 100.0 * present / added if added else 100.0
            note = f"added-line presence {present}/{added} ({pct:.1f}%)" + (f"; missing: {top}" if top else "")
        recs.append(f"pr:{headref}\t#{num}\t{(merge if merge != '-' else head)[:12]}\t{action}\t{effect}\t{merged_at} base={base}; {title[:90]}" + (f"; {note}" if note else ""))
        print(recs[-1].split("\t")[1], action, note, file=sys.stderr, flush=True)
    with open(out, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("source\tpr\tsha\taction\teffect\tnotes\n")
        fh.write("\n".join(recs) + "\n")


if __name__ == "__main__":
    main()
