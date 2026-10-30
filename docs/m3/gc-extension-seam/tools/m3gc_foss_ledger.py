# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""FOSS_REUSE_DECISION.tsv of the GC seam leaf 2 from the adoption log of m3gc_adopt.py (one row per donor file).

Columns (m3-foss-donor-first, the standard ten): capability, candidate_repo, commit, source_paths, license,
contract_fit, decision, rejected_reason, adapted_hashes, notice_path. adapted_hashes is the git blob sha of the
adopted file in the worktree (sha1 over "blob <size>\\0" + bytes), so a row is checkable with `git hash-object`.

Usage: python -I m3gc_foss_ledger.py <adoption_log.tsv> <worktree> <main_fit> <test_fit>
  main_fit / test_fit: the proof text of the compile and test gates, e.g. "javac 0 errors (compile_m3gc.sh, 918 sources)"
"""
import hashlib
import sys
from pathlib import Path

REPO = "swt-initiative31/skija-canvas"
COMMIT = "9bcb404c421361b7f5e046dc844ec076e539d0e6"
LICENSE = "EPL-2.0:CODE_REUSE_ALLOWED_WITH_ATTRIBUTION"
NOTICE = "docs/m3/gc-extension-seam/README.md#leaf-2"
COLUMNS = ("capability", "candidate_repo", "commit", "source_paths", "license", "contract_fit", "decision",
           "rejected_reason", "adapted_hashes", "notice_path")
CONVERTERS = {"M3ToSwtImageConverter", "SwtToM3ImageConverter", "Test_org_eclipse_swt_m3gc_M3ToSwtImageConvert",
              "Test_org_eclipse_swt_m3gc_M3ToSwtImageConverter"}
CONVERTER_NOTE = ("ADAPT for now: per-draw ImageData<->Skija image conversion; a later leaf replaces it with the BGRA "
                  "direct path of M3RowCanvas (RowImages.bgra, Image.makeRasterFromBytes BGRA_8888 PREMUL)")
GL_NOTE = ("PATTERN_ONLY: owns a GL context, a BackendRenderTarget and a Surface per Canvas and restores undamaged "
           "pixels from a snapshot image; the drawn planes already own their Surface (M3RowCanvas.target over the "
           "BackBuffer DIB or M3Gpu) and retain the frame, so M3PlaneCanvasExtension adapts the plane instead")


def blob_sha(path):
    data = path.read_bytes()
    return hashlib.sha1(b"blob %d\0" % len(data) + data).hexdigest()


def main(log, worktree, main_fit, test_fit):
    rows = ["\t".join(COLUMNS)]
    for line in Path(log).read_text(encoding="utf-8").splitlines()[1:]:
        sha, donor, dest, _, _, decision = line.split("\t")
        donor_name = Path(donor).stem
        is_test = donor.startswith("tests/")
        if decision == "PATTERN_ONLY":
            rows.append("\t".join(("m3-gc-backend:" + donor_name, REPO, COMMIT, "%s@blob:%s" % (donor, sha), LICENSE,
                                   "NOT_APPLICABLE", "PATTERN_ONLY", GL_NOTE, "-", NOTICE)))
            continue
        name = Path(dest).stem
        fit = test_fit if is_test else main_fit
        reason = CONVERTER_NOTE if name in CONVERTERS else "-"
        rows.append("\t".join(("m3-gc-backend:" + name, REPO, COMMIT, "%s@blob:%s" % (donor, sha), LICENSE, fit,
                               "ADAPT", reason, blob_sha(Path(worktree) / dest), NOTICE)))
    out = Path(worktree) / "docs/m3/gc-extension-seam/FOSS_REUSE_DECISION.tsv"
    out.write_text("\n".join(rows) + "\n", encoding="utf-8", newline="")
    print("%s rows=%d" % (out, len(rows) - 1))


if __name__ == "__main__":
    main(*sys.argv[1:5])
