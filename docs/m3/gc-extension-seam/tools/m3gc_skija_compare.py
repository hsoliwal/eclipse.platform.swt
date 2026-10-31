# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""PIXEL_REBASE v1 (census L12): the same real-app smoke on two Skija pins, compared tab by tab (CPU only).

For every mode and tab screen capture of m3_l12_realapp.sh in <old-out> and <new-out>: the driver's result and its
diff against the same build's stock capture (DIFF_<mode>.tsv), and the pixels that differ between the two pins'
captures (the m116 -> m143 natives change, Clang build) with their bounding box, so a difference confined to a strip
that also moves between the two stock runs (no Skija loaded) reads as capture noise, not as the bump. One TSV row per
(mode, tab) and a total per mode.
Usage: python -I m3gc_skija_compare.py <old-out> <new-out> <out.tsv> [modes...]     (modes default: stock m3 gpu)
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image


def table(path, key, value):
    rows = {}
    if path.exists():
        lines = path.read_text(encoding="utf-8").splitlines()
        head = lines[0].split("\t")
        for line in lines[1:]:
            cells = dict(zip(head, line.split("\t")))
            rows[cells[key]] = cells.get(value, "-")
    return rows


def pixels(a, b):
    if not (a.exists() and b.exists()):
        return "-", "-"
    x = np.asarray(Image.open(a).convert("RGB"), dtype=np.int16)
    y = np.asarray(Image.open(b).convert("RGB"), dtype=np.int16)
    if x.shape != y.shape:
        return "SIZE", "-"
    mask = (x != y).any(axis=2)
    if not mask.any():
        return "0", "-"
    ys, xs = np.nonzero(mask)
    return str(int(mask.sum())), f"{xs.min()},{ys.min()},{xs.max()},{ys.max()} of {x.shape[1]}x{x.shape[0]}"


def main(old, new, dest, modes):
    old, new = Path(old), Path(new)
    out = ["mode\ttab\tresult_old\tresult_new\tdiff_vs_stock_old\tdiff_vs_stock_new\tpixels_old_vs_new\tbbox_old_vs_new(x0,y0,x1,y1)"]
    for mode in modes:
        res_o = table(old / f"realapp_{mode}.tsv", "screen_png", "result")
        res_n = table(new / f"realapp_{mode}.tsv", "screen_png", "result")
        res_o = {Path(k).name: v for k, v in res_o.items()}
        res_n = {Path(k).name: v for k, v in res_n.items()}
        dif_o, dif_n = table(old / f"DIFF_{mode}.tsv", "base", "diff_pixels"), table(new / f"DIFF_{mode}.tsv", "base", "diff_pixels")
        names = sorted({p.name for p in (old / "screens" / mode).glob("*_screen.png")} | {p.name for p in (new / "screens" / mode).glob("*_screen.png")})
        total, changed = 0, 0
        for name in names:
            base = name[: -len("_screen.png")]
            px, box = pixels(old / "screens" / mode / name, new / "screens" / mode / name)
            total += int(px) if px.isdigit() else 0
            changed += px not in ("0", "-")
            out.append("\t".join((mode, base, res_o.get(name, "-"), res_n.get(name, "-"), dif_o.get(base, "-"), dif_n.get(base, "-"), px, box)))
        out.append(f"{mode}\tTOTAL({len(names)} tabs)\t-\t-\t-\t-\t{total} in {changed} tabs\t-")
        print(f"{mode}: tabs={len(names)} tabs_changed={changed} pixels_changed={total}")
    Path(dest).write_text("\n".join(out) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4:] or ["stock", "m3", "gpu"])
