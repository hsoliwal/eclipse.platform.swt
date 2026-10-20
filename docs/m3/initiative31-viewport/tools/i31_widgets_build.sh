#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
#
# Build proof of the Nebula widgets on Initiative 31 (SWT on Swing): every widget bundle and its examples/snippets
# are compiled against the Initiative 31 classes and, as the control, against the official SWT jar. An error of the
# Initiative 31 build that the control does not have (same file, line and message) is an Initiative 31 API gap;
# errors on both sides are missing dependencies of the widget, not gaps.
# Run under build_lock.sh. Usage: i31_widgets_build.sh <nebula-dir> <i31-classes> <out-dir> [widget...]
# <nebula-dir> is the repository root (widgets/ and examples/ below it). Output: <out-dir>/build.tsv and, per widget,
# <widget>-i31.log, <widget>-official.log, <widget>-i31-only.txt (the gap errors with their symbol line).
N=$1; I31=$2; OUT=$3; shift 3
M2=$(cygpath -m "$HOME/.m2/repository")
P=$M2/org/eclipse/platform
OFFICIAL=$P/org.eclipse.swt.win32.win32.x86_64/3.135.0/org.eclipse.swt.win32.win32.x86_64-3.135.0.jar
jar () { echo "$M2/$1"; }
DEPS=$(printf '%s;' \
	$(jar org/eclipse/platform/org.eclipse.jface/3.40.0/org.eclipse.jface-3.40.0.jar) \
	$(jar org/eclipse/platform/org.eclipse.core.commands/3.13.0/org.eclipse.core.commands-3.13.0.jar) \
	$(jar org/eclipse/platform/org.eclipse.equinox.common/3.21.0/org.eclipse.equinox.common-3.21.0.jar) \
	$(jar org/eclipse/platform/org.eclipse.core.runtime/3.35.0/org.eclipse.core.runtime-3.35.0.jar) \
	$(jar org/eclipse/platform/org.eclipse.core.jobs/3.15.900/org.eclipse.core.jobs-3.15.900.jar) \
	$(jar org/eclipse/platform/org.eclipse.osgi/3.19.0/org.eclipse.osgi-3.19.0.jar) \
	$(jar org/eclipse/platform/org.eclipse.equinox.registry/3.12.600/org.eclipse.equinox.registry-3.12.600.jar) \
	$(jar org/eclipse/platform/org.eclipse.equinox.preferences/3.12.100/org.eclipse.equinox.preferences-3.12.100.jar) \
	$(jar org/eclipse/platform/org.eclipse.jface.text/3.31.100/org.eclipse.jface.text-3.31.100.jar) \
	$(jar org/eclipse/platform/org.eclipse.e4.ui.css.swt/0.17.100/org.eclipse.e4.ui.css.swt-0.17.100.jar) \
	$(jar org/eclipse/platform/org.eclipse.e4.ui.css.core/0.14.800/org.eclipse.e4.ui.css.core-0.14.800.jar) \
	$(jar org/eclipse/platform/org.eclipse.jface.databinding/1.16.0/org.eclipse.jface.databinding-1.16.0.jar) \
	$(jar org/eclipse/platform/org.eclipse.core.databinding/1.13.700/org.eclipse.core.databinding-1.13.700.jar) \
	$(jar org/eclipse/platform/org.eclipse.core.databinding.observable/1.13.500/org.eclipse.core.databinding.observable-1.13.500.jar) \
	$(jar org/eclipse/platform/org.eclipse.core.databinding.property/1.10.500/org.eclipse.core.databinding.property-1.10.500.jar) \
	$(jar org/eclipse/platform/org.eclipse.ui/3.209.100/org.eclipse.ui-3.209.100.jar) \
	$(jar org/eclipse/platform/org.eclipse.ui.workbench/3.140.0/org.eclipse.ui.workbench-3.140.0.jar) \
	$(jar org/eclipse/platform/org.eclipse.ui.forms/3.14.200/org.eclipse.ui.forms-3.14.200.jar) \
	$(jar org/eclipse/platform/org.eclipse.ui.ide/3.23.200/org.eclipse.ui.ide-3.23.200.jar) \
	$(jar org/eclipse/platform/org.eclipse.e4.core.di/1.9.900/org.eclipse.e4.core.di-1.9.900.jar) \
	$(jar org/eclipse/platform/org.eclipse.e4.core.di.annotations/1.8.500/org.eclipse.e4.core.di.annotations-1.8.500.jar) \
	$(jar org/eclipse/platform/org.eclipse.e4.core.contexts/1.13.300/org.eclipse.e4.core.contexts-1.13.300.jar) \
	$(jar org/eclipse/platform/org.eclipse.e4.core.services/2.5.400/org.eclipse.e4.core.services-2.5.400.jar) \
	$(jar org/eclipse/platform/org.eclipse.e4.ui.di/1.5.700/org.eclipse.e4.ui.di-1.5.700.jar) \
	$(jar org/eclipse/platform/org.eclipse.e4.ui.services/1.6.700/org.eclipse.e4.ui.services-1.6.700.jar) \
	$(jar org/eclipse/platform/org.eclipse.e4.ui.model.workbench/2.4.900/org.eclipse.e4.ui.model.workbench-2.4.900.jar) \
	$(jar org/eclipse/platform/org.eclipse.e4.ui.workbench/1.18.300/org.eclipse.e4.ui.workbench-1.18.300.jar) \
	$(jar org/eclipse/emf/org.eclipse.emf.common/2.46.0/org.eclipse.emf.common-2.46.0.jar) \
	$(jar org/eclipse/emf/org.eclipse.emf.ecore/2.43.0/org.eclipse.emf.ecore-2.43.0.jar) \
	$(jar jakarta/annotation/jakarta.annotation-api/2.1.1/jakarta.annotation-api-2.1.1.jar) \
	$(jar javax/annotation/javax.annotation-api/1.3.2/javax.annotation-api-1.3.2.jar))
JDK="C:/Program Files/Java/jdk-21/bin"
mkdir -p "$OUT"
# the classpaths, for i31_widgets_run.sh
echo "$DEPS" > "$OUT/deps.cp"; echo "$OFFICIAL" > "$OUT/official.cp"
# every bundle source folder of every widget and of the shared examples, so a widget finds the Nebula bundles it
# depends on; Draw2d (GEF classic 3.24, eclipse-gef/gef-classic 4052c50, EPL-2.0, drawn on SWT GC) is compiled from
# source on the same side
SP=$( { find "$N/widgets" "$N/examples" -maxdepth 4 -type d -name src -path '*/org.eclipse.nebula*' | grep -v -i 'test'; echo "${DRAW2D:-S:/m3-scratch/initiative31/gef/org.eclipse.draw2d/src}"; } | sed 's#$#;#' | tr -d '\n')
# an error key: file, line and message, without the checkout prefix
keys () { grep ': error:' "$1" | sed -E 's#^.*[\\/]widgets[\\/]##; s#\\#/#g' | sort -u; }
echo -e "widget\tsources\ti31_errors\tofficial_errors\ti31_only_errors\ti31_only_symbols" > "$OUT/build.tsv"
for w in ${@:-$(ls "$N/widgets")}; do
	SRC=$(find "$N/widgets/$w" -maxdepth 4 -type d -name src -path '*/org.eclipse.nebula*' | grep -v -i 'test')
	[ -n "$SRC" ] || continue
	find $SRC -name '*.java' > "$OUT/$w.files"
	n=$(wc -l < "$OUT/$w.files")
	for side in i31 official; do
		CP="$DEPS$I31"; [ $side = official ] && CP="$DEPS$OFFICIAL"
		rm -rf "$OUT/$w-$side"; mkdir -p "$OUT/$w-$side"
		"$JDK/javac" -J-Xmx1g --release 21 -encoding UTF-8 -nowarn -proc:none -Xmaxerrs 2000 -cp "$CP" -sourcepath "$SP" \
			-d "$OUT/$w-$side" @"$OUT/$w.files" > "$OUT/$w-$side.log" 2>&1
	done
	ei=$(grep -c ': error:' "$OUT/$w-i31.log"); eo=$(grep -c ': error:' "$OUT/$w-official.log")
	comm -23 <(keys "$OUT/$w-i31.log") <(keys "$OUT/$w-official.log") > "$OUT/$w-i31-only.keys"
	gap=$(wc -l < "$OUT/$w-i31-only.keys")
	# each gap error with the symbol javac prints below it
	sed -E 's#^.*[\\/]widgets[\\/]##; s#\\#/#g' "$OUT/$w-i31.log" | awk -v keys="$OUT/$w-i31-only.keys" '
		BEGIN { while ((getline k < keys) > 0) gap [k] = 1 }
		/: error:/ { if (want) print key; key = $0; want = (key in gap); next }
		want && /symbol:/ { s = $0; sub (/^ */, "", s); print key "\t" s; want = 0 }
		END { if (want) print key }' > "$OUT/$w-i31-only.txt"
	syms=$(cut -s -f2 "$OUT/$w-i31-only.txt" | sort | uniq -c | sort -rn | head -8 | awk '{$1=$1; print}' | tr '\n' ';')
	echo -e "$w\t$n\t$ei\t$eo\t$gap\t$syms" >> "$OUT/build.tsv"
done
column -t -s $'\t' "$OUT/build.tsv" | cut -c1-220
