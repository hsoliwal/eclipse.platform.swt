# Viewport contract repair verification

Base: `3b2648ae07dff6241fb050a2ffdb62a0d3b26e83`. This is a correctness repair to the existing viewport work, not completion of the all-widget rewrite.

## Checks

| Check | Result |
| --- | --- |
| OpenRewrite exact-preimage replay, fixed point, drift refusal, missing-source refusal | 4 tests passed (standalone proof project reusing canonical Synexia helpers) |
| Cocoa Java production sources | 622 sources compile with Java 21 |
| GTK Java production sources | 482 sources compile with Java 21 |
| Win32 Java production sources, including merged sparse Tree changes | 661 sources compile with Java 21 |
| GTK matching native library | Existing C sources built using GTK 3, warnings as errors |
| `Test_ViewportRewriteContracts` on GTK/Xvfb | 6 tests passed, 0 skipped |
| Cocoa and Win32 native runtime | Not run on this Linux host |
| Full SWT Maven reactor | Failed dependency resolution: main bundle 3.136.0 requires a matching platform fragment; fragments remain 3.135.100 |
| Full-size GTK screenshot harness | Timed out after 50 seconds during setup; zero captures produced; stress gate failed |
| Full Synexia Maven reactor | Not run; standalone recipe proof does not substitute for this gate |

GTK constructor regressions were observed before repair: sparse storage was used during orientation setup before creation, and viewport state field initializers ran after superclass widget creation. Both paths are covered by native Table/Tree creation tests. Session-manager DBus warnings and a GTK thread-lock warning were emitted but did not fail the six regressions.

## Changes and custody

`M3SwtViewportContractHardeningRecipe` replays 13 exact Java postimages. Existing targets must match their preimage or already-applied postimage SHA-256; the new regression test requires absent-before admission. `M3SwtViewportBundleVersionRecipe` separately repairs the main bundle Maven/OSGi version mismatch, and must be invoked at `bundles/org.eclipse.swt`, where its target is `pom.xml`.

No generated JNI files or native binaries are changed. Native binaries used for the GTK runtime proof were built outside the repository from its existing source.

## Widget and platform coverage

Compilation covers every Java widget source selected by the repository's three platform classpaths. Compilation alone establishes neither behavioral compatibility nor viewport integration.

| Widget family | Cocoa | GTK | Win32 |
| --- | --- | --- | --- |
| Table / TableItem / TableColumn | Sparse-call and explicit-pin repairs; compiled; native gate pending | Sparse-call, creation-order and explicit-pin repairs; compiled; native regressions pass | Sparse text direction and explicit-pin repairs; compiled; native gate pending |
| Tree / TreeItem / TreeColumn | Width-call repair and shared projection; compiled; native gate pending | Adjustment and creation-order repair and shared projection; native regressions pass | Existing merged sparse topology plus shared projection; compiled; native gate pending |
| Canvas / Composite / Scrollable / Shell / ScrolledComposite | Existing code compiled; complete viewport behavior unproven | Existing code compiled; complete viewport behavior unproven | Existing code compiled; complete viewport behavior unproven |
| List / Combo / Text / StyledText and custom controls | No all-widget rewrite established by this change | No all-widget rewrite established by this change | No all-widget rewrite established by this change |
| Button / Label / Link / Group / DateTime / Spinner / Scale / Slider / ProgressBar / Sash | Existing code compiled; no new viewport integration | Existing code compiled; no new viewport integration | Existing code compiled; no new viewport integration |
| Menus / toolbars / tabs / expanders / cool bars / tray / task bar / dialogs / caret / IME | Platform-provided implementations compiled; behavioral gate pending | Platform-provided implementations compiled; behavioral gate pending | Platform-provided implementations compiled; behavioral gate pending |

Remaining completion gates include matching all native platform fragments to the main bundle version, platform-native compatibility suites, bounded-residency and screenshot stress proof, integration across the remaining widget families, and full Synexia validation. None are waived by these repairs.
