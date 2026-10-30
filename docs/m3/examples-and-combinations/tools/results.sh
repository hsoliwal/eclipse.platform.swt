#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Results of the runs (CPU only: awk over the TSVs of the JVM legs):
#   results/COMBINATION_RESULTS.tsv  one line per matrix row: the reference (record) leg and the row's own mode (compare)
#                                    leg; status = FAIL if a leg failed, NOT_RUN if no leg ran, else PASS
#   results/EXAMPLE_MODES.tsv        one line per gallery entry: status in each of the five modes
#   FINDINGS.tsv                     one line per recurring failure class (failure, location or differing step), with the
#                                    count, the rows/entries, the modes, an owner and one message
# Usage: results.sh [worktree]
WT=${1:-C:/m3-superset/swt-examples}; O=${M3_OUT:-H:/m3-scratch/swt-examples}; D=$WT/docs/m3/examples-and-combinations
M=$WT/tests/org.eclipse.swt.tests/tools/m3-combinations/COMBINATION_MATRIX.tsv
cat "$O/results/matrix-headless.tsv" <(tail -n +2 "$O/results/matrix-display.tsv" 2>/dev/null) > "$O/results/matrix-all.tsv"
awk -F'\t' -v OFS='\t' '
  FNR == 1 { file++; next }
  file == 1 { if ($13 == "record") rec[$1] = $14 "\t" $15 "\t" $16 "\t" $18 "\t" $19 "\t" $20; else cmp[$1] = $14 "\t" $12 "\t" $18 "\t" $19 "\t" $20; next }
  file == 2 { row[++n] = $0; id[n] = $1 }
  END {
    print "row\twidget\tmode\tstyle\tfeature\tapi\top\tcount\ttheme\tlanguage\tenv\tstatus\trecord_status\ttheme_language\tleak\tcompare_status\tfailure\tlocation\tmessage"
    for (i = 1; i <= n; i++) {
      hasR = id[i] in rec; hasC = id[i] in cmp
      split (hasR ? rec[id[i]] : "", r, "\t"); split (hasC ? cmp[id[i]] : "", c, "\t")
      rs = hasR ? r[1] : "NOT_RUN"; cs = hasC ? c[1] : "-"
      st = (rs == "FAIL" || cs == "FAIL") ? "FAIL" : (rs == "NOT_RUN" && cs != "PASS") ? "NOT_RUN" : "PASS"
      f = cs == "FAIL" ? c[3] "\t" c[4] "\t" c[5] : rs == "FAIL" ? r[4] "\t" r[5] "\t" r[6] : "-\t-\t-"
      print row[i], st, rs, (hasR ? r[2] : "-"), (hasR ? r[3] : "-"), cs, f
    }
  }' "$O/results/matrix-all.tsv" "$M" > "$D/results/COMBINATION_RESULTS.tsv"
if [ -f "$O/results/examples-modes.tsv" ]; then
  awk -F'\t' -v OFS='\t' 'FNR == 1 { file++; next } file == 1 { impl[FNR - 2] = $7; title[FNR - 2] = $2 "\t" $3; next }
    { key = $2; cls[key] = $3; args[key] = $4; st[key, $1] = $5; det[key, $1] = $8 }
    END {
      print "entry\tclass\targs\timplemented_modes\tviewport\toriginal\tcustom\tm3\tgpu\tdetail_of_first_non_pass"
      for (k = 0; k in impl; k++) {
        split (title[k], t, "\t"); d = "-"; line = k "\t" t[1] "\t" t[2] "\t" impl[k]
        split ("viewport original custom m3 gpu", modes, " ")
        for (m = 1; m <= 5; m++) { s = ((k, modes[m]) in st) ? st[k, modes[m]] : "NOT_RUN"; line = line "\t" s; if (s != "PASS" && d == "-") d = modes[m] ": " substr (det[k, modes[m]], 1, 300) }
        print line, d
      }
    }' "$WT/examples/org.eclipse.swt.snippets/tools/GALLERY_INDEX.tsv" "$O/results/examples-modes.tsv" > "$D/results/EXAMPLE_MODES.tsv"
fi
# failure classes: a mode difference is classed by its differing step (create, feature, theme-language, op, api, dispose)
# and the row's api or op; any other failure by its exception and location
{
  awk -F'\t' -v OFS='\t' 'NR > 1 && $12 == "FAIL" {
      if ($19 ~ /^step [0-9]+ differs/) { split ("create feature theme-language op api dispose", names, " "); s = $19; sub (/^step /, "", s); s = names[substr (s, 1, index (s, " ") - 1) + 1]
        key = "MODE_DIFF\tstep " s " (" (s == "api" ? $6 : s == "op" ? $7 : s) ")" } else key = $17 "\t" $18
      print key, "matrix " $1 " " $2 " " $3, $19 }' "$D/results/COMBINATION_RESULTS.tsv"
  # rows slower than 60 s in a leg: an observation (performance), classed by widget, kind (virtual or plain) and operation
  awk -F'\t' -v OFS='\t' 'NR > 1 && $17 > 60000 { kind = $4 ~ /VIRTUAL/ ? "virtual" : "plain"
      print "SLOW (> 60 s)", $2 " " kind " " $7 " " $8 " items", "matrix " $1 " " $12, "took " int ($17 / 1000) " s in mode " $12 " (" $13 ")" }' "$O/results/matrix-all.tsv"
  [ -f "$O/results/examples-modes.tsv" ] && awk -F'\t' -v OFS='\t' 'NR > 1 && $5 != "PASS" {
      d = $8; ex = d; sub (/:.*/, "", ex); loc = "-"; if (match (d, /\(([A-Za-z0-9_$]+\.java:[0-9]+)\)/)) loc = substr (d, RSTART + 1, RLENGTH - 2)
      print "EXAMPLE_" $5 " " ex, loc, "example " $2 " " $3 " " $4 " " $1, substr (d, 1, 300) }' "$O/results/examples-modes.tsv"
} | awk -F'\t' -v OFS='\t' '
  { key = $1 "\t" $2; n[key]++; if (n[key] <= 8) who[key] = who[key] (n[key] > 1 ? "; " : "") $3; if (!(key in msg)) msg[key] = $4 }
  END {
    for (key in n) {
      split (key, p, "\t"); owner = "viewport"
      if (p[2] ~ /M3Mode|M3ItemSelection|M3Scrolling|M3Residency|M3Prefetch|M3Visibility|M3Emptiness|M3RowMath|M3DiffApply|M3Bulk|M3TypeAhead|M3CheckState|M3Proxy|M3Column|M3Scroll|M3Row/ || p[1] ~ /MODE_DIFF/ && p[2] ~ /\(M3-/) owner = "enhanced API"
      if (p[2] ~ /render|Drawn|RowBackends|M3Canvas|GC|Path|Pattern|TextLayout|StyledText/) owner = "GC fidelity / render modes"
      printf "%s\t%s\t%d\t%s\t%s\t%s\n", p[1], p[2], n[key], owner, who[key], msg[key]
    }
  }' | sort -t$'\t' -k3,3nr -k1,2 | awk 'BEGIN { print "finding\tfailure\tlocation\tcount\towner\tsubjects (first 8)\tmessage" } { printf "F%02d\t%s\n", NR, $0 }' > "$D/FINDINGS.tsv"
awk -F'\t' 'NR > 1 { c[$12]++; if ($14 ~ /NOT_RUN/) tl++ } END { printf "matrix rows: "; for (s in c) printf "%s=%d ", s, c[s]; printf "(theme/language step NOT_RUN in %d rows)\n", tl }' "$D/results/COMBINATION_RESULTS.tsv"
[ -f "$D/results/EXAMPLE_MODES.tsv" ] && awk -F'\t' 'NR > 1 { for (i = 5; i <= 9; i++) c[$i]++ } END { printf "example runs: "; for (s in c) printf "%s=%d ", s, c[s]; print "" }' "$D/results/EXAMPLE_MODES.tsv"
echo "findings: $(($(wc -l < "$D/FINDINGS.tsv") - 1))"
