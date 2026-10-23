#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0
#
# Superset merge, server side (no local objects): a superset branch from the default branch, then every open PR
# head merged into it through POST /repos/{r}/merges in the given order. A conflict (409) or an error is recorded
# and the run continues; nothing is rebased, nothing is forced, no PR is merged or closed (the user merges).
# Usage: superset_merge.sh <owner/repo> <default-branch> <superset-branch> <order.tsv (number head_sha title)>
# Output: merges.<repo>.tsv  number head_sha result merge_sha message
set -uo pipefail
R="$1"; DEF="$2"; SUP="$3"; ORDER="$4"; N=${R#*/}; cd "$(dirname "$0")"; export LC_ALL=C   # grep -P needs a unibyte locale here
if ! gh api "repos/$R/git/ref/heads/$SUP" --jq .object.sha >/dev/null 2>&1; then
	base=$(gh api "repos/$R/git/ref/heads/$DEF" --jq .object.sha)
	gh api -X POST "repos/$R/git/refs" -f ref="refs/heads/$SUP" -f sha="$base" >/dev/null && echo "created $SUP at $DEF ${base:0:10}" >&2
fi
OUT="merges.$N.tsv"; [ -s "$OUT" ] || printf 'number\thead_sha\tresult\tmerge_sha\tmessage\n' > "$OUT"
ok=0; conflict=0; err=0; skip=0
while IFS=$'\t' read -r num sha title; do
	[ -n "$num" ] || continue
	if grep -q -P "^$num\t" "$OUT"; then skip=$((skip + 1)); continue; fi
	out=$(gh api -X POST "repos/$R/merges" -f base="$SUP" -f head="$sha" -f commit_message="Superset: merge PR #$num ($title)" 2>&1); code=$?
	if [ $code -eq 0 ]; then
		msha=$(printf '%s' "$out" | jq -r '.sha // "up-to-date"' 2>/dev/null); res=MERGED; ok=$((ok + 1)); msg=""
	else
		msg=$(printf '%s' "$out" | tr '\n\t' '  ' | grep -o -E 'HTTP [0-9]+.*' | cut -c1-160)
		case "$msg" in *409*|*onflict*) res=CONFLICT; conflict=$((conflict + 1));; *) res=ERROR; err=$((err + 1));; esac; msha=""
	fi
	printf '%s\t%s\t%s\t%s\t%s\n' "$num" "$sha" "$res" "$msha" "$msg" >> "$OUT"
	printf '%s #%s %s\n' "$res" "$num" "${title:0:60}" >&2
done < "$ORDER"
echo "$N $SUP: merged $ok, conflicts $conflict, errors $err, skipped $skip" >&2
