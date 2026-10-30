# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Adopt the Initiative 31 skia_canvas backend sources as the M3 backend behind the GC seam (leaf 2).

Mechanical pass, byte preserving (line endings kept, written in binary): for every donor file of the manifest
(sha<TAB>path, I31 head 9bcb404c42) (1) GNU `sed -E -f m3-naming.sed` (the existing M3 naming rule set: Skija
tokens become M3, licence lines and humbleui imports stay), (2) the exact class renames of the brief and the
Skia identifier rule (Skia/skia inside an identifier -> M3/m3; prose is untouched), (3) package and import
rewrite into one package per tree (main: org.eclipse.swt.internal.render.m3.gc, tests: org.eclipse.swt.tests.junit.m3gc;
imports of the file's own new package are dropped), (4) one contributor line in an I31 Contributors block.
SkiaGlCanvasExtension is PATTERN_ONLY and skipped. Writes an adoption log (TSV) for FOSS_REUSE_DECISION.tsv.

Usage: python -I m3gc_adopt.py <manifest.tsv> <donor-root> <worktree> <log.tsv>
"""
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
SED_RULES = HERE / "m3-naming.sed"
MAIN_SRC = "bundles/org.eclipse.swt.skia/src/org/eclipse/swt/internal/"
TEST_SRC = "tests/org.eclipse.swt.skia.tests/JUnit Tests/org/eclipse/swt/tests/"
MAIN_DST = "bundles/org.eclipse.swt/Eclipse SWT Render M3/win32/org/eclipse/swt/internal/render/m3/gc/"
TEST_DST = "tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/m3gc/"
MAIN_PKG = "org.eclipse.swt.internal.render.m3.gc"
TEST_PKG = "org.eclipse.swt.tests.junit.m3gc"
PATTERN_ONLY = {"SkiaGlCanvasExtension.java"}
CONTRIBUTOR = " *     Synexia (Hitesh Soliwal) - adoption into the M3 fork, 2026\n"

CLASS_RENAMES = {
    "SkiaGC": "M3ExternalGC",
    "SkiaPaintManager": "M3PaintManager",
    "SkiaTextDrawing": "M3TextDrawing",
    "SkiaPathConverter": "M3PathConverter",
    "SkiaRegionCalculator": "M3RegionCalculator",
    "SkiaTransformConverter": "M3TransformConverter",
    "SkiaFontMetrics": "M3ExternalFontMetrics",
    "SkiaColorConverter": "M3ColorConverter",
    "SkijaToSwtImageConverter": "M3ToSwtImageConverter",
    "SwtToSkiaImageConverter": "SwtToM3ImageConverter",
    "ISkCanvas": "IM3Canvas",
    "ISkImage": "IM3Image",
    "ISkSurface": "IM3Surface",
    "ISkiaCanvasExtension": "IM3CanvasExtension",
    "ISkiaResources": "IM3Resources",
    "SkiaResources": "M3Resources",
    "SkijaCanvasAdapter": "M3CanvasAdapter",
    "SkijaImageAdapter": "M3ImageAdapter",
    "SkijaSurfaceAdapter": "M3SurfaceAdapter",
    "SkiaCanvasFactory": "M3CanvasFactory",
    "SkiaGlCanvasExtension": "M3PlaneCanvasExtension",
    "SkCanvasDouble": "M3CanvasDouble",
    "SkSurfaceDouble": "M3SurfaceDouble",
    "ISkImageDouble": "IM3ImageDouble",
    "SkiaResourcesDouble": "M3ResourcesDouble",
    "AllSkiaTests": "AllM3GcTests",
}
# the sed already turned Test_org_eclipse_swt_skia_SkijaToSwt... into ..._skia_M3ToSwt...; the prefix follows
TEST_PREFIX = re.compile(r"Test_org_eclipse_swt_skia_(?=[A-Za-z0-9_])")
IDENT = "[A-Za-z0-9_]"
TOKEN = re.compile(r"(?<!%s)(%s)(?!%s)" % (IDENT, "|".join(sorted(map(re.escape, CLASS_RENAMES), key=len, reverse=True)), IDENT))
SKIA_INNER = re.compile(r"(?<=%s)Skia(?=%s|\b)" % (IDENT, IDENT))
# file names do not pass through the sed: its Skija-inside-an-identifier rule, restated
SKIJA_INNER = re.compile(r"(?<=%s)Skija|Skija(?=%s)" % (IDENT, IDENT))
SKIA_LOWER = re.compile(r"\bskia(?=[A-Z0-9_])")
FQN_RULES = [
    (re.compile(r"org\.eclipse\.swt\.internal\.skia\.cache\."), MAIN_PKG + "."),
    (re.compile(r"org\.eclipse\.swt\.internal\.skia\."), MAIN_PKG + "."),
    (re.compile(r"org\.eclipse\.swt\.internal\.graphics\."), MAIN_PKG + "."),
    (re.compile(r"org\.eclipse\.swt\.internal\.canvasext\.(?=Logger|M3CanvasFactory)"), MAIN_PKG + "."),
    (re.compile(r"org\.eclipse\.swt\.tests\.doubles\."), TEST_PKG + "."),
    (re.compile(r"org\.eclipse\.swt\.tests\.skia\."), TEST_PKG + "."),
]
# the FQN rules run first and have turned `internal.skia.cache` into `internal.render.m3.gc.cache`: digits allowed
PACKAGE = re.compile(r"^package org\.eclipse\.swt\.[a-z0-9.]+;", re.M)
# a file from the donor's canvasext package used that package's types without imports; the leaf-1 seam types stay there
CANVASEXT_IMPORT = "import org.eclipse.swt.internal.canvasext.*;\n"
CONTRIB_BLOCK = re.compile(r"( \* Contributors:\n(?: \*     .*\n)+)")


def sed(text):
    done = subprocess.run(["sed", "-E", "-f", str(SED_RULES)], input=text.encode("utf-8"), capture_output=True, check=True)
    return done.stdout.decode("utf-8")


def rename(text, is_test, from_canvasext=False):
    text = sed(text)
    text = TOKEN.sub(lambda m: CLASS_RENAMES[m.group(1)], text)
    text = TEST_PREFIX.sub("Test_org_eclipse_swt_m3gc_", text)
    text = SKIA_INNER.sub("M3", text)
    text = SKIA_LOWER.sub("m3", text)
    for rule, replacement in FQN_RULES:
        text = rule.sub(replacement, text)
    pkg = TEST_PKG if is_test else MAIN_PKG
    text = PACKAGE.sub("package %s;" % pkg + ("\n\n" + CANVASEXT_IMPORT if from_canvasext else ""), text, count=1)
    text = re.sub(r"^import %s\.[A-Za-z0-9_]+;\n" % re.escape(pkg), "", text, flags=re.M)
    if " * Contributors:\n" in text and CONTRIBUTOR not in text:
        text = CONTRIB_BLOCK.sub(lambda m: m.group(1) + CONTRIBUTOR, text, count=1)
    return text


def destination(path):
    name = Path(path).name
    is_test = path.startswith(TEST_SRC)
    new_name = TOKEN.sub(lambda m: CLASS_RENAMES[m.group(1)], name)
    new_name = TEST_PREFIX.sub("Test_org_eclipse_swt_m3gc_", new_name)
    new_name = SKIA_INNER.sub("M3", new_name)
    new_name = SKIJA_INNER.sub("M3", new_name)
    return (TEST_DST if is_test else MAIN_DST) + new_name, is_test


def main(manifest, donor, worktree, log):
    rows = ["blob_sha\tdonor_path\tdest_path\tbytes_in\tbytes_out\tdecision"]
    for line in Path(manifest).read_text(encoding="utf-8").splitlines():
        sha, path = line.split("\t")
        if Path(path).name in PATTERN_ONLY:
            rows.append("%s\t%s\t-\t%d\t0\tPATTERN_ONLY" % (sha, path, (Path(donor) / path).stat().st_size))
            continue
        raw = (Path(donor) / path).read_bytes()
        crlf = b"\r\n" in raw
        text = raw.decode("utf-8").replace("\r\n", "\n")
        dest, is_test = destination(path)
        out = rename(text, is_test, path.startswith(MAIN_SRC + "canvasext/"))
        if crlf:
            out = out.replace("\n", "\r\n")
        target = Path(worktree) / dest
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(out.encode("utf-8"))
        rows.append("%s\t%s\t%s\t%d\t%d\tADAPT" % (sha, path, dest, len(raw), len(out.encode("utf-8"))))
    Path(log).write_text("\n".join(rows) + "\n", encoding="utf-8", newline="")
    print("\n".join(rows[1:]))


if __name__ == "__main__":
    main(*sys.argv[1:5])
