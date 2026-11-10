| Run | Found | Passed | Failed | Aborted (assumption) | Skipped (`@Disabled`) | Evidence |
| --- | --- | --- | --- | --- | --- | --- |
| Headless (22 classes) | 190 | 187 | 0 | 3 (GTK-only) | 0 | `gates/junit-headless.*` |
| m3gc (AllM3GcTests) | 97 | 97 | 0 | 0 | 0 | `gates/junit-m3gc.*` |
| Display: AllTests without AllBrowserTests (330 classes) | 6,264 | 6,049 | 16 | 176 | 6 | `gates/junit-display.*`, `gates/DISPLAY_FAILURES.tsv` |

All 16 failures are classified; none is introduced by the candidate:

| Class | Count | Reason and evidence |
| --- | --- | --- |
| PRE_EXISTING_MASTER | 7 | The same tests fail on master `4fb49875cc`. Table_Splice: the hidden top and the hidden double paint. Tree_ViewportDeltas: hidden traffic (2). List: the inside-window splice. Combo: the logical bar after an edit-text match. VirtualPageSource: the bulk batch. Root causes and next steps are in `PRE_EXISTING_DEFECTS.tsv`. |
| ENV_INPUT_HIDDEN_DESKTOP | 6 | Display.post and focus traversal do not reach the invisible test desktop. |
| ENV_CLIPBOARD | 3 | The shared OS clipboard was held by another process; these tests passed in earlier runs. |

The official-SWT 3.135.0 oracle (`oracle/`) settled the three disputed expectations. The caret after a shrink was a test error. The zero-height caret was an SWT error and is fixed. The hidden Table top is SWT and still open. The JNI checker reported 0 diagnostics.
