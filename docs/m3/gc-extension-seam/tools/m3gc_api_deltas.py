# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""The smallest mechanical edits that make the adopted I31 backend compile in the M3 fork (leaf 2).

Each delta is one exact text replacement in one adopted file (count 1 unless `all`), anchored on the bytes
m3gc_adopt.py produced; the javac line that demanded it (or `seam` for a seam contract) and the reason are the
record. Running the script applies every delta that is still pending (idempotent: a delta whose `new` text is
present and `old` text absent is DONE) and appends the applied rows to RECIPE_MEMORY.tsv.

FORWARD mode (census L12, docs/m3/gc-extension-seam/LEAF12_ONE_SKIJA.md): `--forward` runs the other way, Skija 0.116.3
-> 0.143.18 (types 0.2.0). Every API break between the two is one typed delta atom in FORWARD:
  REVERT  restores the I31 original (0.143.16) that a D delta rewrote backwards: exactly D.new -> D.old (`reverses`)
  PORT    moves fork-only code written against 0.116.3 to the 0.143 API (there is no I31 original to restore)
  RULE    a pattern over both M3 trees; NO_SITE is a valid state (the code that needed it was retired), a site with no
          mechanical replacement is reported MANUAL_SITE and fails the run
  CHECK   no edit: a counted assertion that an API whose behaviour or binding changed is used compatibly (CHECK_OK)
  PIN     the one version line, skija.version in bundles/org.eclipse.swt.m3/pom.xml (SKIJA_PIN v1)
The run repeats until a round changes nothing (fixed point: a second run reports only DONE/NO_SITE/CHECK_OK), appends the
applied rows to RECIPE_MEMORY.tsv as L12-NN and exits 0 only at a fixed point with no MISSING_ANCHOR/AMBIGUOUS/MANUAL_SITE.

Usage: python -I m3gc_api_deltas.py <worktree>                       (backward, leaf 2: D01-D17)
       python -I m3gc_api_deltas.py --forward <worktree> [--dry-run]  (forward, leaf 12: L12-01..L12-17)
"""
import re
import sys
import os
if __name__ == "__main__":  # M3_GRAPHICS_GUARD v1: NOT_APPLICABLE on jdk.m3.graphics trees
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from m3_graphics_guard import exit_if_m3_graphics
    exit_if_m3_graphics(sys.argv)
from pathlib import Path

GC = "bundles/org.eclipse.swt/Eclipse SWT Render M3/win32/org/eclipse/swt/internal/render/m3/gc/"
RM = "bundles/org.eclipse.swt/Eclipse SWT Render M3/win32/org/eclipse/swt/internal/render/m3/"
GPU = "bundles/org.eclipse.swt/Eclipse SWT GPU M3/win32/org/eclipse/swt/internal/gpu/m3/"
TREES = ("bundles/org.eclipse.swt/Eclipse SWT Render M3/win32", "bundles/org.eclipse.swt/Eclipse SWT GPU M3/win32")
POM = "bundles/org.eclipse.swt.m3/pom.xml"
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
    # Leaf 4, P02 (PRE_EXISTING.tsv): the GPU M3 tree's Replay.poly was written against 0.143's PathBuilder; the same
    # delta as D07/D08 (Path is its own builder, one close, drawn directly: no detach())
    ("D16", GPU + "Replay.java", "\t\ttry (PathBuilder builder = new PathBuilder ()) {", "\t\ttry (Path builder = new Path ()) {", False,
     "Replay.java:174: error: cannot find symbol  symbol: class PathBuilder (x2)",
     "P02: Skija 0.116.3 has no PathBuilder; Path.moveTo/lineTo/closePath return the Path itself (D07)"),
    ("D17", GPU + "Replay.java",
     "\t\t\ttry (Path path = builder.detach ()) {\n\t\t\t\tCanvas._nDrawPath (canvas, path._ptr, backend.paint (a [o + 2], a [o + 1]));\n\t\t\t}\n",
     "\t\t\tCanvas._nDrawPath (canvas, builder._ptr, backend.paint (a [o + 2], a [o + 1]));\n", False,
     "Replay.java:180: error: cannot find symbol  symbol: method detach()",
     "P02: the Path is drawn directly and closed once by the outer try (D08: one close, not two)"),
]


# Leaf 12, forward 0.116.3 -> 0.143.18 (types 0.2.0). javac lines: BLLSE of compile_m3gc.sh on 0.143.18 before the deltas
# (LEAF12_JAVAC_ERRORS.tsv, file:line:col); 0.143 API facts: skija-shared-0.143.18 sources and CHANGELOG (0.143.0 PathBuilder,
# 0.143.3 Managed.close idempotent, 0.143.4 FontMgr.matchFamily*(null) = default font, 0.143.5 Region.Op -> RegionOp).
# (id, kind, api, file | D id | trees, old, new, javac_line, reason); REVERT takes file/old/new from the D delta it inverts.
BY_ID = {d[0]: d for d in DELTAS}
BUILD = "PathBuilder.build() returns the immutable Path and closes the builder (0.143.18); the outer try closes it again, a no-op since 0.143.3"
FORWARD = [
    ("L12-01", "REVERT", "PathBuilder", "D07", None, None,
     "M3ExternalGC.java:429:16,432:17,435:16: error: cannot find symbol  symbol: method moveTo(int,int), lineTo(int,int), closePath()  location: variable pathBuilder of type Path",
     "0.143.0 moved the mutable Path methods to PathBuilder: the I31 original (SkiaGC.drawPolygon) builds with PathBuilder"),
    ("L12-02", "REVERT", "PathBuilder", "D08", None, None, "pair of L12-01 (one backward pair D07/D08)", BUILD),
    ("L12-03", "REVERT", "PathBuilder", "D09", None, None,
     "M3PathConverter.java:40:19,43:19,46:19,51:19,55:19: error: cannot find symbol  symbol: method moveTo(float,float), lineTo(float,float), cubicTo(6 x float), quadTo(4 x float), closePath()  location: variable m3PathBuilder of type Path",
     "I31 original (SkiaPathConverter.convertSWTPathToSkijaPath): PathBuilder in try-with-resources"),
    ("L12-04", "REVERT", "PathBuilder", "D10", None, None, "pair of L12-03 (one backward pair D09/D10)", BUILD + "; the caller owns the Path"),
    ("L12-05", "REVERT", "PathBuilder", "D11", None, None,
     "M3PathConverter.java:73:15,76:16,79:15: error: cannot find symbol  symbol: method moveTo(int,int), lineTo(int,int), closePath()  location: variable pathBuilder of type Path",
     "I31 original (SkiaPathConverter.createPath): PathBuilder in try-with-resources; setFillMode is a PathBuilder method in 0.143"),
    ("L12-06", "REVERT", "PathBuilder", "D12", None, None, "pair of L12-05 (one backward pair D11/D12)", BUILD + "; the caller owns the Path"),
    ("L12-07", "REVERT", "PathBuilder", "D16", None, None,
     "Replay.java:177:11,178:43,179:42: error: cannot find symbol  symbol: method moveTo(float,float), lineTo(float,float), closePath()  location: variable builder of type Path",
     "the GPU M3 tree was written for 0.143.18 (M3PlaneBackend header): Replay.poly builds with PathBuilder (P02 undone)"),
    ("L12-08", "REVERT", "PathBuilder", "D17", None, None, "pair of L12-07 (one backward pair D16/D17)",
     "PathBuilder.detach() returns the Path and resets the builder; the detached Path is drawn by its pointer and closed by its own try"),
    ("L12-09", "PORT", "PathBuilder", RM + "M3RowCanvas.java", "\tprivate final Path path = new Path ();\n",
     "\tprivate final PathBuilder path = new PathBuilder (); // Skija 0.143: the mutable path is a PathBuilder (L12-09)\n",
     "M3RowCanvas.java:455:7,456:39,457:7: error: cannot find symbol  symbol: method moveTo(int,int), lineTo(int,int), closePath()  location: variable path of type Path",
     "fork-only code (row canvas polygon, written for 0.116.3): the reused field becomes the builder; reset() and close() are PathBuilder methods too"),
    ("L12-10", "PORT", "PathBuilder", RM + "M3RowCanvas.java", "\t\tcanvas.drawPath (path, paint);\n",
     "\t\ttry (Path shape = path.detach ()) { // Skija 0.143: draw the immutable Path the builder detaches (L12-10)\n"
     "\t\t\tcanvas.drawPath (shape, paint);\n\t\t}\n",
     "pair of L12-09: Canvas.drawPath(PathBuilder, Paint) has no overload",
     "detach() hands over the built Path and resets the builder for the next polygon (one native Path per polygon, closed at once)"),
    ("L12-11", "RULE", "RegionOp", TREES, r"\bio\.github\.humbleui\.skija\.Region\.Op\b|\bRegion\.Op\b", "io.github.humbleui.skija.RegionOp",
     "none on 0.143.18 (no site left)",
     "reverses D05/D06: 0.143.5 renamed Region.Op to the top-level RegionOp; leaf 6 (CLIP_STATE) retired M3RegionCalculator's ops, so 0 sites"),
    ("L12-12", "RULE", "PathBuilder", TREES, r"\.addPoly\s*\(", None, "none on 0.143.18 (no site left)",
     "reverses D13/D14/D15: Path.addPoly(float[], boolean) is PathBuilder.addPolygon in 0.143; the only site (M3RegionCalculator) was retired by leaf 6; a new site needs a PathBuilder (MANUAL_SITE)"),
    ("L12-13", "CHECK", "FontMgr", (GC + "M3Resources.java",), r"fm\.matchFamilyStyle\(null, style\)", 2, "no javac line (same signature)",
     "0.143.4: FontMgr.matchFamily*(null) returns the default font; both fallbacks ask for exactly that (\"use system default\"): compatible, no edit"),
    ("L12-14", "CHECK", "Canvas", TREES, r"\bCanvas\._n[A-Z]\w*\s*\(", 1, "no javac line: every used Canvas._n* native resolves on 0.143.18",
     "the GPU M3 tree calls Canvas natives directly; the descriptors of every used native are compared by m3gc_native_sig_gate.sh (NATIVE_SIG_GATE v1)"),
    ("L12-15", "CHECK", "Graphics", TREES, r"\bGraphics\.(init|set|get|purge)\w*\s*\(", 0, "no javac line (class new in 0.143.17)",
     "io.github.humbleui.skija.Graphics (font/resource cache limits) has no 0.116.3 counterpart and no use: nothing to port; CACHE_LIMITS adopts it after the switch"),
    ("L12-16", "CHECK", "FontMgr", TREES, r"\bFontMgr\.useSystemRenderingParams\s*\(", 0, "no javac line (method new in 0.143.11)",
     "FontMgr.useSystemRenderingParams(boolean) has no 0.116.3 counterpart and no use: nothing to port; CLEARTYPE_PARAMS adopts it after the switch"),
    ("L12-17", "PIN", "SKIJA_PIN", POM, "<skija.version>0.116.3</skija.version>", "<skija.version>0.143.18</skija.version>",
     "seam (SKIJA_PIN v1)", "the one Skija version of the fragment and of every gate (m3_skija_pin.sh); types 0.2.0 follows from skija-shared 0.143.18's POM"),
]


def java_files(worktree, trees):
    return [p for t in trees for p in sorted((Path(worktree) / t).rglob("*.java"))]


def forward_one(worktree, atom, dry):
    fid, kind, api, where, old, new, javac, reason = atom
    if kind == "REVERT":
        _, where, new, old = BY_ID[where][:4]
    if kind in ("REVERT", "PORT", "PIN"):
        state = apply_text(Path(worktree) / where, old, new, False, dry)
        return state, [(fid, where, old, new, javac, "%s %s: %s" % (kind, api, reason))] if state == "APPLIED" else []
    paths = [Path(worktree) / w for w in where] if kind == "CHECK" and where[0].endswith(".java") else java_files(worktree, where)
    hits = {p: len(re.findall(old, p.read_text(encoding="utf-8"))) for p in paths if p.exists()}
    total = sum(hits.values())
    if kind == "CHECK":
        return ("CHECK_OK(%d)" % total) if (total >= new if new else total == 0) else "CHECK_FAILED(%d)" % total, []
    if total == 0:
        return "NO_SITE", []
    if new is None:
        return "MANUAL_SITE(%d)" % total, []
    rows = []
    for p, n in hits.items():
        if n and not dry:
            p.write_bytes(re.sub(old, new, p.read_text(encoding="utf-8")).encode("utf-8"))
        if n:
            rows.append((fid, p.relative_to(worktree).as_posix(), old, new, javac, "%s %s: %s" % (kind, api, reason)))
    return "APPLIED", rows


def apply_text(path, old, new, every, dry):
    text = path.read_bytes().decode("utf-8")
    if old not in text:
        return "DONE" if new in text else "MISSING_ANCHOR"
    count = text.count(old)
    if count != 1 and not every:
        return "AMBIGUOUS(%d)" % count
    if not dry:
        path.write_bytes(text.replace(old, new).encode("utf-8"))
    return "APPLIED"


def forward(worktree, dry=False):
    states, rows = {}, []
    for round_no in range(1, 5):
        changed = False
        for atom in FORWARD:
            state, new_rows = forward_one(worktree, atom, dry)
            states[atom[0]] = state
            rows += new_rows
            changed |= state == "APPLIED" and not dry
        if not changed:
            break
    for atom in FORWARD:
        target = atom[3] if isinstance(atom[3], str) else atom[3][0] if atom[3][0].endswith(".java") else "M3 trees"
        target = BY_ID[target][1] if atom[1] == "REVERT" else target
        print("%s\t%s\t%s\t%s\t%s" % (atom[0], atom[1], atom[2], states[atom[0]], target.rsplit("/", 1)[-1]))
    bad = [k for k, v in states.items() if v.split("(")[0] in ("MISSING_ANCHOR", "AMBIGUOUS", "MANUAL_SITE", "CHECK_FAILED")]
    fixed = "APPLIED" not in states.values() if dry else not changed
    print("FORWARD rounds=%d applied=%d bad=%s %s" % (round_no, len(rows), ",".join(bad) or "-", "FIXED_POINT" if fixed and not bad else "NOT_FIXED"))
    if not dry:
        record(worktree, rows)
    return 0 if fixed and not bad else 1


def record(worktree, rows):
    memory = Path(worktree) / MEMORY
    if not memory.exists():
        memory.write_text(HEADER, encoding="utf-8", newline="")
    recorded = memory.read_text(encoding="utf-8")
    esc = lambda s: s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
    out = ["\t".join((r[0], r[1], esc(r[2]), esc(r[3] or ""), esc(r[4]), r[5])) + "\n" for r in rows
           if ("\n" + r[0] + "\t") not in recorded]
    if out:
        with memory.open("a", encoding="utf-8", newline="") as f:
            f.writelines(out)


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
    if sys.argv[1] == "--forward":
        sys.exit(forward(sys.argv[2], "--dry-run" in sys.argv[3:]))
    main(sys.argv[1])
