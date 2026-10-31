#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others
# Runtime differential of ARRAY_LOOP_INTRINSICS v1 on SWT's JPEG decoders (headless, no natives): the JPEGDecoder and
# JPEGFileFormat of <base-rev> are compiled over the gate classes and put first on the class path; JpegLoopProbe
# decodes the same generated JPEGs with the old and the new classes, on both decoder paths, and the outputs must match.
# Usage: prove_jpeg_loops.sh <worktree> <gate-classes> <base-rev> <out-dir>
set -u
WT=${1:?worktree}; CLASSES=${2:?gate classes}; BASE=${3:?base revision}; OUT=${4:?out-dir}
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
HERE=$(cd "$(dirname "$0")" && { pwd -W 2>/dev/null || pwd; })
IMG="bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/image"
rm -rf "$OUT"; mkdir -p "$OUT/old-src" "$OUT/old" "$OUT/probe"
for f in JPEGDecoder.java JPEGFileFormat.java; do git -C "$WT" show "$BASE:$IMG/$f" > "$OUT/old-src/$f" || exit 2; done
"$JDK/bin/javac" -nowarn -encoding UTF-8 --release 21 -cp "$CLASSES" -d "$OUT/old" "$OUT/old-src/JPEGDecoder.java" \
  "$OUT/old-src/JPEGFileFormat.java" > "$OUT/old.javac.log" 2>&1 || { echo "JAVAC old: FAIL"; head "$OUT/old.javac.log"; exit 1; }
"$JDK/bin/javac" -nowarn -encoding UTF-8 -cp "$CLASSES" -d "$OUT/probe" "$HERE/JpegLoopProbe.java" > "$OUT/probe.javac.log" 2>&1 \
  || { echo "JAVAC probe: FAIL"; head "$OUT/probe.javac.log"; exit 1; }
fail=0
for path in default 3.2; do
  prop=""; [ "$path" = 3.2 ] && prop="-Dorg.eclipse.swt.internal.image.JPEGFileFormat_3.2=1"
  "$JDK/bin/java" -Djava.awt.headless=true $prop -cp "$OUT/probe;$OUT/old;$CLASSES" JpegLoopProbe > "$OUT/$path.old.txt" 2>&1 || fail=1
  "$JDK/bin/java" -Djava.awt.headless=true $prop -cp "$OUT/probe;$CLASSES" JpegLoopProbe > "$OUT/$path.new.txt" 2>&1 || fail=1
  n=$(grep -c ' sha256=' "$OUT/$path.new.txt"); n=${n:-0}
  if [ "$n" -gt 0 ] && diff "$OUT/$path.old.txt" "$OUT/$path.new.txt" > "$OUT/$path.diff"; then
    echo "JPEG $path decoder: SAME ($n images)"
  else
    echo "JPEG $path decoder: FAIL"; head -5 "$OUT/$path.new.txt" "$OUT/$path.diff"; fail=1
  fi
done
# DISCRIMINATOR: a mutant of the rewritten statements (fill 1 instead of 0) must change the decoded pixels, so the
# probe does reach both rewritten decoder sites
mkdir -p "$OUT/mutant-src" "$OUT/mutant"
sed 's/Arrays.fill(blk, (short) 0);/Arrays.fill(blk, (short) 1);/' "$WT/$IMG/JPEGDecoder.java" > "$OUT/mutant-src/JPEGDecoder.java"
sed 's/Arrays.fill(dataUnit, 0);/Arrays.fill(dataUnit, 1);/' "$WT/$IMG/JPEGFileFormat.java" > "$OUT/mutant-src/JPEGFileFormat.java"
m=$(cat "$OUT/mutant-src/"*.java | grep -c -e 'Arrays.fill(blk, (short) 1);' -e 'Arrays.fill(dataUnit, 1);'); m=${m:-0}
"$JDK/bin/javac" -nowarn -encoding UTF-8 --release 21 -cp "$CLASSES" -d "$OUT/mutant" "$OUT/mutant-src/JPEGDecoder.java"   "$OUT/mutant-src/JPEGFileFormat.java" > "$OUT/mutant.javac.log" 2>&1 || { echo "JAVAC mutant: FAIL"; fail=1; }
for path in default 3.2; do
  prop=""; [ "$path" = 3.2 ] && prop="-Dorg.eclipse.swt.internal.image.JPEGFileFormat_3.2=1"
  "$JDK/bin/java" -Djava.awt.headless=true $prop -cp "$OUT/probe;$OUT/mutant;$CLASSES" JpegLoopProbe > "$OUT/$path.mutant.txt" 2>&1
  if [ "$m" -eq 2 ] && ! diff -q "$OUT/$path.new.txt" "$OUT/$path.mutant.txt" > /dev/null; then
    echo "DISCRIMINATOR $path decoder: mutant DIFFERS ($(diff "$OUT/$path.new.txt" "$OUT/$path.mutant.txt" | grep -c '^>') lines)"
  else
    echo "DISCRIMINATOR $path decoder: FAIL (mutant sites $m, output unchanged)"; fail=1
  fi
done
[ "$fail" -eq 0 ] && echo "JPEG_DIFFERENTIAL: PASS" || { echo "JPEG_DIFFERENTIAL: FAIL"; exit 1; }
