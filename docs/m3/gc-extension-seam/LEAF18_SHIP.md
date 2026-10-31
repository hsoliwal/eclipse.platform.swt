# Leaf 18 - ship M3 inside the product (one M3 fragment)

State (2026-10-31): the fragment, its loader, the API filters, the notice and the build are in place and gated
locally; the built jars start ControlExample in stock, m3 and gpu with no extra class path entry, and the existing
Eclipse JEE 2026-09 install starts its workbench on the fork's SWT plus the M3 fragment in m3 and gpu. Tycho, API
tools and fork CI did not run (artifacts missing locally, CI disabled by GitHub).

Base: `origin/m3/superset-20261030b` @ `43ee2395cea`. Branch `m3/ship-m3-fragment-20261031`.
Census: LEAF_QUEUE.md L18, root cause 5 (PR-23 PR-26 PR-31 PR-32 PR-33 PR-36 PR-40 SC21 SH08 SH21).
Recipe rows: `RECIPE_MEMORY.tsv` L18-01..L18-17. FOSS rows: `FOSS_REUSE_DECISION.tsv` `m3-ship:*`.

## Atoms

| atom | files |
|---|---|
| M3_FRAGMENT v1 | `bundles/org.eclipse.swt.m3/{META-INF/MANIFEST.MF,build.properties,pom.xml}` (git mv from `org.eclipse.swt.gpu.m3`); root `pom.xml` profile `m3-fragment` (`-Dm3.fragment`) |
| SKIJA_ONE_PROPERTY | `<skija.version>0.116.3</skija.version>` in the fragment pom is the only Skija version; types (0.1.1) follows from skija-shared's own POM; the nested jars are unversioned (`lib/skija-shared.jar`, `lib/skija-windows-x64.jar`, `lib/types.jar`), so 0.143.18 is this one line (plus the notice) |
| FRAGMENT_CLASSPATH v1 | `Eclipse SWT/common/.../internal/M3Fragment.java`; `RowBackends`, `ExternalCanvasHandler.registerFactory`, `PlaneBackend.load` load by name through it |
| API_FILTERS v1 | `binaries/*/.settings/.api_filters` (+34), `tools/m3_api_filters_merge.py`, `LEAF18_API_FILTERS.tsv` |
| ONE_BUILD v1 | `tools/build_m3_ship.sh` -> `swt.jar`, `swt-m3.jar`, `swt-host.jar`, receipt `LEAF18_BUILD_RECEIPT.tsv` |
| NOTICE v1 | `docs/licenses/swt-gpu-skija-packaging-NOTICE.txt` + `swt-gpu-skija-{ICU-LICENSE,HarfBuzz-COPYING,FreeType-FTL}.txt` |
| ARTIFACT_SMOKE v1 | `tools/m3_ship_artifact_smoke.sh`, `tools/m3_ship_smoke_summary.sh`, `LEAF18_SMOKE.tsv` |
| IDE_SMOKE v1 | `tools/m3_ship_ide_smoke.sh`, `tools/m3_ship_idesmoke/` (scratch smoke bundle), `LEAF18_IDE_SMOKE.tsv` |

Fragment layout (`swt-m3.jar`, 9,721,975 bytes): 42 classes (Render M3 39, GPU M3 3) at the root;
`lib/skija-shared.jar`, `lib/skija-windows-x64.jar`, `lib/types.jar` byte-identical to the 0.116.3 / 0.1.1 jars;
`META-INF/LICENSE`, `META-INF/NOTICE`, `META-INF/licenses/*` (7), `META-INF/m3/SKIJA.tsv` (coordinates + SHA-256);
manifest: `Fragment-Host: org.eclipse.swt;bundle-version="[3.138.0,4.0.0)"`, win32 x86_64 platform filter,
`Bundle-ClassPath: .,lib/skija-shared.jar,lib/skija-windows-x64.jar,lib/types.jar`.

How the fragment loads: under OSGi the fragment joins the host's class space and Equinox opens the nested jars from
`Bundle-ClassPath` (IDE smoke: Skija classes come from `<configuration>/org.eclipse.osgi/<id>/0/.cp/lib/skija-shared.jar`),
so `M3Fragment` keeps the SWT loader. On a plain class path without Skija it reads the fragment jar's own
`Bundle-ClassPath`, extracts the nested jars once to `${java.io.tmpdir}/swt-m3-<crc>` and loads the two M3 packages
child-first from the fragment jar (artifact smoke: `M3 fragment: ...swt-m3.jar Bundle-ClassPath 3 nested jars in
...\tmp\m3\swt-m3-f73a6067`). Any failure keeps the SWT loader and the callers fall back to stock as before.

## Gates

| gate | command | result |
|---|---|---|
| compile win32 (incl. M3Fragment) | `compile_platform.sh` copy, output under H:/m3-scratch/l18 | PASS sources=915 classes=1403 errors=0 |
| compile gtk | same | PASS sources=704 classes=1050 errors=0 |
| compile cocoa | same | PASS sources=843 classes=1159 errors=0 |
| ONE_BUILD | `bash tools/build_m3_ship.sh C:/m3-superset/swt-l18 H:/m3-scratch/l18/build` | PASS swt 915 sources / 0 errors, m3 36 / 0; natives 6/6 OID_MATCH; 3 nested jars BYTE_IDENTICAL (receipt) |
| Maven offline verify, M3 fragment alone | `MAVEN_BASEDIR=<fragment> JAVA_HOME=jdk-21 mvn -o verify -Dm3.skija.lib=... -Dm3.skija.types.jar=... -Dm3.swt.jar=dist/swt.jar -Dm3.buildDirectory=<scratch>` | PASS BUILD SUCCESS: 36 sources, 42 classes, 3 nested jars (same SHA-256), class list identical to build_m3_ship.sh; target deleted |
| API filter merge, fixed point | `m3_api_filters_merge.py` twice | PASS 34 added, replay 0 changed; 9 files well-formed XML |
| Tycho verify + API tools | - | NOT_RUN: `org.eclipse.tycho:tycho-build:5.0.4` (.mvn/extensions.xml) and `org.eclipse:eclipse-platform-parent:4.42.0-SNAPSHOT` absent from the local repository (tycho-build 4.0.12 only); p2 target and API baseline need network; fork CI disabled by GitHub |
| ARTIFACT_SMOKE | `m3_ship_artifact_smoke.sh stock|m3|gpu` | PASS 31/31 per mode (table below) |
| IDE_SMOKE | `m3_ship_ide_smoke.sh m3|gpu|baseline` | PASS start + 4 steps PAINTED per mode, 0 errors during the smoke; fidelity findings below |

## Artifact smoke (first real-app run on genuine r19 natives)

Class path `swt.jar;swt-m3.jar;<example classes>;<example resources>;<driver>`; no Skija jar, no classes dir, no
`java.library.path`. Natives extracted from swt.jar into the per-run `swt.library.path`: `swt-win32-4975r19.dll`
9f758f09eda88724, `swt-gdip` ad0b938c1c3c97d2, `swt-osversion` 648dda8943d36171 (+ `swt-wgl` 5a6069aab6461a86 in
gpu) = the LFS oids of HEAD. Earlier real-app runs used `H:/m3-scratch/gc-wiring/natives`, a renamed r10 set
(`swt-win32-4975r19.dll` == `swt-win32-4974r10.dll`, 1abad3ccaf3200ed).

| mode | rows | PASS | EXCEPTION | BLANK | HANG | tabs with M3CanvasHandler | tabs with drawn plane | Skija classes (from nested jar) | Render M3 classes |
|---|---|---|---|---|---|---|---|---|---|
| stock | 31 | 31 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| m3 | 31 | 31 | 0 | 0 | 0 | 4 | 26 | 105 (104 + 1 lambda) | 74 |
| gpu | 31 | 31 | 0 | 0 | 0 | 4 | 26 | 107 (106 + 1 lambda) | 75 |

Backend witness: m3 loads `M3RowCanvas`, never `GcRowCanvas`, no fallback line; gpu also loads `M3Gpu` and Skija
`DirectContext`. Per tab (`LEAF18_SMOKE.tsv`): every tab PASS in all three modes; M3 canvas handlers on Canvas,
CLabel x3, CTabFolder, StyledText; drawn planes on every Table/Tree/List (the driver prints the `MirroredCanvas`
wrapper the base now puts around the backend).

## IDE smoke

The install `C:/Users/hsoliwal/eclipse/jee-2026-09` was used read-only. Command line: `eclipse.exe --launcher.ini
<private ini> -vm "C:\Program Files\Java\jdk-21\bin\javaw.exe" -configuration <private> -data <private> -clean`. In
the private configuration `bundles.info` names `swt-host.jar`, `swt.jar`, `swt-m3.jar` and the smoke bundle in place
of the install's SWT 3.135; `user.home`, `osgi.user.area`, p2 data area, keyring, tmpdir and `swt.library.path` are
private; the p2 dropins reconciler is not started. Snapshot of the install tree and `~/.p2` (minus the pool), before
and after: identical (`4e27959fa145...`). The smoke bundle closes the intro, creates a project with Hello.java and
notes.txt, shows Package Explorer and Outline, opens the Java editor and a text editor, switches tabs, and after each
step prints the workbench shell (Control.print) to PNG.

| mode | steps PAINTED | M3 canvas handlers | drawn Trees | CTabFolders | errors during smoke | workspace .log |
|---|---|---|---|---|---|---|
| m3 | 4/4 | 24-34 | 4 x M3RowCanvas(raster) | 4 | 0 | 20 entries, 11 errors |
| gpu | 4/4 | 24-34 | 4 x M3RowCanvas(gpu) | 4 | 0 | 20 entries, 11 errors |
| baseline (install SWT 3.135) | 4/4 | 0 | 0 | 4 | 0 | 20 entries, 11 errors |

The 11 errors are the same plugins and severities in all three runs (bundle resolution of wst.server.preview,
wst.wsi, mockito, lemminx; the MPC early startup NPE) and none names SWT: they belong to the install. Class-load log:
Skija from the fragment's nested jar via Equinox, Render M3 classes from `swt-m3.jar`.

Findings for the owning leaves (not fixed here): in m3 and gpu the Java editor (StyledText through M3) paints white
boxes behind text runs and drops punctuation glyphs (`{ ( ) [ ] ;`), and the drawn Trees (Package Explorer, Outline,
Project Explorer) paint white text backgrounds in the dark theme and show no item images; the baseline shows neither.
Screenshots: `H:/m3-scratch/l18/ide/out/{m3,gpu,baseline}/*.png`.

## Findings

- The interim scope keeps Skija 0.116.3; L12 moves `skija.version` to 0.143.18 (types 0.2.0 follows).
- Native inventory of `skija.dll` (sha256 3ff80f39...) by strings, revisions from Skia chrome/m116 DEPS: Skia,
  ICU 73, HarfBuzz, FreeType, zlib 1.2.11, libpng, libjpeg-turbo 2.1.4, libwebp, Expat, Wuffs. The zlib, libpng,
  libjpeg-turbo, libwebp and Expat licence texts are still to be added (named in the notice).
- Push from this shallow blobless worktree: `GIT_NO_LAZY_FETCH=1` makes `pack-objects` fail on a missing base blob
  and the remote hangs up; allowing that one lazy fetch fixes it (L18-16).
- Eclipse on a hidden desktop needs `windows.defender.startup.check.skip=true` in `org.eclipse.ui.prefs`, or the
  Defender exclusion dialog blocks the UI thread (L18-15).

## NOT_RUN

Tycho verify and API tools; fork CI (disabled by GitHub); `compile_platform` for the other 6 fragments
(aarch64/ppc64le/riscv64/loongarch64 share the gtk/win32 source sets checked above); IDE input/IME/caret
(hidden desktop has no input); publishing Maven/p2 artifacts (PR-36 PUBLISH atom).
