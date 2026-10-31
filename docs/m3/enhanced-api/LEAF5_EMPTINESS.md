# Leaf 5: uniform emptiness notification (M3Emptiness)

Map row M3-EMPTINESS. Stock SWT has `SWT.EmptinessChanged` (56, `@since 3.118`), but only for Tree, and only on two
paths: the first item created (detail 0) and the last item disposed (detail 1). Stock `Tree.removeAll` and
`Tree.setItemCount` send nothing, and Table and List have no emptiness event at all. The tree viewport reproduces the
stock rules exactly (`TreeViewport.becameEmpty`, the EmptyOracle parity), so the stock event must not change.

## Surface (package `org.eclipse.swt.widgets`, public, `@since 3.138`)

| member | meaning |
|---|---|
| `M3Emptiness.EVENT` | the event type, `0x4D330001`: outside the range SWT uses for its own types, and distinct from `SWT.EmptinessChanged` so that both can be listened to |
| `BECAME_NON_EMPTY` (0), `BECAME_EMPTY` (1) | `event.detail`, the same values the stock event uses |
| `addListener(Tree / Table / List, Listener)` | records the current emptiness and adds the listener to the widget's event table under `EVENT` |
| `removeListener(Tree / Table / List, Listener)` | removes it; the recorded state goes with the last listener |

The event also carries `event.count`, the item count after the change (for a Tree, the root item count).

## Contract

- The event is sent when the item count crosses zero, through every structural path:
  - Tree: item creation and disposal, `removeAll`, `setItemCount`;
  - Table: item creation and disposal, `remove(int)`, `remove(int, int)`, `remove(int[])`, `removeAll`, `setItemCount`;
  - List: `add`, `remove` (all forms), `removeAll`, `setItems`, `setItemCount`.
- Nothing is sent when the count stays on the same side of zero: 1 to 2, 2 to 1, `setItem`, `clearAll`, or
  `setItemCount` to the same count. Child items of a Tree do not count.
- One operation sends at most one event. Nested public calls (`List.remove(String)` calls `remove(int)`) announce at
  the first crossing and move the latch, so the outer call cannot repeat it. `List.setItem` (remove, then add) and
  `setItemCount` of all three widgets (the stock paths create and dispose items through the item seams) are held and
  announce once, at the end, with the final count;
  a one-item list never reports a transient empty state.
- A listener that changes the widget again is answered after every listener has the current event, so all listeners
  see the crossings in order (BECAME_EMPTY, then BECAME_NON_EMPTY).
- The behavior is identical in the viewport and original modes, because it compares `getItemCount()` and that is
  mode-independent.
- The stock Tree `SWT.EmptinessChanged` is untouched.
- Platforms: the win32 widgets send the event. On GTK and Cocoa the listener is kept but not called yet (stated in the
  Javadoc).

## DOOP

- Listeners live in the widget's own event table.
- One small per-widget state object exists only while a listener exists: the last announced emptiness (boolean) and
  the open holds (int). It is kept as keyed widget data.
- The funnel is gated by `hooks(EVENT)`, so a widget nobody listens to pays one event-table probe per structural call.
- A listened Tree reads its root count only when a root item is created or disposed (the parent is checked first), or
  on `removeAll` and `setItemCount`.

## Widget seam (win32)

Each public mutator keeps its signature and Javadoc and becomes a two-line wrapper: it calls the unchanged body under a
new package-private name, then `M3Emptiness.changed(this)`. New early returns that the viewport agents add later are
covered automatically. `List.setItem`
and the three `setItemCount` methods wrap their bodies in `M3Emptiness.hold` / `release` (try/finally). A Tree tests emptiness in O(1)
(`TVM_GETCOUNT == 0` natively, the topology root count with lanes) and `TreeItem.destroyWidget` is the seam for item
disposal, because the tree viewport disposes facades without `Tree.destroyItem`.

## Algorithm choices

See `ALGORITHM_CATALOGUE.tsv` (rule 21): the edge detection is a latched level comparison (last announced state)
rather than before/after counts per call, native notifications, or an asynchronous settle check.

## Widget seam (GTK and Cocoa, 2026-10-30)

The same wrappers, applied by the text-lane recipe `tools/m3_emptiness_seams.sh` (`tools/m3_seam_wrap.awk`; a second
run changes nothing): List `add` (both), `remove (int)`, `remove (int, int)`, `remove (int [])`, `removeAll`,
`setItems`, and `setItem` / `setItemCount` held; Table `createItem (TableItem, int)`, `destroyItem (TableItem)`,
`remove` (three forms), `removeAll`, and `setItemCount` held; Tree `createItem`, `destroyItem (TreeItem)`, `removeAll`,
and `setItemCount` held. `List.remove (String)` calls `remove (int)` and needs no wrapper. A listened Tree on these
platforms reads `getItemCount` after an item is created or destroyed (the win32 Tree tests `TVM_GETCOUNT`).
Compiled: win32, GTK and Cocoa source sets, 0 errors each. Not run on GTK or Cocoa (no Linux or macOS display host
here).

## Status (2026-10-30)

| gate | result |
|---|---|
| build | master `d5f6048f398` jar `master-d5f6048` (sha256 `d3e26aff00a3...`), win32 888 / GTK 678 / Cocoa 817 sources, 0 errors; with the GTK/Cocoa seams GTK and Cocoa 0 errors |
| JUnit, hidden desktop | `Test_M3Emptiness` 18/18 (viewport) and 18/18 (original), jar `master-d5f6048` with the TableItem guard below |
| differential against the official 3.135.0 jar | `Test_M3EmptinessDifferential` (seeded scripts over a Table, List and Tree, 8 seeds x 120 steps, plain and virtual; recorded with the official jar): the M3Emptiness column (zero crossings) matches the official count oracle in every step in viewport and original, plain and virtual. Plain: whole lines match in both modes. Virtual: the original mode matches whole lines; the viewport mode differs only in the stock column: 28 steps (survey) where the virtual Tree's own `SWT.EmptinessChanged` fires detail 0 and the official jar does not (seed 0 step 21: `removeAll`, then a new root item). Stock fires only when the new item takes id 0, and `removeAll` does not reset `lastID`; `TreeViewport` fires on "first root" (its EmptyOracle). That is a tree-viewport parity defect, not M3Emptiness; it is open |
| coverage (JaCoCo 0.8.15, viewport and original merged) | lines 64/64; branches 66/72: the 6 others are defensive edges that cannot run: `isDisposed ()` true behind `hooks (EVENT)` in `changed`, `hold` and `release` (a disposed widget has no event table, so `hooks` answers false first), `hooks` false in `changed (Control, boolean)` (its only caller tests `watched` first), and a `release` whose `hold` found no latch while the latch exists at release (a listener added during one held call). Every reachable branch covered |

Open: the TreeViewport stock-event parity above; runs on GTK and Cocoa.

## Re-entrancy fix (2026-10-30)

`test_listenerThatEmptiesTheWidgetAgainIsAnsweredAfterTheDispatch` failed in both modes with
`NullPointerException: Cannot read field "stockItems" because "this.parent" is null` at
`TableItem.pinVirtualFacade` from the `TableItem` constructor: the creation is announced from `Table.createItem`, a
listener removed every item, and the constructor then pinned the disposed item. The `TableItem` constructors of win32,
GTK and Cocoa now run `pinVirtualFacade` only for an item that is not disposed (recipe step in
`tools/m3_emptiness_seams.sh`). With the guard the test passes in both modes.
