#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others
# Proof of ARRAY_LOOP_INTRINSICS v1 on its fixture (CPU only):
#   1. CLASSIFY      every `// expect:` marker of fixtures/ArrayLoopFixtures.java matches the recipe's decision
#   2. FIXED_POINT   a second --apply over the rewritten copy rewrites nothing
#   3. DIFFERENTIAL  the fixture compiled and run before and after the recipe prints the same trace
#   4. DISCRIMINATOR the naive replacement of every skipped form changes behaviour (each line DIFFERS)
# Usage: prove_array_intrinsics.sh <out-dir>        (JDK: M3_JDK, default C:/Program Files/Java/jdk-21)
set -u
HERE=$(cd "$(dirname "$0")" && { pwd -W 2>/dev/null || pwd; })
OUT=${1:?usage: prove_array_intrinsics.sh <out-dir>}
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
FIX="$HERE/../fixtures/ArrayLoopFixtures.java"
TOOL="$HERE/array_loop_intrinsics.py"
fail=0
rm -rf "$OUT"; mkdir -p "$OUT/before" "$OUT/after"
python -I "$TOOL" scan --expect "$FIX" > "$OUT/classify.tsv" || fail=1
m=$(grep -c "$(printf '\tMISMATCH$')" "$OUT/classify.tsv"); m=${m:-0}
ok=$(grep -c "$(printf '\tOK$')" "$OUT/classify.tsv"); ok=${ok:-0}
echo "CLASSIFY: $ok markers OK, $m mismatches"; [ "$ok" -gt 0 ] && [ "$m" -eq 0 ] || fail=1
cp "$FIX" "$OUT/before/"; cp "$FIX" "$OUT/after/"
python -I "$TOOL" scan --apply "$OUT/after/ArrayLoopFixtures.java" > "$OUT/apply.tsv"; tail -1 "$OUT/apply.tsv"
python -I "$TOOL" scan --apply "$OUT/after/ArrayLoopFixtures.java" > "$OUT/fixed_point.tsv"
grep -q ' rewrites=0 ' "$OUT/fixed_point.tsv" && echo "FIXED_POINT: PASS" || { echo "FIXED_POINT: FAIL"; fail=1; }
for v in before after; do
  if ! "$JDK/bin/javac" -nowarn -d "$OUT/$v/classes" "$OUT/$v/ArrayLoopFixtures.java" > "$OUT/$v/javac.log" 2>&1; then
    echo "JAVAC $v: FAIL"; head -20 "$OUT/$v/javac.log"; fail=1
  fi
  "$JDK/bin/java" -cp "$OUT/$v/classes" ArrayLoopFixtures > "$OUT/$v/trace.txt" 2>&1 || { echo "RUN $v: FAIL"; fail=1; }
done
if diff "$OUT/before/trace.txt" "$OUT/after/trace.txt" > "$OUT/trace.diff"; then
  echo "DIFFERENTIAL: PASS ($(wc -l < "$OUT/before/trace.txt") trace lines identical)"
else
  echo "DIFFERENTIAL: FAIL"; head -20 "$OUT/trace.diff"; fail=1
fi
d=$(grep -c '^DISCRIMINATOR ' "$OUT/before/trace.txt"); d=${d:-0}
n=$(grep -c '^DISCRIMINATOR [A-Z_]* DIFFERS ' "$OUT/before/trace.txt"); n=${n:-0}
echo "DISCRIMINATOR: $n of $d differ"; [ "$d" -gt 0 ] && [ "$n" -eq "$d" ] || fail=1
diff -u "$OUT/before/ArrayLoopFixtures.java" "$OUT/after/ArrayLoopFixtures.java" > "$OUT/rewrite.diff"
r=$(grep -c '^+[^+]' "$OUT/rewrite.diff"); echo "rewritten statements: ${r:-0}"
[ "$fail" -eq 0 ] && echo "PROOF: PASS" || { echo "PROOF: FAIL"; exit 1; }
