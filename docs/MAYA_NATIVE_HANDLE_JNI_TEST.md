# Maya native token JNI lifetime regression

This test-only addition exercises the exact production
`MayaNativeHandleRegistry.h` issued by SWT PR #543 through Java 21 JNI.
It does **not** introduce a replacement Maya graphics engine, a shipped
JNI export or a second native handle registry.

## Test design

The portable C++ smoke library (`tests/MayaNativeHandleJniStub.cc`)
compiles against the existing production token registry header.
It exports six methods **only in the standalone test shared library**.
It does not join `maya.def`, `sources.txt`, `maya.dll`,
`NativeLayers` or any public SWT API.

Two typed registry instances use a fixed aligned C++ memory slot each.
The slot deliberately reuses exactly the same memory address after a
native handle is retired; each later issuance must return a different
monotonically increasing positive `jlong`. Java checks:

- Native handles for compositions and layers cannot be confused.
- Unknown, zero, stale and wrong-kind handles cannot resolve.
- A stale Java token cannot retire the new occupant of reused native memory.
- 10,000 JNI layer retirement/reissuance cycles remain ordered and unique.
- Composition retirement/recreation also rejects its previous token.

This tests native identity through the real Java/JNI calling convention
under `java -Xcheck:jni`, rather than C++-only assertions.
The production Win32 COM owner is not linked in this smoke library.

## Exact executed local proof

The committed test source matches the locally tested GitHub blobs:

- `MayaNativeHandleJniStub.cc`:
  `9433d111bdb50682be140361bd4d724c04b8b3c6`
- `MayaNativeHandleJniSmoke.java`:
  `294ded6984e56608a0c741e4e7260cb2073d4963`

The existing production header blob is
`6dbba166468293a6721cddd3b5e88afe64410142`.

Local GCC and Clang C++17 `-O2 -Wall -Wextra -Werror -pedantic`
built the smoke library. Java 21 `javac --release 21 -Xlint:all -Werror`
and `javac -h` completed, and both libraries passed
`java -Xcheck:jni` with 10,000 stale token cycles.
No Java native warnings occurred.

The new workflow `.github/workflows/maya-native-handle-jni.yml`
runs the same test on GitHub Actions (GCC/Clang, JDK21/25).
**Hosted results are not yet established merely by creating the workflow.**

## Remaining native release gates

This smoke cannot certify the actual DirectComposition DLL, COM
visual resources, independent window/layer disposal or pixel output.
Before promoting the underlying registry: run the Win32/MSVC JDK21/25
source build and full pinned `maya.dll` link, the original
`AllMayaGcTests` with `-Xcheck:jni`, native screenshot and
device-loss/resize tests, GPU memory/COM handle accounting, and
old-versus-new handle lookup/creation cost comparison.

The test is an additive CI safety net, not a performance improvement,
not a native source replacement, and not a cross-thread authorization.
Existing SWT UI-thread ownership and Java API behavior are unchanged.
