# Maya native surface budget — opt-in logical BGRA quota

Status: **implemented as a source candidate; Win32 GPU qualification pending**.

## Scope and authority

The original `LayeredViewport → NativeLayers → MayaNativeLayers →
MayaComposition` pipeline is preserved, with the original D3D11 and
DirectComposition visual/texture owner. No new compositor, child Control
peer, Java event thread, packed-frame format, renderer or virtual-surface
engine is introduced.

The native `MayaComposition` owns one allocation-free
`swt_maya::SurfaceBudget` (pure C++17) and each native `Layer`
records its original logical BGRA reservation count. The nominal
accounted size is `width × height × 4` for
`DXGI_FORMAT_B8G8R8A8_UNORM`. All arithmetic is overflow-checked
against signed 64-bit Java `long` capacity.

This counts **logical image bytes only**. It does not measure
actual GPU residency, tiling/alignment, driver backing allocations,
COM overhead, VRAM, texture-pool size or physical DMA bandwidth.

## Fail-closed native reservations

- Default quota `0` means unlimited, preserving preexisting behavior.
- A positive quota limits the **aggregate live logical image bytes per
  composition**, not a machine-wide GPU budget.
- `_nAddLayer` reserves logical bytes **before** calling
  `IDCompositionDevice::CreateSurface`. On refusal it returns zero
  without creating any COM image surface; SWT retains its painted fallback.
- On COM failure, registry or vector allocation failure, removal or
  composition disposal, the original `destroy(Layer*)` releases the
  layer's reservation exactly once.
- A cap cannot shrink below currently reserved bytes. A rejected
  configuration leaves the original limit and live visuals unchanged.
- Setting the quota back to zero restores unlimited admission.
- Accepted-reservation counters include subsequently rolled-back COM
  failures; they are not counts of successfully presented GPU layers.
- All native calls stay on the existing SWT Display owner thread;
  typed never-reused native handle tokens remain the authority.

## Optional JVM configuration

The existing Win32 Maya backend accepts a VM property **at backend class
initialization**:

```text
-Dswt.maya.surfaceBudgetBytes=67108864
```

This requests a 64 MiB logical BGRA quota for each composition created
by that backend. An absent or zero value preserves previous behavior.
Malformed/negative values and native DLLs without the new setter
fail closed to SWT software painting rather than silently ignoring
a user-requested cap.

## Additive internal JNI (no original symbol changes)

```java
int _nSetSurfaceBudget(long composition, long maxLogicalBytes);
int _nReadSurfaceBudget(long composition, long[] output);

// Optional Java helpers: -1 for old native libraries; null if no snapshot.
int result = MayaComposition.setSurfaceBudget(composition, 64L * 1024 * 1024);
long[] state = MayaComposition.readSurfaceBudget(composition);
```

A snapshot writes exactly six nonnegative signed-long-safe values into
a destination array of at least six elements; any trailing slots remain
untouched. The version-1 schema is:

| Slot | Java field | Meaning |
| --- | --- | --- |
| 0 | `BUDGET_VERSION` | Always 1 |
| 1 | `BUDGET_LIMIT_BYTES` | Current cap, 0 = unlimited |
| 2 | `BUDGET_RESERVED_BYTES` | Current logical BGRA bytes |
| 3 | `BUDGET_PEAK_BYTES` | Peak logical bytes of this owner |
| 4 | `BUDGET_DENIALS` | Refused reservations |
| 5 | `BUDGET_ACCEPTED` | Admitted reservations before COM |

The previously merged 16-slot native telemetry ABI is unchanged.

## Executed proof and pending Windows gates

Locally executed against the exact new GitHub C++ header and test source:
GCC and Clang strict C++17 under AddressSanitizer/UBSan passed a
100,000-event independent reservation/release differential oracle,
bounds checks, quota resizing, overflow refusal and resource-release
checks.

A separate JNI test library built under GCC and Clang uses the
**production C++ header**. Java 21 strict compilation and
`-Xcheck:jni` passed six-field snapshots, failed quotas,
prefix-only output, and an intentionally older native library lacking
the new exports. The local Java smoke used a compatible Maya ABI
mirror; hosted CI compiles the actual production Java class.

The original Windows Maya screenshot JUnit now tests quotas and real
layer pixels, but it **has not been run on actual Win32 graphics**.
For the native release gate, set
`-Dswt.maya.surfaceBudget.required=true`: this must fail rather than
skip when the DLL lacks the quota JNI ABI.

**Still mandatory:** execute the hosted GCC/Clang and Win32 MSVC JDK21/25
source builds, full pinned Maya/Skija DLL link and `maya.def` export
closure, headful `AllMayaGcTests` under `-Xcheck:jni`, resource
teardown and device-loss/recreation, multi-Display isolation,
COM/device allocation failure probes, frame p50/p95/p99 and separate
actual GPU-memory measurements. Reconcile current SWT #563 native scene
work and #564 native scratch tests; neither is overwritten by this PR.

## Authoritative references

- https://learn.microsoft.com/en-us/windows/win32/api/dcomp/nf-dcomp-idcompositiondevice-createsurface
- https://learn.microsoft.com/en-us/windows/win32/api/dcomp/nf-dcomp-idcompositiondevice-createvirtualsurface

DirectComposition virtual surfaces can realize and trim smaller parts
of a large logical surface, but they require a **separate tested
implementation**. This quota patch deliberately does not claim to
provide surface caching, eviction or GPU paging.
