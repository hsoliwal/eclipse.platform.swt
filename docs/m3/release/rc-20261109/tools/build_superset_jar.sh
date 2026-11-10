#!/usr/bin/env bash
# Package the M3 superset SWT win32 x86_64 fragment jar from already compiled classes (compile_gate.sh win32).
# Everything shipped comes from the fork worktree: classes, package resources, legal files, fragment.properties,
# and the fork's own win32 natives (binaries/org.eclipse.swt.win32.win32.x86_64, version from Library.java).
# Derived from C:/m3-superset/build-swt.sh (20261013.2); differences: fork natives instead of official ones,
# fork resources (SVG skins, ._properties -> .properties) instead of the official jar's, fork manifest as base,
# OSGi-valid qualifier, sha256 per shipped entry in MAPPING.tsv.
# Usage: build_superset_jar.sh <worktree> <classes-dir> <stamp> <ledger.tsv> <out-dir>
set -euo pipefail
WT=$1; CLASSES=$2; STAMP=$3; LEDGER=$4; OUT=$5
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
OFFICIAL_VERSION=${SWT_OFFICIAL_VERSION:-3.135.0}
OFFICIAL_JAR=${SWT_OFFICIAL_JAR:-$HOME/.m2/repository/org/eclipse/platform/org.eclipse.swt.win32.win32.x86_64/$OFFICIAL_VERSION/org.eclipse.swt.win32.win32.x86_64-$OFFICIAL_VERSION.jar}
FRAG=binaries/org.eclipse.swt.win32.win32.x86_64
JAR_NAME="org.eclipse.swt.win32.win32.x86_64-m3-superset-${STAMP}.jar"
log() { printf '%s %s\n' "$(date +%H:%M:%S)" "$*" >&2; }

w=$OUT/stage-work; st=$w/stage
rm -rf "$w"; mkdir -p "$st/META-INF/m3" "$OUT"
cp -r "$CLASSES/." "$st/"
find "$st" -type f ! -name '*.class' -delete
find "$st" -name '*.class' -type f | sed "s#^$st/##" | sort > "$w/fork-classes.txt"

# package resources from the fragment's source folders (no library/ native sources, no package.html)
grep -o 'bundles/org.eclipse.swt/[^,\\]*' "$WT/$FRAG/build.properties" | tr -d '\r' | sed 's/ *$//' | sort -u > "$w/dirs.txt"
: > "$w/resources.tsv"
while IFS= read -r d; do
  [ -d "$WT/$d" ] || continue
  ( cd "$WT/$d" && find . -type f ! -name '*.java' ! -path './library/*' ! -path '*/library/*' ! -name package.html ! -name '*.c' ! -name '*.h' ! -name '*.mak' ) \
    | sed 's#^\./##' | while IFS= read -r f; do
        dst=${f%._properties}; [ "$dst" != "$f" ] && dst=$dst.properties
        mkdir -p "$st/$(dirname "$dst")"; cp "$WT/$d/$f" "$st/$dst"
        printf '%s\t%s/%s\n' "$dst" "$d" "$f" >> "$w/resources.tsv"
      done
done < "$w/dirs.txt"
# legal files and localization
cp -r "$WT/binaries/legal_files/win32.win32/." "$st/"
cp "$WT/$FRAG/fragment.properties" "$st/"
( cd "$WT/binaries/legal_files/win32.win32" && find . -type f | sed 's#^\./##' ) | awk -v p="binaries/legal_files/win32.win32" '{print $0"\t"p"/"$0}' >> "$w/resources.tsv"
printf 'fragment.properties\t%s/fragment.properties\n' "$FRAG" >> "$w/resources.tsv"

# natives: the fork's DLLs for the Library.java version, plus WebView2Loader
lib="$WT/bundles/org.eclipse.swt/Eclipse SWT PI/common/org/eclipse/swt/internal/Library.java"
ver=$(sed -n 's/.*MAJOR_VERSION = \([0-9]*\);.*/\1/p; s/.*MINOR_VERSION = \([0-9]*\);.*/\1/p; s/.*REVISION = \([0-9]*\);.*/\1/p' "$lib" | tr '\n' ' ' | awk '{printf "%s%03dr%s", $1, $2, $3}')
: > "$w/natives.tsv"
for dll in "$WT/$FRAG"/swt-*win32-"$ver".dll "$WT/$FRAG/WebView2Loader.dll"; do
  [ -f "$dll" ] || { log "missing native $dll"; exit 6; }
  head -c 2 "$dll" | grep -q MZ || { log "not a PE image (LFS pointer?): $dll"; exit 6; }
  cp "$dll" "$st/"; printf '%s\t%s/%s\n' "$(basename "$dll")" "$FRAG" "$(basename "$dll")" >> "$w/natives.tsv"
done
[ "$(wc -l < "$w/natives.tsv")" -ge 6 ] || { log "expected >= 6 natives for $ver"; exit 6; }

# official-only classes (not shipped) for the record
unzip -Z1 "$OFFICIAL_JAR" | grep '\.class$' | sort > "$w/official-classes.txt"
comm -23 "$w/official-classes.txt" "$w/fork-classes.txt" > "$w/official-only-classes.txt"

head=$(git -C "$WT" rev-parse HEAD); branch=$(git -C "$WT" rev-parse --abbrev-ref HEAD)
git -C "$WT" ls-tree -r HEAD -- bundles/org.eclipse.swt binaries | awk -F'\t' '{split($1,m," "); print $2"\t"m[3]}' > "$w/blobs.tsv"
{
  printf 'entry\tsource\tsource_blob\torigin\tsha256\n'
  awk -F'\t' -v dirs="$(tr '\n' '|' < "$w/dirs.txt")" '
    BEGIN { n = split(dirs, d, "|") }
    FNR == NR { src[$1] = $2; next }
    { cls = $0; outer = cls; sub(/\$.*\.class$/, ".class", outer); sub(/\.class$/, ".java", outer); found = "-"; blob = "-"
      for (i = 1; i <= n; i++) if (d[i] != "") { p = d[i] "/" outer; if (p in src) { found = p; blob = src[p]; break } }
      printf "%s\t%s\t%s\tFORK\n", cls, found, blob }' "$w/blobs.tsv" "$w/fork-classes.txt" \
    | while IFS=$'\t' read -r e s b o; do printf '%s\t%s\t%s\t%s\t%s\n' "$e" "$s" "$b" "$o" "$(sha256sum < "$st/$e" | cut -c1-64)"; done
  awk -F'\t' 'FNR == NR { src[$1] = $2; next } { printf "%s\t%s\t%s\tFORK_RESOURCE\n", $1, $2, ($2 in src ? src[$2] : "-") }' "$w/blobs.tsv" "$w/resources.tsv" \
    | while IFS=$'\t' read -r e s b o; do printf '%s\t%s\t%s\t%s\t%s\n' "$e" "$s" "$b" "$o" "$(sha256sum < "$st/$e" | cut -c1-64)"; done
  awk -F'\t' 'FNR == NR { src[$1] = $2; next } { printf "%s\t%s\t%s\tFORK_NATIVE\n", $1, $2, ($2 in src ? src[$2] : "-") }' "$w/blobs.tsv" "$w/natives.tsv" \
    | while IFS=$'\t' read -r e s b o; do printf '%s\t%s\t%s\t%s\t%s\n' "$e" "$s" "$b" "$o" "$(sha256sum < "$st/$e" | cut -c1-64)"; done
  sed "s#\$#\t-\t-\tOFFICIAL_${OFFICIAL_VERSION}_ONLY_NOT_SHIPPED\t-#" "$w/official-only-classes.txt"
} > "$st/META-INF/m3/MAPPING.tsv"
cp "$LEDGER" "$st/META-INF/m3/LEDGER.tsv"

# manifest: the fork fragment manifest, qualifier resolved, M3 provenance headers appended
qual="m3-superset-${STAMP}"
tr -d '\r' < "$WT/$FRAG/META-INF/MANIFEST.MF" | sed "s/^Bundle-Version: \(.*\)\.qualifier\$/Bundle-Version: \1.$qual/" | sed '/^$/d' > "$w/MANIFEST.MF"
grep -q "^Bundle-Version: .*\.$qual\$" "$w/MANIFEST.MF" || { log "qualifier not resolved"; exit 8; }
{
  printf 'Implementation-Version: %s %s\n' "$branch" "$head"
  printf 'M3-Build: m3-superset-%s\n' "$STAMP"
  printf 'M3-Source-Repository: https://github.com/hsoliwal/eclipse.platform.swt\n'
  printf 'M3-Source-Branch: %s\nM3-Source-Commit: %s\n' "$branch" "$head"
  printf 'M3-Natives: fork %s (%s)\n' "$FRAG" "$ver"
  printf 'M3-Mapping: META-INF/m3/MAPPING.tsv\nM3-Ledger: META-INF/m3/LEDGER.tsv\n'
} >> "$w/MANIFEST.MF"
rm -f "$OUT/$JAR_NAME"
"$JDK/bin/jar" --create --file "$OUT/$JAR_NAME" --manifest "$w/MANIFEST.MF" -C "$st" .
log "built $JAR_NAME: $(wc -l < "$w/fork-classes.txt") classes, $(wc -l < "$w/resources.tsv") resources, $(wc -l < "$w/natives.tsv") natives ($ver), $(wc -l < "$w/official-only-classes.txt") official-only not shipped"
( cd "$OUT" && sha256sum "$JAR_NAME" )
