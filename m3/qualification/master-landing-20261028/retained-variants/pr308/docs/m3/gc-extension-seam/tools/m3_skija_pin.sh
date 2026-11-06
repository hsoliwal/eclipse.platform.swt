#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# SKIJA_PIN v1 (census L12, LEAF12_ONE_SKIJA.md): the one Skija version of every M3 build and gate is <skija.version> in
# bundles/org.eclipse.swt.m3/pom.xml (leaf 18, SKIJA_ONE_PROPERTY); the types version follows from skija-shared's own POM
# inside the jar (0.116.3 -> 0.1.1, 0.143.18 -> 0.2.0), read the way build_m3_ship.sh reads it. Moving every gate to
# another Skija is that one pom line plus the three jars in the lib dir; no script names a version.
# Sourced by the gate scripts once WT and LIB are set:
#   . "$(dirname "$0")/m3_skija_pin.sh"; m3_skija_pin "$WT" "$LIB" || exit 2   -> SKIJA_VERSION TYPES_VERSION SKIJA_CP
# M3_SKIJA_VERSION overrides the pom (a candidate set in M3_SKIJA_LIB); a worktree without the fragment pom keeps 0.116.3.
# Run directly: bash m3_skija_pin.sh [worktree] [lib-dir]   prints the pin and exits 1 when a jar is missing.
m3_skija_pin() {
	local pom="$1/bundles/org.eclipse.swt.m3/pom.xml" lib=$2 v t
	v=${M3_SKIJA_VERSION:-$([ -f "$pom" ] && tr -d '\r' < "$pom" | sed -n 's#.*<skija.version>\([^<]*\)</skija.version>.*#\1#p' | head -1)}
	v=${v:-0.116.3}
	t=$(unzip -p "$lib/skija-shared-$v.jar" META-INF/maven/io.github.humbleui/skija-shared/pom.xml 2>/dev/null | tr -d '\r\n ' \
		| sed -n 's#.*<artifactId>types</artifactId><version>\([^<]*\)</version>.*#\1#p')
	SKIJA_VERSION=$v; TYPES_VERSION=${t:-?}
	SKIJA_CP="$lib/skija-shared-$v.jar;$lib/skija-windows-x64-$v.jar;$lib/types-$TYPES_VERSION.jar"
	echo "skija: $SKIJA_VERSION types: $TYPES_VERSION lib: $lib"
	[ -n "$t" ] && [ -f "$lib/skija-windows-x64-$v.jar" ] && [ -f "$lib/types-$t.jar" ] && return 0
	echo "SKIJA_PIN: skija-shared-$v.jar, skija-windows-x64-$v.jar or types-$TYPES_VERSION.jar missing in $lib"; return 1
}
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
	m3_skija_pin "${1:-C:/m3-superset/swt-gcseam}" "${2:-${M3_SKIJA_LIB:-C:/m3-superset/i31/binaries/org.eclipse.swt.win32.win32.x86_64/lib}}"
fi
