#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# Pass five: the per-file plan, composed mechanically from passes three (enhance targets) and four (code mapping),
# written the way an LLM would approach the file. One row per main-code file: repo, bundle, sites, targets with
# counts, and `approach`: the ordered steps the final pass takes, derived from the lanes of its targets in the
# fixed lane precedence of LANES.tsv (model first, then change sources, delta views, JFace forwarding, text API,
# collections and precompute, behaviours, threads last, review items at the end), each step naming its recipe
# kind. Leaves are ordered by the programme's fill order (platform.ui, PDE, JDT, m2e, rest), then by sites.
# Pure awk: no per-row processes. Usage: pass5_plan.sh <publish-dir> > PLAN.tsv
set -euo pipefail
P="$1"; cd "$(dirname "$0")"; export LC_ALL=C
{ zcat "$P/enhance-targets/FILE_MARKS.tsv.gz"; zcat "$P/code-mapping/FILE_MARKS.tsv.gz" | tail -n +2; } \
| awk -F'\t' -v OFS='\t' '
	NR == FNR { if (FNR > 1) { lane[$1] = $2; kind[$1] = $3; track[$1] = $4; if (!($2 in prec)) prec[$2] = ++np; lk[$2] = $3 } next }
	FNR == 1 { next }
	$3 == "MAIN" {
		k = $1 "\t" $2; n[k] += $5; t[k] = t[k] (t[k] == "" ? "" : ";") $4 "=" $5
		if ($4 in lane) { ls[k, lane[$4]] += $5 }
	}
	function order(r) { return r == "eclipse.platform.ui" ? 1 : r == "initiative31-eclipse.pde" ? 2 : r ~ /^eclipse\.jdt\./ ? 3 : r == "m2e-core" ? 4 : 5 }
	END {
		for (k in n) {
			split(k, a, "\t"); b = a[2]; sub(/\/(src|ui|core|model|compiler|common|eclipseui|jface|extensions|parts|loggers)(\/|$).*/, "", b)
			ap = ""; step = 0; rk = ""
			for (p = 1; p <= np; p++) for (l in prec) if (prec[l] == p && ((k, l) in ls)) {
				step++; ap = ap (ap == "" ? "" : " > ") step ". " l " (" lk[l] ", " ls[k, l] ")"
				if (lk[l] != "REVIEW") rk = (rk == "" || rk == lk[l]) ? lk[l] : "TEXT+JAVA" }
			print order(a[1]), a[1], b, a[2], n[k], t[k], ap, (rk == "" ? "REVIEW" : rk), "PLANNED"
		}
	}' LANES.tsv - \
| sort -t$'\t' -k1,1n -k5,5nr | awk -F'\t' -v OFS='\t' 'BEGIN { print "leaf", "order", "repo", "bundle", "path", "sites", "targets", "approach", "recipe_kind", "state" } { print NR, $0 }'
