#!/usr/bin/env bash
# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
# Applies the M3Emptiness seams of leaf 5 to the GTK and Cocoa List, Table and Tree with m3_seam_wrap.awk (the shape
# win32 has had since 21fddee9e39). Run twice: the second run must change nothing. Usage: m3_emptiness_seams.sh <worktree>
WT=$1; HERE=$(cd "$(dirname "$0")" && pwd)
W="$WT/bundles/org.eclipse.swt/Eclipse SWT"
wrap () { # <file> <kind> <helper> <signature>
	awk -v BINMODE=3 -v SIG="$4" -v HELPER="$3" -v KIND="$2" -f "$HERE/m3_seam_wrap.awk" "$1" > "$1.m3tmp" && mv "$1.m3tmp" "$1"
}
for p in gtk cocoa; do
	L="$W/$p/org/eclipse/swt/widgets/List.java"; T="$W/$p/org/eclipse/swt/widgets/Table.java"; R="$W/$p/org/eclipse/swt/widgets/Tree.java"
	wrap "$L" changed addItem 'public void add (String string) {'
	wrap "$L" changed insertItem 'public void add (String string, int index) {'
	wrap "$L" changed removeIndex 'public void remove (int index) {'
	wrap "$L" changed removeSpan 'public void remove (int start, int end) {'
	wrap "$L" changed removeIndices 'public void remove (int [] indices) {'
	wrap "$L" changed removeAllItems 'public void removeAll () {'
	wrap "$L" hold setItemText 'public void setItem (int index, String string) {'
	wrap "$L" hold setCount 'public void setItemCount (int count) {'
	wrap "$L" changed setAllItems 'public void setItems (String... items) {'
	wrap "$T" changed createTableItem 'void createItem (TableItem item, int index) {'
	wrap "$T" changed destroyTableItem 'void destroyItem (TableItem item) {'
	wrap "$T" changed removeIndex 'public void remove (int index) {'
	wrap "$T" changed removeSpan 'public void remove (int start, int end) {'
	wrap "$T" changed removeIndices 'public void remove (int [] indices) {'
	wrap "$T" changed removeAllItems 'public void removeAll () {'
	wrap "$T" hold setCount 'public void setItemCount (int count) {'
	wrap "$R" changed destroyTreeItem 'void destroyItem (TreeItem item) {'
	wrap "$R" changed removeAllItems 'public void removeAll () {'
	wrap "$R" hold setRootItemCount 'public void setItemCount (int count) {'
done
wrap "$W/gtk/org/eclipse/swt/widgets/Tree.java" changed createTreeItem 'void createItem (TreeItem item, long parentIter, int index) {'
wrap "$W/cocoa/org/eclipse/swt/widgets/Tree.java" changed createTreeItem 'void createItem (TreeItem item, TreeItem parentItem, int index) {'
# A listener of the creation's announcement may dispose the new item before its constructor ends: guard the one
# statement each TableItem constructor runs after createItem (win32 already announced from createItem before this leaf).
for p in win32 gtk cocoa; do
	f="$W/$p/org/eclipse/swt/widgets/TableItem.java"
	awk -v BINMODE=3 '{ line = $0; sub(/\r$/, "", line); cr = ($0 ~ /\r$/) ? "\r" : "" }
		prev ~ /^\t\tparent\.createItem \(this, index\);$/ && line == "\t\tpinVirtualFacade ();" {
			print "\t\t/* an M3Emptiness listener of the creation may have disposed the item */" cr
			print "\t\tif (!isDisposed ()) pinVirtualFacade ();" cr; prev = line; next }
		{ print; prev = line }' "$f" > "$f.m3tmp" && mv "$f.m3tmp" "$f"
done
