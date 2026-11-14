# org.eclipse.swt.tests in the Eclipse SDK build: access restrictions (2026-11-14)

## Failure

`org.eclipse.swt.tests` does not compile in the Eclipse SDK build on JDK 21 (Tycho 5.0.4, ECJ 3.47, G6 run of
2026-11-10): 3,041 `Access restriction: ... is not accessible` errors in 52 test sources, 3,040 of them on SWT
packages. Tycho's ECJ stops at 100 problems per file; without that cap the same sources carry 5,241.

## Root cause

The tests use five fork packages that no SWT MANIFEST exports. PDE/Tycho build the access rules of the SWT
classpath entry from `Export-Package` of the host (`bundles/org.eclipse.swt`) plus the platform fragment
(`Eclipse-ExtensibleAPI: true`); a package that is not exported is forbidden (error).

| Package | Test sources that name it |
|---|---|
| `org.eclipse.swt.internal.canvasext` | 4 |
| `org.eclipse.swt.internal.gpu` | 1 |
| `org.eclipse.swt.internal.m3.collections` | 5 |
| `org.eclipse.swt.internal.render` | 27 |
| `org.eclipse.swt.m3` | 15 |

All five live in `Eclipse SWT/common` (plus `win32`, `gtk`, `cocoa` parts of `canvasext` and `render`), so the
export belongs in the host MANIFEST.

## Change

`bundles/org.eclipse.swt/META-INF/MANIFEST.MF` exports the five packages with
`x-friends:="org.eclipse.swt.tests"`. They are not public API: the test bundle gets access, every other bundle
gets a discouraged-access warning. No plain export.

`x-internal:=true` was the alternative for packages other fork bundles need. None does: 0 references to the five
packages in eclipse.platform.ui (master 32aa77a), Nebula (a007ac825), and the G6 aggregator submodules
eclipse.platform, eclipse.platform.common, eclipse.jdt.core, eclipse.jdt.ui, eclipse.jdt.debug, eclipse.pde and
equinox.p2 (`*.java` and `MANIFEST.MF`).

Applied with `tools/swt_friend_exports.py` (package-name order, the file's line endings kept; a second run adds
nothing).

## Proof

`tools/ecj_access_gate.sh` compiles the test sources with ECJ under the access rules, compiler preferences
(`examples/.settings_shared/org.eclipse.jdt.core.prefs`) and options (`-enableJavadoc -proceedOnError`) of the
Tycho run, read from its `target/compilelogs/@dot.xml`. Files below Tycho's cap of 100 problems report the same
count in both (for example RenderSwitchBenchmark 40/40, Test_M3Collation 48/48, TableHiddenScripts 7/7).

ECJ 3.45.0 on JDK 21.0.9, SWT win32 classes and test sources of 6060fce913 (master 9025c63805 differs by one
docs file), 424 sources:

| Run | Errors | Forbidden access, SWT | Forbidden access, JUnit 4 | Unused import | Unresolved (m3gc) | Evidence |
|---|---|---|---|---|---|---|
| master MANIFEST | 6,176 | 5,241 | 1 | 16 | 918 | `gates/ecj-master.*` |
| this change | 935 | **0** | 1 | 16 | 918 | `gates/ecj-pr-friend-exports.*` |
| this change + the three other fixes | 918 | 0 | 0 | 0 | 918 | `gates/ecj-four-prs.summary.tsv` |
| the same, M3 render tree and Skija on the classpath | **0** | 0 | 0 | 0 | 0 | `gates/ecj-four-prs-with-m3-render-tree.summary.tsv` |

Discouraged-access warnings stay at 4,079 (`x-internal` packages such as `org.eclipse.swt.internal.laf`); they
are warnings in the SDK build too.

`api_loss_audit`: java_files=0 lost=0 gained=0 (`gates/api-loss.tsv`).

## What still stops the module in the SDK build

Three more root causes, each with its own change:

1. `Test_ViewportAffineBounds` imports the JUnit 4 `org.junit.Test`; the bundle requires JUnit 5 only
   (1 forbidden access on `org.junit` 4.13.2).
2. 17 unused imports in 8 test sources; the shared compiler preferences make `unusedImport` an error.
3. The m3gc tests need `org.eclipse.swt.internal.render.m3.*` ("Eclipse SWT Render M3/win32"). No bundle of the
   reactor compiles that source folder (the win32 fragment's `build.properties` does not list it and
   `bundles/org.eclipse.swt.gpu.m3` compiles three GPU sources only), so the m3gc sources do not resolve
   (918 errors here, 466 under Tycho's cap). Open: either a reactor bundle ships the render tree, or the Tycho
   compile of the test bundle leaves `**/m3gc/**` out until one does.

## Gates on master (6060fce913)

JDK 21.0.9, `javac --release 21`, fork natives 4975r19, invisible test desktop.

| Gate | Result | Evidence |
|---|---|---|
| win32 x86_64 compile | 893 sources, 1,373 classes, 0 errors | `gates/javac-win32.summary.tsv` |
| GTK linux x86_64 compile | 682 sources, 1,020 classes, 0 errors | `gates/javac-gtk.summary.tsv` |
| Cocoa macosx aarch64 compile | 821 sources, 1,129 classes, 0 errors | `gates/javac-cocoa.summary.tsv` |
| Test compile (javac) | 424 sources, 0 errors | `gates/javac-tests.summary.tsv` |
| Headless JUnit | 190 found, 187 passed, 0 failed, 3 aborted (GTK-only) | `gates/junit-headless.*` |
| m3gc JUnit | 97 found, 97 passed | `gates/junit-m3gc.*` |
| Display JUnit (AllTests without AllBrowserTests) | 6,266 found, 6,059 passed, 8 failed, 176 aborted by assumptions, 6 skipped; 0 real failures | `gates/junit-display.*`, `gates/DISPLAY_FAILURES.tsv` |

The 8 Display failures: 6 ENV_INPUT_HIDDEN_DESKTOP (the same six tests as the 2026-11-10 baseline), 1 ENV_CLIPBOARD
(dnd transfer, shared OS clipboard), 1 FLAKY_NOT_REPRODUCED (BusyIndicator.testShowWhileWithFuture, 11.7 s on a loaded
machine; 3 isolated reruns pass, `gates/junit-display-busy-rerun{1,2,3}.*`). The suite leak-check container fails as on
the baseline. Rules: `tools/classify_display_ki.py`. `-Xcheck:jni` diagnostics: 0 in all three JVMs.

## Reproduce

```
bash tools/ecj_access_gate.sh <worktree> <swt-win32-classes> <evidence-dir> <label>
HOST_MF=<other host MANIFEST.MF> bash tools/ecj_access_gate.sh ...     # before/after on the same sources
python tools/swt_friend_exports.py bundles/org.eclipse.swt/META-INF/MANIFEST.MF
```
