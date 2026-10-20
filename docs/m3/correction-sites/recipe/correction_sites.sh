#!/usr/bin/env bash
# Correction-site census over the Eclipse forks (user 2026-10-20: "identify and mark all the places where platform UI,
# JDT, JDT UI and resources need to be corrected", "mark all the eclipse forks", "using grep, then multipass
# mechanical, and finally your pass", "save multipass mechanical as recipe"). Brief rules 18, 24, 25.
# Pass 1 grep (git grep -P over HEAD of a shallow bare clone of each fork, tracked .java only).
# Pass 2 drop tests/examples.  Pass 3 enclosing method (awk).  Pass 4 classify the caller.  Pass 5 permalink + summary.
# The model's own pass reads only the classified output. Usage: correction_sites.sh <out-dir> [repo...]
OUT=${1:-S:/m3-scratch/correction-sites}; shift; mkdir -p "$OUT"; F=S:/m3-scratch/forks; mkdir -p "$F"
REPOS=${*:-"eclipse.platform.ui eclipse.platform eclipse.platform.swt nebula gef-classic eclipse.jdt.core eclipse.jdt.ui eclipse.jdt.debug eclipse.platform.text org.eclipse.emf egit initiative31-eclipse.pde"}
P=(
"full_refresh \.refresh\(\s*(true|false)?\s*\)"
"set_input \.setInput\("
"remove_all \.removeAll\(\s*\)"
"clear_all \.clearAll\(\s*\)"
"item_count_reset \.setItemCount\(\s*0\s*\)"
"refresh_children \brefreshChildren\(\s*\)"
"swing_full_event \bfire(TableDataChanged|TableStructureChanged|TreeStructureChanged)\(|\bnodeStructureChanged\(|\.reload\(\s*\)"
"per_element_object new\s+(MarkerEntry|MarkerCategory|TreePath)\s*\("
"per_row_item new\s+(TreeItem|TableItem)\s*\("
"element_map new\s+(Linked)?Hash(Map|Set)\s*<"
"sync_exec \.syncExec\("
)
printf 'repo\tcategory\tcaller_class\tenclosing_method\tpath\tline\tpermalink\tsnippet\n' > "$OUT/CORRECTION_SITES.tsv"
for r in $REPOS; do
  g="$F/$r.git"
  if [ ! -d "$g" ]; then timeout 900 git clone -q --bare --depth 1 --filter=blob:limit=2m "https://github.com/hsoliwal/$r.git" "$g" || { echo "clone failed $r" >&2; continue; }; fi
  sha=$(git -C "$g" rev-parse HEAD); : > "$OUT/.raw"
  for p in "${P[@]}"; do                                                   # pass 1: grep
    cat=${p%% *}; re=${p#* }
    GIT_NO_LAZY_FETCH=1 git -C "$g" grep -n -P -e "$re" HEAD -- '*.java' 2>/dev/null | sed "s#^HEAD:##; s#^#$cat\t#" >> "$OUT/.raw"
  done
  # pass 2: drop tests, examples, SWT's own widget implementation (per_row_item only) and element_map outside UI paths
  awk -F'\t' '{ split($2, a, ":"); path=a[1]
    if (path ~ /(^|\/)(tests?|test-[^\/]*|examples?|snippets?)\// || path ~ /Test[A-Za-z0-9_]*\.java$/ || path ~ /^\./) next
    if ($1=="per_row_item" && path ~ /org\/eclipse\/swt\/widgets\//) next
    if ($1=="element_map" && path !~ /([Vv]iewer|\/views?\/|[Nn]avigator|markers|[Cc]ontent[Pp]rovider|[Ll]abel[Pp]rovider|[Oo]utline|[Hh]ierarch)/) next
    print }' "$OUT/.raw" > "$OUT/.kept"
  # pass 3: enclosing method per file (one git show per file)
  : > "$OUT/.encl"
  awk -F'\t' '{ split($2, a, ":"); print a[1] "\t" a[2] }' "$OUT/.kept" | sort -t$'\t' -k1,1 -k2,2n | awk -F'\t' '
    $1 != f { if (f != "") print f "\t" l; f = $1; l = $2; next } { l = l "," $2 } END { if (f != "") print f "\t" l }' |
  while IFS=$'\t' read -r path lines; do
    GIT_NO_LAZY_FETCH=1 git -C "$g" show "HEAD:$path" 2>/dev/null | awk -v want="$lines" -f C:/m3-superset/tools/enclosing_method.awk |
      sed "s#^#$path\t#" >> "$OUT/.encl"
  done
  # pass 4 + 5: classify the caller, add permalink, write rows
  awk -F'\t' -v repo="$r" -v sha="$sha" -v OFS='\t' '
    FNR == NR { enc[$1 ":" $2] = $3; next }
    { cat = $1; rest = $2; for (i = 3; i <= NF; i++) rest = rest " " $i; split(rest, a, ":"); path = a[1]; ln = a[2]; snip = substr(rest, length(path) + length(ln) + 3)
      gsub(/\t/, " ", snip); gsub(/^ +/, "", snip); gsub(/[ ]+$/, "", snip); snip = substr(snip, 1, 160); gsub(/[ ]+$/, "", snip)
      m = ((path ":" ln) in enc) ? enc[path ":" ln] : "-"
      if (m ~ /^(resourceChanged|elementChanged|handleDebugEvents|notifyChanged|propertyChange|propertyChanged|markersChanged|modelChanged|launches(Added|Removed|Changed|Terminated)|breakpoints?(Added|Removed|Changed)|documentChanged|documentAboutToBeChanged|changed|stateChanged|valueChanged|handleChange|handleListChange|handleSetChange|handleValueChange|inputChanged|refreshChanged|resourceChanged0|problemsChanged|annotationModelChanged|textChanged)$/) cls = "MODEL_LISTENER"
      else if (m ~ /^(selectionChanged|widgetSelected|widgetDefaultSelected|handleEvent|partActivated|partVisible|partOpened|partBroughtToTop|focusGained|modifyText|keyPressed|keyReleased|mouseDown|mouseUp|doubleClick|treeExpanded|treeCollapsed|checkStateChanged|selectionChangedInternal)$/) cls = "UI_EVENT"
      else if (m ~ /^(run|runInUIThread|runInWorkspace|done|scheduled|aboutToRun|execute|refresh|refreshVisuals|refreshChildren|update|updateView|refreshView|refreshViewer|doRefresh|asyncRefresh|scheduleRefresh|internalRefresh|refreshTree|refreshTable|reset|rebuild|reload)$/) cls = "ASYNC_OR_REFRESH_METHOD"
      else if (m ~ /^(createPartControl|createControl|createContents|createDialogArea|createViewer|createTreeViewer|createTableViewer|init|initialize|initializeViewer|setInput|open|create|configure|hookViewer)$/ || m ~ /^[A-Z]/) cls = "INITIAL_OR_SETUP"
      else cls = "REVIEW"
      print repo, cat, cls, m, path, ln, "https://github.com/hsoliwal/" repo "/blob/" sha "/" path "#L" ln, snip }' "$OUT/.encl" "$OUT/.kept" >> "$OUT/CORRECTION_SITES.tsv"
  echo "$r $sha rows=$(awk -F'\t' -v r="$r" '$1==r' "$OUT/CORRECTION_SITES.tsv" | wc -l)" >&2
done
rm -f "$OUT/.raw" "$OUT/.kept" "$OUT/.encl"
# summary: repo x caller_class, and repo x category
awk -F'\t' 'NR > 1 { k = $1 "\t" $3; n[k]++ } END { for (k in n) print k "\t" n[k] }' "$OUT/CORRECTION_SITES.tsv" | sort > "$OUT/SUMMARY_BY_CALLER.tsv"
awk -F'\t' 'NR > 1 { k = $1 "\t" $2; n[k]++ } END { for (k in n) print k "\t" n[k] }' "$OUT/CORRECTION_SITES.tsv" | sort > "$OUT/SUMMARY_BY_CATEGORY.tsv"
# pass 4b (multipass, one source for the classes): reclassify every row with correction_classes.awk
awk -f C:/m3-superset/tools/correction_classes.awk "$OUT/CORRECTION_SITES.tsv" > "$OUT/.re" && mv "$OUT/.re" "$OUT/CORRECTION_SITES.tsv"
awk -F'\t' 'NR > 1 { k = $1 "\t" $3; n[k]++ } END { for (k in n) print k "\t" n[k] }' "$OUT/CORRECTION_SITES.tsv" | sort > "$OUT/SUMMARY_BY_CALLER.tsv"
