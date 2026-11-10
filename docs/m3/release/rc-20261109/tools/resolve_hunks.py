#!/usr/bin/env python3
"""Resolve git conflict hunks per hunk: ours (master) / theirs (#325) / union.

Usage: resolve_hunks.py <file> [choices]
  choices: comma list per hunk of o|t|ot|to|auto (default auto for every hunk).
  auto: ours-lines subset of theirs -> t; theirs subset of ours -> o; else UNRESOLVED (left as is).
Prints one TSV record per hunk: file, hunk, decision, ours_lines, theirs_lines.
"""
import sys

def split(path):
    with open(path, encoding="utf-8", newline="") as fh:
        lines = fh.readlines()
    out, hunks, i = [], [], 0
    while i < len(lines):
        if lines[i].startswith("<<<<<<< "):
            ours, theirs, j = [], [], i + 1
            while not lines[j].startswith("======="):
                ours.append(lines[j]); j += 1
            j += 1
            while not lines[j].startswith(">>>>>>> "):
                theirs.append(lines[j]); j += 1
            hunks.append((len(out), ours, theirs, lines[i:j + 1]))
            out.append(None)
            i = j + 1
        else:
            out.append(lines[i]); i += 1
    return out, hunks

def key(ls):
    return {l.strip() for l in ls if l.strip()}

def decide(ours, theirs, choice):
    if choice != "auto":
        return choice
    ko, kt = key(ours), key(theirs)
    if ko <= kt:
        return "t"
    if kt <= ko:
        return "o"
    return "?"

def main():
    path = sys.argv[1]
    out, hunks = split(path)
    choices = sys.argv[2].split(",") if len(sys.argv) > 2 else ["auto"] * len(hunks)
    if len(choices) == 1 and len(hunks) > 1:
        choices = choices * len(hunks)
    for n, (pos, ours, theirs, raw) in enumerate(hunks):
        d = decide(ours, theirs, choices[n])
        body = {"o": ours, "t": theirs, "ot": ours + theirs, "to": theirs + ours}.get(d, raw)
        out[pos] = "".join(body)
        print(f"{path}\t{n + 1}\t{d}\t{len(ours)}\t{len(theirs)}")
    with open(path, "w", encoding="utf-8", newline="") as fh:
        fh.write("".join(out))

if __name__ == "__main__":
    main()
