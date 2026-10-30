#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Stages the win32 natives a JVM of this worktree can load (GC seam, leaf 3). The headless m3gc tests initialize
# org.eclipse.swt.graphics.Device (Color(null, ...) -> Resource(Device) -> Device.getDevice() -> Display -> OS -> Library),
# so they need swt-osversion/swt-win32/swt-gdip of the worktree's Library revision (Library.java REVISION). Master bumped it
# to 4975r19 without a built dll (the newest built ones are r15 in ~/.swt/lib and r14/r10 in the superset clones), so this
# script copies the newest set it finds under the worktree's revision names into a staging dir, which the gates pass as
# -Dswt.library.path (M3_NATIVES). Never writes into ~/.swt. Pass Windows paths.
# Usage: m3gc_natives.sh [worktree] [staging-dir]   (defaults: C:/m3-superset/swt-gcseam, H:/m3-scratch/gc-wiring/natives)
WT=${1:-C:/m3-superset/swt-gcseam}; OUT=${2:-H:/m3-scratch/gc-wiring/natives}
LIBJ="$WT/bundles/org.eclipse.swt/Eclipse SWT PI/common/org/eclipse/swt/internal/Library.java"
minor=$(grep -oE 'MINOR_VERSION = [0-9]+' "$LIBJ" | grep -oE '[0-9]+$'); rev=$(grep -oE 'REVISION = [0-9]+' "$LIBJ" | grep -oE '[0-9]+$')
want="4${minor}r${rev}"
mkdir -p "$OUT"
# candidate sets, newest first; the first set that has a given library wins
CANDIDATES="$HOME/.swt/lib/win32/x86_64 C:/m3-superset/swt-tree/binaries/org.eclipse.swt.win32.win32.x86_64 C:/m3-superset/build-swt/stage"
for lib in swt-osversion-win32 swt-win32 swt-gdip-win32 swt-awt-win32 swt-wgl-win32; do
  target="$OUT/$lib-$want.dll"
  [ -f "$target" ] && { echo "have $lib-$want.dll"; continue; }
  src=$(for d in $CANDIDATES; do ls "$d"/$lib-4${minor}r*.dll 2>/dev/null; done | sed -E 's/.*r([0-9]+)\.dll$/\1 &/' | sort -rn | head -1 | cut -d' ' -f2-)
  [ -n "$src" ] || { echo "none for $lib"; continue; }
  cp "$src" "$target" && echo "$lib-$want.dll <- $src"
done
[ -f C:/m3-superset/build-swt/stage/WebView2Loader.dll ] && cp -n C:/m3-superset/build-swt/stage/WebView2Loader.dll "$OUT/"
echo "natives: $OUT ($want)"
