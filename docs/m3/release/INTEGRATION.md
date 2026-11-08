<!-- SPDX-License-Identifier: EPL-2.0 -->
# SWT release receiving integration

Status: **HOLD**. This change integrates the reviewed naming, documentation,
packaging, test inventory and report checks from PRs 321, 322, 323 and 324.
The frozen receiving base is `62460082d7caa06b21c49da99abbab53f73b3ffc`.
PR323 depends on PR321. Its checked Java candidate packager supersedes the
PR324 shell zipper while preserving explicit Windows/macOS naming, missing
input failures, source identity, and both previously proposed aggregate aliases.
The primary candidate artifacts retain their exact source and run-attempt names.

## Preserved contributions and contracts

| Contribution | Receiving behavior |
| --- | --- |
| PR321/323 | Readiness and evidence audit, Java candidate packager, source/attempt identity and checksums |
| PR322 | Sparse ItemBitSet JUnit registration and differential tests, exact Java21 leaf runner, suite omission inventory, required Surefire classes, release preparation guide |
| PR324 | Focused historical coverage audit, M3 naming and coverage guide, ItemRange/RowExtents tests, shared platform/Javadoc wiring |
| Earlier reviewed receiving proposal | Complete M3 report contract and refusal tests, original/viewport differential suite gates, overflow-safe ItemRange adjacency, RowExtents boundary tests |
| Current receiving tests | Deterministic filter ancestor and stable-sort edit oracles, rejected-update preservation, criteria copy and synchronous-generation checks |

Existing Java names, descriptors, source licenses, JNI declarations/generated C,
widget lifetimes, event semantics, and native artifact rules remain unchanged.
The already reviewed ItemRange adjacency correction uses widened arithmetic at
`Integer.MAX_VALUE`; it preserves the documented normalization contract.
Existing test bodies and assertions are retained. New tests use public contracts
and independent collection/linear oracles; they do not open private members.
The four inherited reflective cache assertions remain separately identifiable.

## Coverage selection

The prior Java21 campaign compiled 682 actual GTK Java sources and passed 84
unique JUnit methods. Its measured ten-owner scope was 926/1024 lines and
750/854 branches. Those are historical observations, not claims about this
integrated candidate. Its original compiler-log custody exception remains
recorded with the later compile-only, byte-identical recovery; this change does
not replace either receipt.

Filter/Sort missing lines predominantly belong to asynchronous worker/display
application and actual header installation. A synchronous test cannot qualify
those paths. The added tests target reachable existing contracts: invalid parent
updates, bounded ancestor-cycle fallback, criteria/generation state, stable
source-row tie identity through filter changes and edits around 64-row mask
boundaries, and atomic rejection of invalid sort-key configurations. Tests use
fixed operation sequences; no new random partitioning or nondeterministic oracle
is introduced. Current source, selected methods, compiler output, JUnit XML and
JaCoCo XML must be retained together. No denominator exclusions or threshold
reductions are permitted; the per-owner 90% line and branch floors remain gates.

## Algorithm categories and provenance

Range normalization, mutable prefix sums, inverse-prefix boundary selection,
stable sorting, and incremental filter deltas are the relevant comparison
categories. LeetCode 307, HackerRank Array Manipulation and GeeksforGeeks Fenwick
lower-bound descriptions inform category evaluation; they do not provide copied
solution code. Range interval conventions and nonnegative extent assumptions
must match the SWT contracts before adapting an idea. The complete evaluated
source catalogue and source-sealed recipe execution belong to the canonical
Synexia convergence crate. No external challenge statement or solution is
redistributed here.

## Explicit remaining release gates

The SWT bundle manifest does not export `org.eclipse.swt.m3`, although the
separate test bundle consumes that package. Public exported surface is locked;
this integration preserves the manifest and records the minimal descriptor
repair as a held review item. The OSGi/Tycho/provider result remains HOLD until
that exact visibility decision and a real installed bundle run are qualified.
Classpath compilation is not an OSGi visibility test.

The inspected Linux x86_64 archive binaries are Git LFS pointer files, not
loadable native libraries. A native build needs the actual GTK3/GLib/X11/Cairo/
Pango/ATK headers, pkg-config metadata, GL/GLU libraries, matching JNI headers and
a display server for GUI execution. Native build, generated JNI consistency,
full provider, Java25 matrix, GTK4, Windows, Cocoa, viewport screenshot oracles,
attached GtkTreeView model row counts, and independent heap/RSS/CPU budgets each
require their own current-source receipts. Unsupported or unexecuted lanes stay
HOLD. No built native binary is committed.

See [report gate](REPORT_GATE.md), [release requirements](../RELEASE.md),
[default suite inventory](../../RELEASE_PREPARATION.md), and
[candidate packaging](../../RELEASE_PACKAGING.md) for acceptance contracts.

## JNI checker in the actual test JVM

The test bundle deliberately disables `tycho-surefire-plugin` execution and runs
`maven-surefire-plugin` in `integration-test`. That existing JVM argument line
now includes `-Xcheck:jni`, while preserving the late-bound
`@{tycho.testArgLine}` instrumentation slot, the printing property, and
`${os-jvm-flags}` (including platform startup flags). No command-line override
replaces a caller's instrumentation arguments. Existing serial JUnit execution
and the private test `user.home` remain intact.

The shared platform workflow retains both Surefire XML and text output. JNI
checker diagnostics are reviewed independently of JUnit counts: zero failed
testcases does not establish a clean JNI check when native warnings are present.
`tools/release/test_jni_workflow.py` reads the actual POM and workflow and rejects
lost JNI checks, lost instrumentation/platform flags, missing native-test
selection, or missing diagnostic artifacts. These are source-backed configuration
checks; hosted runtime evidence remains required.

The local environment reached a private headless compositor but rejected Unix
socket creation with `EPERM`, including an independent bind probe. The prepared
native test lane therefore remains unexecuted here. Existing hosted GTK3/X11,
GTK4/X11 and GTK4/Wayland matrix jobs provide the native/GUI execution route;
GTK3/Wayland is excluded by that matrix. No local GUI pass, socket workaround,
new permanent workflow, or full 245-method release receipt is inferred.
