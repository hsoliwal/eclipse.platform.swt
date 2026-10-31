# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Mechanical multi-site deltas of the real-app fidelity leaf (docs/m3/gc-extension-seam/LEAF_FIDELITY_REALAPP.md).

One atom per call. A delta is (id, file, anchor, text): the text is inserted before the anchor, which must occur exactly
once. A delta whose text is already in the file is skipped, so a second run reports UNCHANGED. A missing or repeated
anchor stops the run before any file is written. Semantic edits are made by hand and recorded in RECIPE_MEMORY.tsv (LF-*).
Usage: python -I m3gc_realapp_deltas.py <worktree> <ATOM> [--check]
"""
import sys
import os
if __name__ == "__main__":  # M3_GRAPHICS_GUARD v1: NOT_APPLICABLE on jdk.m3.graphics trees
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from m3_graphics_guard import exit_if_m3_graphics
    exit_if_m3_graphics(sys.argv)
from pathlib import Path

W = "bundles/org.eclipse.swt/Eclipse SWT/"
WIDGET_HOOKS = (
    "/*\n"
    " * MODE_TRUTH v1 (census L10, real-app fidelity leaf): the mode a drawn route of this widget realizes (a drawn plane, an M3\n"
    " * canvas handler), or null when the widget's gate decides; M3Mode.of answers it first.\n"
    " */\n"
    "org.eclipse.swt.internal.WidgetMode m3Route () {\n"
    "\treturn null;\n"
    "}\n"
    "\n"
    "/* BACKEND_REPORT v1: the renderer that draws this widget, for reports (M3Mode.backend), or null when the platform draws it */\n"
    "String m3Backend () {\n"
    "\treturn null;\n"
    "}\n"
    "\n"
)
PLANE_HOOKS = (
    "/* MODE_TRUTH v1: a drawn plane answers the mode its backend realizes (a gpu plane without a GL context draws in m3) */\n"
    "@Override\n"
    "WidgetMode m3Route () {\n"
    "\treturn drawnPlane != null ? drawnPlane.route () : super.m3Route ();\n"
    "}\n"
    "\n"
    "/* BACKEND_REPORT v1: the drawn plane and its backend */\n"
    "@Override\n"
    "String m3Backend () {\n"
    "\treturn drawnPlane != null ? drawnPlane.backend () : super.m3Backend ();\n"
    "}\n"
    "\n"
)
DELTAS = {
    "MODE_TRUTH": [
        ("LF-03a", W + "win32/org/eclipse/swt/widgets/Widget.java", "protected void checkWidget () {", WIDGET_HOOKS),
        ("LF-03b", W + "gtk/org/eclipse/swt/widgets/Widget.java", "protected void checkWidget () {", WIDGET_HOOKS),
        ("LF-03c", W + "cocoa/org/eclipse/swt/widgets/Widget.java", "protected void checkWidget () {", WIDGET_HOOKS),
        ("LF-03d", W + "win32/org/eclipse/swt/widgets/Table.java", "@Override\nint defaultBackground () {", PLANE_HOOKS),
        ("LF-03e", W + "win32/org/eclipse/swt/widgets/List.java", "@Override\nint defaultBackground () {", PLANE_HOOKS),
        ("LF-03f", W + "win32/org/eclipse/swt/widgets/Tree.java", "@Override\nint defaultBackground () {", PLANE_HOOKS),
    ],
}


def plan(root, deltas):
    """(id, path, new text or None when unchanged); raises on a missing or repeated anchor."""
    out, texts = [], {}
    for did, rel, anchor, text in deltas:
        path = root / rel
        s = texts.get(path) or path.read_bytes().decode("utf-8")
        if text in s:
            out.append((did, path, None))
            continue
        n = s.count(anchor)
        if n != 1:
            raise SystemExit(f"{did}: anchor found {n} times in {rel}")
        s = s.replace(anchor, text + anchor)
        texts[path] = s
        out.append((did, path, s))
    return out, texts


def main(argv):
    root, atom = Path(argv[1]), argv[2]
    rows, texts = plan(root, DELTAS[atom])
    for did, path, s in rows:
        print(f"{did}\t{'UNCHANGED' if s is None else 'APPLIED'}\t{path.relative_to(root).as_posix()}")
    if "--check" not in argv:
        for path, s in texts.items():
            path.write_bytes(s.encode("utf-8"))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
