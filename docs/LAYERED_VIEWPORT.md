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
| blend modes | `Layer.setBlendMode(mode)` with the `BLEND_` constants, composited by `org.eclipse.swt.internal.render.LayerArgbCompositor` (its premultiplied kernel, extended with the W3C modes): normal and the W3C Compositing and Blending separable modes (multiply, screen, overlay, darken, lighten, color dodge, color burn, hard light, soft light, difference, exclusion), the layer modes of GIMP, Inkscape and Photoshop. An image layer in a mode other than normal is blended with the store's pixels below it, tile by tile (nearest source pixel, its alpha times the layer's opacity); a painter layer draws normally; a blended layer is never native |
| native layers | `Layer.setNative(true)` on an image layer: the window system composites it (a DirectComposition visual through maya's `MayaComposition`, found by class name through `org.eclipse.swt.internal.NativeLayers`); its pan, opacity, visibility and Z order then change without repainting anything, and it shows above every painted layer, like a control layer. Native only while the image is drawn one to one (zoom 1, world size the image's size, no world clip, 100% device zoom) and a backend exists; otherwise, and as soon as that stops being true, it is painted in the store as before (`isNative()` tells which) |
| control layers | `addControlLayer(name, control, x, y, w, h)` places a native child control (any SWT or Nebula widget) at world bounds; it moves and resizes with every pan and zoom, hides with its layer, at opacity 0 and outside the view; among control layers the native Z order is the layers' order. The window system draws child controls above every painted layer (they cannot be blended). Removing the layer leaves the control where it was |
| retained layers | `Layer.setRetained(true)` keeps the composite of that layer and everything below it in an image of its own with its own dirty region (Photoshop's flatten-below kept as a cache): a change above it is recomposed from that image and the layers beneath are not repainted; a paint brings the retained composites up to date bottom to top, each from the one below it; `Layer.redraw()` declares a layer's own content changed |
| invalidation | a layer change dirties the store and the retained composites at or above its Z index (batched changes keep that precision); old and new projected layer bounds, `redraw()`, zoom: their pixels become dirty; a whole-pixel pan at zoom 1 moves the retained pixels (and the dirty region) and dirties only the band that comes into view; other pans recompose the view |
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
grey as either shape alone). Blend modes other than normal apply to image layers (see the table).

The initial clipping region is world-space rectangular; arbitrary alpha masks and per-pixel clip masks are not implemented. The visibility gate is rectangular. It avoids work for fully invisible
layers but does not yet spatially index millions of scattered layers. The
Z-order storage is an array snapshot updated on structure changes, so paint
iteration does not allocate a new layer array every frame.

## Independently scrolling layers

Each `Layer` defaults to `SCROLL_BOTH` (the historical behavior).
`Layer.setScrollAxes(LayeredViewport.SCROLL_X)` makes a header that follows
horizontal column scrolling while its vertical position remains fixed;
`SCROLL_Y` freezes a column, and zero fixes both axes. This works for
painted, raster-image, native-image, and borrowed native Control layers.
`Layer.toScreen`/`toWorld` and `PaintContext.originX/originY` use that
layer's axis-local coordinates. Picking and pointer callbacks use the same
coordinate system as the corresponding paint.

The parent keeps ordinary scrolling world rectangles in `LayerRectIndex`,
with a small primitive Z-index lane for fixed-axis overlays. Paint and hit
queries merge these in stable Z order. A pan with a painted fixed-axis layer
cannot safely move the flattened backing store by `copyArea` and therefore
recomposes it. Native-image layers receive their axis-local offsets through
maya's existing frame call. Native Controls remain above painted layers, so
arbitrary interleaving is not promised. For a native Tree, the existing OS
header window remains the authority; the axis policy is for new composite
widgets and overlays.

## Next implementation boundaries

1. **Z-group surfaces:** retained composites below a change are done; next are
   surfaces above a change (a layer's own transparent surface) and OS compositor
   layers (DirectComposition visuals, CALayer, ASurfaceControl, Wayland
   subsurfaces), where Z order, offset and opacity are properties the system
   composites without repainting; then parent/child groups and clip/mask
   propagation. Group opacity for one layer is done.
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

## Reference designs (all run on Windows, macOS and Linux)

GIMP and its engine GEGL/babl (layer modes, layer groups, masks, the Photoshop
layer model), Inkscape (SVG layers and Z order of objects), GTK 4 GSK (render
node composition: opacity, transform, clip, blend nodes) and the W3C
Compositing and Blending specification (the blend formulas). They are used as
designs and as pixel oracles to compare against; no code is copied. Licences
(GPL-3 GIMP, LGPL-3 GEGL/babl, GPL-2+ Inkscape, LGPL-2.1 GTK) are recorded, not
acted on yet (owner, 2026-11-18).

## OS compositor routes (native layers, not bound yet)

Current systems already composite Z-ordered layers themselves: a layer's order,
offset and opacity are properties of a system object, and changing them costs
no repaint. These are the native backends the layer API maps onto, through JNI.
Names are from each platform's public API; the "today" column was measured in
this fork with `git grep` (2026-11-18).

| Platform | Facility | Z order | Offset | Opacity | Content | Since | This fork today |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Windows | DirectComposition (`dcomp.dll`: `DCompositionCreateDevice`, `IDCompositionTarget`, `IDCompositionVisual`) | `IDCompositionVisual::AddVisual(visual, insertAbove, reference)` | `SetOffsetX/Y` | effect group `SetOpacity` | `IDCompositionSurface` or a swap chain | Windows 8 | bound in maya: `org.eclipse.swt.internal.maya.MayaComposition` (one visual per layer, premultiplied pixels uploaded, one `_nApply` per frame for order, offset, opacity, visibility; `Test_org_eclipse_swt_maya_Composition`); used by `LayeredViewport` image layers (`Layer.setNative`; `Test_org_eclipse_swt_maya_NativeLayers`) |
| Windows 10+ | Windows.UI.Composition visual layer (`Compositor`, `ContainerVisual`, `SpriteVisual`) | `Children.InsertAbove` | `Offset` | `Opacity` | composition surface brush | 1803 | none |
| macOS | Core Animation `CALayer` | sublayer order, `zPosition` | `position` | `opacity` | `contents` (CGImage, IOSurface) | 10.5 | `CALayer` class bound, sublayer order and `zPosition` not |
| iOS | `CALayer` under UIKit views | as macOS | as macOS | as macOS | as macOS | 2.0 | mobile port: not bound |
| Android (NDK) | `ASurfaceControl` / `ASurfaceTransaction` | `ASurfaceTransaction_setZOrder` | `setGeometry` (29), `setPosition` (31) | `setBufferAlpha` | `setBuffer` (`AHardwareBuffer`) | API 29 | none |
| Linux, Wayland | `wl_subsurface` | `place_above` / `place_below` | `set_position` | buffer alpha | `wl_surface.attach` | core | none |
| Linux, GTK 4 | GSK render nodes (`GtkSnapshot`: opacity, transform, clip nodes) | node order | transform node | opacity node | texture node | 4.0 | GSK referenced in 5 files |
| Browsers | CSS `z-index`, compositor layers (`will-change: transform, opacity`) | stacking context | `transform` | `opacity` | layer bitmap | - | RAP/RWT route |

Order of work: one native layer per `Layer` on win32 first (DirectComposition:
a visual per retained or translucent layer, the store as the base visual), then
CALayer on cocoa and iOS, ASurfaceControl on Android; the Java composite above
stays the reference implementation every native route is compared with pixel by
pixel.

## Verification

The accompanying JUnit test exercises ordering, stable IDs, remove,
visibility, opacity, lock, 64-bit origin and hit testing, and on screen pixels:
an expose served from the backing store without calling painters, `redraw()`
still recomposing, a pan in either direction recomposing only the band that
comes into view with pixels equal to a full recomposition, dirty pixels moving
with a pan, isolated group opacity, a retained layer not repainted by changes
above it (batched or not) but recomposed for its own change and for a change
below it, retained composites moving with a pan, a background change after
the first paint, and control layers (a native button following pan, zoom and
its layer's bounds, hiding with the layer, at opacity 0 and outside the view,
released on remove; native Z order following layer order). Run on win32 (JDK 21): 134/134 of the
class with its inherited Canvas tests and the damage region tests (one
inherited test skips itself on win32); the store tests fail on the first
implementation; mutants rejected: 7 of the store and `ViewportDamageRegion.remove`,
9 of the retained layers, 6 of the control layers.

A separate manual snippet demonstrates painted overlapping layers.

Not yet established: GTK and Cocoa runs, platform parity, benchmarks and
release qualification.
Before promotion run the fork's recipe/replay, source and binary API diff,
JDK 21 compilation, widget JUnit tests, native GTK/Win32/Cocoa tests,
pixel comparisons (alpha and damage), and resource/disposal measurements.

## Callback-created damage under a retained backing store (candidate #484)

The damage region has a capture/consume rule. A paint pass snapshots the dirty
rectangles intersecting its clipping rectangle and consumes that **old**
damage before invoking application painter callbacks. If a painter changes a
layer, the resulting `redraw` adds **new** damage, which must remain pending
for a later SWT paint event. On a failed compose the captured old regions are
restored.

This prevents a user callback from having its own new invalidation erased by
a late `dirty.remove`. The primitive snapshot lane is owned by this viewport
and reused after warming. Nested paint work gets separate temporary space.
This does **not** claim native UI reentrancy or disposal behavior has been
qualified for all SWT backends; focused native visual tests and the canonical
source-sealed Synexia result-only handoff are still required.
