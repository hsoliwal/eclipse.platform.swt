#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# ecj_access_gate.sh: compile org.eclipse.swt.tests with ECJ under the access rules Tycho/PDE give it in the Eclipse
# SDK build, so the 'Access restriction' errors of that build are reproduced and gated locally (CPU only, BLLSE records).
#
# Access rules of the SWT win32 classes entry: Export-Package of the host MANIFEST plus the win32 fragment MANIFEST
# (Eclipse-ExtensibleAPI: true merges them, as the Tycho command line in target/compilelogs/@dot.xml shows):
#   plain export, or x-friends naming org.eclipse.swt.tests      -> accessible
#   x-internal:=true, or x-friends without org.eclipse.swt.tests  -> discouraged (warning)
#   every other package                                            -> forbidden (error)
# JUnit: the bundle requires JUnit 5 only, so the JUnit 3/4 packages of the console launcher jar are forbidden.
# Compiler options: examples/.settings_shared/org.eclipse.jdt.core.prefs + -enableJavadoc -proceedOnError, the options
# of the Tycho run. Tycho's ECJ stops at 100 problems per file; this gate reports all of them (-maxProblems).
# Sources: "JUnit Tests" + data (build.properties source..) minus */performance/* (org.eclipse.test.performance is not
# on this classpath). The m3gc tests need the M3 render tree and Skija: pass both in M3_CP to compile them; without
# it their unresolved types are counted as "unresolved", not as access errors.
#
# Environment: M3_JDK (JDK 21 home), ECJ_JAR, JUNIT_CONSOLE_JAR, SCREENSHOTS_STUB (org.eclipse.test.Screenshots stub),
#              HOST_MF (host MANIFEST to read instead of the worktree one, for before/after proofs), M3_CP.
# Usage: ecj_access_gate.sh <worktree> <swt-win32-classes> <evidence-dir> <label>
# Output: <evidence-dir>/ecj-<label>.{log,records.tsv,rules.tsv,summary.tsv}; exit 0 only with 0 forbidden accesses.
set -u
WT=$1; SWT=$2; EV=$3; LABEL=$4; JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
ECJ=${ECJ_JAR:-$(cygpath -m ~/.m2/repository/org/eclipse/jdt/ecj/3.45.0/ecj-3.45.0.jar)}
CS=${JUNIT_CONSOLE_JAR:-$(cygpath -m ~/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar)}
STUB=${SCREENSHOTS_STUB:-C:/m3-superset/test-stubs/org/eclipse/test/Screenshots.java}
JUNIT_RULES='-org/junit/*;-org/junit/rules/**;-org/junit/runner/**;-org/junit/runners/**;-org/junit/experimental/**;-org/junit/function/**;-org/junit/matchers/**;-org/junit/validator/**;-org/junit/internal/**;-junit/**;-org/hamcrest/**;+**'
T="$WT/tests/org.eclipse.swt.tests"; W=$EV/ecj-$LABEL.work
mkdir -p "$EV"; rm -rf "$W"; mkdir -p "$W/out"
# Export-Package clause -> one "package;directive;..." line per entry
exports() { tr -d '\r' < "$1" | awk '/^Export-Package:/{on=1; sub(/^Export-Package:/,""); print; next} on && /^ /{print; next} {on=0}' | tr -d '\n' |
  awk 'BEGIN{RS=","} { gsub(/^[ \t\n]+|[ \t\n]+$/,""); if ($0!="") print }' | sed -E 's/ *; */;/g'; }
{ exports "${HOST_MF:-$WT/bundles/org.eclipse.swt/META-INF/MANIFEST.MF}"; exports "$WT/binaries/org.eclipse.swt.win32.win32.x86_64/META-INF/MANIFEST.MF"; } |
  awk -F';' '{pkg=$1; acc="+"; for(i=2;i<=NF;i++){ if ($i ~ /^x-internal:=true/) acc="~"; if ($i ~ /^x-friends:=/) acc = ($i ~ /org\.eclipse\.swt\.tests/) ? "+" : "~" }
    if (!(pkg in a) || acc=="+") a[pkg]=acc } END{for (p in a) print a[p] "\t" p}' | sort -k2 > "$W/rules.tsv"
RULES=$(awk -F'\t' '{gsub(/\./,"/",$2); printf "%s%s/*;", $1, $2}' "$W/rules.tsv")-**
( cd "$T" && find "JUnit Tests" data -name '*.java' ) | sort > "$W/all-src.txt"
grep -vE '/performance/' "$W/all-src.txt" | sed "s#^#$T/#" > "$W/src.lst"; echo "$STUB" >> "$W/src.lst"
sed 's#.*#"&"#' "$W/src.lst" > "$W/src.txt"
"$JDK/bin/java" -Xmx2g -jar "$ECJ" --release 21 -encoding UTF-8 -proc:none -properties "$WT/examples/.settings_shared/org.eclipse.jdt.core.prefs" -enableJavadoc -proceedOnError \
  -warn:+discouraged -err:+forbidden -maxProblems 100000 -d "$W/out" -cp "$SWT[$RULES];$CS[$JUNIT_RULES]${M3_CP:+;$M3_CP}" @"$W/src.txt" > "$EV/ecj-$LABEL.log" 2>&1
ecj_rc=$?
# BLLSE: ECJ prints "<n>. ERROR in <file> (at line <l>)", the source line(s), the caret line, the message, then "----------"
awk '/^[0-9]+\. (ERROR|WARNING|INFO) in /{sev=$2; f=$0; sub(/^[0-9]+\. [A-Z]+ in /,"",f); l=f; sub(/ \(at line [0-9]+\)$/,"",f); sub(/.*\(at line /,"",l); sub(/\)$/,"",l); on=1; next}
  on && /^----------$/{print sev "\t" f "\t" l "\t" msg; on=0; next} on{msg=$0}' "$EV/ecj-$LABEL.log" |
  sed -E 's#\\#/#g; s#^([A-Z]+\t)[^\t]*/tests/org.eclipse.swt.tests/#\1#' > "$EV/ecj-$LABEL.records.tsv"
kinds() { awk -F'\t' -v s="$1" '$1==s' "$EV/ecj-$LABEL.records.tsv" | awk -F'\t' '{m=$4; c="other";
  if (m ~ /^Access restriction/) c = (m ~ /junit-platform-console/) ? "access_forbidden_junit4" : "access_forbidden_swt"; else if (m ~ /^Discouraged access/) c="access_discouraged";
  else if (m ~ /^Javadoc/) c="javadoc"; else if (m ~ /^The import .* is never used/) c="unused_import";
  else if (m ~ /cannot be resolved|refers to the missing type|must override or implement|is undefined for the type/) c="unresolved"; n[c]++} END{for (k in n) print k "=" n[k]}' | sort | tr '\n' ' '; }
forbidden=$(awk -F'\t' '$1=="ERROR" && $4 ~ /^Access restriction/' "$EV/ecj-$LABEL.records.tsv" | wc -l)
printf 'label\tecj_rc\terrors\twarnings\taccess_forbidden\terror_kinds\twarning_kinds\tsources\trules\tsource_commit\n%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$LABEL" "$ecj_rc" \
  "$(awk -F'\t' '$1=="ERROR"' "$EV/ecj-$LABEL.records.tsv" | wc -l)" "$(awk -F'\t' '$1=="WARNING"' "$EV/ecj-$LABEL.records.tsv" | wc -l)" "$forbidden" \
  "$(kinds ERROR)" "$(kinds WARNING)" "$(wc -l < "$W/src.lst")" "$(wc -l < "$W/rules.tsv")" "$(git -C "$WT" rev-parse HEAD)" | tee "$EV/ecj-$LABEL.summary.tsv"
cp "$W/rules.tsv" "$EV/ecj-$LABEL.rules.tsv"; rm -rf "$W"
[ "$forbidden" -eq 0 ]
