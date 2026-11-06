"""Text leaf IDE editor diff (CPU only): differing pixels of the Java editor area (the StyledText client rectangle the
IDE_SMOKE v2 TSV records as editor_rect) and of the whole shell, each m3/gpu capture against the baseline capture of the
same step. Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others. SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
Usage: python -I m3text_editor_diff.py <baseline-out> <label>=<out-dir> ...   (out dirs hold ide_<mode>.tsv and <mode>/*.png)
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image


def rows(out, mode):
    tsv = Path(out) / f"ide_{mode}.tsv"
    if not tsv.exists():
        return {}
    lines = [l.split("\t") for l in tsv.read_text(encoding="utf-8").splitlines() if l and not l.startswith(("#", "mode\t"))]
    return {r[1]: r for r in lines}


def load(png):
    return np.asarray(Image.open(png).convert("RGB"), dtype=np.int16)


def diff(a, b, rect=None):
    if a.shape != b.shape:
        return "size"
    if rect:
        x, y, w, h = rect
        a, b = a[y:y + h, x:x + w], b[y:y + h, x:x + w]
    return int((a != b).any(axis=2).sum())


def main(base_out, runs):
    base = rows(base_out, "baseline") or rows(base_out, "stock")  # the smoke names a run without swt.widget.mode "stock"
    print("label\tmode\tstep\teditor_rect\teditor_px_differ\teditor_px\tshell_px_differ")
    for label, out in runs:
        for mode in ("m3", "gpu"):
            for step, r in rows(out, mode).items():
                b = base.get(step)
                if b is None or len(b) < 15 or b[14] == "-":
                    continue
                rect = tuple(int(v) for v in b[14].split(","))
                a_img, b_img = load(b[13]), load(r[13])
                print(f"{label}\t{mode}\t{step}\t{b[14]}\t{diff(b_img, a_img, rect)}\t{rect[2] * rect[3]}\t{diff(b_img, a_img)}")


if __name__ == "__main__":
    main(sys.argv[1], [tuple(a.split("=", 1)) for a in sys.argv[2:]])
