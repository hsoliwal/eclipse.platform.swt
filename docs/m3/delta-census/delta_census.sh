#!/usr/bin/env bash
# Delta census (brief rule 24, user 2026-10-20 "all delta based view"): counts full-rebuild call sites per repo/module.
# A call site is not automatically a defect (initial input, user Refresh); each track justifies or removes it in DELTA_GATE.tsv.
# Usage: delta_census.sh <out-dir>   Repos are the local checkouts listed in R below. Uses git grep -P (tracked .java only),
# not rg: in Claude Code shells rg is a function that child scripts cannot see.
OUT=${1:-S:/m3-scratch/delta-census}; mkdir -p "$OUT"
R=(
"platform.ui S:/m3-scratch/worktrees/platform-ui ."
"platform S:/m3-scratch/worktrees/eclipse.platform ."
"swt K:/m3-work/agents/consolidate/wt bundles"
"nebula S:/m3-scratch/worktrees/nebula-widgets ."
"gef-classic S:/m3-scratch/worktrees/gef-classic ."
"m3jdk21-desktop K:/m3-work/agents/m3jdk-swing/wt src/java.desktop"
)
P=(
"jface_refresh_all \.refresh\(\s*(true|false)?\s*\)"
"jface_setInput \.setInput\("
"swt_removeAll \.removeAll\(\s*\)"
"swt_clearAll \.clearAll\("
"swt_setItemCount0 \.setItemCount\(\s*0\s*\)"
"swing_tableDataChanged fireTableDataChanged\("
"swing_tableStructureChanged fireTableStructureChanged\("
"swing_treeStructure (fireTreeStructureChanged|nodeStructureChanged)\("
"swing_treeReload \.reload\(\s*\)"
"swing_contentsChanged fireContentsChanged\("
"gef_refreshChildren refreshChildren\(\s*\)"
)
printf 'repo\tmodule\tpattern\tmain\ttest\n' > "$OUT/DELTA_CENSUS.tsv"
printf 'repo\tfile\tpattern\tcount\n' > "$OUT/DELTA_CENSUS_FILES.tsv"
for r in "${R[@]}"; do
  set -- $r; name=$1; root=$2; sub=$3
  [ -d "$root/$sub" ] || { echo "skip $name ($root/$sub missing)" >&2; continue; }
  head=$(git -C "$root" rev-parse --short=10 HEAD 2>/dev/null)
  for p in "${P[@]}"; do
    pn=${p%% *}; re=${p#* }
    if [ "$sub" = . ]; then ps=":(glob)**/*.java"; else ps=":(glob)$sub/**/*.java"; fi
    git -C "$root" grep -c -P -e "$re" -- "$ps" > "$OUT/.hits" 2>/dev/null
    awk -F: -v repo="$name" -v pat="$pn" -v OFS='\t' '
      { f=$1; c=$NF; n=split(f, a, "/"); m=(a[1]=="bundles"||a[1]=="widgets"||a[1]=="tests"||a[1]=="examples"||a[1]=="src")&&n>2 ? a[1]"/"a[2] : a[1];
        if (a[1]=="src" && n>4) m=a[1]"/"a[2]"/"a[3]"/"a[4]"/"a[5];
        t=(f ~ /(^|\/)tests?\// || f ~ /\/test\// || f ~ /Test[^\/]*\.java$/) ? "test" : "main";
        k=m; mainc[k]+=(t=="main")?c:0; testc[k]+=(t=="test")?c:0;
        if (t=="main") print repo, f, pat, c > "/dev/stderr" }
      END { for (k in mainc) print repo, k, pat, mainc[k], testc[k] }' "$OUT/.hits" >> "$OUT/DELTA_CENSUS.tsv" 2>> "$OUT/DELTA_CENSUS_FILES.tsv"
  done
  echo "$name $root@$head" >> "$OUT/.heads"
done
rm -f "$OUT/.hits"
{ head -1 "$OUT/DELTA_CENSUS_FILES.tsv"; tail -n +2 "$OUT/DELTA_CENSUS_FILES.tsv" | sort -t$'\t' -k4,4nr; } > "$OUT/.f" && mv "$OUT/.f" "$OUT/DELTA_CENSUS_FILES.tsv"
