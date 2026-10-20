#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# Build proof of the Nebula widgets on Initiative 31 (SWT on Swing): every widget bundle and its examples/snippets
# are compiled against the Initiative 31 classes and, as the control, against the official SWT jar. A widget whose
# control compiles but whose Initiative 31 build does not exposes an Initiative 31 API gap.
# Run under build_lock.sh. Usage: i31_widgets_build.sh <nebula-widgets-dir> <i31-classes> <out-dir>
N=$1; I31=$2; OUT=$3
M2=$(cygpath -m "$HOME/.m2/repository")
P=$M2/org/eclipse/platform
OFFICIAL=$P/org.eclipse.swt.win32.win32.x86_64/3.135.0/org.eclipse.swt.win32.win32.x86_64-3.135.0.jar
DEPS="$P/org.eclipse.jface/3.40.0/org.eclipse.jface-3.40.0.jar;$P/org.eclipse.core.commands/3.13.0/org.eclipse.core.commands-3.13.0.jar;$P/org.eclipse.equinox.common/3.21.0/org.eclipse.equinox.common-3.21.0.jar;$P/org.eclipse.core.runtime/3.35.0/org.eclipse.core.runtime-3.35.0.jar;$P/org.eclipse.osgi/3.19.0/org.eclipse.osgi-3.19.0.jar;$P/org.eclipse.jface.text/3.31.100/org.eclipse.jface.text-3.31.100.jar;$P/org.eclipse.e4.ui.css.swt/0.17.100/org.eclipse.e4.ui.css.swt-0.17.100.jar;$P/org.eclipse.e4.ui.css.core/0.14.800/org.eclipse.e4.ui.css.core-0.14.800.jar;$P/org.eclipse.jface.databinding/1.16.0/org.eclipse.jface.databinding-1.16.0.jar;$P/org.eclipse.core.databinding/1.13.700/org.eclipse.core.databinding-1.13.700.jar;$P/org.eclipse.core.databinding.observable/1.13.500/org.eclipse.core.databinding.observable-1.13.500.jar;$P/org.eclipse.core.databinding.property/1.10.500/org.eclipse.core.databinding.property-1.10.500.jar"
JDK="C:/Program Files/Java/jdk-21/bin"
mkdir -p "$OUT"
# every bundle source folder of every widget, so a widget finds the Nebula bundles it depends on
SP=$(find "$N" -maxdepth 3 -type d -name src -path '*/org.eclipse.nebula*' | grep -v -i 'test' | sed 's#$#;#' | tr -d '\n')
echo -e "widget\tsources\ti31_errors\tofficial_errors\ti31_only_symbols" > "$OUT/build.tsv"
for w in $(ls "$N"); do
	SRC=$(find "$N/$w" -maxdepth 3 -type d -name src -path '*/org.eclipse.nebula*' | grep -v -i 'test')
	[ -n "$SRC" ] || continue
	find $SRC -name '*.java' > "$OUT/$w.files"
	n=$(wc -l < "$OUT/$w.files")
	for side in i31 official; do
		CP="$DEPS;$I31"; [ $side = official ] && CP="$DEPS;$OFFICIAL"
		rm -rf "$OUT/$w-$side"; mkdir -p "$OUT/$w-$side"
		"$JDK/javac" -J-Xmx1g --release 21 -encoding UTF-8 -nowarn -proc:none -Xmaxerrs 500 -cp "$CP" -sourcepath "$SP" \
			-d "$OUT/$w-$side" @"$OUT/$w.files" > "$OUT/$w-$side.log" 2>&1
	done
	ei=$(grep -c ': error:' "$OUT/$w-i31.log"); eo=$(grep -c ': error:' "$OUT/$w-official.log")
	syms=$(grep -A2 ': error:' "$OUT/$w-i31.log" | grep -o 'symbol: *[a-z]* [A-Za-z0-9_()<>,. ]*\|location: [a-z]* [A-Za-z0-9_.]*' | sort | uniq -c | sort -rn | head -6 | awk '{$1=$1; print}' | tr '\n' ';')
	echo -e "$w\t$n\t$ei\t$eo\t$syms" >> "$OUT/build.tsv"
done
column -t -s $'\t' "$OUT/build.tsv" | cut -c1-200
