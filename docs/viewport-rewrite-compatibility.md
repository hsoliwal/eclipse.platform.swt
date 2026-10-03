# SWT viewport rewrite: compatibility and stress catalogue

This branch treats SWT `Table`, `Tree`, and large scrolled surfaces as a logical coordinate model plus a bounded paint viewport.

## Compatibility evidence

The implementation is checked against SWT's own snippets/manual tests and against behavioral categories represented by the long-running Java2s SWT catalogue. Java2s is reference material only; source is not copied.

Useful Java2s categories:

- SWT Table, TableItem, Table renderer and Table event examples.
- SWT Tree examples, including lazy population.
- SWT TreeViewer examples.
- SWT ScrolledComposite examples.
- SWT Canvas / paint examples.

Reference catalogue:

- https://www.java2s.com/Tutorial/Java/CatalogJava.html
- https://java2s.com/Tutorial/Java/0280__SWT/1180__Tree.html

The catalogue exposes dedicated SWT sections for ScrolledComposite, Table, Tree, TreeViewer and Canvas/paint behavior. The rewrite preserves those API shapes while changing the internal storage and residency model.

## Rewrite invariant

The public widget API is a facade. The authority underneath it is:

- logical coordinate/count;
- columnar primitive/reference arrays;
- packed state masks;
- range-compressed selection;
- viewport origin and extent;
- visible + overscan paint window;
- identity-pinned SWT `Item` facades once exposed.

A materialized `TableItem` / `TreeItem` is never rebound to a different logical coordinate. Future native-residency compaction may discard only private render/native cache that can be reconstructed without violating the public item identity contract.

## Redraw-locked mutation

Large projection changes use the same safe shape:

```java
widget.setRedraw(false);
try {
	// mutate logical coordinates / expansion / selection / native residency
} finally {
	if (!widget.isDisposed()) widget.setRedraw(true);
}
```

The lock is scoped to the mutation. It is not a substitute for the viewport model and must not become a global lock.

## Collapse/expand direction

Collapse is a projection boundary:

1. record logical child count and child state lanes;
2. lock repaint;
3. make the collapsed subtree non-resident for paint/native purposes where the platform allows it;
4. keep public SWT item facades identity-pinned if they have escaped through API/SetData;
5. restore native/render residency from logical coordinates and masks when expansion or direct API demand requires it.

This separates semantic lifetime from render lifetime.

## Manual stress harness

Run `org.eclipse.swt.tests.manual.ViewportRewriteStress`.

It contains three deliberately large scenarios:

- **2,000,000-row virtual Table** with `SWT.SetData`, checkbox state, selection and owner-paint counting.
- **500,000-root lazy virtual Tree**, with 10,000 logical children per demanded branch.
- **1,000,000-row ScrolledComposite + Canvas**, where paint derives its row window from the GC clipping rectangle and expands it by only eight overscan rows. Selection is held in one packed `long[]` bit lane.

The status line reports logical extent versus SetData/paint activity so viewport behavior is visible while scrolling.

Existing SWT manual tests such as `Bug548982_TreeAddRemoveMany` remain important regression inputs, particularly its redraw-lock, reverse insertion, expand/collapse and large tree cases.


## Synexia concepts distilled into SWT

Recent `hsoliwal/com.synexia` viewer work was reviewed as design evidence. SWT should absorb the low-level data structures that belong below JFace, while rejecting higher-level model/session dependencies.

### Inline into SWT core

1. **Visible-tree preorder intervals**

   The useful idea from `MIndexVisibleTreeIndex` is the representation, not the class: assign the known logical projection a DFS/preorder coordinate lane, retain depth and subtree-end lanes, and represent expansion independently from native items. A subtree collapse then becomes a range/state operation rather than recursive widget destruction. Visible-row rank/select can be computed from the logical projection and used to manufacture only the visible + overscan facade set.

2. **Sparse row extent geometry**

   The useful idea from `MIndexSwingExtentIndex` is a uniform row-height baseline with sparse changed blocks. A million- or billion-row logical surface should not allocate one height value per row merely because a few owner-drawn rows measure differently. Pixel offset -> logical row and logical row -> pixel offset belong in the viewport model.

3. **Caller-owned viewport buffers**

   `MIndexVisibleTreeIndex.windowInto(...)` demonstrates the right allocation shape: paint code supplies reusable coordinate/depth buffers and the model fills them. SWT paint passes should not allocate a new row object graph for every scroll frame.

4. **Frame-atomic projection changes**

   The XViewer reconciler's useful invariant is one long-lived control plus a bounded transaction:

   ```java
   control.setRedraw(false);
   try {
       // update logical state / projection / native residency
   } finally {
       if (!control.isDisposed()) control.setRedraw(true);
   }
   ```

   State restoration and top-row publication belong inside the same transaction.

5. **Coordinate remapping after structural edits**

   Synexia's coordinate-remap work is useful as a rule: insertion/removal must move selection, checks, expansion, focus and viewport coordinates together. SWT's mutable implementation can do this directly in its parallel lanes instead of allocating an immutable remap object for every edit.

### Keep above SWT

Do **not** inline these Synexia owners into SWT:

- `MIndexViewerSession`, contexts, generation ownership or source snapshots;
- XViewer/Nebula/JFace content-provider abstractions;
- workbench Jobs, model filtering/sorting, source refresh or persistence;
- domain scene models such as `MIndexNativeSceneWindow`;
- MIndex string/token dictionaries.

Those are clients of SWT's viewport machinery, not toolkit primitives.

### Deferred / measurement-gated

Two Synexia ideas are deliberately not copied into the current heap implementation yet:

- the fixed 128-byte direct-address row image used by `MappedIndexWordRows`;
- the general JNI addressed-array execution engine.

SWT's mutable widget state is currently better represented by flat Java primitive/reference lanes. A direct/mapped fixed-stride row image becomes attractive only after profiling shows a JNI/FFM/native batch boundary is worthwhile.

The resulting ownership stack is:

```text
Workbench/JFace/Nebula model semantics
        |
        v
SWT public widget/item facade API
        |
        v
logical coordinate + topology arrays
state masks + range selection + sparse extents
visible/overscan viewport planner
        |
        v
bounded native residency / paint
```
