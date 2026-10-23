#!/usr/bin/env bash
# Downstream requirements (user 2026-10-20: "platform ui is like a downstream, gives requirements").
# For every public method of the upstream API classes (JFace viewers, SWT Tree/Table/List/Combo widgets), count call
# sites in the downstream forks (platform.ui, platform), counting only calls whose receiver names a viewer, tree, table,
# combo, path, selection or item (fViewer.add(, getTreeViewer().remove(, treePath.getSegment(); plain list.add( is not counted). Still an approximate
# census, a ranking of what downstream needs first, not a call graph. Uses git grep (rg is invisible to child scripts).
# Usage: downstream_requirements.sh <out.tsv>
OUT=${1:-S:/m3-scratch/delta-census/DOWNSTREAM_REQUIREMENTS.tsv}
UI=S:/m3-scratch/worktrees/platform-ui; PL=S:/m3-scratch/worktrees/eclipse.platform; SWT=K:/m3-work/agents/consolidate/wt
JV=bundles/org.eclipse.jface/src/org/eclipse/jface/viewers; SW="bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/widgets"
A=(
"jface $UI/$JV/Viewer.java" "jface $UI/$JV/ContentViewer.java" "jface $UI/$JV/StructuredViewer.java"
"jface $UI/$JV/ColumnViewer.java" "jface $UI/$JV/AbstractTreeViewer.java" "jface $UI/$JV/TreeViewer.java"
"jface $UI/$JV/AbstractTableViewer.java" "jface $UI/$JV/TableViewer.java"
"swt $SWT/$SW/Tree.java" "swt $SWT/$SW/TreeItem.java" "swt $SWT/$SW/Table.java" "swt $SWT/$SW/TableItem.java"
"swt $SWT/$SW/List.java" "swt $SWT/$SW/Combo.java"
"jface $UI/$JV/TreePath.java" "jface $UI/$JV/TreeSelection.java" "jface $UI/$JV/StructuredSelection.java"
"jface $UI/$JV/ViewerCell.java" "jface $UI/$JV/ViewerRow.java" "jface $UI/$JV/ViewerComparator.java" "jface $UI/$JV/ViewerFilter.java"
"jface $UI/$JV/CheckboxTreeViewer.java" "jface $UI/$JV/CheckboxTableViewer.java" "jface $UI/$JV/ListViewer.java" "jface $UI/$JV/ComboViewer.java"
)
printf 'layer\tclass\tmethod\tplatform_ui_main\tplatform_main\ttotal\n' > "$OUT.tmp"
for a in "${A[@]}"; do
  layer=${a%% *}; f=${a#* }; c=$(basename "$f" .java)
  [ -f "$f" ] || { echo "missing $f" >&2; continue; }
  awk -f C:/m3-superset/tools/public_method_names.awk "$f" | sort -u |
  while read -r m; do
    [ -n "$m" ] && [ "$m" != "$c" ] || continue
    u=$(git -C "$UI" grep -c -i -E "(viewer|tree|table|combo|path|selection|item)[A-Za-z0-9_]*(\(\))?\.$m\s*\(" -- ':(glob)bundles/**/*.java' ':(exclude)bundles/org.eclipse.jface/**' ':(exclude)**/test*/**' 2>/dev/null | awk -F: '{s+=$NF} END{print s+0}')
    p=$(git -C "$PL" grep -c -i -E "(viewer|tree|table|combo|path|selection|item)[A-Za-z0-9_]*(\(\))?\.$m\s*\(" -- ':(glob)**/*.java' ':(exclude)**/test*/**' 2>/dev/null | awk -F: '{s+=$NF} END{print s+0}')
    printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$layer" "$c" "$m" "$u" "$p" $((u+p)) >> "$OUT.tmp"
  done
done
{ head -1 "$OUT.tmp"; tail -n +2 "$OUT.tmp" | sort -t$'\t' -k6,6nr; } > "$OUT"; rm -f "$OUT.tmp"
