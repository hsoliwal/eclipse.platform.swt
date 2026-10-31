# Leaf 18 - ship M3 inside the product (one M3 fragment)

State: **HOLD** (2026-10-31). The source atoms are in place; no artifact was built and no smoke ran, because H: fell
to 729-753 MB free before the first build (lead's rule: stop below 1 GB on H: or C:). Own usage on H: was 934 KB.

Base: `origin/m3/superset-20261030b` @ `43ee2395cea`. Branch `m3/ship-m3-fragment-20261031`.
Census: LEAF_QUEUE.md L18, root cause 5; closes PR-23 PR-26 PR-31 PR-32 PR-33 PR-36 PR-40 SC21 SH08 SH21 once the
gates below pass. Recipe rows: `RECIPE_MEMORY.tsv` L18-01..L18-13.

## Atoms

| atom | files | state |
|---|---|---|
| M3_FRAGMENT v1 | `bundles/org.eclipse.swt.m3/{META-INF/MANIFEST.MF,build.properties,pom.xml}` (git mv from `org.eclipse.swt.gpu.m3`), root `pom.xml` profile `m3-fragment` | written |
| SKIJA_ONE_PROPERTY | `<skija.version>0.116.3</skija.version>` in the fragment pom; types version from skija-shared's own POM (0.1.1); nested jars unversioned (`lib/skija-shared.jar`, `lib/skija-windows-x64.jar`, `lib/types.jar`) | written |
| FRAGMENT_CLASSPATH v1 | `Eclipse SWT/common/.../internal/M3Fragment.java`; `RowBackends`, `ExternalCanvasHandler.registerFactory`, `PlaneBackend.load` call `M3Fragment.forName` | written, not compiled |
| API_FILTERS v1 | `binaries/*/.settings/.api_filters` (+34 filters), `tools/m3_api_filters_merge.py`, receipt `LEAF18_API_FILTERS.tsv` | PASS (fixed point: second run changes 0 fragments); API tools NOT_RUN |
| ONE_BUILD v1 | `tools/build_m3_ship.sh` -> `swt.jar`, `swt-m3.jar`, `swt-host.jar`, `BUILD_RECEIPT.tsv` | HOLD (disk gate refused) |
| NOTICE v1 | `docs/licenses/swt-gpu-skija-packaging-NOTICE.txt`, `swt-gpu-skija-{ICU-LICENSE,HarfBuzz-COPYING,FreeType-FTL}.txt` | written |

How the fragment loads on a plain class path: `M3Fragment` keeps the SWT class loader when it already sees Skija
(OSGi attaches the fragment and its `Bundle-ClassPath`; an explicit `-cp` with Skija behaves the same). Otherwise it
reads the fragment jar's own `Bundle-ClassPath`, extracts the nested jars once into
`${java.io.tmpdir}/swt-m3-<crc>` and loads `org.eclipse.swt.internal.render.m3.*` and `...gpu.m3.*` child-first
from the fragment jar; host packages always come from the SWT loader. Any failure keeps the SWT loader, so the
callers fall back to stock exactly as before.

## Findings

- Natives: `H:/m3-scratch/gc-wiring/natives` is a renamed r10 set (`swt-win32-4975r19.dll` has the same SHA-256
  as `swt-win32-4974r10.dll`, 1abad3ccaf3200ed; awk/wgl equal the r10 LFS oids). `build_m3_ship.sh` stages the
  worktree's LFS objects instead and refuses any dll whose content differs from HEAD's pointer oid (6/6 OID_MATCH).
- Native inventory of `skija.dll` (0.116.3, sha256 3ff80f39...) by strings, revisions from Skia chrome/m116 DEPS:
  Skia, ICU 73, HarfBuzz, FreeType, zlib 1.2.11, libpng, libjpeg-turbo 2.1.4, libwebp, Expat, Wuffs.
- Tycho: the fork needs `org.eclipse.tycho:tycho-build:5.0.4` (`.mvn/extensions.xml`) and
  `org.eclipse:eclipse-platform-parent:4.42.0-SNAPSHOT`; the local repository has tycho-build 4.0.12 only and no
  parent pom, so the Tycho verify and API tools cannot run offline. Fork CI is disabled by GitHub.

## Gates

| gate | command | result |
|---|---|---|
| API filter merge, fixed point | `python -I tools/m3_api_filters_merge.py binaries <receipt> <donor dirs> --template=gtk=org.eclipse.swt.gtk.linux.x86_64` (twice) | PASS: 9 fragments, 34 added; replay 0 changed, 34 present |
| XML well-formed (9 files) | `xml.dom.minidom.parse` | PASS |
| ONE_BUILD | `bash tools/build_m3_ship.sh C:/m3-superset/swt-l18 H:/m3-scratch/l18/build` | HOLD: `FAIL disk gate: /h below 1 GB free (0 GB)` |
| Maven offline verify of the M3 fragment | `mvn -o verify -Dm3.skija.lib=... -Dm3.skija.types.jar=... -Dm3.swt.jar=...` (plugins compiler 3.14.1, dependency 3.8.1, jar 3.4.2, build-helper 3.6.1 are local) | NOT_RUN (HOLD) |
| Tycho verify + API tools | - | NOT_RUN: tycho-build 5.0.4 and eclipse-platform-parent 4.42.0-SNAPSHOT absent locally; fork CI disabled |
| compile_platform win32/gtk/cocoa (M3Fragment is in common) | `C:/m3-superset/tools/compile_platform.sh` | NOT_RUN (HOLD) |
| ARTIFACT_SMOKE ControlExample m3/gpu, `-cp swt.jar;swt-m3.jar;<example>;<driver>` | RealAppDriver (H:/m3-scratch/realapp/driver) | NOT_RUN (HOLD) |
| IDE_SMOKE | Eclipse JEE 2026-09 exists at `C:/Users/hsoliwal/eclipse/jee-2026-09` (p2 pool `~/.p2/pool`), launch with a private configuration and bundles.info naming the fork host, win32 and M3 fragments | NOT_RUN (HOLD) |

## Resume

1. Check `df -h /c /h` (both above 1 GB).
2. `bash docs/m3/gc-extension-seam/tools/build_m3_ship.sh C:/m3-superset/swt-l18 H:/m3-scratch/l18/build`.
3. Maven offline verify of `bundles/org.eclipse.swt.m3` with the provisioned profile, `MAVEN_BASEDIR` set to the
   fragment so the root `.mvn/extensions.xml` (tycho-build 5.0.4) is not loaded; delete its target afterwards.
4. Artifact smoke with RealAppDriver in stock, m3, gpu (per-run `-Djava.io.tmpdir` and `-Dswt.library.path` under
   H:/m3-scratch/l18 so the natives come out of swt.jar, not ~/.swt); record the mode x tab table here.
5. IDE smoke as above; then compile_platform for gtk/cocoa.
