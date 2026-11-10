#!/usr/bin/env python3
"""Assemble RELEASE_NOTES.md = head (placeholders filled) + gate rows from CHECKLIST.md + tests + PR tables.
Usage: assemble_notes.py <head.md> <tests.md> <prs.md> <CHECKLIST.md> <jar_sha> <leak_text> <out.md>"""
import sys

head, tests, prs, checklist, jar_sha, leak, out = sys.argv[1:8]
gates = ["| # | Gate | Status |", "| --- | --- | --- |"]
for line in open(checklist, encoding="utf-8"):
    cells = [c.strip() for c in line.strip().strip("|").split("|")]
    if len(cells) >= 5 and cells[0][:1].isdigit():
        gates.append(f"| {cells[0]} | {cells[1]} | {cells[2]} |")
text = open(head, encoding="utf-8").read()
text = text.replace("@JAR_SHA@", jar_sha).replace("@GATES@", "\n".join(gates))
text = text.replace("@TESTS@", open(tests, encoding="utf-8").read().replace("@LEAK@", leak).rstrip())
text = text.rstrip() + "\n\n" + open(prs, encoding="utf-8").read()
open(out, "w", encoding="utf-8", newline="\n").write(text)
print(f"gates={len(gates) - 2}")
