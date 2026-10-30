# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Baseline overlay for the leaf-2 gates: the pre-existing javac errors of the base branch, repaired in scratch copies only.

The base branch m3/gc-extension-seam-20261030 (leaf 1, e86f5f6fbcb) and the GPU M3 tree do not compile against the
fork's Skija 0.116.3 on win32 (see PRE_EXISTING below); those files belong to other leaves (no leaf escape, DRY-RUN:
proposals only), so this script writes repaired copies to an overlay directory outside the worktree, compile_m3gc.sh
compiles the overlay copy instead of the original (M3_OVERLAY), and docs/m3/gc-extension-seam/PRE_EXISTING.tsv records
each defect with the proposed owner fix (identical to the overlay edit). The worktree files are never modified.

Usage: python -I m3gc_baseline_overlay.py <worktree> <overlay-dir>
"""
import sys
from pathlib import Path

WIN32_REGION = "bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/graphics/Region.java"
REPLAY = "bundles/org.eclipse.swt/Eclipse SWT GPU M3/win32/org/eclipse/swt/internal/gpu/m3/Replay.java"
COCOA_REGIONLOG = "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/graphics/RegionLog.java"
COLUMNS = ("id", "platform", "file", "line", "javac", "origin", "owner", "proposed_fix", "overlay")

# (id, platform, file, line, javac message, origin, owner, proposed fix, [(old, new), ...] or None when not overlaid)
PRE_EXISTING = [
    ("P01", "win32", WIN32_REGION, 584, "cannot find symbol: variable region (in set(ZoomToRegionMap, int))",
     "leaf 1 e86f5f6fbcb, seam_rejects.py re-applied the I31 Region log call into set() as well as intersect(Region) (line 436)",
     "gc-extension-seam leaf 1", "delete the duplicate `log.intersect(region);` in set(ZoomToRegionMap, int); intersect(Region) already logs",
     [("\tthis.temporaryHandleZoomHint = contextZoom;\n\tlog.intersect(region);\n", "\tthis.temporaryHandleZoomHint = contextZoom;\n")]),
    ("P02", "win32", REPLAY, 174, "cannot find symbol: class PathBuilder (x2)",
     "GPU M3 tree (merge 2153916d3ef) written against a Skija with PathBuilder; the fork binds skija 0.116.3 (RENDER_MODES.md)",
     "render-modes / GPU M3", "Path is its own builder in 0.116.3: `try (Path builder = new Path ())`, draw `builder` directly (no detach)",
     [("\t\ttry (PathBuilder builder = new PathBuilder ()) {", "\t\ttry (Path builder = new Path ()) {"),
      ("\t\t\ttry (Path path = builder.detach ()) {\n\t\t\t\tCanvas._nDrawPath (canvas, path._ptr, backend.paint (a [o + 2], a [o + 1]));\n\t\t\t}\n",
       "\t\t\tCanvas._nDrawPath (canvas, builder._ptr, backend.paint (a [o + 2], a [o + 1]));\n")]),
    ("P03", "cocoa", COCOA_REGIONLOG, 45, "cannot find symbol: method getLog() (cocoa Region has no RegionLog)",
     "leaf 1 e86f5f6fbcb: the I31 Region log field and getLog() were added to win32 and gtk Region only",
     "gc-extension-seam leaf 1", "add `private final RegionLog log`, the add/intersect/subtract log calls and `RegionLog getLog()` to cocoa Region.java as gtk has",
     None),
    ("P04", "tests", "tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/m3gc/Test_org_eclipse_swt_m3gc_drawText_NoCache.java", 29,
     "test-order leak: System.setProperty(M3TextDrawing.NO_TEXT_CACHE, \"true\") is never cleared; a classpath scan that runs this class before "
     "drawText/drawString fails those 6 tests (Expected 4 method calls, was 2); I31's suite order (AllM3GcTests, NoCache last) passes",
     "I31 head 9bcb404c42 tests/doubles/Test_org_eclipse_swt_skia_drawText_NoCache.java (identical logic), observed with junit-platform-console-standalone 1.11.4 --scan-classpath",
     "I31 tests (donor)", "clear the property in an @AfterAll (System.clearProperty(M3TextDrawing.NO_TEXT_CACHE)); the gate runs the suite order meanwhile",
     None),
]


def main(worktree, overlay):
    rows = ["\t".join(COLUMNS)]
    for pid, platform, rel, line, javac, origin, owner, fix, edits in PRE_EXISTING:
        if edits:
            source = Path(worktree) / rel
            text = source.read_bytes().decode("utf-8")
            for old, new in edits:
                if text.count(old) != 1:
                    raise SystemExit("%s: anchor not unique in %s: %r" % (pid, rel, old[:60]))
                text = text.replace(old, new)
            target = Path(overlay) / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(text.encode("utf-8"))
            print("%s overlay %s" % (pid, rel))
        rows.append("\t".join((pid, platform, rel, str(line), javac, origin, owner, fix, "yes" if edits else "no")))
    out = Path(worktree) / "docs/m3/gc-extension-seam/PRE_EXISTING.tsv"
    out.write_text("\n".join(rows) + "\n", encoding="utf-8", newline="")
    print(out)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
