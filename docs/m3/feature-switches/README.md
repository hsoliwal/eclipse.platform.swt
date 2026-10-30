<!-- SPDX-License-Identifier: EPL-2.0 OR Apache-2.0 -->
<!-- Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others -->
# Feature switches: the M3Features registry and the first switch, frame-overlay

Branch `m3/feature-switches-20261030` (base `origin/master` `d5f6048f398`). User directive 2026-10-30 (four times): "the
web UI has outdone the native one ... we must have all the features selectable and configurable". The law and the ledger are
in com.synexia PR #14213 (`docs/M3-SCALE/runs/20261030-web-feature-switches/`: `FEATURE_SWITCH_MATRIX.tsv`, 178 rows,
`FEATURE_SWITCH_RULES.md`). This leaf ships the registry and one switch; every other row stays a ledger row until its leaf.

## What is here

| File | Purpose |
|---|---|
| `FEATURE_SWITCH_MATRIX.tsv` | committed copy of the ledger (178 rows of PR #14213) plus row 179, `frame-overlay` (bit 178), in the TSV's 13 columns; append-only: the bit index of a feature is its row order |
| `SHIPPED.tsv` | the features whose seam is shipped (only these switch; a property or `set` on another row is refused or ignored) |
| `FEATURES.tsv` | generated view: bit, feature id, constant, concept, family, state, default, native owner, enhanced API id, alias, shipped, keys |
| `tools/gen_features.py` | TFG: the ledger and SHIPPED.tsv -> `M3FeatureIds.java`, `M3FeatureTable.java`, `FEATURES.tsv`; `python -I`, fixed point (a second run prints `UNCHANGED` three times; `--check` exits 1 when an output would change) |
| `tools/test_m3features.sh` | headless gate (no Display, no Device, no natives): the m3features tests against the win32 classes of `compile_platform.sh` |
| `tools/differential_off.sh` | off == stock gate of a drawn-plane switch: the recorded Tree differential in a drawn mode with `-Dswt.widget.feature.<id>=<value>` on the hidden desktop (`ui_lock.sh`) |
| `FOSS_REUSE_DECISION.tsv` | donors: OpenCLAW (no fit), Togglz/FF4J (pattern only: an object per feature, a map and a dependency), WidgetMode/SwtLog (reused), OpenRewrite (gap) |

Code (`bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/internal/render/`):

- `M3FeatureIds` (generated): one `int` constant per row, its value the bit index (`CARD = 0` ... `FRAME_OVERLAY = 178`).
- `M3FeatureTable` (generated): the rows the registry reads once (id, concept, family, state, default, native owner, enhanced
  API id, alias, shipped).
- `M3Features`: the registry. `isOn (Widget, id)`, `set (Widget, id, on)`, `ids ()`, `id (String)`, `describe (id)`, `table ()`,
  `key (id)`, `key (Widget, id)`, `alias (id)`, `resolve (Class)`/`resolve (String...)`, and the bitset kernels
  `isOn (long [], id)` (one word read, one bit test; `//FLLDCIM: HPCPOP`) and `set (long [], long [], id, on)`.
- `Widget.m3Features` (win32, gtk, cocoa; `@noreference`, like `nativeZoom`): the instance bitset, `null` while every switch
  is at its class default (the stock widget allocates nothing).

## Configuration

| Precedence | Key | Scope |
|---|---|---|
| 1 | `M3Features.set (widget, M3Features.<ID>, on)` | one widget instance |
| 2 | `-Dswt.widget.feature.<Widget>.<id>=true\|false` | one widget class; the most specific class of the instance first (`Tree`, then `Composite`, `Scrollable`, `Control`, `Widget`) |
| 3 | `-Dswt.widget.feature.<id>=true\|false` | every widget |
| 4 | the row's alias (`-Dswt.drawn.overlay=true` for `frame-overlay`) | the property the feature was hard-wired to before it had a switch, kept |
| 5 | the ledger default | `on` for every `NATIVE_HAS` row (read-only), `off` for every other row |

Properties are read once per widget class, when its first widget asks (`ClassValue`; JVM-wide, so every Display sees the same
class defaults, as `WidgetMode.of` reads once per class). Only `true` (any case, trimmed) switches on. Refused and logged at
`info` through `SwtLog` (`org.eclipse.swt.internal.render.M3Features`), never thrown at startup: a value that would switch a
`NATIVE_HAS` row off, a value for a row whose seam is not shipped, a key that names no feature. `set` throws instead:
`IllegalArgumentException` (`SWT.ERROR_INVALID_ARGUMENT`) for a `NATIVE_HAS` row off, `SWTError`
(`SWT.ERROR_NOT_IMPLEMENTED`) for an unshipped row, `IllegalArgumentException` (`SWT.ERROR_INVALID_RANGE`) for an id outside
`[0, COUNT)`. `-Dswt.widget.feature.list=true` logs the registry table once, when `M3Features` initializes.

## The first switch: frame-overlay

`DrawnPlane.java` (win32 drawn planes of Tree, Table and List in the `custom`, `m3` and `gpu` modes) had
`static final boolean OVERLAY = Boolean.getBoolean ("swt.drawn.overlay")`, read in three places. The field is gone; the three
places read `M3Features.isOn (control, M3Features.FRAME_OVERLAY)`: the 500 ms repaint timer in the constructor, the `retain`
decision of the frame (an overlay frame keeps no retained rows) and the overlay paint after the rows. Unset, the value is
`false` exactly as before; `-Dswt.drawn.overlay=true` still turns it on (alias); `-Dswt.widget.feature.frame-overlay=false`
beats the alias. The constructor reads the switch once for the timer: an instance switched on later with `set` paints the
overlay on every frame but repaints it only on damage. The change was made by the CPU twin of `M3FeatureSwitchRecipe`
(com.synexia crate `m3-feature-switch-20261030`, `tools/apply_feature_switch.py`), whose pinned postimage is this file.

## Gates (2026-10-30)

| Gate | Result |
|---|---|
| `python -I tools/gen_features.py` | 179 features (72 `NATIVE_HAS` on, 1 shipped, 1 alias); second run `UNCHANGED` x3; `--check` rc 0 |
| `compile_platform.sh C:/m3-superset/swt-features win32\|gtk\|cocoa` (Windows paths) | **win32 891 / 0 errors, gtk 681 / 0, cocoa 820 / 0** (base `d5f6048f398`: 888, 678, 817 sources; +3 common files) |
| `tools/test_m3features.sh` (headless, no Display) | javac 1 source / 0 errors; **12 tests found, 12 passed, 0 failed** (defaults, global key, widget override, most specific class, class chain, alias, `NATIVE_HAS` not switchable, unshipped ignored, set/isOn with null-at-defaults, refusals, discovery, generated table == `FEATURES.tsv`) |
| off == stock: `tools/differential_off.sh frame-overlay false custom` | `Test_org_eclipse_swt_widgets_Tree_ViewportDifferential` against the official SWT 3.135.0 recording made in the same run (official 6/6), `-Dswt.widget.mode=custom -Dswt.widget.feature.frame-overlay=false`, hidden desktop: **6 found, 6 passed, 0 failed**. Pixels (similarity, not a gate in drawn modes): plain 99.55 %, columns 85.65 %, presentation 95.62 %, keys 99.71 %, splice 99.24 %, custom draw 77.70 % (RENDER_MODES `rm20261017c`: plain 99.52 %, columns 85.57 %). [evidence/tree-similarity-custom-frame-overlay-false.tsv](evidence/tree-similarity-custom-frame-overlay-false.tsv) |
| on: `tools/differential_off.sh frame-overlay true custom` | same recording: **6 found, 6 passed, 0 failed** (every stock probe unchanged); pixels lower by the overlay box only: plain 95.15 %, columns 82.60 %, presentation 91.40 %, keys 95.31 %, splice 94.83 %, custom draw 76.07 %. [evidence/tree-similarity-custom-frame-overlay-true.tsv](evidence/tree-similarity-custom-frame-overlay-true.tsv) |
| NOT_RUN | `m3` and `gpu` modes (need the M3 backend and Skija jars on the class path); the Table and List differentials; a side-by-side run of the base classes (the `false` run is compared with the official recording and the earlier RENDER_MODES numbers instead); gtk/cocoa runtime (`DrawnPlane` is win32 only; the `Widget.m3Features` field compiles on all three) |

## Follow-ups (not migrated in this leaf)

The fork's other hard-wired startup booleans stay as they are; each becomes a row + seam by the same recipe in its own leaf:

| Property | Owner |
|---|---|
| `swt.visibility.gate`, `swt.visibility.occlusion`, `swt.layout.skipNoop` | win32 `EffectiveVisibility.java:61, 63, 68` |
| `swt.drawn.renderThread` | win32 `DrawnPlane.java:74` |
| `swt.gpu`, `swt.gpu.debugOps` (and the `swt.gpu.*` sizes) | common `RetainedPlanes.java:71`, win32 `RetainedFrame.java:55` |
| `swt.m3.threadCheck` | common `ThreadRole.java:42` |
| `swt.svg.currentColor` | common `SVGFileFormat.java:76` |
| `swt.widget.theme`, `swt.lookAndFeel` (the matrix's `theme-provider` row names `-Dswt.widget.theme`) | common `LookAndFeel.java:45, 47` |

Next leaves of the ledger: the `VIEWPORT_HAS` rows (23) get their seams first (their code exists), then `ENHANCED_API_ROW` (27)
and `GAP` (56); each appends nothing to the TSV (the row exists), adds the id to `SHIPPED.tsv`, regenerates, and runs the
host's differential with the switch `false` and `true` (rule 6). Nebula and Swing read the same ids from their own copies.
