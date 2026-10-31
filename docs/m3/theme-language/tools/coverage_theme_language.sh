#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# JaCoCo report of the theme and language leaf: merges the exec files of test_theme_language.sh (M3_JACOCO=1; headless and the
# five display modes) and reports lines and branches of org.eclipse.swt.m3 (the leaf's classes), M3ThemeTable and the win32
# M3NativeTheme against the win32 classes of compile_platform.sh. JaCoCo CLI 0.8.15. Output: $OUT/coverage/{cov.csv,html}.
# Usage: coverage_theme_language.sh
WT=${WT:-C:/m3-superset/swt-theme-lang}; MAIN=${MAIN:-H:/m3-scratch/theme-lang/platform/win32}; OUT=${OUT:-H:/m3-scratch/theme-lang/tests}
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
CLI=${M3_JACOCO_CLI:-C:/Users/hsoliwal/.m2/repository/org/jacoco/org.jacoco.cli/0.8.15/org.jacoco.cli-0.8.15-nodeps.jar}
C=$OUT/coverage; rm -rf "$C"; mkdir -p "$C/classes/org/eclipse/swt/m3" "$C/classes/org/eclipse/swt/internal/laf" "$C/classes/org/eclipse/swt/widgets"
LEAF="M3Theme M3ThemeSlot M3ThemeBuiltins M3ThemeFile M3Themes M3ThemeState M3ThemeApplier M3ThemeSwitcher M3Language M3LanguageId M3Languages M3LanguageDisplays M3LanguageSwitcher M3LabelRegistry M3MessageLookup"
for c in $LEAF; do cp "$MAIN/org/eclipse/swt/m3/$c.class" "$MAIN/org/eclipse/swt/m3/$c\$"*.class "$C/classes/org/eclipse/swt/m3/" 2>/dev/null; done
cp "$MAIN/org/eclipse/swt/internal/laf/M3ThemeTable.class" "$C/classes/org/eclipse/swt/internal/laf/"
cp "$MAIN/org/eclipse/swt/widgets/M3NativeTheme.class" "$C/classes/org/eclipse/swt/widgets/"
"$JDK/bin/java" -jar "$CLI" merge "$OUT"/jacoco/*.exec --destfile "$C/merged.exec" > "$C/merge.log" 2>&1
"$JDK/bin/java" -jar "$CLI" report "$C/merged.exec" --classfiles "$C/classes" \
  --sourcefiles "$WT/bundles/org.eclipse.swt/Eclipse SWT/common" --sourcefiles "$WT/bundles/org.eclipse.swt/Eclipse SWT/win32" \
  --csv "$C/cov.csv" --html "$C/html" --name "theme-language" > "$C/report.log" 2>&1
awk -F, 'NR>1{lm+=$8; lc+=$9; bm+=$6; bc+=$7; printf "%s\tlines %d/%d\tbranches %d/%d\n", $3, $9, $8+$9, $7, $6+$7}
  END{printf "TOTAL\tlines %d/%d (%.1f%%)\tbranches %d/%d (%.1f%%)\n", lc, lm+lc, 100*lc/(lm+lc), bc, bm+bc, 100*bc/(bm+bc)}' "$C/cov.csv"
