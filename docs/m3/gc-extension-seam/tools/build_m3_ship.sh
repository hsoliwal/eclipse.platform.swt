#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Leaf 18, ONE_BUILD v1: builds the shipped artifacts from the worktree, nothing else on the class path:
#   swt.jar       org.eclipse.swt.win32.win32.x86_64 fragment: the win32 build.properties source folders, their
#                 resources (glyphs, skins, messages), the fragment's genuine r19 natives (checked against the LFS
#                 pointer oid of HEAD; a renamed or mismatched dll is refused), fragment.properties, legal files
#   swt-m3.jar    org.eclipse.swt.m3 fragment: the source folders of bundles/org.eclipse.swt.m3/build.properties
#                 (Render M3 + GPU M3) and the Skija jars nested under the names its Bundle-ClassPath lists, at the
#                 one version of pom.xml (skija.version; the types version read from skija-shared's own POM)
#   swt-host.jar  org.eclipse.swt host bundle (manifest, plugin.properties, about files), for OSGi launches
# Gates (positive counts): sources > 0 and javac errors = 0 per jar, 6 natives with OID_MATCH, every Bundle-ClassPath
# entry present and byte-identical to its source jar. Receipt: <out>/BUILD_RECEIPT.tsv. One JVM at a time.
# Usage: build_m3_ship.sh [worktree] [out-dir] [skija-lib-dir]
set -u
WT=${1:-C:/m3-superset/swt-l18}; OUT=${2:-H:/m3-scratch/l18/build}
LIB=${3:-C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib}
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
FRAG=$WT/binaries/org.eclipse.swt.win32.win32.x86_64; M3B=$WT/bundles/org.eclipse.swt.m3; HOST=$WT/bundles/org.eclipse.swt
STAMP=v$(date +%Y%m%d-%H%M)-m3l18
fail() { echo "FAIL $*"; exit 1; }
free_gb() { df -k "$1" | awk 'NR==2 {print int($4/1048576)}'; }
for d in /c /h; do g=$(free_gb $d); [ "${g:-0}" -ge 1 ] || fail "disk gate: $d below 1 GB free (${g:-?} GB)"; done
mem=$(powershell -NoProfile -Command "[int]((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1024)")
[ "${mem:-0}" -ge 1536 ] || { echo "MEMORY_GATE free=${mem}MB < 1536MB"; exit 2; }
rm -rf "$OUT"; mkdir -p "$OUT/dist" "$OUT/swt/classes" "$OUT/m3/classes" "$OUT/host"
R=$OUT/BUILD_RECEIPT.tsv; printf 'key\tvalue\n' > "$R"
rec() { printf '%s\t%s\n' "$1" "$2" >> "$R"; }
rec commit "$(git -C "$WT" rev-parse HEAD)"; rec dirty_files "$(git -C "$WT" status --porcelain | wc -l)"
rec javac "$("$JDK/bin/javac" -version 2>&1)"; rec stamp "$STAMP"

# source folders of a build.properties (source.. entries), resolved against its directory
src_dirs() { tr -d '\r' < "$1/build.properties" | awk '/^source\.\. *=/ {on=1} on {print} on && !/\\$/ {on=0}' \
  | sed 's/^source\.\. *= *//; s/\\$//; s/,/\n/g' | sed 's/^[ \t]*//; s/[ \t]*$//; s#/$##' | grep -v '^$' | sed "s#^#$1/#"; }
compile() { # <tag> <classes> <classpath> <dirs-file>
  : > "$OUT/$1.src"; while IFS= read -r d; do [ -d "$d" ] && find "$d" -name '*.java' -type f | sed 's#.*#"&"#' >> "$OUT/$1.src"; done < "$4"
  n=$(wc -l < "$OUT/$1.src"); [ "$n" -gt 0 ] || fail "$1: 0 sources"
  "$JDK/bin/javac" -J-Xmx1536m --release 21 -encoding UTF-8 -nowarn -proc:none -Xmaxerrs 300 ${3:+-cp "$3"} -d "$2" @"$OUT/$1.src" > "$OUT/$1.javac.log" 2>&1
  e=$(grep -cE ': error:|^error: ' "$OUT/$1.javac.log"); e=${e:-0}
  rec "$1.sources" "$n"; rec "$1.javac_errors" "$e"; echo "$1: sources=$n errors=$e"
  [ "$e" -eq 0 ] || { grep ': error:' "$OUT/$1.javac.log" | head -20; fail "$1: javac errors"; }
}
resources() { # <dirs-file> <stage>: non-Java files outside library/, *._properties renamed to *.properties (as the official jar)
  while IFS= read -r d; do [ -d "$d" ] || continue
    (cd "$d" && find . -type f ! -name '*.java' ! -name 'package.html' ! -path '*/library/*') | while IFS= read -r f; do
      t=$2/${f#./}; t=${t%._properties}; [ "$t" = "$2/${f#./}" ] || t=$t.properties
      mkdir -p "$(dirname "$t")"; cp "$d/$f" "$t"; done; done < "$1"; }
manifest() { sed "s/\.qualifier$/.$STAMP/" "$1" | tr -d '\r'; }
sha() { sha256sum "$1" | cut -d' ' -f1; }

# 1. swt.jar
src_dirs "$FRAG" > "$OUT/swt.dirs"
compile swt "$OUT/swt/classes" "" "$OUT/swt.dirs"
S=$OUT/swt/stage; mkdir -p "$S/META-INF"; cp -r "$OUT/swt/classes/." "$S/"; resources "$OUT/swt.dirs" "$S"
nat=0
for f in "$FRAG"/*.dll; do
  b=$(basename "$f"); oid=$(git -C "$WT" cat-file -p "HEAD:binaries/org.eclipse.swt.win32.win32.x86_64/$b" | sed -n 's/^oid sha256://p')
  [ -n "$oid" ] && [ "$oid" = "$(sha "$f")" ] || fail "native $b: content does not match the LFS pointer oid of HEAD (refused)"
  cp "$f" "$S/"; rec "native.$b" "$oid OID_MATCH"; nat=$((nat + 1))
done
[ "$nat" -eq 6 ] || fail "natives: $nat of 6"
cp "$FRAG/fragment.properties" "$S/"; manifest "$FRAG/META-INF/MANIFEST.MF" > "$OUT/swt.MF"
"$JDK/bin/jar" --create --file "$OUT/dist/swt.jar" --manifest "$OUT/swt.MF" -C "$S" . || fail "jar swt"

# 2. swt-m3.jar (Skija at the one version of pom.xml)
SK=$(tr -d '\r' < "$M3B/pom.xml" | sed -n 's#.*<skija.version>\([^<]*\)</skija.version>.*#\1#p' | head -1)
[ -n "$SK" ] || fail "skija.version not found in $M3B/pom.xml"
SHARED=$LIB/skija-shared-$SK.jar; WIN=$LIB/skija-windows-x64-$SK.jar
[ -f "$SHARED" ] && [ -f "$WIN" ] || fail "Skija $SK jars missing in $LIB"
TV=$(unzip -p "$SHARED" META-INF/maven/io.github.humbleui/skija-shared/pom.xml | tr -d '\r\n ' | sed -n 's#.*<artifactId>types</artifactId><version>\([^<]*\)</version>.*#\1#p')
TYPES=$LIB/types-$TV.jar; [ -n "$TV" ] && [ -f "$TYPES" ] || fail "types $TV (from skija-shared $SK POM) missing in $LIB"
rec skija.version "$SK"; rec types.version "$TV"
src_dirs "$M3B" > "$OUT/m3.dirs"
compile m3 "$OUT/m3/classes" "$OUT/swt/classes;$SHARED;$TYPES" "$OUT/m3.dirs"
M=$OUT/m3/stage; mkdir -p "$M/META-INF/m3" "$M/META-INF/licenses"; cp -r "$OUT/m3/classes/." "$M/"; resources "$OUT/m3.dirs" "$M"
cp "$WT/LICENSE" "$WT/NOTICE" "$M/META-INF/"; cp "$WT"/docs/licenses/swt-gpu-skija-*.txt "$WT/docs/licenses/swt-gpu-retained-planes-NOTICE.txt" "$M/META-INF/licenses/"
manifest "$M3B/META-INF/MANIFEST.MF" > "$OUT/m3.MF"
printf 'nested\tcoordinates\tsha256\n' > "$M/META-INF/m3/SKIJA.tsv"
cp_entries=$(sed -n '/^Bundle-ClassPath:/,/^[A-Z]/p' "$OUT/m3.MF" | sed 's/^Bundle-ClassPath://; /^[A-Z]/d' | tr -d ' \n' | tr ',' '\n' | grep -v '^\.$')
for e in $cp_entries; do
  case "$(basename "$e")" in skija-shared.jar) src=$SHARED; c=skija-shared:$SK ;; skija-windows-x64.jar) src=$WIN; c=skija-windows-x64:$SK ;;
    types.jar) src=$TYPES; c=types:$TV ;; *) fail "Bundle-ClassPath entry $e has no source jar" ;; esac
  mkdir -p "$M/$(dirname "$e")"; cp "$src" "$M/$e"; printf '%s\tio.github.humbleui:%s\t%s\n' "$e" "$c" "$(sha "$src")" >> "$M/META-INF/m3/SKIJA.tsv"
done
[ "$(echo "$cp_entries" | grep -c .)" -eq 3 ] || fail "Bundle-ClassPath: expected 3 nested jars"
"$JDK/bin/jar" --create --file "$OUT/dist/swt-m3.jar" --manifest "$OUT/m3.MF" -C "$M" . || fail "jar swt-m3"

# 3. swt-host.jar
H=$OUT/host/stage; mkdir -p "$H"; cp "$HOST/plugin.properties" "$HOST/about.html" "$H/"; cp -r "$HOST/about_files" "$H/"
mkdir -p "$H/META-INF"; cp "$HOST/META-INF/p2.inf" "$H/META-INF/"; manifest "$HOST/META-INF/MANIFEST.MF" > "$OUT/host.MF"
"$JDK/bin/jar" --create --file "$OUT/dist/swt-host.jar" --manifest "$OUT/host.MF" -C "$H" . || fail "jar host"

# 4. artifact checks (positive witnesses) + receipt, then drop the staging trees (disk)
for j in swt swt-m3 swt-host; do f=$OUT/dist/$j.jar
  rec "$j.jar.sha256" "$(sha "$f")"; rec "$j.jar.bytes" "$(stat -c %s "$f")"
  rec "$j.jar.classes" "$(unzip -Z1 "$f" | grep -c '\.class$')"; done
rec swt.jar.dlls "$(unzip -Z1 "$OUT/dist/swt.jar" | grep -c '\.dll$')"
rec swt.jar.glyphs "$(unzip -Z1 "$OUT/dist/swt.jar" | grep -c 'internal/render/glyphs/.*\.svg$')"
rec swt-m3.jar.render_m3 "$(unzip -Z1 "$OUT/dist/swt-m3.jar" | grep -c '^org/eclipse/swt/internal/render/m3/.*\.class$')"
rec swt-m3.jar.gpu_m3 "$(unzip -Z1 "$OUT/dist/swt-m3.jar" | grep -c '^org/eclipse/swt/internal/gpu/m3/.*\.class$')"
for e in $cp_entries; do
  x=$(unzip -p "$OUT/dist/swt-m3.jar" "$e" | sha256sum | cut -d' ' -f1)
  grep -q "$x" "$M/META-INF/m3/SKIJA.tsv" && rec "nested.$e" "$x BYTE_IDENTICAL" || fail "nested $e differs from its source jar"
done
rm -rf "$OUT/swt" "$OUT/m3" "$OUT/host"
echo "OK $(ls "$OUT/dist" | tr '\n' ' ')"; column -t -s$'\t' "$R" | sed -n '2,200p' | grep -v '^native\.' | head -40
