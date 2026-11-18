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
| alpha | 0..255, Photoshop layer opacity: an image layer is one image draw; a painter layer below 255 is isolated (painted onto a copy of the composite beneath it, then blended once) |
| backing store | the composite of all layers retained between paints (widget pixels) with a dirty region (`ViewportDamageRegion`); a paint recomposes only damage that is dirty, an expose is a copy from the store |
| invalidation | old and new projected layer bounds, `redraw()`, zoom: their pixels become dirty; a whole-pixel pan at zoom 1 moves the retained pixels (and the dirty region) and dirties only the band that comes into view; other pans recompose the view |
| virtualization | offscreen layer painting culled by the GC dirty clip |
| hit testing | topmost bounding rectangle, optional application hit tester |
| resource contract | borrowed images and paint GCs never disposed by viewport |
| existing SWT API | original widget names and native ordering remain unchanged |

Painter callbacks receive a **GC in widget pixel coordinates**: the viewport's backing store, or for a translucent layer a scratch copy of the composite beneath it.
They should use PaintContext.clip() and transform world geometry using
LayeredViewport.toScreen(). Callbacks must restore any GC settings they change
except alpha/clipping, which the viewport restores. The GC is borrowed.

**Opacity:** for an Image layer, alpha applies to one image draw. A paint
callback layer below 255 paints onto a copy of the composite beneath it (the
scratch image); that copy is then drawn once over the store with the layer's
alpha, so overlapping primitives of one layer form one transparent group (the
test checks that an overlap of two black shapes at 128 over white is the same
grey as either shape alone). Only normal (source-over) blending exists.

The initial clipping region is world-space rectangular; arbitrary alpha masks and per-pixel clip masks are not implemented. The visibility gate is rectangular. It avoids work for fully invisible
layers but does not yet spatially index millions of scattered layers. The
Z-order storage is an array snapshot updated on structure changes, so paint
iteration does not allocate a new layer array every frame.

## Next implementation boundaries

1. **Z-group surfaces:** per-layer retained surfaces (a changed layer recomposes
   from cached layers below and above instead of repainting them), parent/child
   groups, and clip/mask propagation. Group opacity for one layer is done.
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
visibility, opacity, lock, 64-bit origin and hit testing, and on screen pixels:
an expose served from the backing store without calling painters, `redraw()`
still recomposing, a pan in either direction recomposing only the band that
comes into view with pixels equal to a full recomposition, dirty pixels moving
with a pan, and isolated group opacity. Run on win32 (JDK 21): 125/125 of the
class with its inherited Canvas tests (one inherited test skips itself on
win32); the new tests fail on the first implementation, and seven mutants of
the store and of `ViewportDamageRegion.remove` are rejected.

A separate manual snippet demonstrates painted overlapping layers.

Not yet established: GTK and Cocoa runs, platform parity, benchmarks and
release qualification.
Before promotion run the fork's recipe/replay, source and binary API diff,
JDK 21 compilation, widget JUnit tests, native GTK/Win32/Cocoa tests,
pixel comparisons (alpha and damage), and resource/disposal measurements.
