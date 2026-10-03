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
