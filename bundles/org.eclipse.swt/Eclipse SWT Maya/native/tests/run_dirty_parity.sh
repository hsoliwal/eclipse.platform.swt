#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
src="$(cd "$here/../cc/swt" && pwd)"
region_source="$here/../../../Eclipse SWT/common/org/eclipse/swt/internal/render/NativeDirtyRegions.java"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/classes"
python3 "$here/GenerateParityCases.py" > "$tmp/cases.txt"
javac --release 21 -Xlint:all -Werror -d "$tmp/classes" \
    "$region_source" "$here/NativeDirtyParity.java"
java -cp "$tmp/classes" org.eclipse.swt.internal.render.NativeDirtyParity < "$tmp/cases.txt" > "$tmp/java.txt"
"${CXX:-clang++}" -std=c++17 -O2 -Wall -Wextra -Werror -pedantic \
    -I"$src" "$here/NativeDirtyParity.cc" -o "$tmp/native"
"$tmp/native" < "$tmp/cases.txt" > "$tmp/native.txt"
if ! cmp -s "$tmp/java.txt" "$tmp/native.txt"; then
  paste "$tmp/cases.txt" "$tmp/java.txt" "$tmp/native.txt" | awk '$NF!=$(NF-1){print;exit}' >&2
  echo 'FAIL Java/C++ native dirty admission mismatch' >&2
  exit 1
fi
cases="$(wc -l < "$tmp/cases.txt")"
accepted="$(grep -c '^1$' "$tmp/java.txt" || true)"
rejected="$(grep -c '^0$' "$tmp/java.txt" || true)"
echo "PASS $cases Java21 / C++17 native dirty admission parity cases ($accepted accepted, $rejected rejected)"
