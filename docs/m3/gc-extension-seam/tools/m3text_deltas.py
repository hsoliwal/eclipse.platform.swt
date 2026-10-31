"""Text leaf L13/L14 mechanical deltas (CPU, exact-match, fail-closed). Each atom is a list of (file, old, new) replacements
applied only when `old` occurs exactly once; a second run is a no-op (fixed point: `new` present, `old` absent).
Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others. SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
Usage: python -I m3text_deltas.py <worktree> <ATOM> [replacement-text-dir, default tools/m3text_bodies]
"""
import re
import sys
from pathlib import Path

RM = "bundles/org.eclipse.swt/Eclipse SWT Render M3/win32/org/eclipse/swt/internal/render/m3/gc/"
W32G = "bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/graphics/"


def text_layout_draw_span(src):
    """the stock-delegating textLayoutDraw of M3ExternalGC: @Override line through the method's closing brace"""
    m = re.search(r"\t@Override\n\tpublic void textLayoutDraw\(.*?\n\t}\n", src, re.S)
    return m.group(0) if m else None


def atom_fallback(wt, extra):
    f = Path(wt, RM, "M3ExternalGC.java")
    src = f.read_text(encoding="utf-8")
    body = Path(extra, "l13_fallback_body.txt").read_text(encoding="utf-8")
    if body in src:
        return [(f, "fixed point")]
    old = text_layout_draw_span(src)
    assert old and src.count(old) == 1 and "new Image(device, rectangle.width" in old, "textLayoutDraw anchor"
    src = src.replace(old, body)
    imp = "import io.github.humbleui.skija.ClipMode;\n"
    assert src.count(imp) == 1
    src = src.replace(imp, "import io.github.humbleui.skija.ClipMode;\nimport io.github.humbleui.skija.ColorAlphaType;\n"
                      "import io.github.humbleui.skija.ColorType;\n", 1)
    imp2 = "import io.github.humbleui.skija.GradientStyle;\n"
    assert src.count(imp2) == 1
    src = src.replace(imp2, imp2 + "import io.github.humbleui.skija.ImageInfo;\n", 1)
    f.write_bytes(src.encode("utf-8"))
    return [(f, "applied")]


ATOMS = {"TEXT_FALLBACK_SAFE": atom_fallback}

if __name__ == "__main__":
    wt, atom = sys.argv[1], sys.argv[2]
    extra = sys.argv[3] if len(sys.argv) > 3 else str(Path(__file__).with_name("m3text_bodies"))
    for path, state in ATOMS[atom](wt, extra):
        print(f"{atom}\t{Path(path).name}\t{state}")
