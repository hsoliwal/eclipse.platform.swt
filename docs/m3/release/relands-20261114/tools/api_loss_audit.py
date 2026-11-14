# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others
"""api_loss_audit.py <git_dir> <before> <after> <out.tsv> : API-LOSS AUDIT v1 (CPU, source level, git only).
A superset must be additive, but a non-conflicting 3-way merge silently takes a donor's deletion. For every .java
file changed between two commits, census the public and protected declarations (types, methods, constructors, fields)
of both versions on comment- and string-masked source, keyed by kind + name + parameter list, and report every key of
<before> that is missing in <after> (LOST) together with the count of added keys (GAINED). Deleted files count all
their declarations as LOST. Writes path, status, kind, key, line_before."""
import re
import subprocess
import sys

MASK = re.compile(r"//[^\n]*|/\*.*?\*/|\"(?:\\.|[^\"\\\n])*\"|'(?:\\.|[^'\\\n])*'", re.S)
DECL = re.compile(
    r"^[ \t]*((?:@\w+(?:\([^)]*\))?\s+)*)((?:public|protected)\s+(?:(?:static|final|abstract|synchronized|native|"
    r"default|sealed|non-sealed|strictfp|transient|volatile)\s+)*)"
    r"(?:(class|interface|enum|record|@interface)\s+(\w+)|(?:<[^>]*>\s*)?([\w.<>\[\], ?]+?)\s+(\w+)\s*(\(([^)]*)\))?)",
    re.M)
SPACES = re.compile(r"\s+")
GENERIC = re.compile(r"<[^<>]*>")


def git(git_dir, *args):
    return subprocess.run(["git", "-C", git_dir, *args], capture_output=True, text=True, encoding="utf-8",
                          errors="replace").stdout


def params(raw):
    text = raw
    while GENERIC.search(text):
        text = GENERIC.sub("", text)
    types = []
    for p in text.split(","):
        p = SPACES.sub(" ", p.replace("final ", "")).strip()
        if p:
            types.append(p.rsplit(" ", 1)[0] if " " in p else p)
    return ",".join(types)


def census(source):
    keys = {}
    masked = MASK.sub(lambda m: " " * len(m.group(0)) if m.group(0).count("\n") == 0 else
                      "\n" * m.group(0).count("\n"), source)
    for m in DECL.finditer(masked):
        line = masked.count("\n", 0, m.start()) + 1
        if m.group(3):
            key = f"{m.group(3)} {m.group(4)}"
        elif m.group(7):
            key = f"method {m.group(6)}({params(m.group(8) or '')})"
        else:
            if m.group(6) in ("class", "interface", "enum", "record"):
                continue
            # a field declaration may name several variables: "public static final boolean A, B = x, C;"
            end = masked.find(";", m.start(6))
            for name in field_names(masked[m.start(6):end if end >= 0 else m.end()]):
                keys.setdefault(f"field {name}", line)
            continue
        keys.setdefault(key, line)
    return keys


def field_names(declarators):
    names, depth, part = [], 0, []
    for ch in declarators + ",":
        if ch in "([{<":
            depth += 1
        elif ch in ")]}>":
            depth -= 1
        if ch == "," and depth == 0:
            head = "".join(part).split("=", 1)[0].strip()
            if head:
                names.append(head.split()[0].rstrip("[]"))
            part = []
        else:
            part.append(ch)
    return names


def main():
    git_dir, before, after, out = sys.argv[1:5]
    changed = [l.split("\t") for l in git(git_dir, "diff", "--name-status", "--no-renames", before, after).splitlines()]
    rows, gained, lost = [], 0, 0
    for status, path in ((c[0], c[-1]) for c in changed if c and c[-1].endswith(".java")):
        old = census(git(git_dir, "show", f"{before}:{path}")) if status != "A" else {}
        new = census(git(git_dir, "show", f"{after}:{path}")) if status != "D" else {}
        for key, line in sorted(old.items()):
            if key not in new:
                rows.append((path, status, key.split(" ", 1)[0], key, str(line)))
                lost += 1
        gained += sum(1 for k in new if k not in old)
    with open(out, "w", encoding="utf-8", newline="\n") as w:
        w.write("path\tstatus\tkind\tkey\tline_before\n")
        w.writelines("\t".join(r) + "\n" for r in rows)
    print(f"java_files={sum(1 for c in changed if c and c[-1].endswith('.java'))} lost={lost} gained={gained}")


main()
