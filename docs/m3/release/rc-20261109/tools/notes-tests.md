| Run | Found | Passed | Failed | Aborted (assumption) | Skipped (`@Disabled`) | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| Headless (22 classes) | 190 | 187 | 0 | 3 (GTK-only) | 0 | `gates/junit-headless.*` |
| m3gc (AllM3GcTests, M3 backend + Skija 0.116.3) | 97 | 97 | 0 | 0 | 0 | `gates/junit-m3gc.*` |
| Display: AllTests without AllBrowserTests (330 classes reachable) | 6,264 | 6,040 | 28 | 176 | 3 | `gates/junit-display.*`, `gates/DISPLAY_FAILURES.tsv` |

The 28 Display failures are all classified, and none is introduced by the candidate:

| Class | Count | Reason and evidence |
| --- | --- | --- |
| PRE_EXISTING_MASTER | 16 | Same tests and messages on master `4fb49875cc` in the same harness: viewport acceptance and visibility tests (Tree/Table/List/Combo viewport, AllWidgets_HiddenViewport) among the newly wired classes, and the VirtualPageSource bulk batch. Root cause and next step in `PRE_EXISTING_DEFECTS.tsv`. |
| UPSTREAM_KNOWN_FAILING | 3 | CoolItem. Upstream AllWidgetTests lists it as `// Failing test` (0ef6051a20). |
| ENV_INPUT_HIDDEN_DESKTOP | 7 | Display.post, focus traversal and shell activation do not reach the invisible test desktop. |
| ENV_CLIPBOARD | 2 | Another process held the shared OS clipboard; both tests passed in earlier runs. |

The `AllNonBrowserTests` suite-level leak detector reports "1 leaks found", on master as well. The JNI checker reported 0 diagnostics in every JVM.

Fixed in this candidate, compared with master in the same harness:
- 61 HARNESS_ORDER failures: Test_Display now releases the thread's Display.
- 8 ENV_SVG failures: the SVG fragment and JSVG are on the gate classpath.
- 9 Tracker failures: the test now releases the Tracker.
- 6 of 7 earlier pre-existing failures: StyledText_PagedContent, Java2DCanvas, M3SortFilterWidgets, and three VirtualPageSource tests (Tree page-source facades and the swap fixture).
- The M3ItemRange MAX_VALUE test (#325).
