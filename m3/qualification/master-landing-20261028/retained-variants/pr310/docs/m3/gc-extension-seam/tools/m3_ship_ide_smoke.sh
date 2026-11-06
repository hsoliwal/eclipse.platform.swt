#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Leaf 18 IDE_SMOKE (scratch): the existing Eclipse JEE 2026-09 install, read-only. Private --launcher.ini, -configuration
# (config.ini + bundles.info naming the fork's swt-host.jar, swt.jar, swt-m3.jar and the scratch smoke bundle), -data,
# user.home, osgi.user.area, keyring and p2 data area under H:/m3-scratch/l18/ide/<mode>; -vm jdk-21 javaw on the
# command line. Nothing under the install or ~/.p2 is written (p2 reconciler not started, p2 data area private).
# Usage: run_ide.sh <m3|gpu|stock> [timeout-seconds]
# Text leaf (IDE_SMOKE v2): M3_IDE_SCRATCH (scratch root, default H:/m3-scratch/l18), M3_UI_AGENT, M3_UI_DESKTOP; the smoke jar
# is <scratch>/ide/idesmoke.jar (m3text_idesmoke_build.sh) and the install hash <scratch>/ide/install_eclipse_ini.sha;
# M3_IDE_VMARGS: extra -D arguments (space separated), e.g. -Dswt.m3.text=gdi
MODE=${1:?mode}; TIMEOUT=${2:-480}; L=${M3_IDE_SCRATCH:-H:/m3-scratch/l18}; I=C:/Users/hsoliwal/eclipse/jee-2026-09/eclipse
P=C:/Users/hsoliwal/.p2/pool/plugins; D=$L/build/dist; B=$L/ide/$MODE
g=$(df -k /h | awk 'NR==2{print int($4/1048576)}'); [ "${g:-0}" -ge 1 ] || { echo "DISK_GATE H: ${g}GB < 1"; exit 3; }
[ "$(sha256sum "$I/eclipse.ini" | cut -c1-64)" = "$(cat $L/ide/install_eclipse_ini.sha)" ] || { echo "install eclipse.ini changed?"; exit 4; }
rm -rf "$B"; mkdir -p "$B/configuration/org.eclipse.equinox.simpleconfigurator" "$B/ws" "$B/home" "$B/user" "$B/p2" "$B/swtlib" "$B/tmp" "$L/ide/out" "$L/ide/logs"
# no modal startup dialogs on the hidden desktop: Windows Defender exclusion prompt skipped (configuration + instance scope)
for d in "$B/configuration/.settings" "$B/ws/.metadata/.plugins/org.eclipse.core.runtime/.settings"; do mkdir -p "$d"
  for q in org.eclipse.ui org.eclipse.ui.workbench; do printf '%s\n' eclipse.preferences.version=1 windows.defender.startup.check.skip=true > "$d/$q.prefs"; done; done
V=$(unzip -p "$D/swt.jar" META-INF/MANIFEST.MF | tr -d '\r' | sed -n 's/^Bundle-Version: //p')
sed -e "s#^eclipse.p2.data.area=.*#eclipse.p2.data.area=file\\:/$(echo $B/p2 | sed 's#:#\\:#')/#" -e '/^eclipse.p2.profile=/d' -e '/^osgi.splashPath=/d' \
  -e '/^osgi.instance.area.default=/d' "$I/configuration/config.ini" > "$B/configuration/config.ini"
awk -F, -v base="$([ "$MODE" = baseline ] && echo 1)" -v v="$V" -v d="$D" -v s="$L/ide/idesmoke.jar" '
  base && $1=="org.eclipse.swt.win32.win32.x86_64" {print; print "org.eclipse.swt.m3.idesmoke,1.0.0.l18,file:/" s ",4,false"; next}
  base {if ($1=="org.eclipse.equinox.p2.reconciler.dropins") {OFS=","; $5="false"} print; next}
  $1=="org.eclipse.swt" {print "org.eclipse.swt," v ",file:/" d "/swt-host.jar,4,false"; next}
  $1=="org.eclipse.swt.win32.win32.x86_64" {print "org.eclipse.swt.win32.win32.x86_64," v ",file:/" d "/swt.jar,4,false";
     print "org.eclipse.swt.m3," v ",file:/" d "/swt-m3.jar,4,false"; print "org.eclipse.swt.m3.idesmoke,1.0.0.l18,file:/" s ",4,false"; next}
  $1=="org.eclipse.equinox.p2.reconciler.dropins" {OFS=","; $5="false"; print; next}
  {print}' "$I/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info" > "$B/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"
grep -c "m3-scratch" "$B/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info" | sed 's/^/bundles.info lines naming scratch jars: /'
W() { cygpath -w "$1"; }
case "$MODE" in stock|baseline) PROP="" ;; *) PROP="-Dswt.widget.mode=$MODE" ;; esac
{ printf '%s\n' -startup "$I/plugins/org.eclipse.equinox.launcher_1.8.0.v20260804-1928.jar" --launcher.library \
    "$P/org.eclipse.equinox.launcher.win32.win32.x86_64_1.3.100.v20260804-1928" -product org.eclipse.epp.package.jee.product \
    -nosplash --launcher.suppressErrors -vmargs -Dosgi.requiredJavaVersion=21 -Xms256m -Xmx1536m --add-modules=ALL-SYSTEM \
    "-Duser.home=$(W $B/home)" "-Dosgi.user.area=$(W $B/user)" "-Djava.io.tmpdir=$(W $B/tmp)" "-Dswt.library.path=$(W $B/swtlib)" \
    -Doomph.setup.skip=true -Doomph.setup.questionnaire.skip=true -Dorg.eclipse.swt.graphics.Resource.reportNonDisposed=true \
    "-Didesmoke.out=$(W $L/ide/out)" "-Xlog:class+load:file=\"$(W $L/ide/logs/classload_$MODE.log)\"" "-XX:ErrorFile=$(W $L/ide/logs/hs_err_$MODE.log)"
  [ -n "$PROP" ] && echo "$PROP"; for a in $M3_IDE_VMARGS; do echo "$a"; done; } > "$L/ide/eclipse_$MODE.ini"
LOG=$L/ide/logs/launcher_$MODE.log; : > "$LOG"
( while :; do sleep 300; echo "HEARTBEAT $(date -Iseconds) ide mode=$MODE"; done ) & HB=$!
echo "START $(date -Iseconds) ide mode=$MODE version=$V"
CMD="\"$(W $I/eclipse.exe)\" --launcher.ini \"$(W $L/ide/eclipse_$MODE.ini)\" -vm \"C:\Program Files\Java\jdk-21\bin\javaw.exe\" -configuration \"$(W $B/configuration)\" -data \"$(W $B/ws)\" -eclipse.keyring \"$(W $B/user/keyring)\" -clean"
echo "CMD $CMD"
bash C:/m3-superset/tools/ui_lock.sh ${M3_UI_AGENT:-claude-l18} powershell -NoProfile -ExecutionPolicy Bypass -File "$(W C:/m3-superset/tools/run_hidden_desktop.ps1)" -TimeoutSec "$TIMEOUT" -Log "$(W $LOG)" -CommandLine "$CMD" -Desktop ${M3_UI_DESKTOP:-m3-ui-tests-l18}
rc=$?; kill $HB 2>/dev/null
echo "END $(date -Iseconds) ide mode=$MODE exit=$rc"
cp "$B/ws/.metadata/.log" "$L/ide/logs/workspace_$MODE.log" 2>/dev/null
echo "workspace .log: $(grep -c '^!ENTRY' "$L/ide/logs/workspace_$MODE.log" 2>/dev/null) entries, $(grep -c '^!ENTRY.* 4 ' "$L/ide/logs/workspace_$MODE.log" 2>/dev/null) errors"
echo "tsv: $(cat $L/ide/out/ide_$MODE.tsv 2>/dev/null | wc -l) lines"
[ "$(sha256sum "$I/eclipse.ini" | cut -c1-64)" = "$(cat $L/ide/install_eclipse_ini.sha)" ] && echo "install eclipse.ini unchanged"
