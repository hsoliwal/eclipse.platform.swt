#!/usr/bin/env python3
"""Render the PR sections of RELEASE_NOTES.md from the candidate LEDGER.tsv (CPU only, no prose generation).

Usage: release_notes.py <LEDGER.tsv> <STACKED_NOT_ON_MASTER.tsv> <out.md>
"""
import sys

INCLUDED = ("MERGED", "MERGED_NOOP", "CONTAINED", "RE-LANDED", "CARRIED")


def main():
    ledger, stacked, out = sys.argv[1:4]
    rows = [l.rstrip("\n").split("\t") for l in open(ledger, encoding="utf-8")][1:]
    prs = [r for r in rows if r[0].startswith("pr:")]
    inc = [r for r in prs if r[3] in INCLUDED]
    exc = [r for r in prs if r[3] not in INCLUDED]
    st = [l.rstrip("\n").split("\t") for l in open(stacked, encoding="utf-8")]
    head, st = st[0], st[1:]
    col = {name: i for i, name in enumerate(head)}

    def title(r):
        notes = r[5].split("; ", 1)
        t = notes[1] if len(notes) > 1 else notes[0]
        return t.split("; ")[0].replace("|", "/")

    lines = [f"## Included pull requests since 2026-10-13 ({len(inc)})", "",
             "MERGED: the merge commit is on the candidate and changes the tree. MERGED_NOOP: the merge is on the candidate "
             "and its content was already there. CONTAINED: the PR head is an ancestor of the candidate. RE-LANDED: the PR "
             "was merged into another PR's branch, never reached master, and was folded into this candidate. CARRIED: its "
             "content arrives through #325. Source: `LEDGER.tsv` (also in the jar as `META-INF/m3/LEDGER.tsv`).", "",
             "| PR | merged | class | effect | title |", "| --- | --- | --- | --- | --- |"]
    lines += [f"| {r[1]} | {r[5][:10]} | {r[3]} | {r[4]} | {title(r)} |" for r in inc]
    lines += ["", f"## Merged pull requests not applicable to master ({len(exc)})", "",
              "| PR | outcome | proof |", "| --- | --- | --- |"]
    by_pr = {s[col["pr"]]: s for s in st}
    for r in exc:
        s = by_pr.get(r[1])
        lines.append(f"| {r[1]} | {s[col['outcome']] if s else r[3]} | {(s[col['proof']] if s else r[5]).replace('|', '/')} |")
    lines += ["", "Re-landed and carried PRs, with their own-line presence before and after, are in `STACKED_NOT_ON_MASTER.tsv`."]
    open(out, "w", encoding="utf-8", newline="\n").write("\n".join(lines) + "\n")
    print(f"included={len(inc)} not_applicable={len(exc)}")


if __name__ == "__main__":
    main()
