# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Leaf 6 (GC fidelity of M3ExternalGC against the stock win32 GC): the mechanical multi-site deltas as exact byte-preserving
text replacements with an expected occurrence count, plus the record of the semantic edits made by Read + Edit in the owner
files, the decisions and every correction of my own script, tests and code, all appended to LEAF6_RECIPE_MEMORY.tsv with ids
L6-xx (the six columns of RECIPE_MEMORY.tsv; merged into it after leaf 5 lands).

Idempotent: a delta whose `new` text is present and `old` text absent is DONE; a record-only row (apply False: old and new are
descriptions) is appended once. Usage: python -I m3gc_fidelity_deltas.py <worktree>
"""
import sys
from pathlib import Path

GC = "bundles/org.eclipse.swt/Eclipse SWT Render M3/win32/org/eclipse/swt/internal/render/m3/gc/"
WIDGETS = "bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/widgets/"
GRAPHICS = "bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/graphics/"
TESTS = "tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/m3gc/"
MEMORY = "docs/m3/gc-extension-seam/LEAF6_RECIPE_MEMORY.tsv"
HEADER = "delta_id\tfile\told\tnew\tjavac_line\treason\n"

# (id, file, old, new, evidence, reason, apply, count)  -- apply False: record only; count: occurrences of `old` replaced
DELTAS = [
    ("L6-01", GC + "M3PaintManager.java", "paint.setAntiAlias(gc.getAntialias() != SWT.OFF);",
     "paint.setAntiAlias(gc.getAntialias() == SWT.ON);",
     "Test_org_eclipse_swt_m3gc_HintState.testDefaultAntialiasDrawsAliased: expected <false> but was <true>",
     "HINT_STATE (M3-GC-HINT v1), GF06: the fresh GC answers SWT.DEFAULT (stock: no GDI+ graphics), and DEFAULT strokes and fills "
     "aliased as GDI does (and GDI+ SmoothingModeDefault); only ON antialiases, as fillPath already tested (performDraw, performDrawFilled)",
     True, 2),
    ("L6-02", GC + "M3TextDrawing.java",
     "final boolean antiAlias = gc.getAntialias() != SWT.OFF || gc.getTextAntialias() != SWT.OFF;",
     "final boolean antiAlias = gc.getTextAntialias() != SWT.OFF;",
     "Test_org_eclipse_swt_m3gc_HintState.testTextAntialiasAloneDecidesText: expected <false> but was <true>",
     "HINT_STATE, GF06: text smoothing is the text antialias alone (stock getTextAntialias javadoc: 'only for text drawing'; GDI+ "
     "TextRenderingHint); DEFAULT keeps the system smoothing (antialiased, the adopted text tests pin it); the cached and uncached paths",
     True, 2),
    ("L6-03", GC + "M3ExternalGC.java", "antialias 0 (= SWT.OFF), textAntiAlias SWT.ON, getInterpolation decoded from the sampling, "
     "setInterpolation(DEFAULT) = Mitchell, setTextAntialias unvalidated",
     "antialias and textAntiAlias start at SWT.DEFAULT; int interpolation (SWT.DEFAULT) stored by setInterpolation and answered by "
     "getInterpolation; DEFAULT and NONE sample nearest; setTextAntialias rejects values other than DEFAULT/OFF/ON (Read + Edit)",
     "Test_org_eclipse_swt_m3gc_HintState: 6 failures before (antialias expected <-1> but was <0>; interpolation 1 read back 2; "
     "no IllegalArgumentException; DEFAULT sampled Mitchell), 6/6 after",
     "HINT_STATE, GF06; stock W32G/GC.java getAntialias/getTextAntialias/getInterpolation (DEFAULT without GDI+), setTextAntialias "
     "(ERROR_INVALID_ARGUMENT); departs from I31 SkiaGC (fields at :70/:100, getInterpolation at :851)", False, 0),
    ("L6-04", GC + "M3ExternalGC.java", "setLineAttributes wrote width 1 into the caller's object for width <= 0 and stored "
     "autoScaleUp(width) (pixels) in lineWidth (points); getLineAttributes divided lineWidth by the zoom",
     "setLineAttributes keeps the width in points in a local (<= 0 -> 1), the caller's object untouched; getLineAttributes = new "
     "LineAttributes(lineWidth, cap, join, style, autoScaleDown(lineDashes), dashOffset, miterLimit), getLineAttributesInPixels "
     "removed (no other caller) (Read + Edit)",
     "Test_org_eclipse_swt_m3gc_StrokeScale: getLineWidth expected <2> but was <4>; width read back 1.5 for 3; caller width 0 "
     "became 1.0",
     "STROKE_SCALE (M3-GC-STROKE v2), GF14; stock W32G/GC.java setLineAttributes (SetLineAttributesOperation copies; point and "
     "pixel attributes apart), getLineAttributes; same defect in I31 SkiaGC.setLineAttributes (:1028)", False, 0),
    ("L6-05", GC + "M3PaintManager.java", "LINE_CUSTOM: even arrays x scaled line width, odd arrays doubled x autoScaleUp(1)",
     "LINE_CUSTOM: PathEffect.makeDash(doubled(lineDashes), phase): absolute pixel lengths, every array doubled (Read + Edit)",
     "Test_org_eclipse_swt_m3gc_StrokeScale: {4,2} width 3 at 200 % ran [48, 24] instead of [8, 4]; {3} ran 12 instead of 6",
     "STROKE_SCALE, GF11 = PRE_EXISTING P05 (leaf 5 proposal); stock W32G/GC.java getPen: GDI+ dashes / width with the array "
     "doubled, GDI PS_USERSTYLE absolute; same in I31 SkiaPaintManager.createPathEffect; the predefined styles keep the "
     "width-relative tables (GF12, approximated, not in this leaf)", False, 0),
]


def tsv(value):
    return value.replace("\t", "\\t").replace("\n", "\\n")


def apply_delta(wt, delta_id, rel, old, new, count):
    path = wt / rel
    text = path.read_bytes().decode("utf-8")
    if new in text and old not in text:
        print(f"{delta_id} DONE {rel}")
        return True
    found = text.count(old)
    if found != count:
        print(f"{delta_id} ANCHOR x{found} (expected {count}) {rel}")
        return False
    path.write_bytes(text.replace(old, new).encode("utf-8"))
    print(f"{delta_id} APPLIED x{count} {rel}")
    return True


def main(worktree):
    wt = Path(worktree)
    memory = wt / MEMORY
    existing = memory.read_text(encoding="utf-8") if memory.exists() else HEADER
    if not existing.startswith(HEADER):
        print("LEAF6_RECIPE_MEMORY.tsv: unexpected header")
        return 1
    known = {line.split("\t", 1)[0] for line in existing.splitlines()[1:] if line}
    rows = []
    for delta_id, rel, old, new, evidence, reason, apply, count in DELTAS:
        if apply and not apply_delta(wt, delta_id, rel, old, new, count):
            return 1
        if delta_id not in known:
            rows.append("\t".join([delta_id, rel, tsv(old), tsv(new), evidence, reason]) + "\n")
    if rows:
        if not existing.endswith("\n"):
            existing += "\n"
        memory.write_bytes((existing + "".join(rows)).encode("utf-8"))
        print(f"LEAF6_RECIPE_MEMORY.tsv: +{len(rows)} rows")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "C:/m3-superset/swt-gcfix"))
