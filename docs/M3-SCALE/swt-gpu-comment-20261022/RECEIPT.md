# SWT M3 retained GPU compiler header repair — receiver receipt

Date: 22 October 2026

Source authority: existing Eclipse SWT owner hsoliwal/eclipse.platform.swt, baseline commit 7fddb6bd1f596acbe0613b91d7211bd81aaa06a3. This is stacked on the separately reviewed 3.138 metadata alignment and GTK source fixes.

Canonical Synexia recipe: com.synexia.eclipse.SwtGpuComment20261022 in the Maven/OpenRewrite crate m3-swt-gpu-comment-20261022, submitted as hsoliwal/com.synexia PR #13162.

## Exact compilation signal

- M3PlaneBackend.java: dangling Javadoc at source position 1:1
- M3Target.java: dangling Javadoc at source position 1:1
- Replay.java: dangling Javadoc at source position 1:1

In all three files, the leading Apache-2.0/EPL-2.0 copyright header is erroneously recognized by javac as an unattached documentation comment. The recipe turns only the leading decorative multi-star opening into an ordinary block-comment opener. Every later character, license statement, Java API, class body, JNI call and GPU runtime behavior is unchanged.

## Evidence

| Gate | Evidence | Result |
| --- | --- | --- |
| Receiver Git source identity | All three complete baseline Java files compared byte-for-byte against GitHub commit 7fddb6b | Passed |
| SHA-256 of three before and three after fixtures | Canonical manifest in Synexia recipe crate | Passed |
| Java21/OpenRewrite deterministic recipe | 6/6 JUnit tests, fixed-point and order permutation | Passed |
| Missing/changed/duplicate source refusal | JUnit negative tests | Passed |
| Executable code and license-body preservation | Full suffix after first line identical | Passed |
| Generated Java diff formatting | git diff --check | Passed |
| Full Tycho Java compilation with -Werror | Complete reactor replay | In progress |
| Native Win32 GPU/Skija hardware lifecycle | Platform-specific runtime tests | Not qualified |

This header repair does not relax -Werror or suppress compiler diagnostics. Do not merge without the independently required full-SDK and Windows/native tests, or if the pinned three-file source preimage changes.
