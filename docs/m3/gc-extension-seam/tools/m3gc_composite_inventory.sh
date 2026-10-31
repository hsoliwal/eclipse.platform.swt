#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Leaf 7 inventory (CPU only: GNU grep + awk; leaf 5's m3gc_canvas_inventory.sh is run, not copied): COVERAGE.tsv with three
# leaf-7 columns appended. Canvas rows keep the leaf-5 decision; every composite that is not a canvas (the fork's, transitive from
# the `class X extends Y` edges, the Composite base, Nebula's direct subclasses) is classified by the Composite hook rule:
#   foreign (OLE sites, Browser: M3CanvasFactory.foreignComposite)          -> no
#   native control (win32 createHandle clears the CANVAS state)             -> no (Table/Tree: drawn planes, leaf 3)
#   paints itself (its constructor adds a Paint listener on itself)         -> yes
#   pure container (no Paint listener)                                      -> stock (CONTAINER_OPT_OUT; yes once one is added)
# "Paints itself" comes from the probe's measured census (census_m3.tsv: listensPaint after construction, handler after the first
# paint) when given, else from grep (a receiver-less addListener(SWT.Paint/addPaintListener, or a listener array holding SWT.Paint
# registered receiver-less). Usage: m3gc_composite_inventory.sh [worktree] [nebula-root] [census_m3.tsv]
WT=${1:-C:/m3-superset/swt-gcleaf7}; NEB=${2:-C:/m3-superset/nebula}; CENSUS=${3:-H:/m3-scratch/gc-leaf7/probes/census_m3.tsv}
B="$WT/bundles/org.eclipse.swt"; T=$(mktemp -d)
bash "$WT/docs/m3/gc-extension-seam/tools/m3gc_canvas_inventory.sh" "$WT" "$NEB" > "$T/leaf5.tsv"
grep -rnoE --include='*.java' '^\s*(public |abstract |final )*class\s+\w+(\s*<[^>]*>)?\s+extends\s+\w+' "$B" \
  | sed -E 's#\\#/#g; s#^.*/bundles/org.eclipse.swt/##; s/:[0-9]+:/\t/; s/\t.*class\s+(\w+).*extends\s+(\w+)/\t\1\t\2/' > "$T/edges"
# per fork class: does the win32/common file clear CANVAS, does it hook Paint on itself (grep signals)
selfpaint() {  # file -> 1 when a Paint listener is registered on the receiver itself
  awk '
    /int *\[\] *[A-Za-z]*[Ee]vents *=/ { match($0, /[A-Za-z]*[Ee]vents *=/); cur = substr($0, RSTART, RLENGTH - 1); sub(/ +$/, "", cur); collecting = 1 }
    collecting && /SWT\.Paint/ { arrays[cur] = 1 }
    collecting && /}/ { collecting = 0 }
    /(^|[^.A-Za-z_])(this\.)?(addListener *\( *SWT\.Paint|addPaintListener *\()/ && !/void +add(Paint)?Listener/ { hit = 1 }
    pending > 0 { if ($0 ~ /^[ \t]*(this\.)?addListener *\(/) hit = 1; pending-- }
    { for (a in arrays) { name = a; sub(/ +$/, "", name)
        if ($0 ~ ("for *\\(int [A-Za-z]+ *: *" name " *\\)")) { if ($0 ~ /addListener/) { if ($0 ~ /\)[ \t]*(this\.)?addListener *\(/) hit = 1 } else pending = 2 } } }
    END { print hit ? 1 : 0 }' "$1"
}
awk -F'\t' '{ up[$2] = $3; f[$2] = (($2 in f) ? f[$2] ";" : "") $1 }
  END { for (c in up) { s = c; x = c; n = 0; while ((x in up) && n < 20) { x = up[x]; s = s "<" x; n++; if (x == "Canvas" || x == "Composite") break }
        if (x == "Composite" && s !~ /Canvas/) print c "\t" f[c] "\t" s } }' "$T/edges" | sort > "$T/composites"
: > "$T/signals"
while IFS=$'\t' read -r c files chain; do
  own=$(echo "$files" | tr ';' '\n' | grep -E '/win32/|/common/' | head -1)
  clears=$(grep -cE 'state\s*&=\s*~\(?[A-Z_ |]*CANVAS' "$B/$own"); sp=$(selfpaint "$B/$own")
  printf '%s\t%s\t%s\t%s\t%s\n' "$c" "$files" "$chain" "${clears:-0}" "$sp" >> "$T/signals"
done < "$T/composites"
[ -f "$CENSUS" ] && tr -d '\r' < "$CENSUS" > "$T/census" || : > "$T/census"
awk -F'\t' -v OFS='\t' -v census="$T/census" -v signals="$T/signals" '
  BEGIN {
    while ((getline line < census) > 0) { split(line, a, "\t"); if (a[2] != "class") { listens[a[2]] = a[3]; handled[a[2]] = a[4] } }
    probe["CTabFolder"] = "M3CompositeProbe CTabFolder (3 tabs, selected, close)"; probe["CBanner"] = "M3CompositeProbe CBanner"
    probe["ViewForm"] = "M3CompositeProbe ViewForm"; probe["Composite"] = "M3CompositeProbe ChildClip + Container"
    probe["CollapsibleButtons"] = "M3CompositeProbe CollapsibleButtons (Nebula, built from source)"
    foreign["OleClientSite"] = foreign["OleControlSite"] = foreign["WebSite"] = foreign["Browser"] = 1
  }
  function measured(c) { return (c in handled) ? "census m3: listensPaint=" listens[c] ", handler=" handled[c] : "" }
  function proofs(c, extra,   p) { p = (c in probe) ? probe[c] : ""; if (measured(c) != "") p = p (p == "" ? "" : "; ") measured(c)
    if (extra != "") p = p (p == "" ? "" : "; ") extra; return p == "" ? "Test_org_eclipse_swt_m3gc_CompositeCoverage (class matrix)" : p }
  function classify(c, clears, sp) {
    if (c in foreign) return "no\tforeign: pixels from the OLE object or the web engine (M3CanvasFactory.foreignComposite)\t" proofs(c, "CompositeCoverage.testForeignTransparentAndMirroredCompositesStayStock")
    if (clears > 0) return "no\tnative control: createHandle clears the CANVAS state, Composite.WM_PAINT never reaches the hook" (c ~ /^(Table|Tree)$/ ? " (drawn planes in m3/gpu, leaf 3)" : "") "\t" proofs(c, "")
    if ((c in listens) ? listens[c] == "true" : sp == 1) return "yes\tpaints itself (its constructor hooks Paint): the M3 handler at its first painted WM_PAINT in m3/gpu; native children clipped (CHILD_CLIP)\t" proofs(c, "")
    return "stock\tpure container (nothing hooks Paint: CONTAINER_OPT_OUT, the stock path); yes once an application adds a Paint listener\t" proofs(c, "")
  }
  FNR == 1 && FILENAME !~ /signals/ { print $0, "m3_after_leaf7", "condition_leaf7", "proof_leaf7"; next }
  FILENAME ~ /leaf5/ { if ($3 == "Composite" && $1 != "Canvas") next; print $0, $5, "Canvas hook (leaf 5), unchanged", "-"; next }
  FILENAME ~ /signals/ { print $1, $2, $3, "no", "no", "outside the Canvas hook", "-", classify($1, $4, $5) }
  END {
    print "Composite", "Eclipse SWT/win32/org/eclipse/swt/widgets/Composite.java", "Composite (the hook)", "no", "no", "outside the Canvas hook", "-",
      "yes\tper instance: a Composite with a Paint listener (an application panel, Nebula child panels: CTree body, DateChooser panels, CompositeTable rows); a pure container otherwise\t" proofs("Composite", "")
  }' "$T/leaf5.tsv" "$T/signals"
if [ -d "$NEB" ]; then
  grep -rlE --include='*.java' 'class\s+\w+(\s*<[^>]*>)?\s+extends\s+Composite\b' "$NEB" | sed 's#\\#/#g' | grep -viE '/test|\.tests/|snippet|example|donor-inventory|/\.m3/' | sort \
    | while IFS= read -r f; do
        c=$(basename "$f" .java); rel=${f#"$NEB"/}
        if [ "$(selfpaint "$f")" = 1 ]; then v="yes"; why="paints itself by class (receiver-less Paint listener); the M3 handler at its first painted WM_PAINT in m3/gpu"
        else v="stock"; why="pure container by class (no Paint listener on itself); yes once one is added"; fi
        proof="by class only: Nebula not built here"; [ "$c" = CollapsibleButtons ] && proof="M3CompositeProbe CollapsibleButtons (built from source)$(awk -F'\t' -v c="$c" '$2 == c { printf "; census m3: listensPaint=%s, handler=%s", $3, $4 }' "$T/census")"
        printf '%s\tnebula:%s\tComposite (Nebula)\tno\tno\toutside the Canvas hook\t-\t%s\t%s\t%s\n' "$c" "$rel" "$v" "$why" "$proof"
      done
fi
rm -rf "$T"
