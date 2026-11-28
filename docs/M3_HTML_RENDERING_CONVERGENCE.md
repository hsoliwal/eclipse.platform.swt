# M3 layered SWT rendering convergence: native widgets, HTML/CSS, and mobile

Status: architecture and test gates; **not** a claim of complete HTML/CSS rendering or mobile native compositor support.
Existing owner: `org.eclipse.swt.custom.LayeredViewport`, `ViewportRuntime`, `ViewportPaintGraph`,
`LayerRectIndex`, `LayerArgbCompositor`, and the optional Maya native backend.
Do not introduce a competing Canvas or change SWT's `Control` ownership.

## Already present

The SWT fork has Java retained backing stores, painter/image/control layers, stable Z order,
rectangle lookup, source-over and separable blend modes, pinned and sticky positions, pointer
routing, hit tests, and the Windows DirectComposition `MayaComposition` path.
The native path currently requires one-to-one pixel mapping and normal blend mode; other
content is painted through SWT's retained store. Native and painted layers are not yet a
general interleaved CSS stacking-context tree. See `docs/LAYERED_VIEWPORT.md`.

## What layers alone cannot render

| HTML/CSS responsibility | Layer machinery already useful | Missing contract / implementation |
| --- | --- | --- |
| Nested stacking contexts | Stable Z stack, isolated painter/image blend | Group surfaces, atomic parent-child stacking, clip/mask inheritance, opacity isolation across children |
| CSS box layout | Coordinates, viewport pan/zoom | Parsing/computed style, intrinsic sizing, margins/padding/borders, containing blocks, min/max, percentage units |
| CSS positioning | Pinned/sticky axes | Relative/absolute/fixed positioning in nested containing blocks; full sticky constraints; transforms and hit inverses |
| Inline and typography | Maya Skia text primitives, SWT GC | Font fallback, bidi shaping, line breaking, vertical writing, text decoration/selection, IME and caret placement |
| Flexbox/Grid/tables | Existing table/tree virtualization | Layout algorithms, track sizing, fragmentation, intrinsic constraints, edge cases and spec oracle tests |
| SVG and vector art | Paths, Skia and retained drawing | SVG element tree, viewBox, filters, masks, gradients, strokes, clipping, inherited paint |
| CSS effects | Separable blend modes and source-over | Filter graphs, backdrop-filter, shadows, non-separable hue/saturation/color/luminosity, color management and HDR |
| Scrolling/overflow | Retained pan, pinned/sticky, damage | Nested scroll containers, scroll anchoring, overflow clip, wheel/touch/overscroll and paint containment |
| DOM/interaction | SWT native controls and pointer routing | HTML parser, CSS selectors/cascade, DOM/CSSOM, event propagation, focus, forms, accessibility tree |
| Browser-grade HTML | None implied by a native widget renderer | JavaScript runtime, web navigation/network, sandboxing, origins, CSP, media, WPT compatibility |

GTK4's GSK render-node and snapshot model is a strong scene-graph reference, but
**GTK's widget CSS is not an HTML/CSS browser engine**. GIMP/GEGL provides layer
and color-operation oracles; Inkscape provides SVG/vector behavior oracles.
Inspect license obligations before borrowing any code: these projects have
different copyleft terms from SWT EPL and Synexia Apache code. Prefer
independent kernels with differential tests where source reuse is unsuitable.

For full unmodified web pages, use an actual embedded browser backend
(WebView2 on Windows, WKWebView on Apple, Android WebView and WebKitGTK where
available) rather than advertising the subset native renderer as browser-complete.

## Native backend boundaries

1. **Windows (implemented)**: DirectComposition visual tree through Maya,
   one JNI `_nApply` per frame; D3D11 premultiplied BGRA image surfaces.
2. **macOS (not implemented)**: `CALayer` / `CAMetalLayer` behind Cocoa UI thread;
   native `NSView` order, secure native ownership and Metal presentation.
3. **iOS (not implemented)**: UIKit `CALayer` / Metal; requires a platform-specific
   SWT peer and lifecycle; a desktop Cocoa HWND-like handle is not transferable.
4. **Android (not implemented)**: Java/NDK `SurfaceControl` / `ASurfaceControl`
   transactions, `ANativeWindow`, HardwareBuffer or Vulkan where supported.
   API-level feature checks and Android UI thread/surface lifecycle are required.
5. **GTK / Linux (not implemented)**: GTK4 GSK snapshots, OpenGL/Vulkan/cairo render
   nodes, or a single Skia-composited backing surface; Wayland subsurface behavior
   is not assumed to match Windows HWND topmost overlays.

Native abstraction is opt-in, requires feature negotiation and explicit fallback.
Compositor threading, GPU-device loss, resize and buffer fences, native handle
lifetime and accessibility remain platform-owned. JNI is not automatically faster:
**batch stable primitive operations per frame**, upload only damaged tiles and
avoid per-glyph JNI calls. Do not pin Java arrays across OS compositor operations.

## Performance and algorithm catalogue

| Category | Candidate optimization | Acceptance evidence |
| --- | --- | --- |
| Spatial index / interval stabbing | Parent-owned primitive rect arrays with offset masks; dirty tile bitsets | Correct hit/paint union vs brute-force oracle, sparse 100k-node scaling |
| Z-order changes / order statistics | Cached rank + sparse retained-mask, incremental splice, small-stack stable insertion sort | Randomized differential order and native Control Z parity |
| Layer composition | Cache immutable group render nodes and flatten-below segments | Pixel parity for premultiplied alpha, order, clip and blend |
| Dirty rectangles | Sweep/interval union or tile masking only when measured cheaper | Exact damaged pixel set, bounded allocation and paint latency |
| Native transfer | Packed `int[]`/direct-buffer batch, dirty-tile upload, GPU texture reuse | JNI call counts, bytes copied, CPU, GPU fences, peak RSS |
| Text | Glyph/run cache by font, script and scale; ICU/HarfBuzz/Pango shaping donors | Unicode bidi/grapheme/line-break oracle and IME/a11y correctness |
| Layout | Cached style/measure DAG, incremental invalidation after parent constraints | WPT/CSS snapshots, RTL, vertical writing and layout-reflow latency |

LeetCode, HackerRank and GeeksforGeeks should be used as **problem-category
discovery**, never as an unlicensed source dump. Record donor URL, revision,
algorithm, license, baseline, and differential benchmark with each canonical
Synexia Maven/OpenRewrite recipe.

## Existing native safety candidate in this PR

`MayaCompositionChecks.h` defines overflow-safe pixel/frame guards and
finite layer-property predicates. The existing seven JNI exports remain
unchanged. The DirectComposition backend now checks live handles and owner
before dereference, refuses duplicate/foreign layer handles before changing
visuals, and uses a monotonically increasing per-composition frame generation
to avoid a per-frame duplicate-detection container. No Java arrays remain
pinned across GPU work.

Local C++17 `-Wall -Wextra -Werror`, AddressSanitizer and
UndefinedBehaviorSanitizer predicates: **22/22 PASS**. This is not a Win32
runtime or native pixel pass. The new CI job validates the portable source
and Linux JNI stub. Win32 Maya/Java 21 `-Xcheck:jni` and screen pixels
must pass before promotion. iOS/Android support is still NOT IMPLEMENTED.

## 2026 native-compositor modernization and incremental DirectComposition leaf

The optimization target is **work crossing the native boundary**, not a larger
number of JNI exports. Preserve SWT `Widget`, `Control`, `Display`,
layout, UI-thread, focus/IME, accessibility, resources and public signatures.
`NativeLayers.apply` remains synchronous and counted; it transports primitive
handles and four validated float properties per layer. No critical Java
array pin survives an OS call. A renderer that cannot satisfy pixel, Z,
clipping and lifecycle equivalence must use the existing SWT backing store.

**Windows native leaf — same seven Maya JNI exports:** the current
`MayaComposition._nApply` removes every DirectComposition visual, sets all
three visual properties and rebuilds the entire sibling tree on every frame.
DirectComposition already retains the visual tree and atomically commits edits.
Retain the last successfully committed visible order and the last applied
offset/opacity per layer. Prevalidate the entire input before mutations.
If visible membership/Z order changes, rebuild the visual sibling list;
otherwise leave the sibling tree intact. Apply only changed properties, and
skip the native `Commit` on a completely identical frame. A failed COM call
or failed commit invalidates the cache; the next successful admission must
rebuild the complete tree and property values. Removing a layer invalidates
the order snapshot. Native properties are cached **only after success**;
failure must never turn an uncommitted attempted frame into authoritative
state. The production header implementing these decisions has a portable
C++17 deterministic regression test.

**Other OS adapters are separate admission leaves, not aliases to Windows:**

| Backend | Native scene/fence primitive | Required admission |
| --- | --- | --- |
| Cocoa/macOS and UIKit/iOS | `CALayer`, `CAMetalLayer`, `CATransaction` | `NSView`/UIKit peer lifetime, main-thread order, layer parentage, texture synchronization, IME and accessibility |
| Android 29+ | `ASurfaceControl`, `ASurfaceTransaction`, `AHardwareBuffer` | existing SWT Android View host, API checks, buffer usage, acquire/release fences and surface recreation |
| GTK4 / Linux | GSK `GskRenderNode` / `GtkSnapshot`, Vulkan/OpenGL as negotiated | original GTK4 native widget lifecycle, X11/Wayland clipping, scale, theme, input and release |
| GTK3 / unsupported | existing Cairo/GC and retained SWT backing surface | deterministic CPU fallback and regression parity |
| Windows | existing DirectComposition/D3D11 Maya owner | native contract tests, bounds, device loss, pixel and resource budgets |

Native composition commands should be batched per frame; native resources
owned and released by their originating compositor. On Java 21, the FFM API
is **preview**, not a universally available replacement for SWT JNI.
FFM experimentation belongs behind optional capability negotiation and
independent Java-version, layout, ABI and crash-recovery gates. Do not change
the Java 21 product baseline or assume Android/iOS ship the same JVM.

Further bounded native leaves: visible-rectangle dirty upload instead of
whole-image copies; texture residency/eviction with fence-safe reuse;
composition group isolation and transforms; GPU color-space/HDR negotiation;
direct memory/arena lifetime audits; batched text shaping and glyph atlas
updates; input event/coalescing paths where UI thread semantics remain exact.
Use measured Java/JNI/native transfer, calls per frame, CPU, GPU memory,
RSS, handles, p50/p95/p99 latency and screenshot oracles. Algorithmic donors
are range queries, sparse masks, interval indexes, retained DAG diffing and
bounded allocation, with independent source and license review.

**This leaf does not implement mobile CALayer/ASurfaceControl/GSK adapters
or complete HTML/CSS; it changes only DirectComposition transaction work.
Keep the PR draft until a real Windows Maya build and native pixel/JNI tests
have executed, and until the overlapping in-flight #503 C++ receiver is
diff-reconciled.**

## Native affine scene, current packed/dirty convergence candidate

The canonical OS layer remains the original `MayaComposition.cc` owner. Merged
SWT #512 supplies retained Z/order native commits; #518 supplies an optional
versioned direct-buffer packet transport that forwards to legacy `_nApply`;
#513 supplies `_nUploadDirty` and the authoritative
`Composition.pendingSurfaceCommit` bit, set after a successful
`IDCompositionSurface::EndDraw` and cleared only after a successful
`IDCompositionDevice::Commit`. Do not introduce a duplicate dirty flag or
lose any packed/dirty method or native export.

The opt-in internal `MayaComposition._nApplyScene(long,long[],float[],int[],int)`
adds 2D affine transforms and local rectangular clips in one counted call:
14 floats per layer (x, y, opacity, visibility, six matrix coefficients,
left/top/right/bottom) plus one per-layer flags word; bit 0 enables the clip.
The existing seven Maya native declarations, and the optional packed and
dirty declarations, retain their signatures, semantics and fallback. Older
libraries return an unsupported result through the Java wrapper rather
than accidentally calling ordinary `_nApply` with unrepresentable visual
state. Existing SWT widgets never call the new API automatically.

Every frame is admitted fully before COM mutation: count/array-prefix
checks without overflow; live, owner-matched, nonduplicated handles; finite
bounded affine and clip coordinates; flags and clipping rectangle validation.
The original `CompositionFrameDelta` retains visible Z-order and
`CommittedLayerProperties` retains offset/opacity; an additional small
per-layer snapshot tracks scene matrix/clip, including failed native
mutations. A visual property update or `pendingSurfaceCommit` triggers
at most one native commit. Failed COM operations invalidate both snapshot
families without clearing pending upload state. A subsequent legacy
`_nApply`, including the existing packed path, restores an identity
matrix and no local clip. Disposal and layer retirement invalidate cached
state without creating or destroying caller-owned SWT images.

The new C++17 `MayaCompositionScene.h` is a platform-independent
validation/cache leaf with an original deterministic sanitizer test;
the optional JNI call is native only on DirectComposition for now.
GTK4/GSK, Core Animation on macOS/iOS, and Android NDK SurfaceControl
remain separately gated backend candidates, not implemented support.
The rendering viewport and native controls stay in their original
SWT owners. Do not advertise complete CSS/HTML compliance on the
strength of a layer compositor, and do not assume JNI itself is
the performance bottleneck.

## Required PR gates

- Source-sealed Synexia recipe (pre/post SHA-256, fixed point, drift refusal),
  followed by product postimage/hash identity and provenance review.
- Java source/API diff and native ABI: original seven symbols unchanged.
- Native sanitizer test, Win32 real-build `maya.dll`, Maya JUnit raster pixels,
  `-Xcheck:jni`, zero native handle leaks and disposal/reentrancy tests.
- OS-specific GTK/Cocoa/iOS/Android builds only where new backends actually
  exist; do not infer platform support from non-Windows stub linkage.
- Measured p50/p95/p99 frame latency, JNI calls/frame, bytes uploaded, heap/RSS,
  native handles and GPU memory vs current master, at 100/1k/10k/100k layers.
- Review source hashes and PR overlap before merging. Never rebase Synexia
  `develop`; do not bypass mandatory CI or use an admin override.

## References

- W3C CSS Positioned Layout: https://www.w3.org/TR/css-position-3/
- W3C CSS Compositing and Blending: https://www.w3.org/TR/compositing-1/
- GTK4 GSK drawing model: https://docs.gtk.org/gtk4/drawing-model.html
- Android SurfaceControl: https://developer.android.com/reference/android/view/SurfaceControl
- Apple Core Animation: https://developer.apple.com/documentation/quartzcore

## Native sparse sibling Z order for ribbons, drawers and inspectors (candidate, 2026-11-28)

The original DirectComposition visual tree already retains no-op frames,
offset, opacity and scene transforms. A Z change previously detached the
entire sibling list and reinserted every visual. The bounded native
`CompositionFrameDelta::planZOrder` now retains the longest stable
subsequence for up to 64 currently visible siblings, using a deterministic
LIS of their previous indices. Moved/retired visuals are removed once and
inserted relative to their already-placed lower sibling with the original
DirectComposition visual owner. Rebuild-all is preserved for the initial
frame, failure recovery, 65+ siblings or when sparse operations would cost
as much as the full path. Existing `_nApply`, `_nApplyScene`, packed
frames, typed handles, native `Commit` and failure invalidation stay intact.

A six-layer reorder that moves one layer to the front requires one
RemoveVisual and one AddVisual instead of RemoveAllVisuals plus six
AddVisual calls; this is a **command-count reduction**, not a measured
frame-latency or GPU performance claim. A portable C++17 differential
oracle covers 75,000 deterministic frames, forced cache invalidation,
visibility differences, and 65+ full-rebuild fallback. Native pixel
qualification on Win32, including the unresolved combined Maya grey-pixel
test, is still required. GTK4 GSK, Core Animation and Android NDK
remain separate, capability-gated backends. Hierarchical native groups
are not implemented by this flat sibling optimization.

Donor categories: LeetCode #300 LIS and permutation editing, HackerRank
array reordering, GeeksforGeeks LIS and minimum moves. Behavioral native
references: Microsoft IDCompositionVisual AddVisual/RemoveVisual.
The DirectComposition reference direction is part of the native contract:
`AddVisual(visual, FALSE, nullptr)` appends at top in a full rebuild, but
`AddVisual(visual, TRUE, lowerSibling)` inserts immediately above that
already-attached sibling, and `TRUE, nullptr` inserts at bottom. Using
`FALSE` for a non-null lower sibling silently reverses this relationship.
The portable oracle now models the actual BOOL/reference pair and contains
a negative regression that proves the former flag produces the wrong order.
The production COM call and the oracle share the planner's compile-time
`INSERT_ABOVE_REFERENCE` flag. Native Win32 pixel/DWM qualification
remains mandatory; standalone source/model testing is not that result.

No donor or challenge implementation has been copied.
