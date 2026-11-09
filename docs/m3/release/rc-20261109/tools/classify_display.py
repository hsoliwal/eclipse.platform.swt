#!/usr/bin/env python3
"""Classify FAIL rows of junit-display.nonpass.tsv with fixed, evidence-backed rules (first match wins).
Usage: classify_display.py <nonpass.tsv> <out.tsv>"""
import collections, sys
RULES = [
    ("HARNESS_ORDER", lambda c, t, m: "Another Display is already associated" in m,
     "Test_Display needs a thread with no Display. Console-launcher order runs nested suites first, and any widget class leaves the default Display behind: junit-attribution-widget-then-display (61) and junit-attribution-m3diff-then-display (61). Test_Display alone: junit-rerun-display-alone 64/65"),
    ("ENV_SVG_FRAGMENT", lambda c, t, m: "No SVG rasterizer found" in m,
     "optional org.eclipse.swt.svg fragment and JSVG not on the test classpath; with them, junit-svg-fragment 121/121"),
    ("ENV_CLIPBOARD", lambda c, t, m: (("Failed while running thread" in m or "clipboard" in m.lower()) and ("Transfer" in c or "Clipboard" in c)) or ("custom_CCombo" in c and t.startswith("test_copy")) or ("widgets_Combo" in c and t.startswith("test_consistency_Segments")),
     "the shared OS clipboard was held by another process (cannot open system clipboard / paste returned the previous clipboard); these tests passed in earlier Display runs of this candidate"),
    ("ENV_INPUT_HIDDEN_DESKTOP", lambda c, t, m: any(x in t for x in ("backspaceAndDelete", "shiftDeleteCutsWithVerifyListener", "traverseTabNext", "test_postL", "NoShellActivateOnSetFocus")),
     "uses Display.post, focus traversal or shell activation; real input and activation do not reach the invisible test desktop (run_hidden_desktop.ps1)"),
    ("UPSTREAM_KNOWN_FAILING", lambda c, t, m: "widgets_CoolItem" in c,
     "upstream AllWidgetTests lists it as '// Failing test' (0ef6051a20, 2025-09-24); same failures on master 4fb49875cc (suite-completeness/junit-master-unref-failing)"),
    ("PRE_EXISTING_MASTER", lambda c, t, m: any(x in c for x in ("VirtualPageSource", "StyledText_PagedContent", "awt_Java2DCanvas", "M3SortFilterWidgets",
        "AllWidgets_HiddenViewport", "Combo_ViewportAcceptance", "List_ViewportAcceptance", "Table_Splice", "Tree_ViewportCoverage",
        "Tree_ViewportDeltas", "Tree_ViewportEdges")),
     "same failure on master 4fb49875cc in the same harness: master-baseline/ and suite-completeness/junit-master-unref-failing; root causes in PRE_EXISTING_DEFECTS.tsv where analysed"),
    ("SUITE_LEAK_CHECK", lambda c, t, m: "leaks found" in m,
     "AllNonBrowserTests.afterSuite resource-leak detector; see master-baseline/junit-master-display for the master comparison"),
]
rows = [l.rstrip("\n").split("\t") for l in open(sys.argv[1], encoding="utf-8")][1:]
out, count = [], collections.Counter()
for mark, node, parent, test, msg in rows:
    if mark != "FAIL":
        continue
    cls = parent.split("/")[-1]
    tag, why = "UNCLASSIFIED", "-"
    for name, rule, evidence in RULES:
        if rule(parent, test, msg):
            tag, why = name, evidence
            break
    count[tag] += 1
    out.append((tag, node, cls, test, msg[:160], why))
with open(sys.argv[2], "w", encoding="utf-8", newline="\n") as fh:
    fh.write("class\tnode\ttest_class\ttest\tmessage\tevidence\n")
    fh.writelines("\t".join(r) + "\n" for r in sorted(out))
print(dict(count))
