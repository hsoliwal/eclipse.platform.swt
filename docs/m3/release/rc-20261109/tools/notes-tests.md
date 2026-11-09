| Run | Found | Passed | Failed | Aborted (assumption) | Skipped (`@Disabled`) | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| Headless (22 classes) | 190 | 187 | 0 | 3 (GTK-only) | 0 | `gates/junit-headless.*` |
| Display (`AllNonBrowserTests` + 4 top-level `AllTests` classes) | 5,135 | 4,951 | 82 | 82 | 3 | `gates/junit-display.*`, `gates/DISPLAY_FAILURES.tsv` |
| Master `4fb49875cc` baseline, same selection and harness | 5,109 | 4,935 | 83 | 71 | 3 | `gates/master-baseline/junit-master-display.*` |
| `Test_org_eclipse_swt_widgets_Display` alone | 65 | 64 | 1 (input) | 0 | 0 | `gates/junit-rerun-display-alone.*` |
| SVG classes with `org.eclipse.swt.svg` + JSVG 2.1.0 | 121 | 121 | 0 | 0 | 0 | `gates/junit-svg-fragment.*` |

Every one of the 82 Display failures is classified, and none is introduced by the candidate. The candidate also fixes `Test_M3ItemRange.test_duplicateAtMaximumIntCoalescesOnce`, which still fails on master.

| Class | Count | Reason and evidence |
| --- | --- | --- |
| HARNESS_ORDER | 61 | `Test_org_eclipse_swt_widgets_Display` needs a thread with no Display. In the single-JVM console run, the nested suites run first, and every widget class leaves the default Display. Attribution runs: Button then Display gives 61 failures; M3 differentials then Display gives 61. Alone it is 64/65. |
| ENV_SVG_FRAGMENT | 8 | The optional SVG fragment was not on the classpath. With it, 121/121 pass. |
| PRE_EXISTING_MASTER | 7 | `VirtualPageSource` (4), `StyledText_PagedContent` (1), `Java2DCanvas` Region retention (1) and `M3SortFilterWidgets` filter row (1). Master `4fb49875cc` fails the same 7 tests with the same messages: `gates/master-baseline/`. |
| ENV_INPUT_HIDDEN_DESKTOP | 5 | Text (2), DateTime (2) and StyledText (1) depend on `Display.post` or focus traversal. Real input does not reach the invisible test desktop. |
| ENV_CLIPBOARD | 1 | `TextTransfer` `blank_non_empty`: "cannot open system clipboard", because another process held the shared OS clipboard. It passed in the first Display run. |

In addition, the `AllNonBrowserTests` suite-level leak detector reports "1 leaks found" (container failure; @LEAK@).

The M3-differential attribution run was made at `88f03ae386`, before commit `4f82a7a9ab` made those two classes dispose their Display. That fix is necessary but not sufficient: the Button attribution run at `4f82a7a9ab` shows that every widget class leaves the default Display behind. A Maven or Tycho run may order the engines differently.

The JNI checker (`-Xcheck:jni`) reported 0 diagnostics in every JVM.
