<!-- SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project -->
<!-- SPDX-License-Identifier: Apache-2.0 OR EPL-2.0 -->
# SWT modern native layers: batched DirectComposition dirty regions

An additive evolution of `LayeredViewport → NativeLayers → MayaNativeLayers → MayaComposition`:
no replacement compositor, new child Control per row, new Java renderer, or
change to the pre-existing seven Maya JNI exports.

**Added ABI:** `MayaComposition._nUploadDirty(layer, packedArgb, int[] rectangles, count)`.
Rects are quadruples x,y,width,height in *image-local* pixels. The payload
concatenates tightly packed premultiplied ARGB scanlines for each rect. It is
copied into native memory with `GetIntArrayRegion`, before each native
`BeginDraw`. A single Java-to-native call can update multiple independent
dirty rectangles, avoiding a full Java→native→GPU image upload. `_nApply`
remains the one-per-frame compositor commit.

Microsoft requires the **first** `IDCompositionSurface::BeginDraw` to cover
the entire nonvirtual surface. A native `initialized` bit enforces the
initial full `_nUpload`; dirty updates afterward use the *returned*
updateOffset, which is not necessarily the request coordinates. The last
completed frame remains valid after invalid **arguments** (all regions
validated before touching the GPU); an HRESULT failure during a later patch
can leave earlier patches applied, so callers fall back to full redraw.

**Public image API:** `Layer.redraw(Rectangle imageLocalPixels)` is
additive. It reads only the rows and spans of a changed image through
`ImageDataArgb.premultipliedSpan`, then uses the existing native layer.
An older Maya binary or non-Windows backend returns unsupported, and the
original full redraw is used. It does not dispose borrowed SWT Images,
retain JNI arrays, or alter control-layer z/focus semantics.

## Platform reality

| Target | Existing native route | Remaining modernization |
|---|---|---|
| Win32 | DirectComposition / D3D11; seven original exports, optional new dirty upload | Rebuild DLL, generated exports, screen-pixel and driver acceptance |
| Android API 29+ | Existing Maya raster; `MayaComposition` returns unsupported | Real `ANativeWindow`/ASurfaceControl parent, `AHardwareBuffer` ownership, fences, release callback and accessibility |
| Cocoa/macOS/iOS | Existing SWT Cocoa path, no Maya compositing | CALayer/Metal surface binding, main-thread/focus/IME and native-child ordering |
| GTK 4 | Existing GTK ownership | GSK/GskRenderNode, snapshot lifetime and GPU invalidation |
| GTK 3 | Existing GTK ownership | Preserve current Cairo/native draw fallback |
| Wayland | Existing compositor protocol integration | Owned wl_surface/wl_subsurface, sync/damage/scale and XDG popup policy |

The table is an **ownership and admission roadmap**, not a claim that
Android/Cocoa/GTK/Wayland native compositor layers have been shipped.

## Acceptance

- Standalone Clang C++17 portable dirty-rectangle + pixel-copy oracle
  **PASS 3,915,491** assertions on the tested header.
- The receiving actual SWT Java, JNI/Windows source, Win32 DLL and native
  screen tests remain **NOT RUN** for this source-sealed draft. Existing
  packaged maya.dll does **not** export `_nUploadDirty`; do not merge Java
  and native files into a release without rebuilding and publishing the
  matching binary+maya.version and running `-Xcheck:jni`/screen pixels.
- `native/tests/run_dirty_proof.sh` is an independent portable C++ test.
- Reconcile open SWT #503 frame-scratch work before promotion: it modifies
  `MayaComposition.cc` and `LayeredViewport.java`. Preserve both, reject
  stale preimages, do not rebase develop.
- Differential gates: alpha/transparency/palettes, 0/1/max rectangle counts,
  malformed and overlapping rects, device-loss, resize/disposal/reentrant
  paint, Win32/GTK/Cocoa native focus/accessibility/IME, CPU/GPU/RSS/handles.

Donors: Microsoft DirectComposition API docs and Android NDK docs are
architecture references only. No upstream implementation source is copied.

## Multi-rectangle damage from a CPU-owned backing image

Additive `Layer.redrawRegions(Rectangle... dirtyRegions)` handles up to 4096
independent image-local regions through **one** Java-to-JNI `uploadDirty`
invocation, even when many rows are changed. It snapshots caller-owned
rectangle geometry into primitive `int[4*N]`, checks all bounds and the
total signed-int packed pixel count on the CPU (shared
`NativeDirtyRegions` validation), then copies only those image spans
into a packed ARGB payload. Overlap is allowed and counted per region.
The existing single-rectangle `redraw(Rectangle)` remains unchanged.

Only the SWT UI thread changes image pixels and native visual ownership.
The CPU image/backing store is the authority; DirectComposition is an
optional presentation endpoint. A missing/failed JNI upload or Commit
falls back to the original `Layer.redraw()` full-image sync. A no-op
empty array never allocates native resources. No browser/DOM renderer,
native widget per rectangle or new OS handle is introduced.

The Java21 proof compiles the exact `NativeDirtyRegions.java` with
`-Xlint:all -Werror` and passed **221,313** deterministic argument and
payload cases. The existing native C++ proof separately passed
3,915,491 packed-pixel and 20,467,204 extended observations. The
new Win32 native screen test and exact Maven/OpenRewrite/Tycho runs
are **NOT RUN**; a missing optional DLL symbol skips the normal test,
while `-Dswt.maya.dirty.required=true` must fail release proof.

## Native upload coalescing and D3D11 box safety

The existing Java `Layer.redrawRegions(Rectangle...)` submits packed ARGB and
image-local rectangles through a single `MayaComposition._nUploadDirty`
JNI call. The Win32 native receiver now copies the validated **complete
prefix once** with `GetIntArrayRegion` before the first GPU
`IDCompositionSurface::BeginDraw`, and then uses checked offsets into that
immutable native staging array for each rectangle. This replaces repeated
per-region JNI array copies/allocations. It can use more *peak native memory*
for a large batch than a single-region scratch buffer; no throughput or
memory improvement is claimed before measurement.

Full-image upload also catches `std::bad_alloc` and does not unwind C++
exceptions across JNI. The existing `MayaCompositionDirty.h` is the single
portable owner for checked 4-byte BGRA row pitches and returned D3D11 update
offsets; unsigned addition avoids signed integer overflow in
`D3D11_BOX` right/bottom geometry. Every successful `EndDraw` still marks
the existing pending native surface Commit. The Java model/backing image
continues to own all source pixels.

Portable Clang++17 source proof: **251,775** box/prefix observations PASS;
existing dirty-region C++ proof: **3,915,493** cases PASS. Actual full
Maya native Windows build/DLL, `-Xcheck:jni` Win32, DirectComposition
screen pixels, GPU/RSS/latency, SWT Java/Tycho and cross-platform native
platform parity remain **NOT RUN** for this candidate.

## Retained per-thread pixel staging without extra JNI copies

The merged full-prefix upload from SWT #554 is retained: native code still
validates every source rectangle, copies the **entire used Java pixel
prefix once before the first BeginDraw**, and then submits each region
without holding a Java GC array or issuing any additional JNI reads.
This preserves its safety boundary for JVM exceptions and GPU partial
frames.

Small multi-region batches (2 or more rectangles; <=262,144 ARGB ints,
1 MiB pixel payload) now borrow a reusable **per-native-UI-thread**
`DirtyPixelScratch` buffer. Its exact-element allocation never retains
more than 1 MiB of pixel data; nested/reentrant calls and larger valid
batches use independent temporary allocations and cannot overwrite
an outer frame. Large batches retain the original full-prefix peak
allocation but do not leave that allocation cached on the UI thread.

This change does not add a renderer, Java process, JNI export or SWT
public API. The original typed native handles, `CompositionFrameDelta`,
device reset handling, surface Commit and CPU-owned backing store remain
authoritative. The same portable C++17 scratch class and tests passed
**302,509** assertions with Clang ASan/UBSan and GCC strict warnings.
Native Win32 builds and CPU/GPU/RSS/latency benchmarks remain unqualified.
