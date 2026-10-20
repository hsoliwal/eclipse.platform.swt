# Public model API: `M3ListModel`, `M3TableModel`, `M3TreeModel`, `M3Patch` (brief rule 26)

Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
SPDX-License-Identifier: Apache-2.0 OR EPL-2.0

Status: **names agreed and frozen, 2026-10-20** (draft 4d4e06e1c3f; every implementer answered "agreed", see
"Agreement"). Owner of the design and of `M3Patch`, `M3LaneModel` (slot lanes, free list, id index, snapshots,
labels, styles, generations) and the differential: enhanced-API agent. Implementers: Tree, Table, List (SWT
widgets), viewport core (run kernel, order index, flattening, extents, window), Swing (M3JDK `java.desktop`),
sort-filter (`M3ProxyModel`), JFace delta viewers (Problems view, the first client). The table "Answers to the
implementers" at the end records each request and where the API meets it.

User, 2026-10-20: "do you want to expose the model through a new API, so clients can start using that too?" The lane
model that leaf 19 built behind `M3Model.of (table)` becomes public toolkit API inside SWT and M3JDK Swing (rule 25):
a client holds one model, and the widget, the sort and filter proxies, JFace and data binding all read the same lanes
and receive the same deltas.

## Rules of the design

1. **One update path.** `model.apply (patch)` is the only way rows, cells, flags, styles and extents change. The
   stock item API (`new TableItem`, `setText`, `remove`, `setItemCount` + `SWT.SetData`) keeps working: on a widget
   without an application model it writes an internal model through the same `apply`.
2. **Lanes, not objects** (DOOP, rule 18). Rows live in `int`/`long`/`double` lanes indexed by slot, cell text in
   interned label ids, flags in bit lanes. `TableItem` and `TreeItem` are lazy facades over a slot, created only when
   the stock API asks for one.
3. **Deltas end to end** (rule 24). A patch costs O(changed + runs * log n) in the model; a widget turns it into
   window work only for the rows it shows, and only scroll extents for the rest. No call ever means "everything
   changed" except an explicit `reset`.
4. **Parallel names** (rule 26). SWT `org.eclipse.swt.m3.M3TableModel` and Swing `javax.swing.table.M3TableModel`
   have the same members where the toolkits allow; differences are listed under "Swing".
5. **Threads** (rule 28). Patches are immutable and carry generations; a model is applied on one thread at a time;
   widgets apply at most one coalesced patch per frame; snapshots are immutable and shareable with a render thread.
6. **Viewport contract** (rules 24 and 25). `SWT.PartVisible` / `SWT.PartHidden` and `SWT.Viewport` complete the
   client contract: a hidden widget only marks its window dirty, a shown one applies one window patch and paints once.

## Identity: rows, ids and slots

| space | type | meaning | stable across | cost |
|---|---|---|---|---|
| row | `int` | position among the children of a parent (`M3Patch.ROOT` for a flat model), `[0, size)` | nothing: patches shift it | row to slot O(log n) |
| id | `long` | the client's identity of a row (a marker id, a database key); unique in the model | the life of the row, moves and reparenting included | id to slot O(1) expected (open addressing) |
| slot | `int` | storage index of the row's lanes, `[0, slotCapacity)` | the life of the row; reused through a free list after removal | slot to row O(log n) |

Inserted rows get their ids from the patch (as in leaf 19). An id is any `long` except `M3Patch.ROOT` (-1), which
names the invisible parent of the roots. Lanes are indexed by slot, so an insert never shifts a lane; the order of
each parent's children is an order-statistic index over slots (blocked sorted lists or an AVL with subtree counts;
`VirtualTreeSiblingIndex` is the first-party donor), and one run costs one index operation however many rows it
carries (a run of 10^4 inserted rows is one block split, not 10^4 shifts). Hot loops read by slot; `order (parent,
from, count, int [] slots)` exports row-to-slot runs in O(count + log n) for bulk work such as a radix sort or a
window move, and `sequence (slot)` is an O(1) order key for comparing two rows of one parent.

## Types (SWT, package `org.eclipse.swt.m3`)

| type | kind | role |
|---|---|---|
| `M3Patch` | final class (exists; extended) | an immutable delta: runs of remove, insert, update, move, reorder, reset per parent section; column masks; eager cell values; generations |
| `M3ListModel` | interface | flat rows: ids, slots, cells of column 0, flags, styles, extents, listeners, `apply` |
| `M3TableModel` | interface, extends `M3ListModel` | columns and their kinds |
| `M3TreeModel` | interface, extends `M3TableModel` | parent and child topology, expansion, visible-row arithmetic |
| `M3LaneModel` | final class, implements `M3TreeModel` | the lane-backed implementation for all three; the internal model of the stock path |
| `M3Column` | final class | column schema: kind (`TEXT`, `INT`, `LONG`, `DOUBLE`, `IMAGE`, `CUSTOM`) and name |
| `M3LabelTable` | final class | interned cell text: label id to `CharSequence`, reference counts, lazy `CollationKey` per label |
| `M3StyleTable` | final class | interned row and cell styles: style id to font, foreground, background |
| `M3CellSource` | functional interface | lazy fill of the cells of a row, asked only for rows that are read (the virtual path) |
| `M3CellWriter` | interface | receiver of cell values (lazy fill, eager patch values); reused, no allocation per cell |
| `M3ModelListener` | interface | patches applied; visible viewports changed |
| `M3ProxyModel` | class (sort-filter track) | a sort, filter or limit stage over a model; implements the model type it wraps |

Widgets: `List.setModel (M3ListModel)`, `Table.setModel (M3TableModel)`, `Tree.setModel (M3TreeModel)`, and
`getModel ()` on all three (never `null`: a widget without an application model answers its internal model).
Items: `TableItem.getId ()` and `TreeItem.getId ()` answer the row id of the facade.

Folded from leaf 19: `M3Model.of (table)` becomes `table.getModel ()`; `M3Model.apply (patch, labels)` becomes
`setSource (labels)` once plus `apply (patch)`; the package-private `org.eclipse.swt.widgets.M3TreeModel` (per-parent
id lanes) is renamed `M3TreeIds` so the public name is free.

## Signatures

```java
package org.eclipse.swt.m3;

/** A delta of one or more ordered child lists. Immutable; built with a reusable Builder. */
public final class M3Patch {
	public static final long ROOT = -1;
	/* column-mask bits of update runs: columns 0..55 are bit c; the rest name row planes */
	public static final long FLAGS = 1L << 63, STYLE = 1L << 62, EXTENT = 1L << 61, PARENT = 1L << 60, ALL = -1L;
	/* section kinds */
	public static final int RUNS = 0, REORDER = 1, RESET = 2;

	public static Builder builder (long parent);
	public static Builder builder (M3ListModel model);        // id-addressed operations resolve against the model
	public static M3Patch diff (long parent, long [] oldIds, long [] newIds);     // exists
	public static M3Patch compose (M3Patch first, M3Patch then);                  // coalescing, rule 28

	public int sections ();                  // one per parent
	public M3Patch section (int index);      // single-parent view; the accessors below read section 0 of a one-section patch
	public long parent ();
	public int kind ();                      // RUNS, REORDER, RESET
	public int removeRuns (); public int removeAt (int i); public int removeCount (int i);      // exist
	public int insertRuns (); public int insertAt (int i); public int insertCount (int i);
	public long insertedId (int run, int k);
	public int updateRuns (); public int updateAt (int i); public int updateCount (int i);
	public long updateColumns (int i);       // column mask of an update run (ALL when not given)
	public int moveCount (); public int moveFrom (int i); public int moveTo (int i);           // exist
	public int reorderedRow (int newRow);    // REORDER: the old row of each new row
	public long resetId (int row);           // RESET: the new children, in order
	public int resetSize ();
	public boolean hasValues (long id);      // eager values staged for this id
	public void values (long id, M3CellWriter out);
	public long baseGeneration ();           // the model generation the builder resolved against, 0 when free
	public long generation ();               // stamped by apply: the generation this patch produced, 0 before
	public void applyTo (Target target);     // exists
	public long [] apply (long [] before);   // exists

	public static final class Builder {
		public Builder parent (long parent);                     // starts the section of another parent
		public Builder remove (int at, int count);               // exist: remove, insert, update, move
		public Builder insert (int at, long... ids);
		public Builder update (int at, int count);
		public Builder update (int at, int count, long columns);
		public Builder move (int from, int to);
		public Builder reorder (int [] oldRowOfNewRow);          // a permutation; the only run of its section
		public Builder reset (long... ids);                      // the section's children are these ids
		public Builder removeId (long id);                       // id-addressed: builder (model) only
		public Builder insertId (long parent, int at, long id);
		public Builder updateId (long id, long columns);
		public Builder moveId (long id, long newParent, int at); // a reparent: PARENT bit on the update
		public M3CellWriter values (long id);                    // eager values for an inserted or updated id
		public M3Patch build ();
		public Builder clear ();                                 // reuse the builder's buffers
	}
}

public interface M3ListModel {
	int size ();                                   // rows of ROOT
	long id (int row);
	int indexOf (long id);                         // -1 when absent
	int slot (long id);                            // -1 when absent
	int slotAt (int row);
	int rowOf (int slot);
	int slotCapacity ();
	int order (long parent, int from, int count, int [] slots);   // bulk row-to-slot export, O(count + log n), returns count
	long sequence (int slot);                      // O(1) order key: within one parent, sequence (a) < sequence (b) iff rowOf (a) < rowOf (b); read, do not cache across patches
	long generation ();                            // +1 per apply

	/* cells, by slot; column 0 for a list */
	M3LabelTable labels ();
	int label (int slot, int column);              // 0 = empty
	int text (int slot, int column, char [] buffer, int offset);  // the length; asks for more room when negative
	String text (int slot, int column);            // convenience: allocates
	int intAt (int slot, int column);
	long longAt (int slot, int column);
	double doubleAt (int slot, int column);
	org.eclipse.swt.graphics.Image image (int slot, int column);  // shared images, no per-cell object
	boolean flag (int slot, int flag);
	long flagWord (int flag, int word);            // 64 slots of one flag
	M3StyleTable styles ();
	int style (int slot, int column);              // -1 = the row's style; 0 = none
	int extent (int slot);                         // row height in points, 0 = default

	/* flags: public bit numbers of the flag lanes, 16..63 are the client's; widgets map them to their internal lanes */
	int CHECKED = 0, GRAYED = 1, EXPANDED = 2, HAS_CHILDREN = 3, DISABLED = 4, CUSTOM = 16;

	long apply (M3Patch patch);                    // the only update path; returns the new generation
	boolean applyIfCurrent (M3Patch patch);        // false when patch.baseGeneration () is stale (rule 28)
	M3ListModel snapshot ();                       // immutable view at this generation; shares unchanged lane chunks

	void addModelListener (M3ModelListener listener);
	void removeModelListener (M3ModelListener listener);
	void viewportVisibility (Object viewport, boolean visible);   // widgets report PartVisible/PartHidden
	int visibleViewports ();
}

public interface M3TableModel extends M3ListModel {
	int columnCount ();
	M3Column column (int index);
}

public interface M3TreeModel extends M3TableModel {
	int childCount (long parent);                  // -1 while unknown (lazy children)
	long childId (long parent, int index);
	long parent (long id);                         // ROOT for a root
	int depth (long id);
	int parentSlot (int slot);                     // lane read, -1 for a root
	long visibleRowCount ();                       // rows of the expanded tree
	long visibleRow (long id);                     // -1 when under a collapsed parent
	long idAtVisibleRow (long row);
}

public interface M3ModelListener {
	void patched (M3ListModel model, M3Patch patch);              // after the lanes changed, on the model's thread
	default void visibleViewportsChanged (M3ListModel model, int visible) {}
}

@FunctionalInterface
public interface M3CellSource {
	void fill (long id, int slot, M3CellWriter out);              // first read of a row, or after an update without values
}

public interface M3CellWriter {
	M3CellWriter text (int column, CharSequence text);            // interned into the label table
	M3CellWriter label (int column, int labelId);
	M3CellWriter intValue (int column, int value);
	M3CellWriter longValue (int column, long value);
	M3CellWriter doubleValue (int column, double value);
	M3CellWriter image (int column, org.eclipse.swt.graphics.Image image);
	M3CellWriter flag (int flag, boolean value);
	M3CellWriter style (int column, int styleId);                 // column -1: the row
	M3CellWriter extent (int extent);
}

public final class M3Column {
	public static final int TEXT = 0, INT = 1, LONG = 2, DOUBLE = 3, IMAGE = 4, CUSTOM = 5;
	public static M3Column text (String name);
	public static M3Column ofInt (String name);
	public static M3Column ofLong (String name);
	public static M3Column ofDouble (String name);
	public static M3Column image (String name);
	public static M3Column custom (String name, M3Column.Text text);   // text computed on read, never stored
	public int kind ();
	public String name ();
	public Text textFunction ();                                  // CUSTOM only
	@FunctionalInterface public interface Text { CharSequence text (long id, int slot); }
}

public final class M3StyleTable {
	public int intern (Font font, Color foreground, Color background);   // 0 when all are null; never freed
	public Font font (int styleId);
	public Color foreground (int styleId);
	public Color background (int styleId);
	public int size ();
}

public final class M3LaneModel implements M3TreeModel {
	public M3LaneModel (M3Column... columns);
	public void setSource (M3CellSource source);                  // lazy fill; null = cells only from patches
	public void setChunkSize (int rows);                          // lane chunk, default 4096 (snapshots copy touched chunks)
}

public final class M3LabelTable {
	public int intern (CharSequence text);                        // the id of the text, +1 reference
	public int intern (char [] buffer, int offset, int length);   // the same without a String for a known text
	public void acquire (int labelId);                            // +1 reference
	public void release (int labelId);                            // -1; at 0 readable and revivable until recycle
	public void recycle ();                                       // the model calls it at the end of each apply: frees ids at 0
	public int references (int labelId);
	public CharSequence text (int labelId);                       // stable object per label
	public int length (int labelId);
	public int getChars (int labelId, char [] buffer, int offset);
	public java.text.CollationKey collationKey (int labelId, java.text.Collator collator);   // lazy, cached per collator
	public int collate (int a, int b, java.text.Collator collator);   // through the collation keys
	public int compare (int a, int b);                            // binary char order
	public int size ();                                           // live labels
	public int maxId ();                                          // ids are 1..maxId; 0 is the empty label
	public long reuseEpoch ();                                    // +1 when freed ids become reusable: caches keyed by label id drop entries older than it
}
```

`SWT.error` codes follow the widgets: `ERROR_NULL_ARGUMENT`, `ERROR_INVALID_ARGUMENT` (a run outside the rows, an
inserted id already present, a stale id-addressed builder), `ERROR_THREAD_INVALID_ACCESS` (apply off the model's
thread).

## Semantics

**Apply.** Sections apply in order; inside a section removes, then inserts, then updates (leaf 19): remove runs are
in old indices, insert runs in final indices in ascending order, update runs in final indices. A builder bound to a
model turns id-addressed operations into these runs: `removeId` for every row that leaves (or is re-placed), then
`insertId (parent, at, id)` with `at` the index in the final order, ascending; the Problems view emits exactly this.
An update run without a column mask means `ALL`. During `patched` the model is final: `slotCapacity ()` covers every
inserted slot, and `slot (id)` answers for the inserted ids. A move pairs a
removed and an inserted row with the same id, so the slot, its lanes and its view state (selection, focus,
expansion) survive. An id inserted in one section and removed in another of the same patch is a reparent: the slot
moves and the update carries `PARENT`. `REORDER` permutes rows and keeps every slot. `RESET` replaces a section's
children: surviving ids keep slots and flags, so expansion and checks survive a rebuild. An update without values
invalidates the named columns; with an `M3CellSource` they refill when read, without one they keep their values
until a patch brings new ones. Values staged with `Builder.values (id)` are written before the listeners run.

**Generations.** `generation ()` grows by one per `apply`; the patch is stamped with it. A builder bound to a model
records `baseGeneration`; `applyIfCurrent` drops a patch built against an older generation (newest wins, rule 28).
`compose (a, b)` gives one patch equal to `a` then `b` (pattern: Glazed Lists `Tree4Deltas`).

**Threads.** A model is applied on one thread at a time (default: the thread that created it; the stock path's
internal model: the UI thread). Listeners run on that thread. A widget showing a model applied elsewhere receives the
patch through `Display.asyncExec (key, runnable)` keyed by the widget, so at most one composed patch per frame
reaches it (rule 28 Synchronizer). Reads from other threads go through `snapshot ()`: an immutable model at one
generation that shares every lane chunk the next patches do not touch (copy on write per chunk).

**Widgets.** `setModel` replaces the rows (one `RESET` of the window, selection kept for ids that survive) and
subscribes; patches then become native or drawn-plane work for the visible window only. Flags `CHECKED`, `GRAYED`,
`EXPANDED` are model state, in the stock event order: a user check flips the state (a one-row `FLAGS` patch) and
then fires `SWT.Selection` with `CHECK`; a user expand or collapse fires `SWT.Expand`/`SWT.Collapse` first (the row
has not changed yet, `getExpanded ()` still answers the old state, and the listener may apply child patches: the lazy
tree), then applies the `EXPANDED` patch unless the listener disposed the row. Because expansion is in the model,
two trees showing one model share their expansion (stock expansion is per widget). Selection and focus are view
state, kept by the widget, because two widgets may show one model: row ranges rebased by each patch (O(runs) for
`selectAll` and `select (start, end)`), with ids carrying them across moves, reorders and resets. A hidden
widget (`SWT.PartHidden`) only unions the touched rows into its dirty window; on `SWT.PartVisible` it applies one
window patch and paints once, and `SWT.Viewport` reports the entered and left ranges (`ViewportEvent`). Widgets
report visibility with `viewportVisibility`, so a proxy chain can park work while no viewport shows.

**Facades.** `getItem (i)`, `getItems ()`, `getSelection ()`, `getTopItem ()` return `TableItem`/`TreeItem` facades
bound to a slot (residency rules of leaf 7: parent-owned, lazy, released when they leave the window and nobody
holds them). Getters read the lanes. Setters apply a one-row patch to the widget's model; on a proxy they are
forwarded to the source row (`map`), and structural edits through a proxy are rejected. The stock virtual path keeps
its meaning: `setItemCount (n)` is a `RESET` of the internal model with synthetic ids, `SWT.SetData` is the internal
`M3CellSource` (the item passed in writes the lanes), and `clear (i)` is an update without values. The stock path
allocates no patch per call: `List.add` or `new TableItem` in a loop of 10^6 goes through one reused builder of the
internal model, and consecutive single-row calls at adjacent indices are one run (List measurements: a lane shift per
row costs 119 to 132 ms per 1,000 rows at 10^6, one run 0.1 to 4 ms).

## Cost

| operation | cost |
|---|---|
| `apply`, k changed rows in r runs | O(k + r log n) lanes and order; O(visible) widget work |
| id to slot / slot to row / row to slot | O(1) expected / O(log n) / O(log n) |
| cell read (`label`, `intAt`, `flag`) | O(1); `text (slot, c, char [], off)` O(length), no allocation |
| `snapshot` | O(n / chunk) pointers; each later patch copies at most the chunks it touches |
| hidden widget, per patch | O(1) amortized (dirty window union) |

## Swing (M3JDK `java.desktop`)

| SWT | Swing | note |
|---|---|---|
| `org.eclipse.swt.m3.M3Patch` | `javax.swing.M3Patch` | exists (leaf S8); gains the same sections, column masks, `reorder`, `reset`, values and generations |
| `M3ListModel` | `javax.swing.M3ListModel implements ListModel<CharSequence>` | `getElementAt` answers the interned label of column 0, no allocation |
| `M3TableModel` | `javax.swing.table.M3TableModel extends AbstractTableModel` | `getValueAt` answers labels and boxed numbers; the `int`/`long`/`double` reads are the fast path for renderers |
| `M3TreeModel` | `javax.swing.tree.M3TreeModel implements TreeModel` | nodes are lazy facades keyed by id (`M3TreeModel.Node`, equal by id), created only for rows JTree touches |
| `M3LaneModel` | the three classes are lane-backed themselves | one shared lane store, `public final sun.swing.M3Lanes` (non-exported package, so the models in `javax.swing`, `javax.swing.table` and `javax.swing.tree` share it outside the public API) |
| `M3LabelTable`, `M3CellSource`, `M3CellWriter`, `M3ModelListener` | same names in `javax.swing` | |

`apply` fires the precise stock events of each run (rows inserted, deleted, updated; tree nodes inserted, removed,
changed per section), never a structure change except for `RESET` (leaf S8 `firePatch` already does this for
tables). `setModel` on `JList`, `JTable`, `JTree` is the stock method. Generations, `applyIfCurrent`, `snapshot` and
the coalescing per frame (EventQueue / RepaintManager, rule 28) are the same.

## Example 1: a table of 10^6 rows fed by patches

```java
M3LaneModel model = new M3LaneModel (M3Column.text ("Id"), M3Column.text ("Name"), M3Column.ofDouble ("Price"));
model.setSource ((id, slot, out) -> out.text (0, Long.toString (id)).text (1, store.name (id)).doubleValue (2, store.price (id)));
long [] ids = store.ids ();                                   // 10^6 ids; no cell is computed here
model.apply (M3Patch.builder (M3Patch.ROOT).reset (ids).build ());
Table table = new Table (shell, SWT.VIRTUAL | SWT.BORDER);
table.setModel (model);                                       // the window asks the source for ~40 rows

/* a worker thread turns the feed into patches; the UI thread applies the newest once per frame */
M3Patch.Builder builder = M3Patch.builder (model);
feed.onTick (tick -> {
	builder.clear ();
	for (Quote q : tick.quotes ()) {
		builder.updateId (q.id (), 1L << 2);                  // column 2 changed
		builder.values (q.id ()).doubleValue (2, q.price ());
	}
	for (long gone : tick.delisted ()) builder.removeId (gone);
	M3Patch patch = builder.build ();
	display.asyncExec (table, () -> model.applyIfCurrent (patch));   // keyed: one per frame, stale ones dropped
});
```

What the widget does: rows outside the window cost lane writes and the scroll extent only; rows in the window are
repainted once per frame; the top row stays on top (leaf 19). `M3DeltaStreamExample` measures the stream on leaf 19
today: 100,000 deltas into 10^6 rows, median 15 us per delta, 31 labels asked in all.

## Example 2: a lazy tree

```java
M3LaneModel model = new M3LaneModel (M3Column.text ("Name"), M3Column.ofLong ("Size"));
M3Patch.Builder roots = M3Patch.builder (M3Patch.ROOT);
int at = 0;
for (Path root : fs.roots ()) {
	long id = fs.id (root);
	roots.insert (at++, id);
	roots.values (id).text (0, root.toString ()).flag (M3ListModel.HAS_CHILDREN, true);
}
model.apply (roots.build ());
Tree tree = new Tree (shell, SWT.VIRTUAL | SWT.BORDER);
tree.setModel (model);
tree.addListener (SWT.Expand, event -> {                      // sent before the row opens, as in stock SWT
	long parent = ((TreeItem) event.item).getId ();
	if (model.childCount (parent) >= 0) return;                  // children known: nothing to load
	M3Patch.Builder children = M3Patch.builder (parent);
	int index = 0;
	for (Path child : fs.list (parent)) {
		long id = fs.id (child);
		children.insert (index++, id);
		children.values (id).text (0, child.getFileName ().toString ()).longValue (1, fs.size (child))
			.flag (M3ListModel.HAS_CHILDREN, Files.isDirectory (child));
	}
	model.apply (children.build ());
});
```

`HAS_CHILDREN` draws the expander before any child exists; `childCount` is -1 until the first patch of that parent.
A collapse may prune with `M3Patch.builder (parent).reset ()` to give memory back; `EXPANDED` of the surviving rows is
kept by `reset`.

## Example 3: a client that reads back through facades

Existing code keeps working on a model-backed widget, and can move to lane reads where it is hot:

```java
table.setModel (model);
TableItem item = table.getItem (5);           // facade over slotAt (5), created now
String name = item.getText (1);               // label lane, then the label's text
item.setText (1, "renamed");                  // one-row update patch with a value, through model.apply
for (TableItem selected : table.getSelection ()) report (selected.getText (0));   // facades for the selected ids

/* the same reads without facades */
int slot = model.slotAt (5);
char [] buffer = new char [64];
int length = model.text (slot, 1, buffer, 0); // no String
boolean checked = model.flag (slot, M3ListModel.CHECKED);
long word = model.flagWord (M3ListModel.CHECKED, 0);   // the checks of slots 0..63
```

A JFace viewer binds its element map to the id lane: the element of a row is a facade keyed by `id`, equality
follows the id, and `refresh` of an element is `updateId (id, ALL)`.

## Differential (what proves the stock API unchanged)

`Test_M3ModelDifferential` (enhanced-API agent): for List, Table and Tree, in the viewport and the original modes,
seeded random sequences of edits run twice: path A through the stock item API (items, `setText`, `setImage`,
`setChecked`, `remove`, `setItemCount` + `SWT.SetData`, `TreeItem.setExpanded`), path B through `setModel` and
patches that produce the same rows. After every step both widgets must agree on item count, the text, image and
check state of every row read through facades, selection, focus, top index, expansion, the events delivered to
listeners (`SWT.Selection`, `SWT.Expand`/`Collapse`, `SWT.SetData` order of the window, `SWT.EmptinessChanged`,
`SWT.Viewport`), and the pixels of the client area. `Test_M3Model` (leaf 19) is the first half of it today: the
`M3Model` path against stock edits, 25/25 in both modes.

## Answers to the implementers

| request | from | answer |
|---|---|---|
| row identity, stable id per row | sort-filter 1, Problems 1 | `long` ids from the patch; `slot` is the dense `int` storage index (free list reuse); id to slot O(1) |
| patch kinds RESET, INSERT, REMOVE, UPDATE with column mask, MOVE, REPARENT; generation | sort-filter 2, Problems 9, Swing 1-2 | `reset`, runs, `update (at, count, columns)`, `move`, `reorder`, reparent = id moved between sections with `PARENT`; `generation`, `baseGeneration`, `applyIfCurrent`, `compose` |
| reusable patch, no allocation per patch | sort-filter 2 | not adopted: rule 28 exchanges immutable patches between threads. The builder is reusable (`clear`), and a patch is one object for any number of runs |
| cells without Strings: label ids, char access | sort-filter 3, Problems 6, rule 26 | `label`, `M3LabelTable` (reference counts, lazy `CollationKey`), `text (slot, c, char [], off)`; `M3CellSource` for computed text |
| typed lanes and column kinds | sort-filter 4, Problems 6 | `M3Column` kinds, `intAt`/`longAt`/`doubleAt`, bulk `order` for radix sorts |
| state bit lanes, word access | sort-filter 5, rule 26 | flag lanes `CHECKED`, `GRAYED`, `EXPANDED`, `HAS_CHILDREN`, `DISABLED`, custom 16..63; `flagWord`. Selection stays view state (by id, in the widget) |
| tree: parent lane, child order, reparent in the patch | sort-filter 6, Problems 2-3 | `parentSlot`, `childId`/`indexOf` (select/rank), `PARENT` bit; groups are parent rows |
| worker-thread reads | sort-filter 7 | `snapshot ()` (immutable, chunk sharing) plus the generation check |
| proxies are models | sort-filter 8 | `M3ProxyModel` implements the type it wraps; patches flow source, proxy, widget |
| hidden viewport hook | sort-filter 9, Problems 12 | `viewportVisibility (viewport, visible)`, `visibleViewports`, `visibleViewportsChanged` |
| ordered children O(log n), head(k), bulk sorted fill, atomic resort | Problems 3 | positions come from the client or the sort proxy; `childId` 0..k-1; `reset (ids)`; `reorder (permutation)` |
| "show N of M" per parent | Problems 4 | a proxy limit stage (`M3ProxyModel`, sort-filter's next leaf): the base answers M per parent in O(1) (`childCount`), the stage answers `childId (parent, i)` for i < N without walking the base; agreed by both |
| apply order of id-addressed patches | Problems note 2 | removes for rows that leave or change, then inserts at final indices ascending (Semantics, Apply) |
| collate/compare pair, acquire/release | Problems note 3 | `M3LabelTable.collate`, `compare`, `acquire`, `release` |
| expand event order; shared expansion; ROOT value | Tree notes 1-3 | Expand/Collapse fire before the `EXPANDED` patch, check fires after its patch; expansion shared by widgets on one model; `ROOT` = -1 |
| public flag numbers vs internal lanes | Table note 1 | public numbers are the API's; widgets map them (TableViewport lanes 2-4 are text direction, 5 the hidden selection snapshot) |
| no allocation per stock call; one shift per run; cheap `order` | List notes 1, 3 | stock path through one reused builder, runs coalesced; `order` O(count + log n) |
| selection as row ranges | List note 2 | ranges rebased per patch, ids across moves, reorders and resets |
| O(1) row compare for the sort | sort-filter note 1 | `sequence (slot)` |
| label id reuse and caches | sort-filter note 2 | freed ids reused only after the next apply; `maxId`, `reuseEpoch` |
| slot growth visible to listeners | sort-filter note 3 | during `patched`, `slotCapacity ()` and `slot (id)` cover the inserted rows |
| `ALL` as the default mask | sort-filter note 4 | yes |
| counters per model and group | Problems 5 | kept in the client (they follow from the patches); not core API |
| sort and filter as lanes, off the UI thread | Problems 7-8 | the sort-filter views over the model, reading snapshots |
| expansion restorable after a rebuild | Problems 10 | `EXPANDED` is a model flag kept by `reset` for surviving ids |
| icon and font/colour per row | Problems 11 | `IMAGE` columns and `M3StyleTable` styles per row or cell |
| `SWT.VIRTUAL` window | Problems 13 | `M3CellSource`: labels asked only for rows read |
| Swing class names and packages | Swing 1, 3, 4 | table "Swing" above |

Donors: `FOSS_REUSE_DECISION.tsv`, capability `public-model-api`.

## Agreement (2026-10-20)

| implementer | agent | answer | binding notes |
|---|---|---|---|
| Problems view (JFace delta viewers) | ae5acc369949b2347 | agreed, no renames | implements `M3TreeModel` on `M3MarkerLanes` directly; drops its window code for the limit stage; switches to `M3LabelTable` |
| Table | a9cb7083090852321 | agreed | `Table.m3Splice`/`m3SetRow` (4d38d24437b) become internal to `setModel`; a `SWT.VIRTUAL` Table without a viewport is a separate leaf for the coordinator |
| Tree | a56862a3f7f3ea02d | agreed with the Expand order change (made) | int topology ids inside, long model ids outside; switches its hidden gate to `PartVisible`/`PartHidden` and `viewportVisibility` |
| List and Combo | aee2e6b4539e3dbab | agreed, no renames | reads windows through `order`; one-run seam `List.m3Splice (at, removed, String [])`; counters for the differential: `m3NativeRowOps`, `m3FullRebuilds`, `m3WindowReplans`, `List.m3HiddenStats`, `Combo.m3WindowPatches` |
| Swing | a4f3571116e413c4d | agreed | lane store `sun.swing.M3Lanes`; mirrors `M3Patch` from `m3/swing-enhanced-api-20261019` (d7284fdd1f9) with a lineage note; viewport events `javax.swing.event.ViewportListener` |
| viewport core | aeba9473d82661f83 | agreed, no renames | gives the run kernel (`com.m3.collections` `M3Patch`, `M3ListDiff`), `M3OrderStatisticForest` (the order index, with `order (parent, from, count, slots)`), `M3TreeTopology`, `M3FlatteningView`, `M3SparseExtents` (extents by visible row), `M3WindowView` (`hide ()`/`show ()` = `viewportVisibility`); core views are confined to the applying thread; adds `Target.update` and run-level `compose` |
| sort-filter | a26d1625a1f9f0a62 | agreed, no renames | proxies keep slot-indexed masks and permutations; limit stage next; selection stays in the widget |

Layering: `M3LaneModel` (enhanced API) owns slots, the free list, the id index (`M3LongIndex`), chunked
copy-on-write lanes and snapshots, labels, styles and generations, and uses the core's order index and run kernel;
a sectioned `M3Patch.compose` calls the core's run-level compose per section.
