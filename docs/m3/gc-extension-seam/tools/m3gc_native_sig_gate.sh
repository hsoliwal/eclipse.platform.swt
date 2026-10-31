#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# NATIVE_SIG_GATE v1 (census L12): the M3 trees call Skija natives directly (Canvas._nDrawRect (canvas, ...), 50 sites);
# javac checks a call against the jar it compiles with, not against the jar a bump replaces. This gate lists every
# <SkijaClass>._n<Name> the two M3 trees use, reads their JVM descriptors with javap from the pinned and the candidate
# skija-shared jar, and fails on any used native whose descriptor set differs or is missing in the candidate.
# Usage: m3gc_native_sig_gate.sh <worktree> <pinned skija-shared jar> <candidate skija-shared jar> [out.tsv]
WT=${1:?worktree}; A=${2:?pinned jar}; B=${3:?candidate jar}; OUT=${4:-/dev/stdout}
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
grep -rhoE '\b[A-Z][A-Za-z0-9]*\._n[A-Z][A-Za-z0-9]*' "$WT/bundles/org.eclipse.swt/Eclipse SWT Render M3/win32" \
  "$WT/bundles/org.eclipse.swt/Eclipse SWT GPU M3/win32" --include='*.java' | sort -u > "$T/used"
cut -d. -f1 "$T/used" | sort -u | sed 's#^#io.github.humbleui.skija.#' > "$T/classes"
sigs() { # jar -> lines "Class._nName<TAB>descriptor"
  "$JDK/bin/javap" -J-Xmx256m -p -s -cp "$1" $(cat "$T/classes") 2>/dev/null | tr -d '\r' | awk '
    /^(public |final |abstract )*(class|interface|enum) / { c = $0; sub(/.*(class|interface|enum) io\.github\.humbleui\.skija\./, "", c); sub(/[ <].*/, "", c) }
    / native / { n = $0; sub(/\(.*/, "", n); sub(/.* /, "", n); want = 1; next }
    want && /descriptor:/ { print c "." n "\t" $2; want = 0 }'
}
sigs "$A" | sort > "$T/a"; sigs "$B" | sort > "$T/b"
{ printf 'native\tdescriptor_pinned\tdescriptor_candidate\tverdict\n'
  while IFS= read -r u; do
    da=$(awk -F'\t' -v k="$u" '$1 == k {print $2}' "$T/a" | paste -sd' '); db=$(awk -F'\t' -v k="$u" '$1 == k {print $2}' "$T/b" | paste -sd' ')
    if [ -z "$da" ] && [ -z "$db" ]; then v=NOT_SKIJA; elif [ -z "$db" ]; then v=MISSING; elif [ "$da" = "$db" ]; then v=SAME; else v=CHANGED; fi
    printf '%s\t%s\t%s\t%s\n' "$u" "${da:--}" "${db:--}" "$v"
  done < "$T/used"; } > "$T/report"
cat "$T/report" > "$OUT"
same=$(grep -c $'\tSAME$' "$T/report"); bad=$(grep -cE $'\t(CHANGED|MISSING)$' "$T/report")
echo "NATIVE_SIG_GATE: used=$(wc -l < "$T/used") same=${same:-0} changed_or_missing=${bad:-0} ($(basename "$A") -> $(basename "$B"))" >&2
[ "${same:-0}" -gt 0 ] && [ "${bad:-0}" -eq 0 ]
