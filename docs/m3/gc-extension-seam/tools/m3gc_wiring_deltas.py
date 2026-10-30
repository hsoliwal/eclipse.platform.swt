# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Leaf 3 (drawn planes: the owner-draw protocol with a real GC): the corrections of adopted leaf-2 classes it needs, as
exact byte-preserving text replacements (count 1), plus the record of the seam deltas made by hand in the owner files
(Read + Edit), all appended to RECIPE_MEMORY.tsv with ids L3-xx (same six columns as leaf 2).

Idempotent: a delta whose `new` text is present and `old` text absent is DONE; a record-only row (old and new are
descriptions, no file edit) is appended once. Usage: python -I m3gc_wiring_deltas.py <worktree>
"""
import sys
from pathlib import Path

GC = "bundles/org.eclipse.swt/Eclipse SWT Render M3/win32/org/eclipse/swt/internal/render/m3/gc/"
M3 = "bundles/org.eclipse.swt/Eclipse SWT Render M3/win32/org/eclipse/swt/internal/render/m3/"
RENDER = "bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/internal/render/"
COMMON = "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/render/"
WIDGETS = "bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/widgets/"
MEMORY = "docs/m3/gc-extension-seam/RECIPE_MEMORY.tsv"
HEADER = "delta_id\tfile\told\tnew\tjavac_line\treason\n"

DISPOSE_OLD = (
    "\t\tsurface.getCanvas().restoreToCount(initialSaveCount);\n"
    "\n"
    "\t}\n")
DISPOSE_NEW = (
    "\t\tsurface.getCanvas().restoreToCount(initialSaveCount);\n"
    "\t\t// M3 (GC seam, leaf 3): the GC is reusable after dispose. A drawn plane disposes it after each owner-draw event\n"
    "\t\t// and hands the same object to the next one, so the clip and transform bookkeeping goes back to the constructed\n"
    "\t\t// state along with the canvas (a stale isClipSet would pop the plane's own frame clip on the next setClipping).\n"
    "\t\tisClipSet = false;\n"
    "\t\tcurrentClipRegion = null;\n"
    "\t\tcurrentClipBounds = m3Extension.getClientArea();\n"
    "\t\tcurrentTransform = Matrix33.IDENTITY;\n"
    "\n"
    "\t}\n")

# (id, file, old, new, javac_line, reason, apply)  -- apply False: record only (the delta was made by Read + Edit)
DELTAS = [
    ("L3-01", GC + "M3ExternalGC.java", DISPOSE_OLD, DISPOSE_NEW, "seam",
     "dispose() restored the canvas but kept isClipSet/currentClip*/currentTransform: a reused GC's next setClipping would restore() the frame's own save; "
     "the plane disposes the one GCExtension per event (as the native widget disposes its GC) and reuses it", True),
    ("L3-02", M3 + "M3RowCanvas.java", "no surface accessor; no GC seam",
     "public Surface surface () (re-read per call: target() replaces it when the buffer grows); gc(Control, BackBuffer) = one M3PlaneCanvasExtension per canvas, "
     "one GCExtension(new M3ExternalGC(extension, SWT.NONE)) per surface made inside a frame (save count = the frame's), dropped in target() with the surface",
     "seam", "the planes need a GC over the M3 renderer on their own surface (leaf 2 README: leaf 3 constructs the extension per plane)", False),
    ("L3-03", RENDER + "BufferCanvas.java", "no GC on the common canvas interface",
     "default GC gc (Control control, BackBuffer buffer) { return null; } overridden by GcRowCanvas (its frame GC, made on first use) and M3RowCanvas; RowCanvas untouched",
     "seam", "the plane (widgets) sees only GC; no Skija type on the interface, nothing new loaded by name (M3RowCanvas is loaded already)", False),
    ("L3-04", COMMON + "RowRenderer.java", "paint(): theme.rowPainter or paintDefault; background/selection/expander/checkBox/gridLines/focus private",
     "override (Theme.RowPainter) installed per frame wins over the theme's; the pieces are public, background split into rowBackground + cellBackground (the SWT.BACKGROUND offer)",
     "seam", "the owner-draw painter interleaves the events with the default pieces per cell; RetainedRows.paint still calls renderer.paint (counters, slots)", False),
    ("L3-05", WIDGETS + "DrawnPlane.java", "behaviour pass: native NM_CUSTOMDRAW sent MeasureItem/EraseItem/PaintItem with a GC on the back-buffer DC, then the drawn pass painted over the listener's drawing",
     "while ownerDraw.hooked(): ignoreNativeCustomDraw(true) around CallWindowProc (Table/Tree.ignoreCustomDraw, previous value restored); the drawn pass raises the events (DrawnOwnerDraw); "
     "the render thread is bypassed for such frames (rule 28: no GC there); retain=false for them",
     "seam", "one source of events per paint, with a GC whose drawing reaches the screen; measured by grep: the four plane files had no custom-draw event before", False),
    ("L3-06", COMMON + "DrawnCustomDraw.java", "-",
     "new headless protocol class: hooks, Sender, Supplier<GC>, Consumer<GC> released; one reusable Event; measure/erase/paint per cell; clip in points edge by edge (DPIUtil)",
     "seam", "the sequencing, fields, clip and release are testable without a Display (tests/.../junit/m3gc/Test_org_eclipse_swt_m3gc_DrawnCustomDraw)", False),
    ("L3-07", "docs/m3/gc-extension-seam/tools/test_m3gc.sh", "junit JVM without swt.library.path: 66 found, 29 passed, 37 failed on master (testDrawLine first: UnsatisfiedLinkError Could not load SWT library, "
     "then NoClassDefFoundError Device)",
     "M3_NATIVES (default H:/m3-scratch/gc-wiring/natives, staged by tools/m3gc_natives.sh under the worktree's Library revision names) passed as -Dswt.library.path",
     "junit.log: java.lang.UnsatisfiedLinkError: Could not load SWT library ... no swt-osversion-win32-4975r19 in java.library.path",
     "root cause is the base, not leaf 3: Color(null, ...) -> Resource(Device) -> Device.getDevice() -> Device.<clinit> Class.forName(Display) -> OS -> Library in every draw test; "
     "leaf 2 passed 59/59 at Library REVISION 15 because ~/.swt/lib/win32/x86_64 holds swt-*-4975r15.dll; master (ef6af883b04) is REVISION 19 and no r19 dll exists on this machine", False),
    ("L3-08", "docs/m3/gc-extension-seam/tools/m3gc_baseline_overlay.py", "SystemExit('P01: anchor not unique') on master, where P01 is fixed: no overlay written, compile_m3gc.sh with M3_OVERLAY still showed the 2 P02 errors",
     "a defect whose anchors are all absent is reported as fixed and skipped; the other overlays are still written; PRE_EXISTING.tsv output unchanged",
     "overlay.log: P01: anchor not unique in .../win32/.../graphics/Region.java", "the gate must be reproducible from master, where leaf 1's correction removed P01", False),
]


def tsv(value):
    return value.replace("\t", "\\t").replace("\n", "\\n")


def main(worktree):
    wt = Path(worktree)
    memory = wt / MEMORY
    existing = memory.read_text(encoding="utf-8") if memory.exists() else HEADER
    if not existing.startswith(HEADER):
        print("RECIPE_MEMORY.tsv: unexpected header")
        return 1
    known = {line.split("\t", 1)[0] for line in existing.splitlines()[1:] if line}
    rows = []
    for delta_id, rel, old, new, javac, reason, apply in DELTAS:
        if apply:
            path = wt / rel
            text = path.read_bytes().decode("utf-8")
            if new in text and old not in text:
                print(f"{delta_id} DONE {rel}")
            else:
                count = text.count(old)
                if count != 1:
                    print(f"{delta_id} ANCHOR x{count} {rel}")
                    return 1
                path.write_bytes(text.replace(old, new).encode("utf-8"))
                print(f"{delta_id} APPLIED {rel}")
        if delta_id not in known:
            rows.append("\t".join([delta_id, rel, tsv(old), tsv(new), javac, reason]) + "\n")
    if rows:
        if not existing.endswith("\n"):
            existing += "\n"
        memory.write_bytes((existing + "".join(rows)).encode("utf-8"))
        print(f"RECIPE_MEMORY.tsv: +{len(rows)} rows")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "C:/m3-superset/swt-gcseam"))
