#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Text leaf, IDE_SMOKE v2: builds the scratch smoke bundle (tools/m3_ship_idesmoke) into <scratch>/ide/idesmoke.jar against
# the p2 pool's workbench bundles and the built swt.jar; copies the install's eclipse.ini hash the runner checks.
# Usage: m3text_idesmoke_build.sh <worktree> <scratch>      (scratch holds build/dist/swt.jar from build_m3_ship.sh)
WT=${1:?worktree}; L=${2:?scratch}; JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
P=C:/Users/hsoliwal/.p2/pool/plugins; I=C:/Users/hsoliwal/eclipse/jee-2026-09/eclipse
S=$WT/docs/m3/gc-extension-seam/tools/m3_ship_idesmoke; O=$L/ide/smoke-build
rm -rf "$O"; mkdir -p "$O/classes" "$L/ide"
CP="$L/build/dist/swt.jar"
for b in org.eclipse.ui.workbench org.eclipse.ui.ide org.eclipse.core.resources org.eclipse.core.runtime org.eclipse.equinox.common \
  org.eclipse.osgi org.eclipse.jface org.eclipse.core.jobs org.eclipse.equinox.registry; do
  j=$(ls "$P/${b}_"*.jar 2>/dev/null | tail -1); [ -n "$j" ] && CP="$CP;$j" || echo "missing bundle $b"
done
"$JDK/bin/javac" --release 21 -encoding UTF-8 -nowarn -proc:none -cp "$CP" -d "$O/classes" "$S/src/org/eclipse/swt/m3/idesmoke/Smoke.java" > "$O/javac.log" 2>&1
n=$(grep -c ': error:' "$O/javac.log"); n=${n:-0}; c=$(find "$O/classes" -name '*.class' | wc -l)
echo "idesmoke: classes=$c errors=$n"; head -5 "$O/javac.log"
[ "$n" -eq 0 ] && [ "$c" -gt 0 ] || exit 1
cp "$S/plugin.xml" "$O/classes/"
"$JDK/bin/jar" --create --file "$L/ide/idesmoke.jar" --manifest "$S/META-INF/MANIFEST.MF" -C "$O/classes" . || exit 1
sha256sum "$I/eclipse.ini" | cut -c1-64 > "$L/ide/install_eclipse_ini.sha"
rm -rf "$O"; echo "idesmoke.jar $(sha256sum "$L/ide/idesmoke.jar" | cut -c1-16)"
