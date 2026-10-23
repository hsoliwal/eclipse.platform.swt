#!/usr/bin/env bash
# Stream-mark every file of each Eclipse fork, mark every UI file, and scaffold a .m3view spec row for every UI file
# that creates a viewer (user 2026-10-20: "mark all Eclipse UI files", "using a stream", "scaffold everything",
# "all eclipse files"). One git cat-file --batch stream per repo through one awk pass (mark_ui_files_stream.awk).
# Clones: S:/m3-scratch/forks (shallow bare, made by correction_sites.sh). Usage: mark_ui_files_stream.sh <out> [repo...]
OUT=${1:-S:/m3-scratch/ui-file-marks}; shift; mkdir -p "$OUT"; F=S:/m3-scratch/forks
REPOS=${*:-"eclipse.platform.ui eclipse.platform eclipse.platform.swt nebula gef-classic eclipse.jdt.core eclipse.jdt.ui eclipse.jdt.debug org.eclipse.emf egit initiative31-eclipse.pde p2"}
printf 'repo\tpath\tkind\tclass\textends\tviewers\tswt_tree_table\trebuild_signals\tthread_signals\tworkspace_io\telement_maps\tfamilies\tneeds\tsource_adapter_guess\tpermalink\n' > "$OUT/UI_FILE_MARKS.tsv"
printf 'repo\tpath\tclass\tkind\tview_id\tsource\tpresentation\tbehaviours\tfooter\tviewbar\n' > "$OUT/SPEC_SCAFFOLD.tsv"
printf 'repo\tpath\tui\n' > "$OUT/ALL_FILES.tsv"
for r in $REPOS; do
  g="$F/$r.git"; [ -d "$g" ] || { echo "no clone $r" >&2; continue; }
  sha=$(git -C "$g" rev-parse HEAD)
  git -C "$g" ls-tree -r HEAD | awk -F'\t' '{ split($1, m, " "); p = $2
    if (m[2] == "blob" && p ~ /\.java$/ && p !~ /(^|\/)(tests?|test-[^\/]*|examples?|snippets?)\// && p !~ /Test[A-Za-z0-9_]*\.java$/ && p !~ /^\./) print m[3] "\t" p }' > "$OUT/.objs"
  cut -f2 "$OUT/.objs" > "$OUT/.paths"
  cut -f1 "$OUT/.objs" | GIT_NO_LAZY_FETCH=1 git -C "$g" cat-file --batch 2>/dev/null |
    LC_ALL=C awk -v REPO="$r" -v PATHS="$OUT/.paths" -v SPEC="$OUT/SPEC_SCAFFOLD.tsv" -v ALL="$OUT/ALL_FILES.tsv" \
      -v BASE="https://github.com/hsoliwal/$r/blob/$sha/" -f C:/m3-superset/tools/mark_ui_files_stream.awk >> "$OUT/UI_FILE_MARKS.tsv"
  echo "$r java=$(wc -l < "$OUT/.objs") ui=$(awk -F'\t' -v r="$r" '$1==r' "$OUT/UI_FILE_MARKS.tsv" | wc -l)" >&2
done
rm -f "$OUT/.objs" "$OUT/.paths"
