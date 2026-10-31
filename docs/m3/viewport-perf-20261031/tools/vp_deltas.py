# Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Viewport performance leaf (2026-10-31): the mechanical multi-site deltas as exact byte-preserving text replacements
with an expected occurrence count, plus record-only rows (Read + Edit in the owner files, decisions, corrections of my own
script, tests and code), appended to RECIPE_MEMORY.tsv with ids VP-xx (the six columns of the RECIPE_MEMORY.tsv files).

Idempotent: a delta whose `new` text is present and `old` text absent is DONE; a record-only row (apply False) is appended
once. Usage: python -I vp_deltas.py <worktree> [--check]   (--check: exit 1 when a delta is still to apply)
"""
import sys
from pathlib import Path

W = "bundles/org.eclipse.swt/Eclipse SWT/win32/org/eclipse/swt/widgets/"
MEMORY = "docs/m3/viewport-perf-20261031/RECIPE_MEMORY.tsv"
HEADER = "delta_id\tfile\told\tnew\tjavac_line\treason\n"

CHILD_COUNT_NEW = """int cachedChildCount (long hParent) {
\tlong hFirstItem = nativeFirstChild (hParent);
\treturn hFirstItem == 0 ? 0 : getItemCount (hFirstItem);
}

int nativeChildIndex (long hParent, long hTarget) {"""

# (id, file, old, new, evidence, reason, apply, count)
DELTAS = [
    ("VP-01", W + "Tree.java", "\tint count = nativeChildCount (OS.TVI_ROOT);\n",
     "\tint count = virtualTopology == null ? cachedChildCount (OS.TVI_ROOT) : nativeChildCount (OS.TVI_ROOT);\n",
     "PerfProbe treecheck 8000 original: getItemCount x1000 = 20070 ms (official 3.135.0: 1 ms); r0200 stack Tree.nativeChildCount (Tree.java:324)",
     "CACHED_CHILD_COUNT v1: a plain Tree answers its root count from the stock sibling cache (getItemCount (long): cachedFirstItem/"
     "cachedItemCount, 3.135.0 Tree.getItemCount bytecode: TVGN_ROOT then getItemCount (J)); a virtual Tree keeps its topology lane", True, 1),
    ("VP-02", W + "Tree.java", "    if (virtualTopology == null) {\n        return nativeChildCount(hParent);\n    }\n",
     "    if (virtualTopology == null) {\n        return cachedChildCount (hParent);\n    }\n",
     "TreeItem.getItemCount -> virtualChildCount -> nativeChildCount: a sibling walk per call (3.135.0 TreeItem.getItemCount: TVGN_CHILD then the cache)",
     "CACHED_CHILD_COUNT v1: the child count of a plain item from the same stock cache", True, 1),
    ("VP-03", W + "Tree.java", "int nativeChildIndex (long hParent, long hTarget) {", CHILD_COUNT_NEW.replace (
        "int cachedChildCount", "/* CACHED_CHILD_COUNT v1: stock 3.135.0 answers child counts from the sibling cache, O(1) after one walk */\nint cachedChildCount", 1),
     "new helper cachedChildCount (4 lines)",
     "CACHED_CHILD_COUNT helper next to nativeChildCount/nativeChildIndex (one owner: the Tree's native sibling access)", True, 1),
    ("VP-04", W + "Tree.java", "setItemCount / ensureVirtualNativeChildren insert every new row after one anchor (stock) or at TVI_LAST",
     "BULK_ITEM_COUNT v1 (tried, rejected and reverted): chain each insert after the row inserted before it (helpers insertedAfter, "
     "nativeLastChild)",
     "PerfProbe treecheck 8000: stock anchor 4000 rows 1196 ms (original) / 1403 ms (viewport), chained 2271 / 2191 ms; treevirt "
     "100000 original getItem (50000): TVI_LAST 82782 ms, chained 98608 ms",
     "correction of my own delta: comctl32 keeps siblings singly linked and renumbers the shown rows on every insert into a shown "
     "list, so an insert costs O(position) at the end (previous visible row) and O(rows after) at the anchor: chaining moves the "
     "cost, it does not remove it; a native TreeView of n shown rows is O(n^2) to fill whatever the anchor, so the only fix is "
     "not to hold rows outside the resident window natively (the viewport)", False, 0),
    ("VP-07", W + "Tree.java", "\tint childIndex = nativeChildIndex (hParent, item.handle);\n",
     "\tint childIndex = pageChildIndex (hParent, item.handle);\n",
     "r0074 stack: Tree.createTreeItem (3417) -> bindVirtualTopology (357) -> nativeChildIndex (334), one sibling walk per materialized facade",
     "SIBLING_CURSOR v1: binding a facade resolves its child index through the existing sibling cursor (pageChildIndex: O(1) for "
     "consecutive rows, reset at every native insert, delete and sort)", True, 1),
    ("VP-13", W + "Tree.java", "\tint resident = nativeChildCount (hParent == 0 ? OS.TVI_ROOT : hParent);\n",
     "\tint resident = cachedChildCount (hParent == 0 ? OS.TVI_ROOT : hParent);\n",
     "PerfProbe treevirt 12000 6000 original, stack histogram every 3 ms: 272 of 1035 samples in requestVirtualFrontier -> "
     "nativeChildCount (Tree.java:622), one sibling walk per TVN_GETDISPINFO",
     "CACHED_CHILD_COUNT v1: the frontier check of every painted row reads the resident count from the sibling cache "
     "(createTreeItem counts each insert, every delete path resets it)", True, 1),
    ("VP-14", W + "Tree.java", "\t\tint current = nativeChildCount (currentParent);\n",
     "\t\tint current = cachedChildCount (currentParent);\n",
     "the same histogram: the frontier's async growth (Tree.java:641) walks the siblings again",
     "CACHED_CHILD_COUNT v1: as VP-13", True, 1),
    ("VP-15", W + "Tree.java", "\tint target = Math.min (logicalCount, Math.max (0, requiredExclusive));\n\tint resident = nativeChildCount (hParent);\n",
     "\tint target = Math.min (logicalCount, Math.max (0, requiredExclusive));\n\tint resident = cachedChildCount (hParent);\n",
     "Tree.getItem (i) of a virtual Tree without the viewport: ensureVirtualNativeChildren walked every resident sibling per call "
     "(a getItem loop over n rows: n walks)",
     "CACHED_CHILD_COUNT v1: as VP-13", True, 1),
    ("VP-16", W + "Tree.java", "\tint resident = nativeChildCount (item.handle);\n\tint target = Math.min (logicalCount, Math.max (resident, VIRTUAL_FRONTIER_CHUNK));\n",
     "\tint resident = cachedChildCount (item.handle);\n\tint target = Math.min (logicalCount, Math.max (resident, VIRTUAL_FRONTIER_CHUNK));\n",
     "restoreVirtualChildren (expanding a row) walked the resident children",
     "CACHED_CHILD_COUNT v1: as VP-13", True, 1),
    ("VP-08", W + "ListViewport.java", "model.getWidestWidth ()", "list.exactWidth (model)",
     "F05/F10/F15: ListViewport.computeSize -> VirtualListModel.getWidestWidth -> VirtualListWidth.measurePending -> "
     "List.measureStoreText (List.java:2474..2495): GetDC, WM_GETFONT, SelectObject, DrawText, ReleaseDC per row (36-92 s for 10^6)",
     "DEFERRED_WIDTH_MEASURE v1: the five exact-width queries of the List viewport (computeSize and the H_SCROLL extent) measure "
     "through List.exactWidth: one DC, the font selected once, the advance lane of the pass", True, 5),
    ("VP-09", W + "List.java", "storeExactWidth: GetDC + font + busy cursor around itemStore.getWidestWidth (VIRTUAL store only)",
     "storeExactWidth () = exactWidth (itemStore); exactWidth (VirtualListModel) holds one DC with the font selected and the advance "
     "lane (measureAdvances) for the pass; busyCursor and endMeasure split out (each method <= 15 lines) (Read + Edit, "
     "H:/m3-scratch/perf/edit_list.py)",
     "javac 0 errors (compile_platform.sh win32)",
     "DEFERRED_WIDTH_MEASURE v1: the store (SWT.VIRTUAL) and the viewport (plain List) share one exact-width owner", False, 0),
    ("VP-10", W + "List.java", "measureStoreText: DrawText (DT_CALCRECT | DT_SINGLELINE | DT_NOPREFIX) per row",
     "inside an exact-width pass, text of printable ASCII (' '..'~') is the sum of the advance widths from one GetCharWidth call "
     "per pass (advanceLane); any other text, and every measurement outside a pass, keeps DrawText (Read + Edit)",
     "Test_ListAdvanceWidth (differential: sum == DrawText over the corpus)",
     "GLYPH_ADVANCE_LANE v1: GDI lays out simple text by per-character advance widths (no kerning in DrawText, no font fallback "
     "for characters the font has); the lane is refilled for every pass, so a font change or a reused font handle cannot leave "
     "a stale lane", False, 0),
    ("VP-11", W + "Tree.java", "setTopTreeItem (no viewport): TVM_SELECTITEM on the frontier as it stands",
     "ensureVirtualPageAfter (item) before TVM_SELECTITEM: at every level from the item up, the parent's native children reach "
     "the row's child index + one page (TVM_GETVISIBLECOUNT + 1), through ensureVirtualNativeChildren (Read + Edit)",
     "r0074 original answered top 49987 for setTopItem (getItem (50000)) of 10^5 virtual roots (m3: 50000)",
     "TOP_ITEM_SEEK v1: the frontier ended at the target, so the TreeView put the target at the bottom of the page; the oracle "
     "(official 3.135.0, every row native) is in TIMINGS.tsv", False, 0),
    ("VP-12", W + "Tree.java", "getItem (index) / TreeItem.getItem (index): the facade bound with a sibling walk",
     "seedChildCursor (hParent, hItem, index) after findItem, so bindVirtualTopology reads the index from the cursor (Read + Edit)",
     "see VP-07",
     "SIBLING_CURSOR v1: the index is known where the row was found", False, 0),
]


def cell (text):
    return text.replace ("\t", "\\t").replace ("\n", "\\n")


def apply (root, check):
    todo = 0
    rows = []
    for (rid, rel, old, new, evidence, reason, do, count) in DELTAS:
        path = root / rel
        if do:
            text = path.read_text (encoding = "utf-8")
            n_old, n_new = text.count (old), text.count (new)
            if n_new >= count:
                print (rid, "DONE")
            elif n_old == count:
                todo += 1
                if not check:
                    path.write_bytes (text.replace (old, new).encode ("utf-8"))
                print (rid, "APPLIED" if not check else "TODO", count)
            else:
                print (rid, "REFUSED old", n_old, "new", n_new, "expected", count)
                sys.exit (2)
        rows.append ("\t".join ([rid, rel, cell (old), cell (new), cell (evidence), cell (reason)]) + "\n")
    return todo, rows


def record (root, rows):
    memory = root / MEMORY
    memory.parent.mkdir (parents = True, exist_ok = True)
    have = memory.read_text (encoding = "utf-8") if memory.exists () else HEADER
    ids = {line.split ("\t", 1) [0] for line in have.splitlines ()}
    added = [row for row in rows if row.split ("\t", 1) [0] not in ids]
    memory.write_bytes ((have + "".join (added)).encode ("utf-8"))
    print ("memory rows added", len (added))


if __name__ == "__main__":
    root = Path (sys.argv [1])
    check = "--check" in sys.argv
    todo, rows = apply (root, check)
    if check:
        sys.exit (1 if todo else 0)
    record (root, rows)
