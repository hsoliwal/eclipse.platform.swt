"""seam_rejects.py <worktree> : apply the four rejected I31 seam hunks by anchor (exact-line matches, byte-preserving),
then delete every *.rej. Region (win32): a `log.<op>(...)` line before each `final Operation operation = new Operation...`
statement, chosen by the enclosing method's signature. TextLayout (win32): the GCExtension delegation after the
8-argument drawInPixels signature. Canvas (gtk): the externalCanvasHandler hooks in gtk_draw and scroll.
Prints one line per edit: path, anchor, inserted lines. Exit 2 when an anchor is missing or ambiguous."""
import os
import re
import sys

WT = sys.argv[1]
BASE = os.path.join(WT, "bundles", "org.eclipse.swt", "Eclipse SWT")
edits = 0
errors = 0


def read(path):
    with open(path, "rb") as fh:
        return fh.read()


def write(path, data):
    with open(path, "wb") as fh:
        fh.write(data)


def insert_after(path, anchor, block, expect=1):
    """Insert block (list of lines, no newline) after every line equal to anchor; expect that many anchors."""
    global edits, errors
    data = read(path)
    nl = b"\r\n" if b"\r\n" in data else b"\n"
    lines = data.split(nl)
    hits = [i for i, l in enumerate(lines) if l == anchor]
    if len(hits) != expect:
        print(f"ANCHOR_MISMATCH {path}: {anchor[:60]!r} found {len(hits)} expected {expect}")
        errors += 1
        return
    for i in reversed(hits):
        lines[i + 1:i + 1] = [b.encode() for b in block]
    write(path, nl.join(lines))
    edits += len(hits)
    print(f"EDIT {os.path.relpath(path, WT)} after {anchor.decode()[:60]!r} +{len(block)} x{len(hits)}")


def region_logs(path):
    """For each public add/intersect/subtract/translate method, insert the log call before its Operation statement."""
    global edits, errors
    data = read(path)
    nl = b"\r\n" if b"\r\n" in data else b"\n"
    lines = data.split(nl)
    sig = re.compile(rb"^public void (add|intersect|subtract|translate) \((.*)\) \{$")
    out = []
    pending = None
    for l in lines:
        m = sig.match(l)
        if m:
            op, params = m.group(1).decode(), m.group(2).decode()
            if params.startswith("int[]"):
                arg = "pointArray"
            elif params.startswith("Rectangle"):
                arg = "rect"
            elif params.startswith("Region"):
                arg = "region"
            elif params.startswith("Point"):
                arg = "pt"
            elif params == "int x, int y, int width, int height":
                arg = "new Rectangle(x,y,width,height)"
            elif params == "int x, int y":
                arg = "new Point(x,y)"
            else:
                arg = None
            pending = f"\tlog.{op}({arg});".encode() if arg else None
        if pending and l.startswith(b"\tfinal Operation operation = new Operation"):
            out.append(pending)
            edits += 1
            pending = None
        out.append(l)
    if pending:
        print(f"ANCHOR_MISMATCH {path}: trailing pending log call")
        errors += 1
    write(path, nl.join(out))
    print(f"EDIT {os.path.relpath(path, WT)} region log calls")


region_logs(os.path.join(BASE, "win32", "org", "eclipse", "swt", "graphics", "Region.java"))
insert_after(
    os.path.join(BASE, "win32", "org", "eclipse", "swt", "graphics", "TextLayout.java"),
    b"void drawInPixels (GC gc, int xInPoints, int yInPoints, int selectionStart, int selectionEnd, Color selectionForeground, Color selectionBackground, int flags) {",
    ["", "\tif(gc instanceof GCExtension gcext) {",
     "\t\tgcext.textLayoutDraw(this, gc,  xInPoints,  yInPoints,  selectionStart,  selectionEnd,  selectionForeground,  selectionBackground,  flags);",
     "\t\treturn;", "\t}", ""])
canvas = os.path.join(BASE, "gtk", "org", "eclipse", "swt", "widgets", "Canvas.java")
insert_after(canvas, b"\tif ((state & OBSCURED) != 0) return 0;",
             ["", "\tif(externalCanvasHandler != null) {",
              "\t\texternalCanvasHandler.paint((e)-> sendEvent(SWT.Paint, e),widget, cairo);",
              "\t\treturn 0;", "\t}", ""])
insert_after(canvas, b"public void scroll (int destX, int destY, int x, int y, int width, int height, boolean all) {",
             ["\tif(this.externalCanvasHandler != null) {",
              "\t\tthis.externalCanvasHandler.scroll(destX, destY, x, y, width, height, all);",
              "\t\treturn;", "\t}"])
for root, _, files in os.walk(os.path.join(WT, "bundles")):
    for f in files:
        if f.endswith(".rej"):
            os.remove(os.path.join(root, f))
print(f"edits={edits} errors={errors}")
sys.exit(2 if errors else 0)
