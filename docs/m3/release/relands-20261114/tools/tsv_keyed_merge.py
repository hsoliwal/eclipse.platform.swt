# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others
"""tsv_keyed_merge.py <git_dir> <path> : resolve a conflicted TSV whose rows are keyed by their first column (CPU, 3-way).

Reads the three index stages of <path> (1 = base, 2 = ours, 3 = theirs) and merges row by row on the key:
  a row only one side changed (the other still equals base) takes the changed side; a row both sides changed the
  same way is kept once; a row theirs added goes after its predecessor in theirs; a row both sides changed
  differently is a real conflict: nothing is written and the keys are listed (exit 1).
Ours' row order is kept; the file's line ending (LF or CRLF) is kept. Writes <path> in the work tree."""
import subprocess
import sys


def stage(git_dir, n, path):
    r = subprocess.run(["git", "-C", git_dir, "show", f":{n}:{path}"], capture_output=True)
    return r.stdout.decode("utf-8") if r.returncode == 0 else ""


def rows(text):
    out = {}
    order = []
    for line in text.replace("\r\n", "\n").split("\n"):
        if line == "":
            continue
        key = line.split("\t", 1)[0]
        if key in out:
            raise SystemExit(f"duplicate key {key!r}")
        out[key] = line
        order.append(key)
    return out, order


def main():
    git_dir, path = sys.argv[1:3]
    base_t, ours_t, theirs_t = (stage(git_dir, n, path) for n in (1, 2, 3))
    (base, _), (ours, oorder), (theirs, torder) = rows(base_t), rows(ours_t), rows(theirs_t)
    merged, conflicts, taken = dict(ours), [], []
    order = list(oorder)
    for key in torder:
        b, o, t = base.get(key), ours.get(key), theirs[key]
        if o == t:
            continue
        if o is None and b is None:                       # theirs added the row
            prev = torder[torder.index(key) - 1] if torder.index(key) else None
            order.insert(order.index(prev) + 1 if prev in order else len(order), key)
            merged[key] = t
            taken.append(key)
        elif t == b:                                      # only ours changed (or ours deleted)
            continue
        elif o == b:                                      # only theirs changed
            merged[key] = t
            taken.append(key)
        else:
            conflicts.append(key)
    for key in oorder:                                    # theirs deleted a row ours left alone
        if key not in theirs and key in base and ours[key] == base[key]:
            order.remove(key)
            del merged[key]
            taken.append("-" + key)
    if conflicts:
        print("conflict rows: " + " ".join(conflicts))
        raise SystemExit(1)
    nl = "\r\n" if "\r\n" in ours_t else "\n"
    with open(f"{git_dir}/{path}", "w", encoding="utf-8", newline="") as w:
        w.write(nl.join(merged[k] for k in order) + nl)
    print(f"rows={len(order)} from_theirs={' '.join(taken) or '-'}")


main()
