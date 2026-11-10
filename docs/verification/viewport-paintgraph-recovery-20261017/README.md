# SWT paint graph recovery review — 2026-10-17

Local branch: `m3/viewport-paintgraph-recovery-20261017`. Local commit:
`d9bd9fd1bf1967a185585615badc4754b198ac52`. Exact local parent:
`ca77c0272fb6c1ea12594ac00baca8fb9b50ce3d`. The worktree is clean. Publication remains
owned by the parent agent; this branch has not been pushed or published.

## Scope and preservation

The history audit located a real partial-publication failure in the package-private
`org.eclipse.swt.widgets.ViewportPaintGraph` source. All four paths in historical
SWT #190 matched their original preimages on audited master `678da68f34007af0aab0689aa72bd85fb44b800e`.
This successor recovers its exact graph, test and historic receipt. The suite is
adapted to the preceding coordinate-visibility repair and retains both new
registrations. The canonical recipe emitted all five paths below, and the exact
Git tree comparison proves all other parent paths retained their bytes.

| Path | Change | Result Git blob |
| --- | --- | --- |
| `.m3/swt-paintgraph-atomic-growth-20261015/RECEIVER.md` | added | `1390cee8ec495d66598d37f71e013d04bd87fc84` |
| `.m3/swt-paintgraph-recovery-20261017/RECEIVER.md` | added | `77bbcc086f54b21aab1391282edc29065131072b` |
| `bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/widgets/ViewportPaintGraph.java` | modified | `254a324952adf97e50bd49ee5646547524e3c68b` |
| `tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/AllWidgetTests.java` | modified | `9979b2fbe45481f1c96db5a399dc3516108726b0` |
| `tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/Test_org_eclipse_swt_widgets_ViewportPaintGraphAtomicGrowth.java` | added | `dd1d9765a822929e61a6c5a2b5f3b85e2f79c148` |

Production Java source currently has no direct consumer of the package-private
widgets graph. The separate public `org.eclipse.swt.internal.ViewportPaintGraph`
has its own implementation and consumers. This PR restores the preserved component's
allocation safety; it does not establish its activation in native Tree/Table
rendering paths.

## Actual qualification

Canonical named recipe `com.synexia.swt.PaintGraphRecovery20261017` passed all
five actual Maven/OpenRewrite SDK tests, with zero failures, errors or skips.
The receiver admitted and changed five paths; its immediate second pass admitted
all five with zero changes. Java LST and exact text resource output seals match
the received sources. Final Maven log SHA-256:
`21ff276366f4a6197cc118e9c3fec3cc554729c2a71e8dc4acc946d39a77583d`.

The final canonical proof driver `fa38b9e9aa16ca2c0b5d16892ffdde5b1020178090a06bcbe13dac26231ae33e` reran against
local committed source. Java 21 strict compilation, identical declared API,
independent 1700-node/2000-edge oracle, and 12 real JUnit tests passed. The oracle
retained 298 callback nodes and digest
`168c389ff895675f9d92cbd885e54d2750d2e0a962eebccb8514d20e4cce3817`.

Under real `-Xmx48m` OutOfMemoryError controls, the old source published three
node-column replacements and one edge-column replacement before failing. The
received source published zero in both cases and successfully retried normal
operations. Original candidate proof, received-source proof before canonical
attribution, and final canonical-driver proof remain separate retained records.

The graph/tests were also compiled against the complete 546-source GTK baseline
SWT output; the same 12 selected real JUnit tests passed. No full-reactor Tycho,
Win32/Cocoa UI, pixel output or end-to-end viewport CPU/heap/RSS claim follows from
these component tests.

## Publication packet

`publication-manifest.json` lists the exact five path names, SHA-256 values,
preimage/result Git blobs, local source parent/commit and proof paths.
`PULL_REQUEST.md` contains the review-ready PR body. Remote Git-data commits may
have different hashes from local commits; record that mapping explicitly and
preserve the five-path source delta when stacking on the first SWT PR.

## Canonical recipe and portable source mirror

The exact authoring source is pinned to [Synexia PR #12108](https://github.com/hsoliwal/com.synexia/pull/12108), commit `a14a26e92fb65d0fbcd7f71e7ae1653222ccc439`. The [portable mirror handoff](recipe/SYNEXIA_HANDOFF.json) records every copied file hash and its original licence. The mirror is for Maven/OpenRewrite authoring and verification and adds no product runtime dependency. Local proof commits and remote source identities are recorded in [PUBLICATION.json](PUBLICATION.json).

From the `recipe` directory, qualify with Java 21 and `mvn -B -ntp -Drecipe.owner.root="$PWD/sdk-owner" verify`, then run `python3 receive.py --target /absolute/path/to/checkout --check`. Use `-o` only when the Maven dependencies are already cached.
