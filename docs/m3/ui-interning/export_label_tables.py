# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Export real UI label tables as TSV for the Java benchmarks (read-only on the sources).

Usage:
  python export_label_tables.py <json-file> <javac.log> <out-dir>

Writes <out-dir>/json-labels.tsv (key, value: the two columns of a JSON tree
viewer, one row per node) and <out-dir>/problems.tsv (Description, Resource,
Path, Location, Type, Severity: the Problems view of real javac diagnostics).
Tabs and newlines inside labels become spaces.
"""
import json
import os
import re
import sys

DIAG = re.compile(r"^(?P<file>[A-Za-z]:.*?\.java):(?P<line>\d+): (?P<sev>warning|error): (?P<msg>.*)$")


def clean(text):
    return text.replace("\t", " ").replace("\n", " ").replace("\r", " ")


def display(value):
    if isinstance(value, dict):
        return "{...}"
    if isinstance(value, list):
        return f"[{len(value)}]"
    return json.dumps(value, ensure_ascii=False)


def walk(value, key, out):
    if key is not None:
        out.write(clean(key) + "\t" + clean(display(value)) + "\n")
    if isinstance(value, dict):
        for k, v in value.items():
            walk(v, k, out)
    elif isinstance(value, list):
        for i, v in enumerate(value):
            walk(v, f"[{i}]", out)


def main(argv):
    json_file, log, out_dir = argv[1:4]
    with open(json_file, encoding="utf-8") as f:
        data = json.load(f)
    with open(os.path.join(out_dir, "json-labels.tsv"), "w", encoding="utf-8", newline="\n") as out:
        walk(data, None, out)
    with open(log, encoding="utf-8", errors="replace") as f, \
            open(os.path.join(out_dir, "problems.tsv"), "w", encoding="utf-8", newline="\n") as out:
        for line in f:
            m = DIAG.match(line.rstrip("\n"))
            if not m:
                continue
            folder, _, name = m.group("file").replace("\\", "/").rpartition("/")
            severity = "Error" if m.group("sev") == "error" else "Warning"
            row = (m.group("msg"), name, folder, "line " + m.group("line"), "Java Problem", severity)
            out.write("\t".join(clean(c) for c in row) + "\n")


if __name__ == "__main__":
    main(sys.argv)
