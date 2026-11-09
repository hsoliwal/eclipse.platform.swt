#!/usr/bin/env bash
# Build the org.eclipse.swt.svg fragment jar the way its build.properties does: source.. = src/, resources/
# (resources/ carries META-INF/services/org.eclipse.swt.internal.image.SVGRasterizer, so ServiceLoader in
# SVGFileFormat finds JSVGRasterizer), bin.includes = META-INF/, ., fragment.properties, about.html.
# JSVG comes from Maven Central at the lower bound of the fragment's Import-Package range [2.1.0,3.0.0).
# Usage: build_svg_fragment.sh <worktree> <swt-jar> <jsvg-jar> <out-dir>   -> prints the fragment jar path
set -euo pipefail
WT=$1; SWT=$2; JSVG=$3; OUT=$4; JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
B="$WT/bundles/org.eclipse.swt.svg"
ver=$(tr -d '\r' < "$B/META-INF/MANIFEST.MF" | sed -n 's/^Bundle-Version: \(.*\)\.qualifier$/\1/p')
w="$OUT/svg-work"; rm -rf "$w"; mkdir -p "$w/stage" "$OUT"
find "$B/src" -name '*.java' | sed 's#.*#"&"#' > "$w/src.txt"
"$JDK/bin/javac" --release 21 -encoding UTF-8 -Xlint:none -proc:none -cp "$SWT;$JSVG" -d "$w/stage" @"$w/src.txt" > "$w/javac.log" 2>&1
cp -r "$B/resources/." "$w/stage/"
cp "$B/fragment.properties" "$B/about.html" "$w/stage/"
tr -d '\r' < "$B/META-INF/MANIFEST.MF" | sed "s/^Bundle-Version: \(.*\)\.qualifier\$/Bundle-Version: \1.m3-superset-20261109/" | sed '/^$/d' > "$w/MANIFEST.MF"
jar="$OUT/org.eclipse.swt.svg-$ver.m3-superset-20261109.jar"
rm -f "$jar"; "$JDK/bin/jar" --create --file "$jar" --manifest "$w/MANIFEST.MF" -C "$w/stage" .
echo "$jar"
