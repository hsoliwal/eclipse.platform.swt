<!-- SPDX-License-Identifier: EPL-2.0 OR Apache-2.0 -->
<!-- Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others -->
# Theme and language switchers (M3-THEME-HOOKS, M3-LANG-ID)

Branch `m3/theme-language-20261030`, base `m3/superset-20261030b` (`0524bbadd47`: master + #271 GC seam leaf 4 + #272
feature switches + #274 leaf 5). User directive 2026-10-30: "themeability and language switcher" for the SWT fork (viewport
widgets, five render modes, drawn planes, the M3 renderer, the enhanced M3 API), with Nebula, the web-widget scaffolds and the
M3JDK Swing port as consumers; "enhance them and perfect them all". Donors first: [`FOSS_REUSE_DECISION.tsv`](FOSS_REUSE_DECISION.tsv).

Everything is **additive and opt-in**: until an application calls `M3Themes.apply` / `M3Language.setLocale` / `register`
(or sets `-Dswt.m3.theme` / `-Dswt.m3.language` and calls `applyStartup` / reads `getLocale`), no widget changes (stock).

## What is here

| File | Role |
|---|---|
| `org.eclipse.swt.m3.M3ThemeSlot` | the 47 slot ids, names, kinds, SWT system-colour mapping, WCAG pairs |
| `org.eclipse.swt.m3.M3Theme` | a theme: one packed `int []` of slots + dark flag + font family; built-ins `LIGHT`, `DARK`, `HIGH_CONTRAST`; builder; `parse` (properties or JSON), `toText`, `toLookAndFeelTable`, `contrastFailures` |
| `org.eclipse.swt.m3.M3Themes` | registry, `apply (Display, M3Theme)`, `reset`, `applySystem`, `applyStartup` (`-Dswt.m3.theme`), `load (Path)`, `color (Display, slot)`, `metric (Control, slot)`, `follows (Control)`, listeners |
| `M3ThemeApplier`, `M3ThemeBuiltins`, `M3ThemeFile` (package) | the switch pass, the built-in values, the file text and the look and feel table |
| `org.eclipse.swt.internal.laf.M3ThemeTable` | theme file reader (properties, or JSON through the LF6 `M3Json`) |
| `org.eclipse.swt.widgets.M3NativeTheme` (win32; gtk and cocoa answer false) | the OS treatment: dark explorer theme, dark combo, menu bar and header line colours for new controls; `AllowDarkModeForWindow` + `SetWindowTheme` + `WM_THEMECHANGED` for existing ones, no handle recreated |
| `org.eclipse.swt.m3.M3Language` | active locale (`-Dswt.m3.language`), bundles + fallback chain, `message`/`format`/`find`, `register (Widget, kind, key[, fallback])`, `setLocale` (relabel + RTL flip + other displays + listeners), `isRightToLeft`, `orientation`, `numberFormat`, `dateFormat`, `collate` (M3-LOCALE-COLLATION) |
| `M3LabelRegistry`, `M3LanguageDisplays` (package) | the per-display label lanes (widget, key id, kind) and the shell flip |
| `org.eclipse.swt.m3.M3Languages` + `M3Languages.tsv` | 1,669 languages (tag, English name, native name, script, direction, models, source) |
| `org.eclipse.swt.m3.M3LanguageId` | M3-LANG-ID: majority script, mixed, right-to-left, letter count of a text, packed in one int |
| `org.eclipse.swt.m3.M3ThemeSwitcher`, `M3LanguageSwitcher` | the two switcher widgets (`new X (Composite, int)`) |
| `org.eclipse.swt.internal.render.MirroredCanvas` (win32) + `DrawnPlane` hook | mirrored drawing of right-to-left drawn planes in the custom, m3 and gpu backends |
| `examples/.../snippets/m3/M3ThemeLanguageExample.java` | Table (10,000 virtual rows), Tree, StyledText, CTabFolder, Canvas switching live in every mode |
| `tests/.../junit/m3theme/` | 4 headless + 2 Display JUnit 5 classes |
| `tools/` | `M3LanguagesGen.java` + `LIKELY_SCRIPT_OVERRIDES.tsv` (TFG of the language list), `test_theme_language.sh`, `probe_theme_language.sh`, `method_gate.py` |

## The theme table (`M3ThemeSlot`, `M3Theme`)

A theme is one `int [47]`: ARGB for a colour slot, a number for a metric slot (`get (slot)` is one bounds check and one array
read). Slots:

| ids | slots | source |
|---|---|---|
| 0-8 | `bg fg muted border accent accent-2 surface` (colours), `radius pad` (px) | the web corpus `--vs-*` slots (com.synexia `d278d4c1c02` STYLE_AND_THEMES.md); theme files accept the `--vs-` prefix |
| 9-28 | `widget-background widget-foreground widget-border widget-light/normal/dark/highlight-shadow widget-disabled-foreground list-background list-foreground list-selection list-selection-text info-background info-foreground title-background title-foreground title-inactive-background title-inactive-foreground link-foreground text-disabled-background` | the SWT system-colour roles (`ofSystemColor (SWT.COLOR_*)`, `systemColor (slot)`) |
| 29-40 | `row-stripe selection selection-text selection-inactive hover focus-ring header-background header-foreground gridline error warning success` | the drawn-plane roles (Qt `AlternateBase`/`Highlight`, Carapace status tokens as patterns) |
| 41-46 | `font-height` (pt, 0 = platform) `font-style` (SWT bits) `row-height indent` (px, -1 = platform) `focus-width border-width` | fonts and metrics |

Built-ins (values are this project's own): every text pair of `M3ThemeSlot.TEXT_PAIRS` reaches WCAG 2 AA 4.5:1 and the focus
ring 3:1 (`Test_org_eclipse_swt_m3_M3Theme`); minimum text contrast **light 4.76, dark 5.94, high contrast 7.09**. The inactive
selection is paired with the selection text because the drawn rows keep the selection text colour without focus.

Theme file (`M3Theme.parse`, `M3Themes.load`), properties or one JSON object (members, or a `"slots"` object):

```
name = ocean
extends = dark            # light | dark | high-contrast | a registered theme (default light)
dark = true
font = Segoe UI
--vs-accent = #36f        # #rgb #rgba #rrggbb #rrggbbaa
selection = #3366ffcc
row-height = 22
```

Every theme defines every slot (a builder starts from a complete base; a file names its base). Errors name the theme, the key
and the cause (unknown key or base, not a colour, not an integer, out of range, no name, malformed JSON). `toText ()` reads back
to an equal theme.

## The runtime switch (`M3Themes.apply`)

One model: the theme becomes the look and feel `m3-<name>` (LF2 table: `@extends = flat|flatdark`, the flat variables and 50
keys from the slots, row height and indent when set). `M3LookAndFeel.setLookAndFeel` (or `reload` when it is already selected)
reinstalls only the delegates whose paint keys change (LF6), so the drawn planes of Tree, Table and List and the M3 renderer
(`M3RowCanvas`, the gpu backend) paint the slots through `ThemeDefaults`/`M3DrawnRowUI`; there is no second drawn-plane theme
path. Then, per display:

1. `M3NativeTheme.prepare`: win32 dark explorer theme and dark combo for new controls, menu bar colours (dark themes only:
   stock menu bars lose their hover highlight with custom colours), table header line colour.
2. Every shell with its redraw suspended (`setRedraw (false)` ... `true`): each control that follows the theme gets the OS
   treatment (dark, or undoing a previous dark one) and `setBackground`/`setForeground` from the list slots (Table, Tree, List,
   Text, StyledText, Combo, CCombo, Spinner) or the widget slots (everything else), plus Table/Tree header colours, StyledText
   selection colours, CTabFolder selection colours and the Link colour. Items are never touched.
3. The shell repaints once: native controls paint their visible rows, drawn planes their visible rows. `Test_..._M3ThemesDisplay.test_applyIsDamageOnly`
   measures it on a 100,000-row virtual Table scrolled to row 50,000: **0 SetData** after the switch (no item rebuilt), repainted
   rows <= the visible rows, paint calls <= visible cells, top index unchanged.
4. The previous theme's colours are disposed. `M3Themes.color (display, slot)` answers one `Color` per slot per display, created
   on first use and valid until the next switch (read it when painting: the stock GC path); in a stock display it answers the
   platform's system colour for a system role and the light theme's value otherwise.

`reset (Display)`: default colours (`setBackground (null)` ...), the look and feel and the OS data of before the first apply.
`applySystem`: high contrast when the display is in high contrast, else dark when the OS prefers dark (`M3SystemTheme`), else
light. `metric (control, slot)`: the theme's metric, or the control's own (item height, font height) when the theme leaves it to
the platform.

## The language switch (`M3Language`)

- Lookup chain: the candidate locales of the active locale (`ResourceBundle.Control` no-fallback candidates: `ar-EG`, `ar`), then
  each fallback's (`setFallbacks`), then the root bundles; per locale every bundle family in `addBundle` order; a key in a
  locale's bundle counts only when that bundle defines it (not inherited from its parent), so a fallback is tried before the root.
- `register (widget, TEXT|TOOLTIP|MESSAGE, key[, fallback])` sets the label now and keeps (widget, key id, kind) in three lanes per
  display (no object per label; disposed widgets dropped on growth and relabel). Texts: items (menu, tool, tab, CTab, table and
  tree columns and items), shells, Button, Label, Link, Group, CLabel; tooltips: controls, tool, tab and CTab items, columns,
  menu and tray items; messages: Text.
- `setLocale (locale)`: every label of the calling thread's display is set again (`requestLayout` of its control), the display's
  shells take the locale's orientation (`setOrientation`, children follow without new handles; a descendant whose class does not
  follow keeps its own), displays on other threads are relabelled with `asyncExec`, listeners are told.
- Right to left: a script subtag decides (`pa-Arab` RTL); else the language's entry in `M3Languages`; else the script of the
  locale's own language name.
- Formats: `numberFormat`, `dateFormat (style)`, `format (key, args)` (`MessageFormat` in the locale), `collate (items)`
  (M3Collation, M3-LOCALE-COLLATION).

### The language list (`M3Languages`, `M3LanguageId`)

`tools/M3LanguagesGen.java` (TFG, deterministic for a JDK: a second run gives the same bytes) reads `HF_LANGUAGE_TAGS.tsv` and
`HF_TRANSLATION_COVERAGE.tsv` (com.synexia `d278d4c1c02`): tags with a translation model that are ISO 639 languages, **1,669
languages**; the native name from the JDK's CLDR data (ISO 639-2 codes read their two-letter code's name: 315 `cldr`), else
`LIKELY_SCRIPT_OVERRIDES.tsv` for right-to-left languages the JDK has no native name for (CLDR likely subtags: 39 `override`), else
the English name (1,315 `hf`); script and direction from `M3LanguageId` over the native name. **60 right-to-left languages**
(Arabic, Hebrew, Thaana, Syriac, N'Ko, Samaritan, Imperial Aramaic scripts). `M3LanguageId.detect` counts the letters per
`Character.UnicodeScript` in one int lane and the strong bidi classes; mixed text reports the majority.

## Right-to-left drawn planes (`MirroredCanvas`)

A drawn plane of a mirrored window (`WS_EX_LAYOUTRTL`, its paint DC has `LAYOUT_RTL`) used to present a left-to-right frame
through the mirrored DC: geometry flipped right but every glyph and icon mirrored. Now the behaviour pass paints through the
back buffer with `LAYOUT_RTL` (window origin moved so that it mirrors around the client width, not the larger size-class buffer),
the row renderer's boxes go through `MirroredCanvas` (`x -> width - x - w`, left/right alignment swapped, polygons mirrored,
images and glyphs unchanged) into physical pixels for every backend (GC, M3 raster, gpu), and the present copies with the target
DC's layout set to left-to-right for the call. Retained rows are not kept for a mirrored frame (they are drawn), the
render-thread and owner-draw paths are unchanged (see non-claims).

## Feature switches

| bit | feature | shipped seam |
|---|---|---|
| 86 | `theme-provider` (existing row, M3-THEME-HOOKS) | `M3Themes.follows (control)`: on for the widget, or no `swt.widget.feature[.<Widget>].theme-provider` key for its class chain (apply is the opt-in). `-Dswt.widget.feature.Table.theme-provider=false` keeps Tables stock (drawn Tables keep the native look through `Themes.select ("Table", Theme.NATIVE)`) |
| 179 | `language-switch` (new row 180, M3-LANG-ID) | `M3Language.follows (widget)`, same rule: a class switched off keeps its labels and orientation |

`lang-id` (bit 177) stays a ledger row: `M3LanguageId` is shipped API without a per-widget seam.

## Switchers for any shell and the example gallery

`org.eclipse.swt.m3.M3ThemeSwitcher` (label, drop-down `stock`, `system`, then the theme names, swatch preview of page, surface,
accent, selection, text) and `org.eclipse.swt.m3.M3LanguageSwitcher` (label, drop-down of native + English names, preview of
native name, script and direction; the 24 most translated languages plus the active one, or `setLanguages (tags...)`). Both have
the `(Composite, int)` constructor the gallery (`M3ExampleGallery.mountSwitchers`, packages `org.eclipse.swt.widgets.`,
`custom.`, `m3.`) mounts by reflection, follow switches made elsewhere, and relabel themselves (keys `swt.m3.switcher.theme`,
`swt.m3.switcher.language`, `swt.m3.theme.<name>`; English fallbacks).

## Gates and runs (2026-10-30/31, Windows 11, JDK 21.0.9, natives 4975r19)

| Gate | Command | Result |
|---|---|---|
| compile | `compile_platform.sh` copy with `out=H:/m3-scratch/theme-lang/platform/<os>` | **win32 909 / gtk 698 / cocoa 837 sources, 0 errors** (base 899 / 696 / 835 minus this leaf's files: +10 common, +1 per platform, +1 win32) |
| compile with the M3 trees | `gc-extension-seam/tools/compile_m3gc.sh <wt> H:/m3-scratch/theme-lang/m3gc` | 943 sources, 0 errors |
| method length | `python -I tools/method_gate.py` over the leaf's sources | 0 methods over 15 lines; every new class under 300 lines |
| language list | `java -cp <classes> tools/M3LanguagesGen.java ...` twice | same bytes; 1,669 languages, 60 right-to-left |
| feature registry | `python -I feature-switches/tools/gen_features.py` twice | 180 features, 3 shipped; second run UNCHANGED x3 |
| JUnit headless | `tools/test_theme_language.sh headless` | **30 found, 30 passed, 0 failed** (M3Theme 12, M3LanguageId 5, M3Languages 5, M3Language 8) |
| JUnit Display, hidden desktop | `tools/test_theme_language.sh display <mode>` | **27/27 in each of viewport, original, custom, m3, gpu** (m3/gpu with `-Dswt.widget.mode.StyledText=original`, GF01) |
| damage-only switch | `test_applyIsDamageOnly` (100,000-row virtual Table at row 50,000) | every mode: SetData 0, 3 rows repainted (6 PaintItem, 2 columns) for 5 visible rows, top index kept |
| native treatment | `test_nativeTreatment` | `M3NativeTheme.available ()` true on this host (Windows dark mode) |
| coverage | `tools/coverage_theme_language.sh` (JaCoCo 0.8.15, headless + five modes merged) | **lines 683/689 (99.1 %), branches 455/499 (91.2 %)**; [evidence/jacoco-theme-language.csv](evidence/jacoco-theme-language.csv). Of the 44 missed branches 25 are `if (...) SWT.error (...)` guards (JaCoCo cannot see the throwing call complete); the rest: the resource-missing and IOException paths of `M3Languages`, `Display.disableCustomThemeTweaks`, a display disposed while a relabel is queued, the `asyncExec` lambdas of a switcher disposed before they run |
| probes | `tools/probe_theme_language.sh <mode>` x5 | 50 PNGs in `H:/m3-scratch/theme-lang/probes` (light/dark/high-contrast English, light Arabic, dark Hebrew on the Table; dark English Tree; dark German and light Japanese StyledText; dark Arabic and high-contrast Hindi Canvas), each mode; list with hashes: [evidence/PROBES.tsv](evidence/PROBES.tsv) |

RTL proof: in all five modes `ar` and `he` flip the shell (`orientation=rtl` in every RTL probe); the Table, Tree, CTabFolder,
switchers and labels are mirrored with readable Arabic and Hebrew, Arabic-Indic digits from the locale's number format, and the
drawn planes (custom, m3, gpu) mirror their rows with readable glyphs (before `MirroredCanvas` the custom probe showed every row
glyph-mirrored). Relabel without recreate: `test_relabelWithoutRecreate` (20 labels of 14 widgets, the same Java objects and the
same native handles after the switch).

Findings for other owners:
- The M3 canvas (`SWT.M3` GC of a Canvas in the m3 and gpu modes, gc-extension-seam) does not mirror a right-to-left Canvas
  (`m3-dark-ar-tab3.png`, `gpu-dark-ar-tab3.png`: swatches and text from the left, unpainted area after the flip); the stock GC
  mirrors it (`viewport-dark-ar-tab3.png`). Not changed here (GC seam leaves 6 and 7 own `M3CanvasHandler`/`M3ExternalGC`).
- `M3Mode.of (table)` answers `viewport` for a Table drawn by a plane in the custom, m3 and gpu modes (the RTL probes show the
  Table is drawn there); the example's mode label therefore reads `viewport`.

## Pre-existing failures (not this leaf)

- **GF01** (docs/m3/gc-extension-seam/README.md leaf 5; owner: leaf-6 GC-fidelity agent): in the `m3` and `gpu` modes every
  StyledText paint on an M3 canvas throws `NullPointerException` at `Composite.drawBackgroundInPixels (Composite.java:398)`
  (`M3ExternalGC.getGCData ()` is null). The m3/gpu runs here pass `-Dswt.widget.mode.StyledText=original` (`M3_GF01=1` runs
  without it).

## Non-claims

- Native Windows controls in a dark theme keep some OS colours SWT cannot set (ListView grid lines, the inactive selection of
  the native Table and Tree in the viewport and original modes).
- GTK and Cocoa get background and foreground colours only (no runtime OS dark treatment); they compile, they were not run.
- Mirrored drawn planes: theme parts (uxtheme expanders, check boxes) are drawn by the backend unmirrored inside their mirrored
  box; the render-thread path (`-Dswt.drawn.renderThread=true`) and owner-draw listeners of a mirrored plane are not mirrored.
- The probes print through `Control.print` (PrintWindow): a capture of the desktop is blank on the hidden desktop; GDI prints a
  right-to-left control mirrored, so the probe flips those children back.
- Application colours set before `apply` are not restored by `reset` (it restores the defaults).
- `org.eclipse.swt.m3` is not in the bundle's `Export-Package` (as before this leaf).
