# Maya native frame protocol v1 — modern SWT/JNI migration seam

Status: **EXPERIMENTAL / opt-in / not yet qualified on a real Win32 compositor**.

This is an additive frame transport for the existing Windows DirectComposition
backend. It is deliberately not a new graphics toolkit, not a second
`LayeredViewport`, and not a replacement for SWT widget ownership, Maya's
existing seven Java native exports, JNI_OnLoad, Skija/JDK21 contracts or the
already-existing native delta optimizations under review.

## Why a binary frame?

The original `NativeLayers.apply(composition, long[] layers, float[] props, count)`
passes two heap arrays and commits a frame. JNI can already batch operations;
it is **not itself tied to 1996 capabilities**. But a versioned fixed-stride
frame lets a backend validate an entire command batch and later consume it
through a direct-buffer (or JDK22+ FFM) route without changing public SWT
`Control` interfaces or handing Java object graphs to native code.

Windows uses the **existing** `MayaComposition._nApply` native implementation,
which owns DirectComposition visuals, D3D11 textures, and commit semantics.
Linux/Cocoa/GTK/Android/iOS native layers are NOT implemented by this patch.

## Exact v1 little-endian binary layout

Header: **32 bytes**, records: **32 bytes**; packet size is exactly
`32 + 32 * count` and count is at most **65,536** (2,097,184 bytes).

| Offset | Type | Header field |
| --- | --- | --- |
| 0 | u32 | magic `0x464C334D` (UTF-8 bytes `M3LF`) |
| 4 | u16 | protocol version, exactly 1 |
| 6 | u16 | header width, exactly 32 |
| 8 | u16 | record width, exactly 32 |
| 10 | u16 | flags, must be 0 |
| 12 | u32 | record count (0..65,536) |
| 16 | u64 | sequence (informational; not yet replay protection) |
| 24 | u64 | reserved, must be 0 |

Each record is in **bottom-to-top Z order**:

| Relative offset | Type | Layer record |
| --- | --- | --- |
| 0 | u64 | opaque native handle; must be nonzero and unique |
| 8 | f32 | finite X offset |
| 12 | f32 | finite Y offset |
| 16 | f32 | finite opacity, 0..1 |
| 20 | u32 | visibility, exactly 0 or 1 |
| 24 | u64 | reserved, must be 0 |

The entire packet is checked in native C++ **before** the platform
compositor is called. Validation never dereferences opaque handles; the
existing Maya compositor remains responsible for checking their ownership
and liveness. The Java encoder makes the same checks with reusable primitive
open-address tables (no boxed layer IDs).

ABI and failure behavior:
- The two additive exports are
  `MayaComposition._nFramePacketVersion()` and
  `MayaComposition._nApplyPacked(composition, directBuffer, byteLength)`.
- A buffer must be **direct**. The explicit byte length must match the
  protocol header and be <= the JNI direct buffer's actual capacity.
  A caller passes the returned buffer at position zero; older/currently
  unsupported buffer slices/nonzero positions are not a supported contract.
- Success returns the original `_nApply` result; invalid packets return
  `-2`, JNI staging failures return `-3` and may leave a pending JVM
  exception. These codes are not Win32 HRESULT equivalents.
- A native packet ABI version does **not** imply support for DirectComposition,
  source-over groups, masks, 3D, Skija, device loss or any other OS feature.
- Never keep a borrowed direct-buffer pointer after the JNI call. The
  synchronously decoded native Frame is an owned, contiguous array snapshot.
- Java's Encoder reuses its direct memory for later frames; native code
  cannot retain a pointer into that memory. It is UI-thread confined.

## Rollout and compatibility

The Win32 `MayaNativeLayers` adapter is unchanged by default.
Only `-Dswt.maya.packedFrame=true` enables the optional packet route, and
only for 64..65,536 layers. Native packet-version negotiation is cached once
per backend class, not performed on every frame. A legacy Maya DLL/SO lacking
either new JNI export falls back to the original seven-symbol path (without
replacing the DLL); a standalone native-symbol test verifies this fallback.
Failure to find the protocol version disables the attempted route; the
seven old method descriptors are unchanged.

**Direct native transaction (follow-up):** the v1 JNI method descriptors
and on-wire packet remain unchanged. `MayaFramePacket.cc` decodes the
bounded direct-buffer bytes into a C++ `Frame` and now calls
`applyDecodedFrame` in the original `MayaComposition.cc` owner.
A single `applyNativeFrame` template consumes both the legacy JNI array
view and the packet view, without creating any new `jlongArray` or
`jfloatArray` in the packed path.

This is **not fully zero-copy**: the Java encoder copies primitive arrays
into a direct buffer, and the JNI ingress checks/decodes that packet into an
owned C++ vector before COM calls. That copy is essential for transaction
stability if Java mutates the originating frame. There is no asserted FPS
speedup or universal JNI crossover. The existing `CompositionFrameDelta`
and `CommittedLayerProperties` still decide when native Z order and
properties change. Critically, a pending successful pixel-only
`IDCompositionSurface::EndDraw` still causes `Commit`, even if the
layer properties and Z order are identical.

Both paths validate opaque native handles and composition ownership before
mutating the COM tree. On native exceptions/Commit failures they invalidate
cached property/order authority for replay; a successful transaction alone
publishes new cache truth. The native method, native export list, direct
frame layout, and feature-negotiation property remain unchanged.

**Release hold:** do not merge based solely on Linux JNI stub tests. Run
the exact new Win32 `MayaComposition.cc` and `MayaFramePacket.cc` in
MSVC JDK21/25, rebuild `maya.dll`, run real Maya screen pixels and
the `-Dswt.maya.packed.required=true` gate, compare legacy versus packet
Z/offset/opacity/visibility and pixel-only commits, then measure native
frame latency/copies/allocations before considering opt-in default.

## Verification and acceptance matrix

Portable work that can execute without a display:
1. GCC/Clang C++17 with address/undefined sanitizers: schema/length
   boundary tests, reserved bytes, duplicates, NaNs/Inf, opaque-handle
   bit preservation, maximum count, 10,000 seeded randomized passes.
2. Java 21/25 `javac --release 21 -Xlint:all -Werror`.
3. Real `-Xcheck:jni` Java-to-native smoke with direct buffers
   (including a 4,096-layer frame), using a **test-only** compositor stub.
4. Linux linking of new exports with the original non-Windows Maya stubs.
5. Windows JDK21/25 MSVC `/W4 /WX` source compilation.

Before shipping a new `maya.dll`, also require:
- Actual full Maya/Skija link under its pinned toolchain and release inputs;
  use the repository `native/build_maya.py` flow. Preserve all original
  symbols and include the two new ones in `maya.def` and `sources.txt`.
- Native Win32 `AllMayaGcTests` and real-screen pixels, `-Xcheck:jni`,
  no COM/texture leaks, device-loss/recovery, resize, disposal and nested
  reentrancy; verify X/Y, opacity, visibility, Z, and control/native overlays.
- A/B native frame measurements, JNI calls and byte copies, p50/p95/p99,
  heap/direct/native/GPU memory, and same-frame pixels against original
  `_nApply`. Do not invent a crossover or enable by default.
- Source/binary descriptor diff and recipe-first Synexia source seals;
  reconcile open work on MayaComposition.cc (#503 and #512) and blend
  (#507 and #511) rather than blind merge or copying other projects.

## Modernization roadmap: native owner per platform

- **Windows:** keep DirectComposition / D3D11 owner and optional Skija;
  merge validated packed frame commands into retained visuals and stage
  subrect pixel uploads with explicit GPU fences and budgeted texture pools.
- **Linux:** GTK4 GSK snapshot/render nodes when a GTK4 backend is available,
  with a separate GTK3/Cairo fallback. Do not invent native Wayland
  subsurface ordering, opacity or input semantics.
- **macOS / iOS:** separate main-thread CALayer/CAMetalLayer adapters with
  platform view lifetime, focus and accessibility contracts.
- **Android:** SurfaceControl/ASurfaceControl transactions only at supported
  API levels, ANativeWindow/HardwareBuffer lifetime and fences, measured
  emulator/device tests.
- **JDK21:** JNI stays stable. Optional FFM/Panama backend is additive and
  version-/feature-negotiated rather than a mandatory JDK21 public
  dependency. Preserve the exact same packet and compositor semantics.

All platform ports must preserve legacy SWT control Z order, focus, IME,
accessibility, editor overlays, DPI/color management and disposal. Use M3
array-backed/offset/pinned view structures; do not copy huge scene graphs
or replicate generic rendering managers in each widget.

Upstream references:
- JNI direct buffers: https://docs.oracle.com/en/java/javase/21/docs/specs/jni/functions.html
- Windows DirectComposition commits: https://learn.microsoft.com/en-us/windows/win32/api/dcomp/nf-dcomp-idcompositiondevice-commit
- GTK4 GSK: https://docs.gtk.org/gsk4/
- JDK21 FFM status: https://openjdk.org/jeps/442
