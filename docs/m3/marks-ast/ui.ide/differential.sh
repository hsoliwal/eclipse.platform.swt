#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Differential of the compiler-AST marks (M3MarksPlugin) against the grep marks of passes three and four for one
# bundle: BOTH / GREP_ONLY (regex over-match, or a site javac could not attribute) / AST_ONLY (regex under-match).
# Usage: differential.sh <ast-marks.tsv> <bundle-prefix> <pass3-sites.gz> <pass4-sites.gz> > DIFFERENTIAL.tsv
set -euo pipefail
AST="$1"; B="$2"; export LC_ALL=C; T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
{ zcat "$3"; zcat "$4"; } | awk -F'\t' -v OFS='\t' -v b="$B" 'index($3, b "/") == 1 && $2 == "MAIN" { print $1, $3, $4, "GREP" }' | sort -u > "$T/grep"
tr '\\' '/' < "$AST" | awk -F'\t' -v OFS='\t' 'NR > 1 { p = $4; sub(/^.*\/jdk21-[^\/]+\//, "", p); print $1, p, $5, "AST" }' | sort -u > "$T/ast"
printf 'template\tpath\tline\tverdict\n'
sort -u "$T/grep" "$T/ast" | awk -F'\t' -v OFS='\t' '{ k = $1 "\t" $2 "\t" $3; v[k] = (k in v) ? "BOTH" : $4 } END { for (k in v) print k, v[k] }' | sort
