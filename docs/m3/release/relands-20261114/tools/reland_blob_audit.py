# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others
"""reland_blob_audit.py <git_dir> <base> <head> <master> <out.tsv> : is a merged PR's change on master? (CPU, git blobs only)

For every path the PR changed (base..head, no rename detection) compare three blobs, base / PR head / master:
  PRESENT          master blob == PR head blob
  LOST             master blob == base blob (or the path the PR added is absent): the PR's change is not on master
  DELETED_OK       the PR deleted the path and master has no such path
  EVOLVED          master differs from both; the row then counts the PR's own lines:
                   added_total / added_on_master  = non-blank lines the PR added (base->head '+') / of those, present in master's file
                   removed_total / removed_back   = non-blank lines the PR removed / of those, present in master's file again
A PR is NOT_NEEDED when no path is LOST and every EVOLVED path carries all of its added lines.
Writes path, class, base, head, master (short blob ids), added_total, added_on_master, removed_total, removed_back."""
import difflib
import subprocess
import sys


def git(git_dir, *args, text=True):
    r = subprocess.run(["git", "-C", git_dir, *args], capture_output=True)
    return r.stdout.decode("utf-8", errors="replace") if text else r.stdout


def blob(git_dir, rev, path):
    out = git(git_dir, "ls-tree", rev, "--", path).strip()
    return out.split()[2] if out else ""


def lines(git_dir, oid):
    return git(git_dir, "cat-file", "-p", oid).splitlines() if oid else []


def main():
    git_dir, base, head, master, out = sys.argv[1:6]
    rows, counts = [], {}
    for entry in git(git_dir, "diff", "--name-status", "--no-renames", base, head).splitlines():
        status, path = entry.split("\t", 1)
        b, h, m = blob(git_dir, base, path), blob(git_dir, head, path), blob(git_dir, master, path)
        at = am = rt = rb = 0
        if m == h:
            cls = "DELETED_OK" if not h else "PRESENT"
        elif m == b:
            cls = "LOST"
        else:
            cls = "EVOLVED"
            bl, hl = lines(git_dir, b), lines(git_dir, h)
            on_master = {l.strip() for l in lines(git_dir, m)}
            for l in difflib.unified_diff(bl, hl, lineterm="", n=0):
                if l.startswith(("+++", "---", "@@")) or not l[1:].strip():
                    continue
                if l[0] == "+":
                    at += 1
                    am += l[1:].strip() in on_master
                elif l[0] == "-":
                    rt += 1
                    rb += l[1:].strip() in on_master
        counts[cls] = counts.get(cls, 0) + 1
        rows.append((path, cls, b[:10] or "-", h[:10] or "-", m[:10] or "-", str(at), str(am), str(rt), str(rb)))
    with open(out, "w", encoding="utf-8", newline="\n") as w:
        w.write("path\tclass\tbase\thead\tmaster\tadded_total\tadded_on_master\tremoved_total\tremoved_back\n")
        w.writelines("\t".join(r) + "\n" for r in rows)
    partial = sum(1 for r in rows if r[1] == "EVOLVED" and r[5] != r[6])
    print(f"files={len(rows)} " + " ".join(f"{k}={v}" for k, v in sorted(counts.items())) + f" evolved_missing_lines={partial}")


main()
