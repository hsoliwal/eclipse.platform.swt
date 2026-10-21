# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Share of UI labels that fit one immediate long (M3StringImmediate layout).

LATIN1: 1..7 units, each <= 0xFF. IDENT: 8..10 units of [0-9A-Za-z_$]. The
empty label is id 0. Everything else needs a pool entry.

Usage:
  python measure_immediates.py <fs-root> <javac.log> <git-tree-list> <out.tsv>

Views: Project Explorer names (os.scandir of <fs-root>, first 2,000,000
entries; and the SWT repository tree), Problems view markers built from real
javac diagnostics (<javac.log>: description, resource, path, location, type).
"""
import collections
import os
import re
import sys

IDENT = set("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_$")
LIMIT = 2_000_000
DIAG = re.compile(r"^(?P<file>[A-Za-z]:.*?\.java):(?P<line>\d+): (?P<sev>warning|error): (?P<msg>.*)$")


def kind(text):
    n = len(text)
    if n == 0:
        return "empty"
    if n <= 7 and all(ord(c) <= 0xFF for c in text):
        return "latin1"
    if 8 <= n <= 10 and all(c in IDENT for c in text):
        return "ident"
    return "pool"


def row(view, source, labels):
    cells = collections.Counter(kind(t) for t in labels)
    distinct = set(labels)
    dkinds = collections.Counter(kind(t) for t in distinct)
    rows = len(labels)
    imm = cells["latin1"] + cells["ident"]
    dimm = dkinds["latin1"] + dkinds["ident"]
    return [view, source, rows, len(distinct), cells["latin1"], cells["ident"], cells["empty"], cells["pool"],
            f"{100.0 * imm / rows:.1f}" if rows else "0", dimm, dkinds["pool"],
            f"{100.0 * dimm / len(distinct):.1f}" if distinct else "0"]


def fs_names(root):
    names, stack = [], [root]
    while stack and len(names) < LIMIT:
        path = stack.pop()
        try:
            entries = sorted(os.scandir(path), key=lambda e: e.name)
        except OSError:
            continue
        for e in entries:
            if len(names) >= LIMIT:
                break
            names.append(e.name)
            try:
                if e.is_dir(follow_symlinks=False) and e.name != ".git":
                    stack.append(e.path)
            except OSError:
                pass
    return names


def problems(log):
    columns = collections.defaultdict(list)
    with open(log, encoding="utf-8", errors="replace") as f:
        for line in f:
            m = DIAG.match(line.rstrip("\n"))
            if not m:
                continue
            path = m.group("file").replace("\\", "/")
            folder, _, name = path.rpartition("/")
            columns["Description"].append(m.group("msg"))
            columns["Resource"].append(name)
            columns["Path"].append(folder)
            columns["Location"].append("line " + m.group("line"))
            columns["Type"].append("Java Problem")
            columns["Severity"].append("Error" if m.group("sev") == "error" else "Warning")
    return columns


def main(argv):
    fs_root, log, tree_list, out = argv[1:5]
    rows = []
    names = fs_names(fs_root)
    rows.append(row("Project Explorer names", f"os.scandir of {fs_root} ({len(names):,} entries)", names))
    with open(tree_list, encoding="utf-8") as f:
        paths = [tuple(line.rstrip("\n").split("/")) for line in f if line.strip()]
    nodes = {p[:i] for p in paths for i in range(1, len(p) + 1)}
    rows.append(row("Project Explorer names", "SWT repository tree 5593cc43ba", [n[-1] for n in nodes]))
    segs = [s for p in paths for s in p]
    rows.append(row("path segments", "SWT repository tree 5593cc43ba (all path components)", segs))
    columns = problems(log)
    total = []
    for name, labels in columns.items():
        rows.append(row(f"Problems view {name}", "javac -Xlint:all over guava + eclipse-collections", labels))
        total += labels
    rows.append(row("Problems view all columns", "javac -Xlint:all over guava + eclipse-collections", total))
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write("view\tsource\tcells\tdistinct\tlatin1_cells\tident_cells\tempty_cells\tpool_cells\timmediate_cell_pct\t"
                "immediate_distinct\tpool_distinct\timmediate_distinct_pct\n")
        for r in rows:
            f.write("\t".join(str(c) for c in r) + "\n")


if __name__ == "__main__":
    main(sys.argv)
