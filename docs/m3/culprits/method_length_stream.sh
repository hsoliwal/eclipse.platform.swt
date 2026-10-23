#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Method length over every Java blob of a bare fork, streamed with git cat-file --batch (no checkout).
# Usage: method_length_stream.sh <repo.git> <rev> [limit]  -> rows: repo path line method body_lines (methods > limit)
set -euo pipefail
G="$1"; REV="$2"; LIMIT="${3:-15}"; R=$(basename "$G" .git); HERE=$(cd "$(dirname "$0")" && pwd); export LC_ALL=C GIT_NO_LAZY_FETCH=1
L=$(mktemp); trap 'rm -f "$L"' EXIT
# ls-tree takes path prefixes, not globs: filter the paths here (main code only; recipe and conflict copies excluded)
git -C "$G" ls-tree -r "$REV" | awk -F'\t' '{ split($1, a, " "); p = $2
	if (p ~ /\.java$/ && p !~ /(^|\/)tests?\// && p !~ /\.tests?(\.[^\/]*)?\// && p !~ /\/src\/test\// && p !~ /^\.superset\// && p !~ /^\.m3\// && p !~ /hash-pinned-java/) print a[3] "\t" p }' > "$L"
cut -f1 "$L" | git -C "$G" cat-file --batch | awk -v BINMODE=3 -v LIMIT="$LIMIT" -v LIST="$L" -f "$HERE/method_length.awk" | sed "s|^|$R\t|"
