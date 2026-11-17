# Skija in SWT

The Java side of the Skija binding to Skia, absorbed into SWT for the Skia GC (`Eclipse SWT Render M3`,
`Eclipse SWT GPU M3`, fragment `org.eclipse.swt.gpu.m3`).

## Origin and licence

- HumbleUI Skija 0.116.3, Apache License 2.0 (`docs/licenses/swt-gpu-skija-Apache-2.0.txt`, packaging notice
  `docs/licenses/swt-gpu-skija-packaging-NOTICE.txt`, Skia's licence `docs/licenses/swt-gpu-skija-Skia-LICENSE.txt`).
- Sources: `io.github.humbleui:skija-shared:0.116.3:sources` (SHA-1 `aade0466dbfc76736ea8135a1fcd14f4722ca023`) and
  `io.github.humbleui:types:0.1.1:sources` (SHA-1 `8ff42ce2e4d9860c84fe4f1bc58030733ccf3fbd`), as published on Maven
  Central. The Java 9 versions of `impl/Cleanable`, `impl/ReferenceUtil` and `impl/ResourceFinder`, which the class jar
  carries under `META-INF/versions/9` and the sources jar omits, are from `shared/java9/impl/` of
  github.com/HumbleUI/Skija at tag `0.116.3` (`90e6bc12a3be9f84b88c077e0c23178499e21b7b`).
- The packages stay `io.github.humbleui.skija` and `io.github.humbleui.types`: the native library binds its JNI
  functions by class name, so the unchanged `skija-windows-x64-0.116.3.jar` (still embedded) loads against these
  classes.

## What is here

The 138 classes SWT needs and nothing more:

- the 57 classes the Skia GC's compiled code reaches, closed over Skija's own class dependencies (measured with
  `skija_closure.py`, com.synexia `docs/M3-SCALE/runs/20261117-skija-absorb/`);
- the classes their sources name, so that they compile;
- the 41 classes the native library looks up by name when it loads.

## Changes

78 files carry a first line `// Changed for SWT (Apache-2.0 section 4b): ...` naming the change:

- 64: the methods SWT does not reach removed (942 methods). A method stays when SWT's code or the m3gc tests reach it
  (com.synexia `docs/M3-SCALE/runs/20261117-skija-absorb/tools/skija_reach.py`, which also follows class
  initialisation, library callbacks and JDK callbacks), when it is a constructor, abstract or an override, or when a
  kept method calls it (`skija_prune_fixpoint.py` with `SkijaPrune.java`). The 97 m3gc tests link the same 77 native
  methods before and after;
- 33: unused `import lombok...;` lines removed (the published sources are already delomboked);
- `impl/Library`: `lombok.Lombok.sneakyThrow` replaced by a private helper that rethrows the same exception;
- `impl/ResourceFinder`: the Java 9 version, its `@SneakyThrows` written out the same way;
- `impl/Cleanable`, `impl/ReferenceUtil`: the Java 9 versions.

The other 60 files are unchanged.
