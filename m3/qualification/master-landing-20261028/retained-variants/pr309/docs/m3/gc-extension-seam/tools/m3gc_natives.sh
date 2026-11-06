#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# NATIVE_STAGE_BY_OID v1 (census L8; real-app fidelity leaf, LF-01): stages the win32 natives a JVM of this worktree loads
# (swt-osversion/swt-win32/swt-gdip/swt-awt/swt-wgl of the Library.java revision, WebView2Loader) from the Git LFS objects
# that the pointers at the worktree's HEAD name, and writes a receipt {name, oid, size, sha256, verdict}. The headless m3gc
# tests and the real-app smoke initialize Device -> Display -> OS -> Library, so a mislabeled dll (leaf 3 staged r15/r10
# copies renamed to r19) silently tests another native. No rename fallback: a missing LFS object is refused, never
# substituted. Never writes into ~/.swt. Pass Windows paths (C:/..., not /c/...).
# Usage: m3gc_natives.sh [worktree] [staging-dir]            stage + receipt (exit 1 unless every oid matched)
#        m3gc_natives.sh --verify [worktree] [staging-dir]   receipt only: exit 1 unless every staged dll's sha256 = its oid
VERIFY=0; [ "$1" = --verify ] && { VERIFY=1; shift; }
WT=${1:-C:/m3-superset/swt-gcseam}; OUT=${2:-H:/m3-scratch/gc-wiring/natives}
BP=binaries/org.eclipse.swt.win32.win32.x86_64
LFS="$(git -C "$WT" rev-parse --path-format=absolute --git-common-dir)/lfs/objects"
LIBJ="$WT/bundles/org.eclipse.swt/Eclipse SWT PI/common/org/eclipse/swt/internal/Library.java"
minor=$(grep -oE 'MINOR_VERSION = [0-9]+' "$LIBJ" | grep -oE '[0-9]+$'); rev=$(grep -oE 'REVISION = [0-9]+' "$LIBJ" | grep -oE '[0-9]+$')
want="4${minor}r${rev}"
# the receipt file is written when staging; --verify only reads (rows on stdout, RECEIPT=/dev/null)
if [ "$VERIFY" = 0 ]; then mkdir -p "$OUT"; RECEIPT="$OUT/natives.receipt.tsv"; : > "$RECEIPT"; else RECEIPT=/dev/null; fi
printf 'name\toid\tsize\tstaged_sha256\tverdict\n' | tee -a "$RECEIPT"
bad=0; good=0
for name in swt-osversion-win32-$want.dll swt-win32-$want.dll swt-gdip-win32-$want.dll swt-awt-win32-$want.dll swt-wgl-win32-$want.dll WebView2Loader.dll; do
  ptr=$(git -C "$WT" cat-file -p "HEAD:$BP/$name" 2>/dev/null | tr -d '\r')
  oid=$(sed -n 's/^oid sha256://p' <<< "$ptr"); size=$(sed -n 's/^size //p' <<< "$ptr")
  if [ -z "$oid" ]; then printf '%s\t-\t-\t-\tNO_POINTER\n' "$name" | tee -a "$RECEIPT"; bad=$((bad + 1)); continue; fi
  obj="$LFS/${oid:0:2}/${oid:2:2}/$oid"
  if [ "$VERIFY" = 0 ]; then
    if [ -f "$obj" ]; then cp -f "$obj" "$OUT/$name"; else rm -f "$OUT/$name"; fi
  fi
  sha=$([ -f "$OUT/$name" ] && sha256sum "$OUT/$name" | cut -d' ' -f1 || echo -)
  if [ "$sha" = "$oid" ]; then v=OK; good=$((good + 1)); elif [ "$sha" = - ]; then v=MISSING_LFS_OBJECT; bad=$((bad + 1)); else v=OID_MISMATCH; bad=$((bad + 1)); fi
  printf '%s\t%s\t%s\t%s\t%s\n' "$name" "$oid" "$size" "$sha" "$v" | tee -a "$RECEIPT"
done
echo "natives: $OUT ($want) ok=$good bad=$bad receipt=$RECEIPT"
[ "$good" -gt 0 ] && [ "$bad" -eq 0 ]
