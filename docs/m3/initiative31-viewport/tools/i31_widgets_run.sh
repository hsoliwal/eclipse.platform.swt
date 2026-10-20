#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# Runtime proof of the Nebula widgets on Initiative 31 (SWT on Swing), after i31_widgets_build.sh: every snippet or
# example main class (at most <max> per widget) and every Nebula example tab runs in its own JVM on the hidden desktop
# through m3.i31.harness.WidgetSmoke (screenshot, colours, exceptions). A target that is not AVAILABLE on
# Initiative 31 runs again on the official SWT as the control, so a failure of the snippet itself is told apart.
# Run under ui_lock.sh. Usage: i31_widgets_run.sh <nebula-dir> <i31-classes> <build-dir> <harness-i31> <out-dir> [max]
N=$1; I31=$2; BD=$3; H=$4; OUT=$5; MAX=${6:-3}
HJ=C:/m3-superset/tools/hidden_java.sh
JDK="C:/Program Files/Java/jdk-21/bin"
DEPS=$(cat "$BD/deps.cp"); OFFICIAL=$(cat "$BD/official.cp")
mkdir -p "$OUT/smoke-off" "$OUT/logs"
# the harness compiled against the official SWT, for the control runs
"$JDK/javac" --release 21 -encoding UTF-8 -nowarn -cp "$OFFICIAL" -d "$OUT/smoke-off" "$(dirname "$0")/../harness/m3/i31/harness/WidgetSmoke.java"
# targets: widget-key, class (tab:class for example tabs)
{
	grep -rl "public static void main" --include=*.java "$N/widgets" | grep -iv test | sed "s#^$N/widgets/##" | sort \
		| awk -F/ -v max="$MAX" '{ w = $1; if (w == "opal") w = $1 "/" $2; if (++n [w] > max) next
			c = $0; sub (/^.*\/src\//, "", c); sub (/\.java$/, "", c); gsub (/\//, ".", c); print w "\t" c }'
	grep -rl "extends AbstractExampleTab" --include=*.java "$N/widgets" | sed "s#^$N/widgets/##" | sort \
		| awk -F/ '{ w = $1; c = $0; sub (/^.*\/src\//, "", c); sub (/\.java$/, "", c); gsub (/\//, ".", c); print w "\ttab:" c }'
} > "$OUT/targets.tsv"
echo "targets=$(wc -l < "$OUT/targets.tsv")"
while IFS=$'\t' read -r w c; do
	top=${w%%/*}
	# resources (images, icons, properties) of the widget's bundles and of the shared examples
	RES=$(find "$N/widgets/$top" "$N/examples" -maxdepth 3 -type d \( -name src -o -name 'org.eclipse.nebula*' \) | sed 's#$#;#' | tr -d '\n')
	tag=$(echo "$w-${c##*.}" | tr '/:' '__')
	bash $HJ 90 "$OUT/logs/$tag-i31.log" -Xmx1g -cp "$I31;$DEPS;$H;$BD/$top-i31;$RES" m3.i31.harness.WidgetSmoke "$OUT/run.tsv" "$OUT/shots-i31" "$w" "$c" 5
	grep -q "^$w	$c	AVAILABLE" "$OUT/run.tsv" 2>/dev/null && continue
	grep -q "^$w	$c	" "$OUT/run.tsv" 2>/dev/null || echo -e "$w\t$c\tKILLED\t0\t0\t90\thidden desktop time limit" >> "$OUT/run.tsv"
	bash $HJ 90 "$OUT/logs/$tag-official.log" -Xmx1g -cp "$OFFICIAL;$DEPS;$OUT/smoke-off;$BD/$top-official;$RES" m3.i31.harness.WidgetSmoke "$OUT/run-official.tsv" "$OUT/shots-official" "$w" "$c" 5
	grep -q "^$w	$c	" "$OUT/run-official.tsv" 2>/dev/null || echo -e "$w\t$c\tKILLED\t0\t0\t90\thidden desktop time limit" >> "$OUT/run-official.tsv"
done < "$OUT/targets.tsv"
cut -f3 "$OUT/run.tsv" | sort | uniq -c
