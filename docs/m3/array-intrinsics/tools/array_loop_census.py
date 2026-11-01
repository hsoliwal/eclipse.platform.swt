# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others
"""array_loop_census.py <out_dir> <label>=<root> ... : ARRAY-LOOP-PATTERN v1 (CPU, read-only, files on disk only).
array_loop_census.py --relabel <tsv>... : rewrites the jdk_route column of existing census rows from ROUTE.
Finds counted loops `for (int i = A; i < B; i++)` whose body is one statement (or one if-statement) over arrays,
in comment- and string-masked Java, and classifies the body:
  COPY     a[i+p] = b[i+q];                     -> System.arraycopy          (HotSpot intrinsic)
  FILL     a[i] = <expr without i>;             -> Arrays.fill               (library loop, not an intrinsic: on
                                                   JDK 21 C2 vectorizes it the same way as the loop it replaces)
  COMPARE  if (a[i] != b[i]) return/false/break -> Arrays.mismatch / equals  (HotSpot intrinsic)
  SEARCH   if (a[i] == x) return i / break      -> M3 kernel candidate (no JDK API for primitive indexOf)
  COUNT    if (<cond on a[i]>) c++;             -> M3 kernel candidate
  MINMAX   if (a[i] > m) m = a[i];  / Math.max  -> M3 kernel candidate
  REDUCE   s += a[i];  s ^= ..., s |= ...       -> M3 kernel candidate (C2 superword vectorizes some)
  MAP      a[i] = f(b[i] ...)                   -> M3 kernel candidate when f is arithmetic
  OTHER    anything else (typed residue, not claimed)
Outputs LOOPS.tsv (label, file, line, pattern, jdk_route, text) and SUMMARY.tsv (label x pattern counts)."""
import os
import re
import sys
from collections import Counter

MASK = re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\\n])*"|\'(?:\\.|[^\'\\\n])*\'', re.S)
LOOP = re.compile(r"for\s*\(\s*int\s+(\w+)\s*=\s*[^;]+;\s*\1\s*<=?\s*[^;]+;\s*(?:\1\s*\+\+|\+\+\s*\1|\1\s*\+=\s*1)\s*\)\s*")
ROUTE = {"COPY": "System.arraycopy (intrinsic)", "FILL": "Arrays.fill (library call, vectorized like the loop; not an intrinsic)",
         "COMPARE": "Arrays.mismatch/equals (intrinsic)", "SEARCH": "M3 kernel candidate", "COUNT": "M3 kernel candidate",
         "MINMAX": "M3 kernel candidate", "REDUCE": "M3 kernel candidate", "MAP": "M3 kernel candidate (arithmetic f)",
         "OTHER": "-"}


def masked(s):
    return MASK.sub(lambda m: "".join(c if c == "\n" else " " for c in m.group(0)), s)


def body_after(m, j):
    """the loop body: a braced block holding exactly one statement, or a single statement"""
    if j < len(m) and m[j] == "{":
        depth, k = 0, j
        while k < len(m):
            depth += (m[k] == "{") - (m[k] == "}")
            k += 1
            if depth == 0:
                break
        inner = m[j + 1:k - 1].strip()
        return inner if inner.count(";") <= 2 else None
    k = m.find(";", j)
    return m[j:k + 1].strip() if k > 0 else None


def classify(i, b):
    b = re.sub(r"\s+", " ", b)
    I = re.escape(i)
    idx = rf"\[\s*(?:\w+\s*\+\s*)?{I}(?:\s*[+-]\s*\w+)?\s*\]"
    if re.fullmatch(rf"\w+(?:\.\w+)*{idx} = \w+(?:\.\w+)*{idx};", b):
        return "COPY"
    if re.fullmatch(rf"if \(\s*\w+{idx} != \w+{idx}\s*\) (?:\{{ )?(?:return [^;]*|break);(?: \}})?", b):
        return "COMPARE"
    if re.fullmatch(rf"if \(\s*\w+{idx} == [^)]+\) (?:\{{ )?(?:return {I}|break|return true|found = true);(?: \}})?", b):
        return "SEARCH"
    if re.fullmatch(rf"if \(.*\w+{idx}.*\) (?:\{{ )?\w+\+\+;(?: \}})?", b):
        return "COUNT"
    if re.fullmatch(rf"if \(\s*\w+{idx} [<>]=? \w+\s*\) (?:\{{ )?\w+ = \w+{idx};(?: \}})?", b) or \
            re.fullmatch(rf"\w+ = Math\.(?:max|min)\(\s*\w+,\s*\w+{idx}\s*\);", b):
        return "MINMAX"
    if re.fullmatch(rf"\w+ (?:\+|\^|\||&|\*)= [^;]*\w+{idx}[^;]*;", b):
        return "REDUCE"
    m = re.fullmatch(rf"\w+(?:\.\w+)*{idx} = (.+);", b)
    if m:
        rhs = m.group(1)
        if not re.search(rf"\b{I}\b", re.sub(idx, "", rhs)) and "[" not in rhs:
            return "FILL"
        if re.search(idx, rhs):
            return "MAP"
    return "OTHER"


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    rows = []
    for spec in sys.argv[2:]:
        label, root = spec.split("=", 1)
        for dirpath, dirs, files in os.walk(root):
            dirs[:] = [d for d in dirs if d not in (".git", "target", "bin", ".superset")]
            for f in files:
                if not f.endswith(".java"):
                    continue
                p = os.path.join(dirpath, f)
                try:
                    s = open(p, encoding="utf-8", errors="replace").read()
                except OSError:
                    continue
                m = masked(s)
                for hit in LOOP.finditer(m):
                    b = body_after(m, hit.end())
                    if not b:
                        continue
                    pat = classify(hit.group(1), b)
                    line = m.count("\n", 0, hit.start()) + 1
                    rel = os.path.relpath(p, root).replace(os.sep, "/")
                    rows.append((label, rel, str(line), pat, ROUTE[pat], re.sub(r"\s+", " ", b)[:160]))
    with open(os.path.join(out, "LOOPS.tsv"), "w", encoding="utf-8", newline="\n") as w:
        w.write("repo\tfile\tline\tpattern\tjdk_route\tbody\n" + "".join("\t".join(r) + "\n" for r in rows))
    c = Counter((r[0], r[3]) for r in rows)
    labels = sorted({r[0] for r in rows})
    pats = list(ROUTE)
    with open(os.path.join(out, "SUMMARY.tsv"), "w", encoding="utf-8", newline="\n") as w:
        w.write("pattern\tjdk_route\t" + "\t".join(labels) + "\ttotal\n")
        for p_ in pats:
            vals = [c.get((l, p_), 0) for l in labels]
            w.write(f"{p_}\t{ROUTE[p_]}\t" + "\t".join(map(str, vals)) + f"\t{sum(vals)}\n")
    print(f"loops={len(rows)}")


def relabel(paths):
    """the jdk_route column of LOOPS.tsv-shaped files, rewritten from ROUTE; every other byte stays as it is"""
    for p in paths:
        with open(p, encoding="utf-8", newline="") as f:
            lines = f.read().split("\n")
        changed = 0
        for k in range(1, len(lines)):
            cols = lines[k].split("\t")
            if len(cols) >= 6 and cols[3] in ROUTE and cols[4] != ROUTE[cols[3]]:
                cols[4] = ROUTE[cols[3]]
                lines[k] = "\t".join(cols)
                changed += 1
        if changed:
            with open(p, "w", encoding="utf-8", newline="") as w:
                w.write("\n".join(lines))
        print(f"relabel {os.path.basename(p)}: rows_changed={changed}")


if __name__ == "__main__":
    if sys.argv[1:2] == ["--relabel"]:
        relabel(sys.argv[2:])
    else:
        main()
