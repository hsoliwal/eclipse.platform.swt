# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""The smallest mechanical edits that make the adopted I31 backend compile in the M3 fork (leaf 2).

Each delta is one exact text replacement in one adopted file (count 1 unless `all`), anchored on the bytes
m3gc_adopt.py produced; the javac line that demanded it (or `seam` for a seam contract) and the reason are the
record. Running the script applies every delta that is still pending (idempotent: a delta whose `new` text is
present and `old` text absent is DONE) and appends the applied rows to RECIPE_MEMORY.tsv.

Usage: python -I m3gc_api_deltas.py <worktree>
"""
import sys
from pathlib import Path

GC = "bundles/org.eclipse.swt/Eclipse SWT Render M3/win32/org/eclipse/swt/internal/render/m3/gc/"
TESTS = "tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/m3gc/"
MEMORY = "docs/m3/gc-extension-seam/RECIPE_MEMORY.tsv"
HEADER = "delta_id\tfile\told\tnew\tjavac_line\treason\n"

# (id, file, old, new, all, javac_line, reason)
DELTAS = [
    ("D01", GC + "M3Resources.java", "import org.eclipse.swt.widgets.Canvas;", "import org.eclipse.swt.widgets.Control;", False,
     "seam", "the plane's widget is a Table/Tree, not a Canvas: the resources only use Control methods (colours, font, display, listeners)"),
    ("D02", GC + "M3Resources.java", "\tprivate final Canvas canvas;", "\tprivate final Control canvas;", False,
     "seam", "see D01"),
    ("D03", GC + "M3Resources.java", "public M3Resources(Canvas canvas, IM3CanvasExtension m3Extension)",
     "public M3Resources(Control canvas, IM3CanvasExtension m3Extension)", False, "seam", "see D01"),
    ("D04", GC + "M3CanvasFactory.java", "\t\t\treturn new M3PlaneCanvasExtension(c);",
     "\t\t\t/* the M3 planes own their surfaces (M3PlaneCanvasExtension, leaf 3); a per-Canvas handler is a later leaf:\n"
     "\t\t\t * I31's SkiaGlCanvasExtension (GL context and surface per Canvas) is PATTERN_ONLY, see FOSS_REUSE_DECISION.tsv */\n"
     "\t\t\treturn null;", False, "seam",
     "the factory keeps I31's shape (IExternalCanvasFactory, failure latch, Logger) and stays unregistered in leaf 2"),
    # Skija API deltas: I31 targets 0.143.16, the fork binds 0.116.3 (RegionOp -> Region.Op; PathBuilder -> Path)
    ("D05", GC + "M3RegionCalculator.java", "import io.github.humbleui.skija.RegionOp;\n", "", False,
     "M3RegionCalculator.java:22: error: cannot find symbol  symbol: class RegionOp",
     "Skija 0.116.3 has the nested enum Region.Op; the top-level RegionOp is 0.143"),
    ("D06", GC + "M3RegionCalculator.java", "RegionOp", "io.github.humbleui.skija.Region.Op", True,
     "M3RegionCalculator.java:27,30,31,32,73,90: error: cannot find symbol  symbol: class/variable RegionOp",
     "see D05; same constants UNION, DIFFERENCE, INTERSECT"),
    ("D07", GC + "M3ExternalGC.java",
     "try (io.github.humbleui.skija.PathBuilder pathBuilder = new io.github.humbleui.skija.PathBuilder()) {",
     "try (io.github.humbleui.skija.Path pathBuilder = new io.github.humbleui.skija.Path()) {", False,
     "M3ExternalGC.java:371: error: cannot find symbol  symbol: class PathBuilder",
     "Skija 0.116.3 has no PathBuilder: Path is its own builder (moveTo/lineTo/closePath return Path)"),
    ("D08", GC + "M3ExternalGC.java", "try (var path = pathBuilder.build()) {",
     "{\n\t\t\t\t\tvar path = pathBuilder; // Skija 0.116.3: Path is its own builder; the outer try closes it", False,
     "M3ExternalGC.java:382: error: cannot find symbol  symbol: method build()", "see D07; one close, not two"),
    ("D09", GC + "M3PathConverter.java",
     "try (final io.github.humbleui.skija.PathBuilder m3PathBuilder = new io.github.humbleui.skija.PathBuilder()) {",
     "{\n\t\t\tfinal io.github.humbleui.skija.Path m3PathBuilder = new io.github.humbleui.skija.Path(); // Skija 0.116.3: Path is its own builder; the caller owns the returned path",
     False, "M3PathConverter.java:31: error: cannot find symbol  symbol: class PathBuilder", "see D07; the path is returned, so no try-with-resources"),
    ("D10", GC + "M3PathConverter.java", "final var p = m3PathBuilder.build();", "final var p = m3PathBuilder;", False,
     "M3PathConverter.java:59: error: cannot find symbol  symbol: method build()", "see D09"),
    ("D11", GC + "M3PathConverter.java",
     "try (io.github.humbleui.skija.PathBuilder pathBuilder = new io.github.humbleui.skija.PathBuilder()) { // Move",
     "{ // Skija 0.116.3: Path is its own builder; the caller owns the returned path\n\t\t\tio.github.humbleui.skija.Path pathBuilder = new io.github.humbleui.skija.Path(); // Move",
     False, "M3PathConverter.java:67: error: cannot find symbol  symbol: class PathBuilder", "see D09"),
    ("D12", GC + "M3PathConverter.java", "return pathBuilder.build();", "return pathBuilder;", False,
     "M3PathConverter.java:80: error: cannot find symbol  symbol: method build()", "see D09"),
    ("D13", GC + "M3RegionCalculator.java", "try (final var pathBuilder = new io.github.humbleui.skija.PathBuilder()) {",
     "try (final var pathBuilder = new io.github.humbleui.skija.Path()) {", False,
     "M3RegionCalculator.java:121: error: cannot find symbol  symbol: class PathBuilder", "see D07"),
    ("D14", GC + "M3RegionCalculator.java", "pathBuilder.addPolygon(scaler.autoScaleUp(toFloat(polygon)), true);",
     "pathBuilder.addPoly(scaler.autoScaleUp(toFloat(polygon)), true);", False,
     "M3RegionCalculator.java:122: error: cannot find symbol  symbol: method addPolygon(float[],boolean)",
     "Skija 0.116.3 Path.addPoly(float[], boolean) is 0.143's PathBuilder.addPolygon"),
    ("D15", GC + "M3RegionCalculator.java", "try (var path = pathBuilder.build()) {",
     "{\n\t\t\t\tvar path = pathBuilder; // Skija 0.116.3: Path is its own builder; the outer try closes it", False,
     "M3RegionCalculator.java:131: error: cannot find symbol  symbol: method build()", "see D07; one close, not two"),
]


def apply(worktree, delta):
    delta_id, rel, old, new, every, javac, reason = delta
    path = Path(worktree) / rel
    raw = path.read_bytes()
    text = raw.decode("utf-8")
    if old not in text:
        return "DONE" if new in text else "MISSING_ANCHOR"
    count = text.count(old)
    if count != 1 and not every:
        return "AMBIGUOUS(%d)" % count
    text = text.replace(old, new)
    path.write_bytes(text.encode("utf-8"))
    return "APPLIED"


def main(worktree):
    memory = Path(worktree) / MEMORY
    if not memory.exists():
        memory.write_text(HEADER, encoding="utf-8", newline="")
    recorded = memory.read_text(encoding="utf-8")
    rows = []
    for delta in DELTAS:
        state = apply(worktree, delta)
        delta_id, rel, old, new, every, javac, reason = delta
        print("%s\t%s\t%s" % (delta_id, state, rel.rsplit("/", 1)[-1]))
        if state == "APPLIED" and not recorded.startswith(delta_id + "\t") and ("\n" + delta_id + "\t") not in recorded:
            esc = lambda s: s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
            rows.append("\t".join((delta_id, rel, esc(old), esc(new), esc(javac), reason)) + "\n")
    if rows:
        with memory.open("a", encoding="utf-8", newline="") as out:
            out.writelines(rows)


if __name__ == "__main__":
    main(sys.argv[1])
