#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# NATIVE_SIG_GATE v2 (census L12; v2 2026-10-31 for jdk.m3.graphics): the M3 trees call the natives directly
# (Canvas._nDrawRect (canvas, ...), 50 sites); javac checks a call against the jar it compiles with, not against the jar
# a bump replaces. This gate lists every <Class>._n<Name> the two M3 trees use, reads their JVM descriptors with javap
# from the pinned and the candidate jar, and fails on any used native whose descriptor set differs or is missing in the
# candidate.
# v2: the call sites may name Skija types (Canvas) or their M3jdk owners (M3Canvas, after CONSUMER_RENAME). Each jar is
# read in its own namespace: a jar holding io/github/humbleui/skija/ classes is read with the Skija names (an M3 prefix
# stripped), a jar holding jdk/m3/graphics/ classes with the M3 names; descriptors are compared after mapping Skija /
# HumbleUI type names onto jdk.m3.graphics (M3 prefix), so a pinned Skija jar and a candidate jdk.m3.graphics jar compare.
# Usage: m3gc_native_sig_gate.sh <worktree> <pinned jar> <candidate jar> [out.tsv]
WT=${1:?worktree}; A=${2:?pinned jar}; B=${3:?candidate jar}; OUT=${4:-/dev/stdout}
JDK=${M3_JDK:-C:/Program Files/Java/jdk-21}
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
grep -rhoE '\b[A-Z][A-Za-z0-9]*\._n[A-Z][A-Za-z0-9]*' "$WT/bundles/org.eclipse.swt/Eclipse SWT Render M3/win32" \
  "$WT/bundles/org.eclipse.swt/Eclipse SWT GPU M3/win32" --include='*.java' | sed 's/^M3//' | sort -u > "$T/used"
ns() { # jar -> skija | m3
  if unzip -l "$1" 2>/dev/null | grep -q ' jdk/m3/graphics/'; then echo m3; else echo skija; fi
}
sigs() { # jar -> lines "Class._nName<TAB>descriptor" with Class and descriptor in the M3-neutral form (Skija names)
  local space; space=$(ns "$1")
  if [ "$space" = m3 ]; then
    cut -d. -f1 "$T/used" | sort -u | sed 's#^#jdk.m3.graphics.M3#' > "$T/classes"
  else
    cut -d. -f1 "$T/used" | sort -u | sed 's#^#io.github.humbleui.skija.#' > "$T/classes"
  fi
  "$JDK/bin/javap" -J-Xmx256m -p -s -cp "$1" $(cat "$T/classes") 2>/dev/null | tr -d '\r' | awk '
    /^(public |final |abstract )*(class|interface|enum) / {
      c = $0; sub(/.*(class|interface|enum) (io\.github\.humbleui\.skija|jdk\.m3\.graphics)\./, "", c); sub(/[ <].*/, "", c); sub(/^M3/, "", c) }
    / native / { n = $0; sub(/\(.*/, "", n); sub(/.* /, "", n); want = 1; next }
    want && /descriptor:/ { print c "." n "\t" $2; want = 0 }' \
  | sed -E 's#Ljdk/m3/graphics/impl/M3#Lio/github/humbleui/skija/impl/#g; s#Ljdk/m3/graphics/([a-z]+/)?M3#Lio/github/humbleui/skija/\1#g'
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
echo "NATIVE_SIG_GATE: used=$(wc -l < "$T/used") same=${same:-0} changed_or_missing=${bad:-0} ($(basename "$A") [$(ns "$A")] -> $(basename "$B") [$(ns "$B")])" >&2
[ "${same:-0}" -gt 0 ] && [ "${bad:-0}" -eq 0 ]
