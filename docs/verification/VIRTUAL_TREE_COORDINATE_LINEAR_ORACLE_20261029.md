<!-- SPDX-License-Identifier: EPL-2.0 -->
# Virtual Tree visible-coordinate linear oracle correction (2026-10-29)

## Contract and independently reproduced failures

Pinned SWT master `505203a9c139f920d5871492461a787c16725968`
includes `VirtualTreeVisibleProjection.visibleIndexOfCoordinate(parentId, childIndex)`.
The production method returns `-1` for any coordinate below a collapsed
parent or a parent hidden by an ancestor. It still returns a visible root-level
coordinate and validates the logical child geometry. An independent, existing
`Test_VirtualTreeCoordinateVisibility` asserts both collapsed immediate
parent and expanded parent under collapsed ancestor cases, and checks the
expanded case against an eager preorder.

The test-only `Test_VirtualTreeOrderStatistic.LinearReference.visibleIndexOfCoordinate`
was missing both visibility checks. Its old formula was:
```
base = parentId == ROOT ? 0 : visibleIndexOf(parentId) + 1;
return base + offsetWithinParent(parentId, childIndex);
```
For a hidden parent, `visibleIndexOf(parentId)` is `-1`; adding one
silently converts the hidden sentinel to a plausible row index. The test then
expects a visible row where no visible row exists. This is a defect in the
test's independent reference model, **not evidence that production must reveal
hidden children**.

The exact 2026-10-29 baseline/candidate comparison retained in private Synexia
`m3-long-widgets-20261029/verification/legacy-core-final/comparison.json`
reports two failures with the same messages on unmodified pinned SWT master
(14 tests: 12 pass, 2 fail) and on long-widget candidate code (110 tests:
108 pass, 2 fail):
- `randomScriptsAtRandomDepthsMatchTheLinearWalk`: seed 0, step 1,
  `visibleIndexOfCoordinate(0,0)`: reference 15, production `-1`.
- `hundredThousandMaterializedSiblingsMatchTheLinearWalk`:
  `visibleIndexOfCoordinate(91799,1)`: reference 52903, production `-1`.

Both error messages are preserved. No expected result is relaxed and no
JUnit failure is silently treated as a pass.

## Single-owner, contract-preserving test refinement

Update only the linear *oracle* method to:
1. Preserve root-level `base = 0`.
2. For non-root parent, return `-1` if it is not expanded.
3. Obtain the parent rank through the **test's own existing linear**
   `visibleIndexOf` method; return `-1` when that rank is negative.
4. For a visible expanded parent use `parentRank + 1`; keep the original
   `offsetWithinParent` linear sibling traversal unchanged.

Add one explicit regression test covering collapsed immediate parent,
expanded child under a hidden ancestor, visible expanded ancestry, subsequent
collapse, and unaffected root coordinates. Existing high-pressure and randomized
tests remain unchanged.

## Proof and publication gates

Source owner: `tests/org.eclipse.swt.tests/JUnit Tests/org/eclipse/swt/tests/junit/Test_VirtualTreeOrderStatistic.java`.
Single-purpose documentation: this file. No production, JNI, exported API,
plugin descriptor, build configuration, or referenced other test changes.

The current baseline test Git blob is
`9a9b2668fd38bb079d29c57674a7776bde187c2f`; production projection
Git blob is `0ff244ad49d6564cdd0b48aa68dd53cbfd27ad51`, and independent
visibility oracle Git blob is `7472c4af2d19b80ecb8d34da9fc3b30138f41891`.
Before modifying, compare these exact blobs and halt on drift.

Qualification must be done serially: diff/source identity, Java 21 strict
lint/compile, original `Test_VirtualTreeOrderStatistic` and
`Test_VirtualTreeCoordinateVisibility`, complete SWT headless group,
native/display/runtime, and the original long-widget candidate replay.
Use the already existing source-sealed M3 recipe owner for any subsequent
automated migration; this test correction is not a new production recipe.
Retain all failures and negative controls.

**Status:** This document records an independent source/receipt inspection and
the acceptance contract. It does **not** assert that Java/Maven/JUnit/native or
the full reactor has yet passed for the proposed change. Keep any PR draft
until current-head execution receipts exist.
