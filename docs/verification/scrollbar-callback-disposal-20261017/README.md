<!-- SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project -->
<!-- SPDX-License-Identifier: Apache-2.0 -->
# GTK scrollbar visibility callback disposal

`ScrollBar.setVisible` delivers synchronous Show/Hide callbacks before the
owner's Resize notification. A callback can dispose the scrollbar or its owner,
clearing the scrollbar's `parent` field. The preceding GTK source then reads that
cleared field and throws `NullPointerException`.

The qualified correction captures the original owner and checks each event
recipient immediately before dispatch. A surviving owner still receives Resize
when only its scrollbar was disposed. Every nested call checks owner lifetime
before sending Resize, while live nested event order and the latest requested
visibility remain intact. The change adds no widget field, public API or JNI
declaration.

## Source stack and preservation

This successor follows [SWT #220](https://github.com/hsoliwal/eclipse.platform.swt/pull/220),
which includes [SWT #219](https://github.com/hsoliwal/eclipse.platform.swt/pull/219).
The four generated paths are the GTK owner, the focused regression, its suite
registration, and the receiving receipt. The suite retains both coordinate
regressions and the recovered AtomicGrowth test. All other source-parent bytes
are preserved; this directory adds review evidence and a recipe authoring mirror.

| Source identity | Commit |
| --- | --- |
| Local qualification parent | `b42becc14a4522d5342649cb527bd5e70aa7c86b` |
| Local qualified source commit | `64b93072569e9f9749d30a065c793fca865ff16e` |
| Published graph parent, SWT #220 | `bff9c98564a575781d9a19f61dc5561c1d13ce2a` |

The complete local and remote parent trees differ. The two existing receiving
preimages have equal Git blobs at the immutable remote parent, and both new
receiving paths are absent there. [PUBLICATION.json](PUBLICATION.json) records
that scoped mapping, the distinct trees, and the exact four source seals.
The full GTK compilation belongs to the recorded local qualification parent.

## Executed verification

| Gate | Result |
| --- | --- |
| Actual canonical Maven/OpenRewrite SDK | 5 passed; no failures, errors or skips |
| Qualified receiver | 4 targets changed; immediate second pass changed 0 |
| Original source, real GTK3/X11 | 8 exact disposal NPE failures and 4 passing controls |
| Received source, real GTK3/X11 | 12 passed; no failures, errors or skips |
| Complete supporting GTK parent | 546 Java sources compiled with Java 21 |
| Declared ScrollBar API descriptors | Identical before and after |
| Owner compiler diagnostics | The same 9 existing warnings; no new diagnostics |
| New regression compilation | Java 21 with `-Xlint:all -Werror` |

The native tests cover owner and scrollbar-only disposal during Show/Hide,
display-filter disposal, nested visibility and Resize disposal, normal horizontal
and vertical behavior, idempotence, selection neutrality, live nested ordering
and application listener failures. They execute actual SWT-built native libraries
under an isolated Xvfb display.

The final [received-source result](evidence/received-native/result.json), exact
command arrays, JUnit XML, API output and dependency hashes are retained together.
The supporting runtime includes five original message/CSS resources. These
inventories bind the executed class, resource and native-library bytes; they do
not constitute an end-to-end memory or resource-leak measurement.
[EVIDENCE.json](EVIDENCE.json) hashes every retained evidence file. The original
canonical and exported-mirror SDK runs remain separate observations.

## Canonical recipe and reproduction

Canonical [Synexia #12129](https://github.com/hsoliwal/com.synexia/pull/12129),
commit `893118ced3ba18f97633c24c8090d1aa83b4113c`, owns
`com.synexia.swt.ScrollBarCallbackDisposal20261017`. The new authoring mirror is
[revision, hash and licence pinned](recipe/SYNEXIA_HANDOFF.json) to that containing
commit. Existing Eclipse payload notices are retained. The mirror adds no SWT
product runtime dependency.

From `recipe`, qualify with Java 21 and
`mvn -B -ntp -Drecipe.owner.root="$PWD/sdk-owner" verify`, then run
`python3 receive.py --target /absolute/path/to/eclipse.platform.swt --check`.
Use Maven `-o` only when its dependencies are cached. The receiver requires actual
successful SDK results and refuses changed source inputs or receiving preimages.

The source-bound native driver is
[verification/run-native-callback-proof.py](verification/run-native-callback-proof.py).
Its `--help` documents the original and received source roots, complete GTK class
and resource directory, Java home, JUnit console and native directory. Run it
inside an isolated Xvfb display with `SWT_GTK4=0` and `GDK_BACKEND=x11`.
[Executed commands](evidence/received-native/commands.json) retain the exact
arguments used for this proof, including distinct before and received roots.

## Remaining acceptance scope

Runtime evidence covers GTK3/X11 callback lifetime and event behavior. GTK4,
Cocoa, Win32, the full Tycho reactor, all-widget viewport acceptance, screen
pixels and controlled CPU/heap/RSS budgets remain separate gates.

The package-private widgets paint graph inherited from #220 currently has no
direct production Java consumer. Its component recovery does not establish
activation in native Tree/Table rendering, and this callback repair makes no
such integration claim. The separate Nebula startup-scroll successor records
its own runtime dependency and acceptance evidence.
