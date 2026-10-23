# UI label interning and path ids: examples

All examples are plain Java on any JDK 21 with the official SWT 3.135.0 jar (or this fork's jar) and the classes of
`org.eclipse.swt.internal.m3.collections`. They log through `SwtLog` (java.util.logging) and run on a hidden desktop
for evidence (`-Dm3.snapshot=<dir>` saves PNGs and exits). Screenshots of the runs below live on
`S:/m3-scratch/ui-interning/widget/shots/` (regenerable; not committed).

Build (as `S:/m3-scratch/ui-interning/swt/build.sh` does):

```sh
javac --release 21 -d out/main "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/m3/collections/"M3*.java \
  "bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/SwtLog.java"
javac --release 21 -cp "swt.jar;out/main" -d out/bench examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/M3*.java \
  docs/m3/ui-interning/bench/M3InterningWidget*.java
```

## 1. A 1,000,000-row Table of repeated labels, pool on and off

`examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/M3LabelPoolTableSnippet.java`

```java
M3LabelPool pool = new M3LabelPool (4096);                   // one per model
M3TextExtentCache extents = new M3TextExtentCache (1 << 18);  // (label, font) -> width x height
M3LabelLanes lanes = new M3LabelLanes (4, pool, extents);     // an int label id per cell
lanes.setRowCount (1_000_000);
lanes.setText (row, column, providerText);                    // interned once, referenced per cell
table.addListener (SWT.SetData, e -> {                        // SWT asks only for visible rows
	int row = table.indexOf ((TableItem) e.item);
	for (int c = 0; c < 4; c++) ((TableItem) e.item).setText (c, lanes.text (row, c));
});
int widest = lanes.widest (column, 0, rows, fontId, (label, font) -> {
	Point p = gc.stringExtent (lanes.text (label));           // called once per distinct label
	return M3TextExtentCache.pack (p.x, p.y);
});
```

`java -cp "swt.jar;out/main;out/bench" org.eclipse.swt.snippets.M3LabelPoolTableSnippet` (pool on) and the same with
`-Dm3.labelpool=off`. The status line shows the model's retained heap and the `GC.stringExtent` calls of the
widest-column pass. Measured on the hidden desktop: see section 4.

## 2. A lazy file-system Tree driven by path ids

`examples/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/M3PathTrieFileTreeSnippet.java`

```java
M3LabelPool names = new M3LabelPool (1024);
M3TreePathTrie paths = new M3TreePathTrie (names);           // segments are pooled file names
int root = paths.internPath ("S:/m3-scratch/worktrees/swt-interning", '/');
tree.addListener (SWT.SetData, e -> {                         // lazy: list a directory only when shown
	TreeItem item = (TreeItem) e.item, parent = item.getParentItem ();
	int parentPath = parent == null ? root : (Integer) parent.getData ();
	String name = children (parentPath) [e.index];
	int path = paths.childNamed (parentPath, name);           // one int per node path, prefix shared
	item.setData (path);
	item.setText (name);
});
tree.addListener (SWT.Expand, e -> paths.flag (pathOf (e.item), M3TreePathTrie.EXPANDED, true));
```

The item keeps only its path id; the file path is rebuilt from the trie (`appendPath`) when the directory is listed.

## 3. Expansion that survives a full refresh

The same snippet's Refresh button disposes every item (`removeAll`) and rebuilds the roots; the EXPANDED flags of the
path ids are then re-applied parents first (`forEachFlagged`), with no String path map and no element map. With
`-Dm3.snapshot` the snippet expands three folders and one subfolder each, saves `tree-before-refresh.png`, refreshes,
saves `tree-after-refresh.png`; the two images show the same expanded folders.

## 4. Measured on the hidden desktop

Filled from `S:/m3-scratch/ui-interning/widget/*.tsv`; the rows are in
M3jdk21 `m3/docs/ui-interning/OPTIMIZATION_LEDGER.tsv`.
