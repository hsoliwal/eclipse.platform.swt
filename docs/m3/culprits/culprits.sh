#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# The culprits ledger (user 2026-10-20: "collections are the number one culprit, StringBuilder/Buffer, IO, regex,
# loops, method length"; "Eclipse has not resisted the urge to put an object every possible place"; "EMF is the
# worst framework"): main-code sites per culprit from the pass-four marks, per repo, with the method-length pass.
# Usage: culprits.sh <publish/code-mapping> <ml-dir> <out-dir>
set -euo pipefail
CM="$1"; ML="$2"; O="$3"; mkdir -p "$O"; export LC_ALL=C
cat > "$O/CULPRITS.tsv" <<'EOF'
rank	culprit	targets	lane	why
1	collections (objects every possible place)	M3_ARRAY_LANES;M3_HASH_LANES;M3_SET_LANES;M3_ORDERED_LANES;M3_SORTED_LANES;M3_IDENTITY_WEAK_LANES;M3_ENUM_BITS;M3_DEQUE_LANES;M3_HEAP_LANES;M3_PRIMITIVE_LANES;MULTIMAP_TO_LANE;BULK_LANE_PASS	collections	one lane model, M3 collections (primitive lanes, bitsets, interned ids); no entry objects, no boxing
2	StringBuilder, StringBuffer, concatenation	M3STRING_COMPOSITE;FORMAT_PRECOMPUTE;CASE_FOLD_FACTS;TOKEN_INTERNER	m3string	String = composite of M3Strings (tuple DAG), compile-time format precompute, interned tokens; never flatten
3	IO	M3FILE_IO;M3FILE_STREAMS;M3FILE_ARCHIVE_STREAM	m3file	M3File entries (attribute-first change detection, shadow streams, sub-progress, deltas); no File object per entry
4	regex	REGEX_PRECOMPUTE	precompute	constant Pattern.compile precompute, regex-cursor over M3String, trigram absence gates
5	loops (hard limit: banned)	LOOP_TO_LANE;ITERATION_TO_LANE	collections	bulk lane passes, fused scans, streams
6	method length (hard limit: 15 lines)	METHOD_SPLIT	method-split	MRL method splitting (template-only, behaviour preserved); METHOD_LENGTH.tsv
EOF
zcat "$CM/FILE_MARKS.tsv.gz" | awk -F'\t' -v OFS='\t' -v C="$O/CULPRITS.tsv" '
	BEGIN { while ((getline l < C) > 0) { split(l, a, "\t"); if (a[1] ~ /^[0-9]/) { n = split(a[3], t, ";"); for (i = 1; i <= n; i++) cul[t[i]] = a[2]; rank[a[2]] = a[1] } } }
	NR > 1 && $3 == "MAIN" && ($4 in cul) { c = cul[$4]; s[c] += $5; f[c "\t" $2] = 1; r[$1 "\t" c] += $5; e4[c] += ($2 ~ /org\.eclipse\.e4\./) ? $5 : 0; emf[c] += ($1 == "org.eclipse.emf") ? $5 : 0 }
	END { print "rank", "culprit", "main_sites", "files", "e4_bundles", "emf" > "/dev/stderr"
		for (c in s) { nf = 0; for (k in f) if (index(k, c "\t") == 1) nf++; printf "%s\t%s\t%d\t%d\t%d\t%d\n", rank[c], c, s[c], nf, e4[c], emf[c] }
		for (k in r) print k, r[k] > "'"$O"'/CULPRITS_BY_REPO.unsorted" }' 2> /dev/null | sort -n > "$O/CULPRITS_COUNTS.body"
{ printf 'rank\tculprit\tmain_sites\tfiles\te4_bundles\temf\n'; cat "$O/CULPRITS_COUNTS.body"; } > "$O/CULPRITS_COUNTS.tsv"; rm -f "$O/CULPRITS_COUNTS.body"
{ printf 'repo\tculprit\tmain_sites\n'; sort -t$'\t' -k1,1 -k3,3nr "$O/CULPRITS_BY_REPO.unsorted"; } > "$O/CULPRITS_BY_REPO.tsv"; rm -f "$O/CULPRITS_BY_REPO.unsorted"
{ printf 'repo\tpath\tline\tmethod\tbody_lines\n'; cat "$ML"/*.tsv | sort -t$'\t' -k5,5nr; } | gzip -9 > "$O/METHOD_LENGTH.tsv.gz"
{ printf 'repo\tmethods_over_15\tover_50\tover_100\tlongest\n'; for f in "$ML"/*.tsv; do awk -F'\t' -v r="$(basename "$f" .tsv)" '{ n++; if ($5 > 50) a++; if ($5 > 100) b++; if ($5 > m) { m = $5; w = $4 " " $5 } } END { printf "%s\t%d\t%d\t%d\t%s\n", r, n, a + 0, b + 0, w }' "$f"; done | sort -t$'\t' -k2,2nr; } > "$O/METHOD_LENGTH_SUMMARY.tsv"
{ cat <<EOF
# Culprits: where Eclipse spends its objects (user 2026-10-20)

Ranked by the user: collections first ("the number one culprit"; "Eclipse has not resisted the urge to put an
object every possible place"), then StringBuilder/StringBuffer, IO, regex, loops, method length. Counts are
main-code sites from pass four (\`../code-mapping\`), the method-length pass is \`method_length_stream.sh\`
(brace-depth over masked source, streamed from the bare forks; hard limit 15 body lines).

| Rank | Culprit | Main sites | Files | in e4 bundles | in EMF |
| --- | --- | --- | --- | --- | --- |
EOF
awk -F'\t' 'NR > 1 { printf "| %s | %s | %d | %d | %d | %d |\n", $1, $2, $3, $4, $5, $6 }' "$O/CULPRITS_COUNTS.tsv"
printf '\n| Repo | Methods over 15 lines | over 50 | over 100 | Longest |\n| --- | --- | --- | --- | --- |\n'
awk -F'\t' 'NR > 1 { printf "| %s | %d | %d | %d | %s |\n", $1, $2, $3, $4, $5 }' "$O/METHOD_LENGTH_SUMMARY.tsv"
cat <<'EOF'

Files: `CULPRITS.tsv` (the ranking and its lanes), `CULPRITS_COUNTS.tsv`, `CULPRITS_BY_REPO.tsv`,
`METHOD_LENGTH.tsv.gz` (every method over the limit: repo, path, line, method, body lines),
`METHOD_LENGTH_SUMMARY.tsv`. The lane order of the transform recipes follows this ranking: collections, m3string,
m3file, precompute, loops into lanes, method split (brief rule 32; LANES.tsv in `../plan`).
EOF
} > "$O/README.md"
echo "$O: $(wc -l < "$O/CULPRITS_COUNTS.tsv") culprits, $(zcat "$O/METHOD_LENGTH.tsv.gz" | wc -l) long methods"
