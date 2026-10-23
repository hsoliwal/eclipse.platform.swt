# SWT GTK list-event and spinner-offset repair — receiver receipt

Date: 22 October 2026

This change adjusts exactly two implementation methods in the **existing** Eclipse SWT GTK owner. It adds no public API, JNI entry point, new widget, framework layer, or native binary.

## Source authority

- Receiver baseline: `hsoliwal/eclipse.platform.swt` commit `c1ed5e568efc8ae628b32ff71d4f9a343a3f8f94`, itself the result of the separately reviewed SWT host/fragment version-alignment PR.
- Canonical Java 21/OpenRewrite recipe: `synexia-openrewrite-recipes/recipe-crates/m3-swt-gtk-selection-20261022` on `hsoliwal/com.synexia` `develop`.
- Recipe name: `com.synexia.eclipse.SwtGtkSelection20261022`.
- Hash/contract authority: `resources/com/synexia/rewrite/hash-pinned-java/m3-swt-gtk-selection-20261022/manifest.tsv` and `M3SwtGtkSelectionRecipeTest` in that crate.
- `List.java`: SHA-256 `e9945de78ccae72a4005fb1a02d44a0bb4a78947b725af81d94bd2d02d780750` to `36d67b68cbb0bac6c0aebf7c52731c1d8d06180b0d21f4c0ac4127d64fde06dc`.
- `Spinner.java`: SHA-256 `a47017f802978793f1b783f9daa447b73026eccaa57163da52821e7329b8317c` to `2f5985a64c2345544623ec6d942be987aca162ad07e666b535e6a90165fe17f9`.

## Behavioral change

1. GTK4 `List.gtk_gesture_press_event` now sets its existing double-click pending flag before the single-click fast-return path. The unreachable tail and duplicate superclass dispatch are removed; GTK row activation logic remains intact.
2. `Spinner.gtk_insert_text` retains a single Unicode-aware `g_utf8_offset_to_utf16_offset` conversion and drops the duplicate variable declaration and incorrect `g_utf16_pointer_to_offset` call.

## Qualification at PR creation

| Gate | Evidence | Status |
| --- | --- | --- |
| Source input SHA-256 and generated output SHA-256 | Two exact paths in pinned manifest | Passed |
| Actual JDK 21 OpenRewrite generation | `M3SwtGtkSelectionRecipeTest` | 6 tests passed |
| Fixed point, drift refusal, duplicate detection, file order, unrelated preservation | Same JUnit suite | Passed |
| Generated target diff | Two source files; `git diff --check` | Passed |
| Full Eclipse/Tycho reactor | Maven 3.9.16, Tycho 5.0.4, JDK 25; toolchains JDK 8/11/17/21/25 | In progress; report separately |
| Native GTK3/GTK4, IME, accessibility, OS-specific JNI matrix | SWT physical/UI integration | Not qualified |

This stacked PR must be reviewed together with the SWT metadata receiver and the canonical Synexia recipe. Keep it draft until native and assembled SDK gates are satisfactory. Any changed baseline source requires regeneration from a requalified recipe; never adjust generated source by hand.
