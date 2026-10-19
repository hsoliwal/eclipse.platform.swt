#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# Coverage of the hook lines this track inserted into Initiative 31 files: the lines the branch adds to each changed
# upstream file (git diff against the base), looked up in a JaCoCo XML report of those classes.
# Usage: hook_coverage.sh <classes-dir> <out-dir> <base-commit> <exec>...
CLASSES=$1; OUT=$2; BASE=$3; shift 3
M2=$(cygpath -m "$HOME/.m2/repository")
CLI=$M2/org/jacoco/org.jacoco.cli/0.8.15/org.jacoco.cli-0.8.15-nodeps.jar
WT=$(cd "$(dirname "$0")/../../../.." && pwd -W)
S="$WT/bundles/org.eclipse.swt"
rm -rf "$OUT"; mkdir -p "$OUT/classes/org/eclipse/swt/widgets" "$OUT/classes/org/eclipse/swt/internal/swing"
for c in Tree TreeItem Table List; do cp "$CLASSES/org/eclipse/swt/widgets/$c.class" "$CLASSES/org/eclipse/swt/widgets/$c\$"*.class "$OUT/classes/org/eclipse/swt/widgets/" 2>/dev/null; done
cp "$CLASSES/org/eclipse/swt/internal/swing/JTreeTable"*.class "$CLASSES/org/eclipse/swt/internal/swing/DefaultMutableTreeTableNode.class" "$OUT/classes/org/eclipse/swt/internal/swing/"
"C:/Program Files/Java/jdk-21/bin/java" -jar "$CLI" report "$@" --classfiles "$OUT/classes" \
	--sourcefiles "$S/Eclipse SWT/swing" --sourcefiles "$S/Eclipse SWT PI/swing" --xml "$OUT/hooks.xml" --quiet
tr '<' '\n' < "$OUT/hooks.xml" | awk '/^sourcefile name=/ {split($0, a, "\""); f=a[2]} /^line nr=/ {split($0, a, "\""); print f "\t" a[2] "\t" a[4] "\t" a[6] "\t" a[8] "\t" a[10]}' > "$OUT/lines.tsv"
total=0; covered=0
for f in "Eclipse SWT/swing/org/eclipse/swt/widgets/Tree.java" "Eclipse SWT/swing/org/eclipse/swt/widgets/TreeItem.java" \
	"Eclipse SWT/swing/org/eclipse/swt/widgets/Table.java" "Eclipse SWT/swing/org/eclipse/swt/widgets/List.java" \
	"Eclipse SWT PI/swing/org/eclipse/swt/internal/swing/JTreeTable.java" "Eclipse SWT PI/swing/org/eclipse/swt/internal/swing/DefaultMutableTreeTableNode.java"; do
	name=$(basename "$f")
	for n in $(git -C "$WT" diff -U0 "$BASE" -- "bundles/org.eclipse.swt/$f" | awk '/^@@/ {split($3, a, ","); s=substr(a[1], 2); c=(a[2]==""?1:a[2]); for (i=0;i<c;i++) print s+i}'); do
		row=$(awk -F'\t' -v f="$name" -v n="$n" '$1==f && $2==n' "$OUT/lines.tsv")
		[ -n "$row" ] || continue
		total=$((total+1))
		mi=$(echo "$row" | cut -f3); mb=$(echo "$row" | cut -f5)
		if [ "$mi" = 0 ] && [ "$mb" = 0 ]; then covered=$((covered+1)); else echo "PARTIAL $row"; fi
	done
done
echo "hook lines with code: $total, fully covered: $covered"
