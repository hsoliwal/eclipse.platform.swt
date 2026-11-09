#!/usr/bin/env python3
"""Render the PR sections of RELEASE_NOTES.md from the candidate LEDGER.tsv (CPU only, no prose generation).

Usage: release_notes.py <LEDGER.tsv> <STACKED_NOT_ON_MASTER.tsv> <out.md>
"""
import sys

INCLUDED = ("MERGED", "MERGED_NOOP", "CONTAINED")


def main():
    ledger, stacked, out = sys.argv[1:4]
    rows = [l.rstrip("\n").split("\t") for l in open(ledger, encoding="utf-8")][1:]
    prs = [r for r in rows if r[0].startswith("pr:")]
    inc = [r for r in prs if r[3] in INCLUDED]
    exc = [r for r in prs if r[3] not in INCLUDED]
    own = {l.split("\t")[0]: l.rstrip("\n").split("\t") for l in open(stacked, encoding="utf-8")}

    def title(r):
        notes = r[5].split("; ", 1)
        t = notes[1] if len(notes) > 1 else notes[0]
        return t.split("; added-line presence")[0].replace("|", "/")

    def date(r):
        return r[5][:10]

    lines = [f"## Included pull requests since 2026-10-13 ({len(inc)})", "",
             "MERGED: the merge commit is on the candidate and changes the tree. MERGED_NOOP: the merge is on the candidate "
             "and its content was already there. CONTAINED: the PR head is an ancestor of the candidate. "
             "Source: `LEDGER.tsv` (also in the jar as `META-INF/m3/LEDGER.tsv`).", "",
             "| PR | merged | class | effect | title |", "| --- | --- | --- | --- | --- |"]
    lines += [f"| {r[1]} | {date(r)} | {r[3]} | {r[4]} | {title(r)} |" for r in inc]
    lines += ["", f"## Merged pull requests whose content is not on master ({len(exc)})", "",
              "These were merged into another PR's branch (stacked PRs, or Initiative 31 prototype lines), not into master. "
              "The table measures each PR's own added lines (25 characters or longer) against the candidate.", "",
              "| PR | merged into | own added lines present | title |", "| --- | --- | --- | --- |"]
    for r in exc:
        o = own.get(r[1])
        pres = o[3] if o else "n/a (initiative31 prototype line)"
        base = r[5].split("base=", 1)[1].split(";", 1)[0]
        lines.append(f"| {r[1]} | {base} | {pres} | {title(r)} |")
    open(out, "w", encoding="utf-8", newline="\n").write("\n".join(lines) + "\n")
    print(f"included={len(inc)} not_on_master={len(exc)}")


if __name__ == "__main__":
    main()
