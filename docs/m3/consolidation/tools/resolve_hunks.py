#!/usr/bin/env python
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0
"""Mechanical conflict resolution per file: mode ours | theirs | union (ours then theirs) | union-sep:<text>
(a separator line inserted between the two sides, e.g. a repeated annotation). Byte-preserving except the
markers. Usage: resolve_hunks.py <file> <mode>"""
import sys, re
path, mode = sys.argv[1], sys.argv[2]
data = open(path, "rb").read(); nl = b"\r\n" if b"\r\n" in data else b"\n"
lines = data.split(nl); out = []; state = None; ours = []; theirs = []; hunks = 0
sep = mode.split(":", 1)[1].encode() if mode.startswith("union-sep:") else None
per_hunk = mode.split(":", 1)[1].split(",") if mode.startswith("list:") else None   # one of ours|theirs|union per hunk
for line in lines:
    if state is None and line.startswith(b"<<<<<<< "):
        state = "ours"; ours = []; theirs = []; continue
    if state == "ours" and line == b"=======":
        state = "theirs"; continue
    if state == "theirs" and line.startswith(b">>>>>>> "):
        hunks += 1
        m = per_hunk[hunks - 1] if per_hunk else mode
        if m == "ours": out += ours
        elif m == "theirs": out += theirs
        else:
            out += ours
            if sep is not None: out += [b"", b"\t" + sep]
            out += theirs
        state = None; continue
    (out if state is None else ours if state == "ours" else theirs).append(line)
if state is not None: sys.exit("unterminated conflict in " + path)
open(path, "wb").write(nl.join(out))
print(f"{path}: {hunks} hunks resolved as {mode}")
