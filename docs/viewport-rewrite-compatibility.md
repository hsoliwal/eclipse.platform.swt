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

## Screenshot + SWT Spy regression lane

The viewport stress harness can capture deterministic visual checkpoints together with structural diagnostics.

Launch `org.eclipse.swt.tests.manual.ViewportRewriteStress` with:

```text
-Dswt.viewport.screenshots=<output-directory>
-Dswt.viewport.screenshots.exit=true
```

The second property is optional; when true, the shell closes after the scripted capture sequence.

Each checkpoint writes:

- `<scenario>.png` — the rendered widget surface;
- `<scenario>.txt` — Spy-style structural diagnostics.

The diagnostic sidecar records:

- platform;
- control class and SWT style bits;
- parent class;
- bounds and client area;
- visibility/enabled state;
- layout and layout-data classes;
- horizontal/vertical scrollbar selection, minimum, maximum, thumb, increment, page increment and visibility;
- Table/Tree logical counts, top coordinate, selection count, row height, columns and header state;
- ScrolledComposite origin/minimum/expansion state;
- live `TableItem` and `TreeItem` counts captured with SWT's existing `WidgetSpy.NonDisposedWidgetTracker`, both globally and scoped to the captured widget subtree.

The scripted sequence includes top/middle/end virtual Table states, expanded/collapsed large Tree states, vertical and horizontal ScrolledComposite states, fixed-surface logical viewport states, and narrow/wide resize cases that exercise interdependent scrollbar visibility. Each PNG is hashed so a same-platform/theme/DPI lane can cheaply detect an unexpected visual change before a human inspects the image.

The purpose is not pixel-identical output across operating systems or themes. Compare screenshots within the same platform/theme/DPI lane and combine visual evidence with the structural sidecar and SetData/paint counters.

### Example-driven visual contracts

The supplied historical examples are mapped to visual checkpoints instead of copied into SWT:

- `ViewPort.java`: narrow/wide resize states verify the interdependent horizontal/vertical scrollbar visibility calculation. Its implementation deliberately recomputes horizontal visibility after vertical visibility changes because one scrollbar reduces the other axis' client extent.
- `TreeViewerWithViewPort.java`: scroll-position checkpoints verify redraw-locked projection changes and top-item stability while an external scrollbar drives the underlying Tree.
- `TreeViewerLazyTool.java`: its bounded `PROGRESSIVE_REVEAL_STEP = 100` pattern motivates frontier/residency screenshots while the full logical child count remains visible in the sidecar.
- `DeferrredTreeViewer.java`: its 2,000-child step, dummy loading element, separate expansion list and retained selection motivate the pinned-child collapse/restore screenshot sequence.
- Virtual TreeView 8.4.1: logical node visibility/state, visible-column calculations, dedicated header painting/backbuffer and scroll-range management motivate separate header/body screenshots plus logical-vs-resident diagnostics.
- SWT/Java2s examples remain API-behavior donors for SetData, owner draw, selection, check state, ScrolledComposite and resize combinations; their source is not copied.

Additional scripted images cover:

- `table-checked-selection.png`: checkbox state and selection painting together;
- `tree-pinned-expanded.png`: a pinned checked/grayed child while expanded;
- `tree-pinned-collapsed.png`: the same logical branch after collapsed native-residency compaction;
- `tree-pinned-restored.png`: restored expansion proving the pinned facade/state remains stable.

For Tree scenes the sidecar also attempts to record virtual visible-row count, topology materialized count, viewport first/visible/paint range, and native resident child count for the scripted root. These diagnostics are reflective and best-effort so the manual harness remains usable on every SWT platform.

### Donor evidence for the screenshot lane

The supplied legacy viewer sources reinforce the test shape:

- the old `ViewPort` recomputes horizontal/vertical scrollbar visibility together because showing one scrollbar changes the other axis' available extent;
- `TreeViewerLazyTool` uses bounded progressive reveal while keeping the full logical child count separately;
- `DeferrredTreeViewer` retains expansion/selection while progressively increasing realized child counts;
- Virtual TreeView separates node/visibility state from current painting and maintains a dedicated header implementation.

SWT's own current `WidgetSpy` creation/disposal hook is reused for residency evidence rather than adding another tracker.


## Automated GTK screenshot regression

The compiled JUnit visual lane is `ViewportScreenshotRegressionTest`. It is property-gated and therefore skipped during ordinary SWT test runs.

The reusable build workflow exposes:

`viewport_screenshots: true`

When enabled for a GTK build it runs the visual test under the same Xvfb/Wayland compositor environment used by SWT CI with:

```text
-Dtest=ViewportScreenshotRegressionTest
-Dswt.viewport.screenshotRegression=true
-Dswt.viewport.screenshots=target/screenshots/viewport
```

`.github/workflows/viewport-screenshots.yml` currently runs the deterministic GTK3/X11 lane on relevant pull requests and on manual dispatch. The existing SWT build artifact uploader already includes `**/target/screenshots/*.png`, so the generated images and their text sidecars are retained with the CI run.

The CI scenes deliberately focus on state that is difficult to prove from unit assertions alone:

- two-million-row virtual Table with checked + selected rows;
- expanded virtual Tree with a pinned checked/grayed child;
- the same branch after collapsed native-residency compaction;
- restored expansion with the same public child facade and semantic state.

This complements, rather than replaces, the larger manual screenshot matrix.

## Compatibility requests and SWT classic

The optimized viewport implementation is the forward development path. Public SWT API and observable behavior remain the compatibility boundary.

When a real application reports a regression:

1. reproduce it against the existing SWT snippets/manual/JUnit coverage;
2. prefer a narrow compatibility repair that preserves the optimized storage/viewport model;
3. add the reproducer as a regression test;
4. use the `swt-classic` branch only when the client genuinely depends on legacy implementation behavior that cannot yet be preserved safely.

`hsoliwal/eclipse.platform.swt:swt-classic` is intentionally retained as the classic escape hatch. It is not the architecture target and should not absorb new viewport work. Compatibility requests are accepted; one exceptional client must not force the optimized core back to dense/eager widget construction.
