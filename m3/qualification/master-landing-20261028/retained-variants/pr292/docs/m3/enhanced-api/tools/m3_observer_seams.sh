#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# M3VisibilityObserver seams of leaf 11: a programmatic scroll (List/Table.setTopIndex, Tree.setTopItem) is checked when
# it returns, on win32, GTK and Cocoa, with m3_seam_wrap.awk (KIND=observe). Run twice: the second run changes nothing.
# Usage: m3_observer_seams.sh <worktree>
WT=$1; HERE=$(cd "$(dirname "$0")" && pwd)
W="$WT/bundles/org.eclipse.swt/Eclipse SWT"
wrap () { # <file> <helper> <signature>
	awk -v BINMODE=3 -v SIG="$3" -v HELPER="$2" -v KIND=observe -f "$HERE/m3_seam_wrap.awk" "$1" > "$1.m3tmp" && mv "$1.m3tmp" "$1"
}
for p in win32 gtk cocoa; do
	wrap "$W/$p/org/eclipse/swt/widgets/List.java" setTopRow 'public void setTopIndex (int index) {'
	wrap "$W/$p/org/eclipse/swt/widgets/Table.java" setTopRow 'public void setTopIndex (int index) {'
	wrap "$W/$p/org/eclipse/swt/widgets/Tree.java" setTopTreeItem 'public void setTopItem (TreeItem item) {'
done
