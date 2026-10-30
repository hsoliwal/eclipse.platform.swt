#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Leaf 5 inventory (CPU only: rg + awk): every Canvas subclass of the fork (transitive, from the `class X extends Y` edges of
# bundles/org.eclipse.swt), the custom widgets that extend Composite (outside the Canvas hook), and the direct Canvas
# subclasses of a Nebula clone (non-test), with the leaf-5 activation rule applied: whether the class reaches M3ExternalGC in
# the m3/gpu modes without SWT.M3, before (leaf 4: never, the bit was required) and after (leaf 5). Writes TSV to stdout.
# Usage: m3gc_canvas_inventory.sh [worktree] [nebula-root]   (defaults: C:/m3-superset/swt-gcseam, C:/m3-superset/nebula)
WT=${1:-C:/m3-superset/swt-gcseam}; NEB=${2:-C:/m3-superset/nebula}
B="$WT/bundles/org.eclipse.swt"
EDGES=$(mktemp)
# file <TAB> class <TAB> superclass, one per declaration
# GNU grep, not rg: rg is a shell function of the interactive profile, absent in a script's shell (L5-42)
grep -rnoE --include='*.java' '^\s*(public |abstract |final )*class\s+\w+(\s*<[^>]*>)?\s+extends\s+\w+' "$B" \
  | sed -E 's#\\#/#g; s#^.*/bundles/org.eclipse.swt/##; s/:[0-9]+:/\t/; s/\t.*class\s+(\w+).*extends\s+(\w+)/\t\1\t\2/' > "$EDGES"
printf 'widget\tfile\textends\tm3_before_leaf4\tm3_after_leaf5\tcondition_after\tproof\n'
awk -F'\t' '
  function chain(c,   s, n) { s = c; n = 0; while ((c in up) && n < 20) { c = up[c]; s = s "<" c; n++; if (c == "Canvas") return s } return "" }
  function after(c, ext) {
    if (ext ~ /Decorations/ || c == "Decorations") return "no\tnever: Decorations (Shell) is excluded for every factory"
    if (c == "GLCanvas") return "no\tonly with SWT.M3: GL pixels are not drawn by paint listeners (M3CanvasFactory.foreign)"
    return "yes\tm3/gpu mode (swt.widget.mode[.<Class>|.Canvas]); no style bit; not with SWT.EMBEDDED unless SWT.M3"
  }
  function proof(c) {
    if (c in probe) return probe[c]
    return "Test_org_eclipse_swt_m3gc_CanvasCoverage (class matrix)"
  }
  BEGIN {
    probe["Canvas"] = "probe Canvas (no bit) + Canvas(SWT.M3); CanvasCoverage; CanvasDamage; StrokeState"
    probe["StyledText"] = "probe StyledText: handler active, its paint reaches the GCExtension and then throws in Canvas.drawBackground (GF01: GCExtension has no GCData; leaf 6); PASS with the GF01 patch measured locally (probes-gf01-local); CanvasCoverage"
    probe["CLabel"] = "probe CLabel (image + text); CanvasCoverage"
    probe["GLCanvas"] = "CanvasCoverage.testForeignCanvasesNeedTheStyleBit"
    probe["Decorations"] = "CanvasCoverage.testDecorationsNever"
    probe["Shell"] = "CanvasCoverage.testDecorationsNever"
    probe["CTabFolder"] = "probe CTabFolder (handler=false in every mode)"
  }
  { file[$2] = (($2 in file) && index(file[$2], $1) == 0) ? file[$2] ";" $1 : $1; up[$2] = $3 }
  END {
    print "Canvas\t" "Eclipse SWT/win32/org/eclipse/swt/widgets/Canvas.java\tComposite (the hook)\tno (SWT.M3 required)\t" after("Canvas", "Composite") "\t" proof("Canvas")
    for (c in up) { s = chain(c); if (s != "") print c "\t" file[c] "\t" s "\tno (SWT.M3 required" (c == "StyledText" || c == "Decorations" || c == "Shell" ? "; excluded even with it" : "") ")\t" after(c, s) "\t" proof(c) | "sort" }
    close("sort")
    for (c in up) if (up[c] == "Composite" && file[c] ~ /Custom Widgets/) print c "\t" file[c] "\tComposite\tno\tno\textends Composite: outside the Canvas hook (a Composite hook is a later leaf)\t" proof(c) | "sort"
    close("sort")
  }' "$EDGES"
if [ -d "$NEB" ]; then
  grep -rlE --include='*.java' 'class\s+\w+(\s*<[^>]*>)?\s+extends\s+Canvas\b' "$NEB" | sed 's#\\#/#g' | grep -viE '/test|snippet|example|donor-inventory' | sort \
    | while IFS= read -r f; do
        c=$(basename "$f" .java); rel=${f#"$NEB"/}
        printf '%s\tnebula:%s\tCanvas (Nebula)\tno (SWT.M3 required)\tyes\tm3/gpu mode, by class (the Canvas constructor hook); no style bit\tby class only: Nebula not built or probed here\n' "$c" "$rel"
      done
fi
rm -f "$EDGES"
