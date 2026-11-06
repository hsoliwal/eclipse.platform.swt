# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others
"""m3_edit_ledger.py <worktree> <ledger.tsv> [--check]

EDIT_LEDGER v1: applies a reviewed edit ledger as exact text replacements. Ledger columns: file, old, new, count,
reason (the STRING_EDITS v1 shape of consumer_rename.py, plus the reviewer's reason). Cells escape newline, tab and
backslash as \\n, \\t and \\\\; a file with CRLF line ends gets \\r\\n for every \\n of its rows.

Per row, against the file text after the earlier rows of the same file:
  APPLY  `old` occurs exactly `count` times (for an insertion, where `old` lies inside `new`: and `new` does not)
  DONE   the row already holds: `new` occurs at least `count` times and `old` is gone (insertion: `new` is there)
  DRIFT  anything else (the file moved under the ledger); nothing is written for any file
Two-phase and fail-closed: every row is planned first; files are written only when no row drifted. Rows apply in
ledger order. A second run reports only DONE (fixed point). --check plans and writes nothing.
Output: one TSV line per row (state, file, line of the first match, count, old) and a summary line; exit 1 on DRIFT.
"""
import sys
from collections import Counter

ESCAPES = {"n": "\n", "t": "\t", "\\": "\\"}
COLUMNS = ("file", "old", "new", "count", "reason")
PREVIEW = 70


def unescape(cell):
    out, chars = [], iter(cell)
    for c in chars:
        out.append(ESCAPES.get(next(chars, ""), None) if c == "\\" else c)
        if out[-1] is None:
            raise SystemExit(f"EDIT_LEDGER: bad escape in cell {cell[:PREVIEW]!r}")
    return "".join(out)


def read_ledger(path):
    with open(path, encoding="utf-8", newline="") as f:
        lines = [line.rstrip("\r\n") for line in f if line.strip()]
    if tuple(lines[0].split("\t")) != COLUMNS:
        raise SystemExit(f"EDIT_LEDGER: header must be {COLUMNS}")
    rows = []
    for n, line in enumerate(lines[1:], 2):
        cells = line.split("\t")
        if len(cells) != len(COLUMNS) or not cells[3].isdigit() or int(cells[3]) < 1:
            raise SystemExit(f"EDIT_LEDGER: ledger line {n} needs {len(COLUMNS)} cells and a count >= 1")
        rows.append((cells[0], unescape(cells[1]), unescape(cells[2]), int(cells[3])))
    return rows


def plan(text, old, new, want):
    got_old, got_new = text.count(old), text.count(new)
    if old in new:
        if got_new >= want:
            return "DONE", text, text.find(new)
        return ("APPLY", text.replace(old, new), text.find(old)) if got_old == want else ("DRIFT", text, -1)
    if got_old == want:
        return "APPLY", text.replace(old, new), text.find(old)
    if got_old == 0 and got_new >= want:
        return "DONE", text, text.find(new)
    return "DRIFT", text, -1


def main():
    args = [a for a in sys.argv[1:] if a != "--check"]
    check = len(args) != len(sys.argv) - 1
    root, ledger = args[0], args[1]
    texts, report, states = {}, [], Counter()
    for rel, old, new, want in read_ledger(ledger):
        if rel not in texts:
            with open(f"{root}/{rel}", encoding="utf-8", newline="") as f:
                texts[rel] = [f.read(), False]
        text, _ = texts[rel]
        if "\r\n" in text:
            old, new = old.replace("\n", "\r\n"), new.replace("\n", "\r\n")
        state, after, at = plan(text, old, new, want)
        texts[rel] = [after, texts[rel][1] or state == "APPLY"]
        states[state] += 1
        line = text.count("\n", 0, at) + 1 if at >= 0 else 0
        report.append(f"{state}\t{rel}\t{line}\t{want}\t{old.splitlines()[0][:PREVIEW] if old else ''}")
    print("\n".join(report))
    if states["DRIFT"]:
        print(f"EDIT_LEDGER: DRIFT {states['DRIFT']} rows; nothing written")
        return 1
    if not check:
        for rel, (text, changed) in texts.items():
            if changed:
                with open(f"{root}/{rel}", "w", encoding="utf-8", newline="") as f:
                    f.write(text)
    summary = " ".join(f"{k}={states[k]}" for k in ("APPLY", "DONE"))
    print(f"EDIT_LEDGER {'check' if check else 'applied'}: {summary} files={sum(c for _, c in texts.values())}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
