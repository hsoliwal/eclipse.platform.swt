<!-- SPDX-License-Identifier: EPL-2.0 -->
# Eclipse SWT additive PR #315 archival source custody into PR #307

Date: 2026-11-03. **SOURCE-HISTORY STAGING ONLY / DRAFT / HARD HOLD.** This document is published before any source-tree or merge-commit operation. No JavaSE minimum, public SWT/JDK API, native event behavior, JNI ABI, platform bundle, or protected `master` source is unlocked.

## Frozen original Git and PR inventory

- Receiving [SWT PR #307](https://github.com/hsoliwal/eclipse.platform.swt/pull/307), exact head `6820a3b978170b0c42ac32f92054e5542be84b80`, Git tree `f2510d4e5808b6664b5c5e4566e8afa0ebf31c41`. It is the previous 16-merge/15-retain additive SWT source candidate, and retains previous native widget/UI source and qualification tree. It remains open draft and has **not** passed full Tycho/SWTBot/Win/GTK/Cocoa native acceptance.
- Donor [SWT PR #315](https://github.com/hsoliwal/eclipse.platform.swt/pull/315), exact head `1e8c6e5066eff44336c52aa35db483998c624177`, Git tree `105cf8c09dcaab3217bacff02b64d8b2942ed73b`, previous `master@108fe6f117a8004fff3846bff8322554040b3dd8`. It contains an inert archive directory `m3/qualification/history-source-recovery-20261102/`, absent on #307. Its original Git tree is **`d84b53c285cddb4b05576921a065c1c4b92bb64a`** (`040000`).
- Git tree enumeration of that exact donor subtree: 989 recursive entries, **746 actual blob entries**, zero truncated, all original allowed file modes; 742 preserved historic variant objects and four index/description files. The five immediate children are `CURRENT_HEAD_RECONCILIATION.md`, `CURRENT_HEAD_RECONCILIATION.tsv`, `INDEX.tsv`, `README.md`, and `objects/` tree (sha `1fc7972b8d592a62de5ea1947b0d612efc2d714d`).
- Diverged Git ancestry is expected. `compare(#307, #315)` is 17 ahead/888 behind and caps its file entries at 300; it cannot be used as an exhaustive file diff or reason to replace the receiver source tree wholesale.

## One-directory source-preserving historical merge protocol

1. Freeze the exact two PR head SHAs, source paths, Git blob identity and all file modes. Reject changed PR heads, moved receiving integration branch, occupied archive destination or truncated donor tree.
2. Publish this documentation in a dedicated `m3/additive-swt-pr315-history-wave-20261103` branch before any subtree merge. No changes to existing SWT product source or original PR heads.
3. Create a new candidate Git tree from that documentation commit's tree, adding exactly `m3/qualification/history-source-recovery-20261102` pointing to the donor's immutable `d84b53c285cddb4b05576921a065c1c4b92bb64a` source tree. All other existing root/source entries must remain Git-tree-identical. Do not unpack, rename or copy the historical Java files into executable `bundles/` paths.
4. Create a **real two-parent Git merge commit**, first parent the documentation-bearing #307 receiver, second parent #315 original head. Use an expected-SHA **non-force** update to move only the dedicated integration branch. Preserve both complete commit histories and the exact 746 archived blob object IDs and modes.
5. Validate candidate vs first parent: 746 added archive files, zero changed/deleted product Java, C/JNI or SWT resources, preserved original archive tree SHA; the documentation file is a separate addition. Verify no difference in existing `m3/qualification/` subtrees.
6. Append a post-execution `VERIFICATION.md` record only with actually observed parent, tree, compare, branch and source-readback results. Open a **draft PR against `master`**, no automatic default-branch merge.

## Live product and source-rights HOLD

These 742 historical source objects are **archived variants**, not approved replacements for contemporary SWT product implementation. Direct restoration could overwrite newer `TreeViewport`, `ListViewport`, `RowRenderer` and JavaSE/native platform contracts. Before choosing a variant, the canonical Synexia Maven/OpenRewrite source-sealed recipe must perform per-leaf byte-preimage checks, result-after/fixed-point/stale refusal, then compile actual Eclipse/Tycho SWT 11/17 baseline and run SWTBot/GTK/Xvfb/Windows/Cocoa/RAP UI event-resource/lifetime tests, native/JNI/JDK ABI and platform checks.

LeetCode/HackerRank/GeeksforGeeks provide algorithm/problem taxonomies only; historical SWT or other GitHub donor source license/provenance is checked independently and this archive makes no challenge-solution imports. The user-requested ALL-`hsoliwal/*` source convergence still requires the cross-repository PR DAG and current exact-head checks; this is only an independently qualified **source-history** addition.

**Default `master` release is HOLD** until actual full tests; a Git ancestry/path union alone cannot establish product correctness.
