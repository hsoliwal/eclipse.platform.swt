#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Proof and JaCoCo coverage of the optimization pass (docs/m3/allwidgets-viewport/OPTIMIZATION_LEDGER.tsv): every line
# the pass changed since BASE (default 5627c6e00b7, the commit before leaf O1) in ExpandBar, ExpandItem, CTabFolder,
# Display and M3LookAndFeel. Records the official SWT 3.135.0 ExpandBar and CTabFolder differentials, then two JVMs into
# one exec file (the viewport mode, and -Dswt.widget.mode=original for the stock arm of every mode flag).
# Writes optimization-changed-lines.tsv next to this script. NO_LOCK=1 runs inside a desktop slot the caller holds.
# Usage: optimization_coverage.sh <worktree> <out-dir> <jar> <extra-test-class-dirs (;-separated)>
WT=$1; OUT=$2; JAR=$3; EXTRA=$4
BASE=${BASE:-5627c6e00b7}
JDK="C:/Program Files/Java/jdk-21"
M2=$(cygpath -w "$HOME/.m2/repository")
J="$M2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"
AGENT="$M2/org/jacoco/org.jacoco.agent/0.8.11/org.jacoco.agent-0.8.11-runtime.jar"
CLI="$M2/org/jacoco/org.jacoco.cli/0.8.15/org.jacoco.cli-0.8.15-nodeps.jar"
OFFICIAL="$M2/org/eclipse/platform/org.eclipse.swt.win32.win32.x86_64/3.135.0/org.eclipse.swt.win32.win32.x86_64-3.135.0.jar"
COV="$OUT/opt-coverage"; DOC="$WT/docs/m3/allwidgets-viewport/coverage"; SRC="$WT/bundles/org.eclipse.swt"
FILES=("Eclipse SWT/win32/org/eclipse/swt/widgets/ExpandBar.java" "Eclipse SWT/win32/org/eclipse/swt/widgets/ExpandItem.java"
  "Eclipse SWT Custom Widgets/common/org/eclipse/swt/custom/CTabFolder.java" "Eclipse SWT/win32/org/eclipse/swt/widgets/Display.java"
  "Eclipse SWT/common/org/eclipse/swt/internal/laf/M3LookAndFeel.java")
W=org.eclipse.swt.widgets; T=org.eclipse.swt.tests.junit
INCLUDES="$W.ExpandBar:$W.ExpandBar\$*:$W.ExpandItem:$W.ExpandItem\$*:org.eclipse.swt.custom.CTabFolder:org.eclipse.swt.custom.CTabFolder\$*:$W.Display:$W.Display\$*:org.eclipse.swt.internal.laf.M3LookAndFeel:org.eclipse.swt.internal.laf.M3LookAndFeel\$*"
DIFF="--select-class $T.Test_org_eclipse_swt_widgets_ExpandBar_ViewportDifferential --select-class $T.Test_org_eclipse_swt_custom_CTabFolder_ViewportDifferential"
STOCK="--select-class $T.Test_org_eclipse_swt_widgets_ExpandBar --select-class $T.Test_org_eclipse_swt_widgets_ExpandItem --select-class $T.Test_org_eclipse_swt_custom_CTabFolder --select-class $T.Test_org_eclipse_swt_custom_CTabItem --select-class $T.Test_org_eclipse_swt_widgets_Text"
OWN="--select-class $T.Test_org_eclipse_swt_widgets_ExpandBar_Viewport --select-class $T.Test_org_eclipse_swt_widgets_ExpandBar_Coverage --select-class $T.Test_org_eclipse_swt_widgets_ExpandBar_Hover --select-class $T.Test_org_eclipse_swt_widgets_Display_WithCrLf --select-class $T.Test_org_eclipse_swt_custom_CTabFolder_Hover"
LAF=""; for c in LookAndFeel M3UIDefaults M3UIDerivation M3LookAndFeel M3ThemeImport M3Palette M3SystemTheme; do LAF="$LAF --select-class $T.Test_org_eclipse_swt_internal_laf_$c"; done
CP="$EXTRA;$OUT/tests"
if [ -z "$REPORT_ONLY" ]; then
rm -rf "$COV" && mkdir -p "$COV/classes" "$COV/rec"
unzip -q "$(cygpath -u "$JAR")" 'org/eclipse/swt/widgets/ExpandBar*.class' 'org/eclipse/swt/widgets/ExpandItem*.class' 'org/eclipse/swt/custom/CTabFolder.class' \
  'org/eclipse/swt/custom/CTabFolder$*.class' 'org/eclipse/swt/widgets/Display.class' 'org/eclipse/swt/widgets/Display$*.class' \
  'org/eclipse/swt/internal/laf/M3LookAndFeel.class' 'org/eclipse/swt/internal/laf/M3LookAndFeel$*.class' -d "$COV/classes" > /dev/null 2>&1
A="-javaagent:$AGENT=destfile=$COV/opt.exec,includes=$INCLUDES"
R="-Xmx1g -Dswt.viewport.recording=$COV/rec -Dswt.viewport.evidence=$COV/evidence"
H="bash C:/m3-superset/tools/hidden_java.sh"
LOCK="bash C:/m3-superset/tools/ui_lock.sh swt-allwidgets"; [ -n "$NO_LOCK" ] && LOCK=
$LOCK bash -c "
$H 900 '$COV/record-official.log' $R -jar '$J' execute -cp '$CP;$OFFICIAL' $DIFF --disable-banner --details=summary
$H 1800 '$COV/viewport.log' $R '$A' -jar '$J' execute -cp '$CP;$JAR' $OWN $STOCK $DIFF $LAF --disable-banner --details=tree
$H 1200 '$COV/original.log' $R -Dswt.widget.mode=original '$A' -jar '$J' execute -cp '$CP;$JAR' $STOCK $DIFF --select-class $T.Test_org_eclipse_swt_widgets_ExpandBar_Hover --select-class $T.Test_org_eclipse_swt_custom_CTabFolder_Hover --disable-banner --details=tree
"
for m in record-official viewport original; do
  echo "== $m: $(sed 's/\x1b\[[0-9;]*m//g' "$COV/$m.log" | grep -E "tests (successful|failed|aborted)" | tr -s ' []' ' ' | tr '\n' ';')"
  sed 's/\x1b\[[0-9;]*m//g' "$COV/$m.log" | grep -E "✘" | cut -c1-300 | head -10
done
fi
"$JDK/bin/java" -Xmx256m -jar "$CLI" report "$COV/opt.exec" --classfiles "$COV/classes" --sourcefiles "$SRC/Eclipse SWT/win32" --sourcefiles "$SRC/Eclipse SWT/common" \
  --sourcefiles "$SRC/Eclipse SWT Custom Widgets/common" --xml "$COV/jacoco.xml" --html "$COV/report" --name "optimization pass" > /dev/null
for f in "${FILES[@]}"; do
  key=$(echo "$f" | sed 's#^[^/]*/[^/]*/##')
  git -C "$WT" diff -U0 "$BASE" HEAD -- "bundles/org.eclipse.swt/$f" | awk -v key="$key" '
    /^@@/ { split ($3, n, ","); start = substr (n[1], 2); count = (2 in n) ? n[2] : 1; for (i = 0; i < count; i++) print key, start + i }'
done > "$COV/changed.txt"
sed 's/></>\n</g' "$COV/jacoco.xml" | awk '
  FNR == NR { changed[$1 " " $2] = 1; next }
  /^<package / { match ($0, /name="[^"]*"/); pkg = substr ($0, RSTART + 6, RLENGTH - 7) }
  /^<sourcefile / { match ($0, /name="[^"]*"/); file = pkg "/" substr ($0, RSTART + 6, RLENGTH - 7) }
  /^<line / && file != "" {
    split ($0, a, "\""); key = file " " a[2];
    if (key in changed) printf "%s\t%d\t%d\t%d\t%d\t%d\n", file, a[2], a[4], a[6], a[8], a[10];
  }' "$COV/changed.txt" - | sort -k1,1 -k2,2n > "$COV/lines.tsv"
{ printf "file\tline\tmissed_instructions\tcovered_instructions\tmissed_branches\tcovered_branches\n"; cat "$COV/lines.tsv"; } > "$DOC/optimization-changed-lines.tsv"
awk -F'\t' '{ f = $1; sub (/.*\//, "", f); lines[f]++; if ($4 > 0) covered[f]++; else missed[f] = missed[f] " " $2; mb[f] += $5; cb[f] += $6 }
  END { for (f in lines) { printf "%-28s changed lines %3d/%-3d branches %3d/%-3d missed:%s\n", f, covered[f], lines[f], cb[f], cb[f] + mb[f], missed[f];
    tl += lines[f]; tc += covered[f]; tb += cb[f] + mb[f]; tcb += cb[f] }
    printf "%-28s changed lines %3d/%-3d (%5.1f%%)  branches %3d/%-3d (%5.1f%%)\n", "TOTAL", tc, tl, tl ? 100 * tc / tl : 100, tcb, tb, tb ? 100 * tcb / tb : 100 }' "$COV/lines.tsv"
