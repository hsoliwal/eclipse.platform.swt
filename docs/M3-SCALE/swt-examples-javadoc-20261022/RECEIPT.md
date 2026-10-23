# SWT examples compiler-Javadoc repair — receiver receipt

Date: 22 October 2026

Owner: existing hsoliwal/eclipse.platform.swt, baseline ee221dfb8535e042524cdddcb8124811298e4e6b. This is a stack after SWT metadata #246, GTK source #247, and retained GPU comment #248, with no unrelated file rewind.

Canonical Synexia Maven/OpenRewrite recipe: com.synexia.eclipse.SwtExamplesJavadoc20261022, crate m3-swt-examples-javadoc-20261022, reviewed independently in com.synexia.

The full Eclipse Tycho compilation exposed four invalid links in the SWT superset example module. The existing package/private implementation references are converted to ordinary code descriptions in Counters, NebulaGalleryDemo and StyledTextDemo, and DemoSpec's Demos#create reference now names the actual two-argument method. The four before-source files were compared byte-for-byte against the precise GitHub receiver baseline before generation.

| Gate | Evidence | Status |
| --- | --- | --- |
| Source-bound SHA-256 | Four exact before/after manifest records | Passed |
| OpenRewrite Java21 recipe | Six JUnit methods; exact output, fixed-point and permutations | Passed |
| Drift/missing/duplicate and partial replay | Fail-closed JUnit checks | Passed |
| Executable Java/API/JNI preserved | Four Javadoc substitutions; all other bytes equal | Passed |
| Receiver generated diff | git diff --check | Passed |
| Whole Eclipse/Tycho reactor | Rebuild using latest owner-local source | In progress |
| Native SWT/GUI, PDE/Resources, Equinox and M3JDK21 | Integration proof matrix | Not qualified |

No compiler warning/error is suppressed, no public Java or JNI contract is modified, and no dependency is hidden. Retain draft status until assembled SDK and platform runtime gates pass.
