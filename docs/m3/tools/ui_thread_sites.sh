#!/usr/bin/env bash
# UI-thread abuse census (user 2026-10-20: "keep doing multipass .. Eclipse is full of UI thread abuse"; brief rule 28).
# Same multipass as correction_sites.sh over the same shallow clones (S:/m3-scratch/forks): pass 1 git grep, pass 2 drop
# tests/examples/dot-dirs, pass 3 enclosing method, pass 4 thread role of the caller, pass 5 verdict + permalink.
# Usage: ui_thread_sites.sh <out-dir> [repo...]
OUT=${1:-S:/m3-scratch/ui-thread-sites}; shift; mkdir -p "$OUT"; F=S:/m3-scratch/forks
REPOS=${*:-"eclipse.platform.ui eclipse.platform eclipse.platform.swt nebula gef-classic eclipse.jdt.core eclipse.jdt.ui eclipse.jdt.debug org.eclipse.emf egit initiative31-eclipse.pde p2"}
P=(
"sync_exec \.syncExec\("
"nested_event_loop \.readAndDispatch\(\s*\)"
"modal_no_fork \.run\(\s*false\s*,"
"busy_wait BusyIndicator\.showWhile\(|busyCursorWhile\("
"thread_sleep \bThread\.sleep\("
"job_join \.join\(\s*\)"
"workspace_io \.(findMarkers|refreshLocal|members|getContents|getPersistentProperty|deleteMarkers|createMarker)\("
"display_default \bDisplay\.getDefault\(\)"
)
printf 'repo\tcategory\tthread_role\tenclosing_method\tpath\tline\tpermalink\tsnippet\n' > "$OUT/UI_THREAD_SITES.tsv"
for r in $REPOS; do
  g="$F/$r.git"; [ -d "$g" ] || { echo "no clone $r (run correction_sites.sh first)" >&2; continue; }
  sha=$(git -C "$g" rev-parse HEAD); : > "$OUT/.raw"
  for p in "${P[@]}"; do cat=${p%% *}; re=${p#* }
    GIT_NO_LAZY_FETCH=1 git -C "$g" grep -n -P -e "$re" HEAD -- '*.java' 2>/dev/null | sed "s#^HEAD:##; s#^#$cat\t#" >> "$OUT/.raw"; done
  awk -F'\t' '{ split($2, a, ":"); path=a[1]
    if (path ~ /(^|\/)(tests?|test-[^\/]*|examples?|snippets?)\// || path ~ /Test[A-Za-z0-9_]*\.java$/ || path ~ /^\./) next
    if (path ~ /org\/eclipse\/swt\/widgets\/(Display|Synchronizer)\.java$/) next
    print }' "$OUT/.raw" > "$OUT/.kept"
  : > "$OUT/.encl"
  awk -F'\t' '{ split($2, a, ":"); print a[1] "\t" a[2] }' "$OUT/.kept" | sort -t$'\t' -k1,1 -k2,2n | awk -F'\t' '
    $1 != f { if (f != "") print f "\t" l; f = $1; l = $2; next } { l = l "," $2 } END { if (f != "") print f "\t" l }' |
  while IFS=$'\t' read -r path lines; do
    GIT_NO_LAZY_FETCH=1 git -C "$g" show "HEAD:$path" 2>/dev/null | awk -v want="$lines" -f C:/m3-superset/tools/enclosing_method.awk | sed "s#^#$path\t#" >> "$OUT/.encl"
  done
  awk -F'\t' -v repo="$r" -v sha="$sha" -v OFS='\t' '
    FNR == NR { enc[$1 ":" $2] = $3; next }
    { cat = $1; rest = $2; for (i = 3; i <= NF; i++) rest = rest " " $i; split(rest, a, ":"); path = a[1]; ln = a[2]
      snip = substr(rest, length(path) + length(ln) + 3); gsub(/\t/, " ", snip); gsub(/\r/, "", snip); gsub(/^ +/, "", snip); gsub(/ +$/, "", snip); snip = substr(snip, 1, 160); gsub(/ +$/, "", snip)
      m = ((path ":" ln) in enc) ? enc[path ":" ln] : "-"
      if (m ~ /^(getText|getImage|getStyledText|getForeground|getBackground|getFont|getToolTipText|getColumnText|getColumnImage|getChildren|getElements|hasChildren|getParent|update|compare|select|isLabelProperty)$/) role = "UI_PROVIDER"
      else if (m ~ /^(widgetSelected|widgetDefaultSelected|handleEvent|selectionChanged|doubleClick|modifyText|paintControl|keyPressed|keyReleased|mouseDown|mouseUp|focusGained|partActivated|partVisible|partOpened|treeExpanded|checkStateChanged|menuAboutToShow|runInUIThread|createPartControl|createControl|createContents|createDialogArea|okPressed|buttonPressed|performOk|setFocus|open|execute)$/) role = "UI_CALLBACK"
      else if (m ~ /^(runInWorkspace|run|done|schedule)$/) role = "RUN_METHOD"
      else role = "OTHER"
      print repo, cat, role, m, path, ln, "https://github.com/hsoliwal/" repo "/blob/" sha "/" path "#L" ln, snip }' "$OUT/.encl" "$OUT/.kept" >> "$OUT/UI_THREAD_SITES.tsv"
  echo "$r rows=$(awk -F'\t' -v r="$r" '$1==r' "$OUT/UI_THREAD_SITES.tsv" | wc -l)" >&2
done
rm -f "$OUT/.raw" "$OUT/.kept" "$OUT/.encl"
# pass 5: verdict. Blocking work in a UI provider or UI callback is UI_THREAD_ABUSE; syncExec from a run method is WORKER_BLOCKS_ON_UI.
awk -F'\t' -v OFS='\t' 'NR == 1 { print $0, "verdict"; next }
  { v = "REVIEW"
    if (($3 == "UI_PROVIDER" || $3 == "UI_CALLBACK") && $2 ~ /^(workspace_io|thread_sleep|job_join|busy_wait|modal_no_fork|nested_event_loop)$/) v = "UI_THREAD_ABUSE"
    else if ($3 == "UI_PROVIDER" && $2 == "sync_exec") v = "UI_THREAD_ABUSE"
    else if ($3 == "RUN_METHOD" && $2 == "sync_exec") v = "WORKER_BLOCKS_ON_UI"
    else if ($2 == "display_default" && $5 !~ /(\/ui\/|\.ui\.|\/ui$|swt)/) v = "OFF_THREAD_DISPLAY"
    else if ($2 ~ /^(nested_event_loop|modal_no_fork|busy_wait)$/) v = "UI_THREAD_WAIT"
    print $0, v }' "$OUT/UI_THREAD_SITES.tsv" > "$OUT/.v" && mv "$OUT/.v" "$OUT/UI_THREAD_SITES.tsv"
awk -F'\t' 'NR > 1 { n[$1 "\t" $9]++ } END { for (k in n) print k "\t" n[k] }' "$OUT/UI_THREAD_SITES.tsv" | sort > "$OUT/SUMMARY_BY_VERDICT.tsv"
