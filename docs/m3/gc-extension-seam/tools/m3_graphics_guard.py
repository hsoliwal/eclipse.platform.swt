# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""M3_GRAPHICS_GUARD v1 (2026-10-31): the *_deltas.py / m3gc_adopt.py recipes of the earlier leaves anchor on exact
source text written with Skija names (io.github.humbleui.skija.Canvas, Paint, PathBuilder, ...). On a worktree whose
M3 trees were moved onto the M3jdk owner jdk.m3.graphics by CONSUMER_RENAME (m3/tooling/skija-absorb in M3jdk21),
those deltas are already applied (they ran before the rename), and their anchors cannot match by design. Running them
there would report MISSING_ANCHOR for text that is present under its M3 name, so they stop with NOT_APPLICABLE instead.
To re-apply one of them on an M3-named tree, map its anchors through TYPE_RENAME.tsv first (a recipe change)."""
import os
import re
import sys

RENDER = os.path.join("bundles", "org.eclipse.swt", "Eclipse SWT Render M3", "win32")
IMPORT = re.compile(r"^import (?:static )?jdk\.m3\.graphics\.", re.M)


def on_m3_graphics(worktree):
    root = os.path.join(worktree, RENDER)
    if not os.path.isdir(root):
        return False
    for dirpath, _, files in os.walk(root):
        for f in files:
            if f.endswith(".java"):
                with open(os.path.join(dirpath, f), encoding="utf-8", errors="replace") as h:
                    if IMPORT.search(h.read(16384)):
                        return True
    return False


def exit_if_m3_graphics(argv):
    for a in argv[1:]:
        if os.path.isdir(os.path.join(a, "bundles", "org.eclipse.swt")) and on_m3_graphics(a):
            print(f"NOT_APPLICABLE {os.path.basename(argv[0])}: {a} is on jdk.m3.graphics (CONSUMER_RENAME); these "
                  "deltas were applied before the rename and anchor on Skija names (m3_graphics_guard.py)")
            sys.exit(0)
