# LayeredViewport: native SWT painted Z-stack

This opt-in SWT custom control introduces arbitrary logical painter/raster
layers above a long-coordinate XY world. It is **not** a change to the behavior
of existing Canvas, Composite, Control.moveAbove/moveBelow, or ScrolledComposite.

## Existing fork authorities

- org.eclipse.swt.internal.ViewportRuntime already defines fixed viewport roles:
  BODY, FROZEN, HEADER, EDITOR, SCROLLBAR, FEEDBACK.
- ScrolledComposite and other fork controls use that authority for viewport
  scroll/invalidation. LayeredViewport reuses it while owning a separate
  user-editable Z-stack.
- Skija/native renderers may be introduced behind this additive API later,
  with backend and source-revision qualification.

## Included in the initial implementation

| Capability | Initial behavior |
| --- | --- |
| XY position | signed 64-bit world coordinates and long-valued pan |
| zoom | 1/4096 through 4096, world-to-screen conversion |
| Z | stable layer IDs, reorderable bottom-to-top array stack |
| layers | painted callback or borrowed SWT Image |
| world clip | optional additional rectangular clip for paint and picking |
| visibility/lock | visible layers render; locked layers are skipped for picking |
| alpha | 0..255; image compositing uses one image draw |
| invalidation | old and new projected layer bounds; full damage for pan and zoom |
| virtualization | offscreen layer painting culled by the GC dirty clip |
| hit testing | topmost bounding rectangle, optional application hit tester |
| resource contract | borrowed images and paint GCs never disposed by viewport |
| existing SWT API | original widget names and native ordering remain unchanged |

Painter callbacks receive the **event GC in widget pixel coordinates**.
They should use PaintContext.clip() and transform world geometry using
LayeredViewport.toScreen(). Callbacks must restore any GC settings they change
except alpha/clipping, which the viewport restores. The GC is borrowed.

**Opacity qualification:** for an Image layer, alpha applies to one image draw.
For a paint callback, GC alpha applies to its individual drawing operations.
Overlapping painter primitives are therefore **not** an isolated
Photoshop-style transparent group. No claim of complete Photoshop blending
or pixel-perfect alpha is made.

The initial clipping region is world-space rectangular; arbitrary alpha masks and per-pixel clip masks are not implemented. The visibility gate is rectangular. It avoids work for fully invisible
layers but does not yet spatially index millions of scattered layers. The
Z-order storage is an array snapshot updated on structure changes, so paint
iteration does not allocate a new layer array every frame.

## Next implementation boundaries

1. **Isolated Z-group surfaces:** clip-sized or tiled offscreen composition
   with correct group opacity, parent/child groups, and clip/mask propagation.
2. **Paint composition:** source-over, multiply, screen, overlay and other
   explicit blend modes; CPU reference pixels and backend differential tests.
3. **Spatial query:** rectangle index, dirty-tile index, generation fences,
   inverse transform clipping and a memory-budgeted cache.
4. **Transforms:** affine per-layer matrices, independent scroll/frozen
   planes, rotation, nested group transforms and inverse hit tests.
5. **Control overlays:** focus, IME, accessibility, selection, drag/drop and
   editor placement stay with SWT ControlEditor/native controls; do not pretend
   an arbitrary native child is an alpha-blended painted layer.
6. **Skija and M3 convergence:** reuse common M3 array-backed indexing and
   viewport work only after the existing owners are inventoried, pinned,
   and checked against native and source/binary compatibility contracts.

## Verification

The accompanying JUnit test exercises ordering, stable IDs, remove,
visibility, opacity, lock, 64-bit origin and hit testing. Run it with a
provisioned SWT native display and the exact fork dependencies.

A separate manual snippet demonstrates painted overlapping layers.

This draft has not established SWT native execution, composited pixel
goldens, platform parity, benchmark claims, or release qualification.
Before promotion run the fork's recipe/replay, source and binary API diff,
JDK 21 compilation, widget JUnit tests, native GTK/Win32/Cocoa tests,
pixel comparisons (alpha and damage), and resource/disposal measurements.

## Optimized candidate — SWT PR #479

The follow-up branch adds a model-only index and guarded Java/JNI pixel atom.
**It is still under draft review**, not a qualified released compositor.

### Visibility and spatial lookup

`org.eclipse.swt.internal.LayerRectIndex` stores five primitive arrays of
rectangle bounds and an array of original Z positions. It creates two sorted
start-coordinate orders (X and Y) using an eight-pass signed-long radix sort
per axis, plus prefix maxima of the opposite ends.

A query finds the first possible interval end and the first start beyond the
query for each axis via binary search, chooses the smaller candidate interval,
filters exact two-axis overlaps, then sorts selected original Z positions.
Half-open boundaries and very large positive/negative world coordinates are
preserved. In a sparse scene this avoids visiting every layer on each paint.
Broad overlapping scenes can still require O(N) candidate work. Edits mark
the index dirty and it is rebuilt lazily before the next query; repeated
single-layer drags currently require a full rebuild when queried again.

Painting and hit-testing capture candidate handles before invoking callbacks.
Warm paint/hit buffers are cleared and reused; nested callback re-entry uses
separate temporary storage to avoid overwriting the active traversal. This
preserves ordering and guards against callback mutation, but does not claim
all application callbacks are safe after widget disposal.

### Damage batching

The control reuses the existing `ViewportDamageRegion` (no new union
algorithm) through `beginUpdate()` and `endUpdate()`. These calls may be
nested and must be balanced in a `try/finally`. Changed visible device-pixel
rectangles coalesce into a bounded region before redraw; for coordinates that
exceed the region's supported bounds the batch falls back to full redraw.

### CPU blend and optional JNI atom

`org.eclipse.swt.internal.render.LayerArgbCompositor` provides deterministic
integer premultiplied-ARGB scanline compositing in three modes:
`SRC_OVER`, `MULTIPLY` and `SCREEN`. The paired
`Eclipse SWT PI/common/library/swt_layerblend.c` implements the exact
same integer operations as an **optional** JNI entry point.
The JNI library is intentionally not built/loaded as part of standard SWT;
its opt-in property and matching `swt-layerblend` native library are required.
The standalone native kernel source has no platform binary in this PR.
The native-library build, packaging and deployment matrix is still pending.

This kernel is **not connected** to `LayeredViewport.paintLayers` yet.
The current Canvas/GC path still only supports per-operation alpha for
user painter callbacks. Multi-primitive isolated opacity, group blend,
masked compositing and Skija saveLayer integration have not been implemented.

### Pending high-value optimization directions

1. **Mutable overlay index for dragging:** keep a bounded journal of edited
   rectangles and periodically rebuild the packed baseline; avoid a global
   sort for every mouse move while retaining exact draw/hit order
2. **Spatial tile plan:** 256x256 demand tiles, clip-derived content residency,
   dirty-tile bitset and scene-generation fences; do not materialize offscreen
   layer bitmaps
3. **Occlusion:** reverse-Z opaque coverage only where provably safe; never
   cull transparent, filtered, blurred, destination-dependent blends
4. **Render commands:** reuse `ViewportPaintGraph` for parent-owned grouped
   command buffers and affine transforms; retain one GC/native control lifetime
5. **Isolated groups:** route native `saveLayer/restore`, masked layers and
   transfer blend modes through a version-pinned Skija backend or qualified
   native Cocoa/GTK/Win32 platform bridge; keep the SWT immediate GC fallback
6. **Pixel correctness:** reference CPU alpha and blend tests against actual
   native Skia/SWT output at DPI 100/200%, fractional zoom, negative world
   origins, overlapping clips, reentrant repaint, disposal and IME overlays
7. **Memory and paint budget:** byte-accounted native tile LRU, per-control
   retained scratch cap and separate heap/native/process measurements;
   record slow cases, rather than claiming universal speedups

Tests `Test_LayerRectIndex` and `Test_LayerArgbCompositor` are registered in
`AllTests`; `Test_org_eclipse_swt_custom_LayeredViewport` is registered
in `AllWidgetTests`. Source registration is **not execution proof**.
The full Tycho/native matrix and canonical Synexia recipe handoff are pending.
