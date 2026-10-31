# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""BLLSE for javac logs (census L12): every diagnostic of a javac.log as one structured record, never prose.

A javac diagnostic is `<path>:<line>: <severity>: <message>`, then the source line, a caret line that copies the source
line's leading whitespace up to the column, and optional `symbol:` / `location:` lines. Each record is
{file, line, col, severity, message, symbol, location, source}; file is repo-relative (from `bundles/`, `tests/` or
`examples/`, forward slashes); col is the 1-based character offset of the caret (a tab counts one character, as in the
file). A `<path>:<line>:` header with no caret keeps col 0. The TSV is the error list a delta cites (javac_line).

Usage: python -I m3gc_javac_bllse.py <javac.log> [out.tsv]     (stdout when out.tsv is omitted; summary on stderr)
"""
import re
import sys
from collections import Counter
from pathlib import Path

HEAD = re.compile(r"^(?P<path>.+?\.java):(?P<line>\d+): (?P<sev>error|warning): (?P<msg>.*)$")
ROOTS = ("bundles/", "tests/", "examples/")
COLUMNS = ("file", "line", "col", "severity", "message", "symbol", "location", "source")


def rel(path):
    p = path.replace("\\", "/")
    hits = [p.find(r) for r in ROOTS if r in p]
    return p[min(hits):] if hits else p


def records(lines):
    out, cur = [], None
    for raw in lines:
        line = raw.rstrip("\r\n")
        m = HEAD.match(line)
        if m:
            cur = {"file": rel(m["path"]), "line": m["line"], "col": "0", "severity": m["sev"], "message": m["msg"],
                   "symbol": "", "location": "", "source": "", "_n": 0}
            out.append(cur)
        elif cur is not None:
            cur["_n"] += 1
            stripped = line.strip()
            if cur["_n"] == 1:
                cur["source"] = stripped
            elif cur["_n"] == 2 and stripped == "^":
                cur["col"] = str(line.index("^") + 1)
            elif stripped.startswith("symbol:"):
                cur["symbol"] = " ".join(stripped[7:].split())
            elif stripped.startswith("location:"):
                cur["location"] = " ".join(stripped[9:].split())
    return out


def main(log, dest=None):
    recs = records(Path(log).read_text(encoding="utf-8", errors="replace").splitlines())
    esc = lambda s: s.replace("\t", " ")
    text = "\t".join(COLUMNS) + "\n" + "".join("\t".join(esc(r[c]) for c in COLUMNS) + "\n" for r in recs)
    if dest:
        Path(dest).write_text(text, encoding="utf-8", newline="\n")
    else:
        sys.stdout.write(text)
    sev = Counter(r["severity"] for r in recs)
    files = Counter(r["file"].rsplit("/", 1)[-1] for r in recs)
    syms = Counter((r["message"], r["symbol"]) for r in recs)
    print("BLLSE %s: records=%d %s" % (log, len(recs), " ".join("%s=%d" % kv for kv in sorted(sev.items()))), file=sys.stderr)
    print("  files: " + " ".join("%s=%d" % kv for kv in files.most_common()), file=sys.stderr)
    for (msg, sym), n in syms.most_common():
        print("  %3d  %s | %s" % (n, msg, sym), file=sys.stderr)


if __name__ == "__main__":
    main(*sys.argv[1:3])
