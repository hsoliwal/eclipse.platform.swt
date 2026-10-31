/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.tests.junit.m3theme;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.*;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.laf.*;
import org.eclipse.swt.internal.render.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.m3.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.swt.widgets.List;
import org.junit.jupiter.api.*;

/**
 * The runtime theme switch on a display (hidden desktop): every role gets its slot, reset restores stock, the switch repaints
 * visible rows only and rebuilds no item, the per-class feature switch, metrics, the registry and startup theme, listeners,
 * argument and thread checks, and the theme switcher. The assertions are the same in every render mode
 * (<code>-Dswt.widget.mode=viewport|original|custom|m3|gpu</code>): passing in all five is the mode differential.
 */
public class Test_org_eclipse_swt_m3_M3ThemesDisplay {
	static final int ROWS = 100_000;
	Display display;
	Shell shell;
	Table table;
	Tree tree;
	StyledText text;
	CTabFolder folder;
	Link link;
	Button button;
	Text field;

	/** A widget class of its own, so that its feature-switch class defaults are resolved in the test. */
	static class Probe extends Canvas {
		Probe (Composite parent) {
			super (parent, SWT.NONE);
		}
	}

	/** A second class of its own. */
	static class OptIn extends Canvas {
		OptIn (Composite parent) {
			super (parent, SWT.NONE);
		}
	}

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setLayout (new FillLayout (SWT.VERTICAL));
		table = new Table (shell, SWT.VIRTUAL | SWT.FULL_SELECTION);
		table.setHeaderVisible (true);
		new TableColumn (table, SWT.NONE).setWidth (120);
		new TableColumn (table, SWT.NONE).setWidth (120);
		table.addListener (SWT.SetData, e -> ((TableItem) e.item).setText (new String [] {"row " + table.indexOf ((TableItem) e.item), "x"}));
		table.setItemCount (ROWS);
		tree = new Tree (shell, SWT.NONE);
		new TreeItem (tree, SWT.NONE).setText ("node");
		text = new StyledText (shell, SWT.NONE);
		text.setText ("styled");
		folder = new CTabFolder (shell, SWT.NONE);
		new CTabItem (folder, SWT.NONE).setText ("tab");
		link = new Link (shell, SWT.NONE);
		link.setText ("<a>link</a>");
		button = new Button (shell, SWT.PUSH);
		field = new Text (shell, SWT.BORDER);
		new List (shell, SWT.NONE).add ("item");
		shell.setSize (500, 700);
		shell.open ();
		settle ();
	}

	@AfterEach
	public void tearDown () {
		M3Themes.reset (display);
		shell.dispose ();
		System.getProperties ().stringPropertyNames ().stream ().filter (k -> k.startsWith ("swt.widget.feature.") || k.equals (M3Themes.PROPERTY))
			.forEach (System::clearProperty);
		M3Themes.names ().forEach (M3Themes::unregister);
	}

	void settle () {
		long until = System.currentTimeMillis () + 300;
		while (System.currentTimeMillis () < until) {
			if (!display.readAndDispatch ()) display.update ();
		}
	}

	static RGB rgb (int argb) {
		return new RGB ((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
	}

	@Test
	public void test_stockUntilApplied () {
		assertEquals (Optional.empty (), M3Themes.current (display));
		assertEquals (display.getSystemColor (SWT.COLOR_LIST_BACKGROUND).getRGB (), table.getBackground ().getRGB ());
		assertEquals (display.getSystemColor (SWT.COLOR_LIST_SELECTION).getRGB (), M3Themes.color (display, M3ThemeSlot.LIST_SELECTION).getRGB ());
		assertEquals (rgb (M3Theme.LIGHT.get (M3ThemeSlot.ACCENT)), M3Themes.color (display, M3ThemeSlot.ACCENT).getRGB ());
		assertSame (M3Themes.color (display, M3ThemeSlot.ACCENT), M3Themes.color (display, M3ThemeSlot.ACCENT));
		assertEquals (table.getItemHeight (), M3Themes.metric (table, M3ThemeSlot.ROW_HEIGHT));
		assertEquals (0, M3Themes.reset (display));
	}

	@Test
	public void test_applySetsEveryRole () {
		int themed = M3Themes.apply (display, M3Theme.DARK);
		settle ();
		M3Theme dark = M3Theme.DARK;
		assertEquals (Optional.of (dark), M3Themes.current (display));
		assertEquals (rgb (dark.get (M3ThemeSlot.LIST_BACKGROUND)), table.getBackground ().getRGB ());
		assertEquals (rgb (dark.get (M3ThemeSlot.LIST_FOREGROUND)), tree.getForeground ().getRGB ());
		assertEquals (rgb (dark.get (M3ThemeSlot.HEADER_BACKGROUND)), table.getHeaderBackground ().getRGB ());
		assertEquals (rgb (dark.get (M3ThemeSlot.HEADER_FOREGROUND)), tree.getHeaderForeground ().getRGB ());
		assertEquals (rgb (dark.get (M3ThemeSlot.WIDGET_BACKGROUND)), shell.getBackground ().getRGB ());
		assertEquals (rgb (dark.get (M3ThemeSlot.SELECTION)), text.getSelectionBackground ().getRGB ());
		assertEquals (rgb (dark.get (M3ThemeSlot.SELECTION_TEXT)), text.getSelectionForeground ().getRGB ());
		assertEquals (rgb (dark.get (M3ThemeSlot.LIST_BACKGROUND)), folder.getSelectionBackground ().getRGB ());
		assertEquals (rgb (dark.get (M3ThemeSlot.LINK_FOREGROUND)), link.getLinkForeground ().getRGB ());
		assertEquals (rgb (dark.get (M3ThemeSlot.LIST_BACKGROUND)), field.getBackground ().getRGB ());
		assertEquals (rgb (dark.get (M3ThemeSlot.WIDGET_FOREGROUND)), button.getForeground ().getRGB ());
		assertEquals (M3Themes.LOOK_AND_FEEL_PREFIX + "dark", M3LookAndFeel.getLookAndFeel ());
		assertEquals (count (shell), themed, "every control of the shell follows by default");
	}

	static int count (Control control) {
		int n = 1;
		if (control instanceof Composite composite) for (Control child : composite.getChildren ()) n += count (child);
		return n;
	}

	@Test
	public void test_colorsLiveUntilTheNextSwitch () {
		M3Themes.apply (display, M3Theme.DARK);
		Color accent = M3Themes.color (display, M3ThemeSlot.ACCENT);
		assertSame (accent, M3Themes.color (display, M3ThemeSlot.ACCENT));
		assertEquals (rgb (M3Theme.DARK.get (M3ThemeSlot.ACCENT)), accent.getRGB ());
		M3Themes.apply (display, M3Theme.HIGH_CONTRAST);
		assertTrue (accent.isDisposed (), "the previous theme's colours are released after the switch");
		assertEquals (rgb (M3Theme.HIGH_CONTRAST.get (M3ThemeSlot.ACCENT)), M3Themes.color (display, M3ThemeSlot.ACCENT).getRGB ());
		M3Themes.apply (display, M3Theme.HIGH_CONTRAST);
		assertEquals (rgb (M3Theme.HIGH_CONTRAST.get (M3ThemeSlot.LIST_BACKGROUND)), table.getBackground ().getRGB (), "applying again keeps the theme");
		assertThrows (IllegalArgumentException.class, () -> M3Themes.color (display, M3ThemeSlot.ROW_HEIGHT));
	}

	@Test
	public void test_resetRestoresStock () {
		Map<Control, RGB> stock = new HashMap<> ();
		for (Control control : new Control [] {shell, table, tree, text, folder, link, button, field}) stock.put (control, control.getBackground ().getRGB ());
		RGB header = table.getHeaderBackground ().getRGB (), linkColor = link.getLinkForeground ().getRGB ();
		String lookAndFeel = M3LookAndFeel.getLookAndFeel ();
		M3Themes.apply (display, M3Theme.DARK);
		M3Themes.apply (display, M3Theme.LIGHT);
		assertTrue (M3Themes.reset (display) > 0);
		settle ();
		stock.forEach ((control, rgb) -> assertEquals (rgb, control.getBackground ().getRGB (), control.toString ()));
		assertEquals (header, table.getHeaderBackground ().getRGB ());
		assertEquals (linkColor, link.getLinkForeground ().getRGB ());
		assertEquals (lookAndFeel, M3LookAndFeel.getLookAndFeel ());
		assertEquals (Optional.empty (), M3Themes.current (display));
		assertEquals (0, M3Themes.reset (display));
	}

	@Test
	public void test_applyIsDamageOnly (TestReporter reporter) {
		table.setTopIndex (50_000);
		settle ();
		AtomicInteger setData = new AtomicInteger (), painted = new AtomicInteger ();
		Set<Integer> rows = new HashSet<> ();
		table.addListener (SWT.SetData, e -> setData.incrementAndGet ());
		table.addListener (SWT.PaintItem, e -> { painted.incrementAndGet (); rows.add (table.indexOf ((TableItem) e.item)); });
		settle ();
		painted.set (0);
		rows.clear ();
		M3Themes.apply (display, M3Theme.DARK);
		settle ();
		int visible = table.getClientArea ().height / table.getItemHeight () + 2;
		reporter.publishEntry ("damage", "mode=" + M3Mode.name (M3Mode.of (table)) + " setData=" + setData + " paintItem=" + painted + " rows=" + rows.size () + " visibleBound=" + visible);
		assertEquals (0, setData.get (), "no item is asked for again (no rebuild)");
		assertTrue (rows.size () <= visible, "repainted rows " + rows.size () + " <= visible " + visible);
		assertTrue (painted.get () <= visible * table.getColumnCount (), "paint calls " + painted + " <= visible cells");
		assertEquals (ROWS, table.getItemCount ());
		assertEquals (50_000, table.getTopIndex (), "the viewport stays");
	}

	@Test
	public void test_featureSwitchPerWidgetClass () {
		System.setProperty ("swt.widget.feature.Probe.theme-provider", "false");
		System.setProperty ("swt.widget.feature.OptIn.theme-provider", "true");
		Probe probe = new Probe (shell);
		OptIn optIn = new OptIn (shell), optOut = new OptIn (shell);
		M3Features.set (optOut, M3Features.THEME_PROVIDER, false);
		RGB stock = probe.getBackground ().getRGB ();
		assertFalse (M3Themes.follows (probe));
		assertTrue (M3Themes.follows (optIn));
		assertFalse (M3Themes.follows (optOut), "an instance switched off in a configured class");
		assertTrue (M3Themes.follows (table), "no key: apply is the opt-in");
		M3Themes.apply (display, M3Theme.DARK);
		assertEquals (stock, probe.getBackground ().getRGB ());
		assertEquals (stock, optOut.getBackground ().getRGB ());
		assertEquals (rgb (M3Theme.DARK.get (M3ThemeSlot.WIDGET_BACKGROUND)), optIn.getBackground ().getRGB ());
	}

	@Test
	public void test_metrics () {
		M3Themes.apply (display, M3Theme.LIGHT.derive ("roomy").set (M3ThemeSlot.ROW_HEIGHT, 30).set (M3ThemeSlot.FOCUS_WIDTH, 3).build ());
		assertEquals (30, M3Themes.metric (table, M3ThemeSlot.ROW_HEIGHT));
		assertEquals (3, M3Themes.metric (button, M3ThemeSlot.FOCUS_WIDTH));
		assertEquals (button.getFont ().getFontData () [0].getHeight (), M3Themes.metric (button, M3ThemeSlot.FONT_HEIGHT));
		M3Themes.apply (display, M3Theme.LIGHT);
		assertEquals (tree.getItemHeight (), M3Themes.metric (tree, M3ThemeSlot.ROW_HEIGHT));
		assertEquals (-1, M3Themes.metric (button, M3ThemeSlot.ROW_HEIGHT));
		assertThrows (IllegalArgumentException.class, () -> M3Themes.metric (button, M3ThemeSlot.BG));
		assertThrows (IllegalArgumentException.class, () -> M3Themes.metric (null, M3ThemeSlot.INDENT));
	}

	@Test
	public void test_registryLoadAndStartup () throws Exception {
		M3Theme ocean = M3Theme.DARK.derive ("Ocean").build ();
		assertTrue (M3Themes.register (ocean));
		assertFalse (M3Themes.register (M3Theme.LIGHT.derive ("dark").build ()), "built-in names are kept");
		assertSame (ocean, M3Themes.find (" OCEAN "));
		assertEquals (java.util.List.of ("light", "dark", "high-contrast", "ocean"), M3Themes.names ());
		assertFalse (M3Themes.unregister ("light"));
		assertFalse (M3Themes.unregister ("nope"));
		assertFalse (M3Themes.unregister (null));
		assertNull (M3Themes.find (null));
		Path file = Files.createTempFile ("m3theme", ".properties");
		Files.writeString (file, "name = reef\nextends = light\naccent = #0a7\n", StandardCharsets.UTF_8);
		assertEquals (0xFF00AA77, M3Themes.load (file).get (M3ThemeSlot.ACCENT));
		Files.writeString (file, "name = light\n", StandardCharsets.UTF_8);
		assertThrows (IllegalArgumentException.class, () -> M3Themes.load (file));
		assertThrows (IllegalArgumentException.class, () -> M3Themes.register (null));
		assertEquals (0, M3Themes.applyStartup (display));
		System.setProperty (M3Themes.PROPERTY, "high-contrast");
		assertTrue (M3Themes.applyStartup (display) > 0);
		assertEquals (Optional.of (M3Theme.HIGH_CONTRAST), M3Themes.current (display));
		Files.writeString (file, "name = lagoon\nextends = dark\n", StandardCharsets.UTF_8);
		System.setProperty (M3Themes.PROPERTY, file.toString ());
		M3Themes.applyStartup (display);
		assertEquals ("lagoon", M3Themes.current (display).get ().name ());
		System.setProperty (M3Themes.PROPERTY, " System ");
		M3Themes.applyStartup (display);
		assertEquals (expectedSystem (), M3Themes.current (display).get ());
		Files.delete (file);
	}

	M3Theme expectedSystem () {
		return display.getHighContrast () ? M3Theme.HIGH_CONTRAST : M3SystemTheme.dark () ? M3Theme.DARK : M3Theme.LIGHT;
	}

	@Test
	public void test_applySystemAndListeners () {
		java.util.List<String> told = new ArrayList<> ();
		M3Themes.Listener listener = (d, from, to) -> told.add ((from == null ? "stock" : from.name ()) + ">" + (to == null ? "stock" : to.name ()));
		M3Themes.addListener (listener);
		M3Themes.applySystem (display);
		M3Themes.apply (display, M3Theme.DARK);
		M3Themes.reset (display);
		M3Themes.removeListener (listener);
		M3Themes.apply (display, M3Theme.LIGHT);
		String system = expectedSystem ().name ();
		assertEquals (java.util.List.of ("stock>" + system, system + ">dark", "dark>stock"), told);
		assertThrows (IllegalArgumentException.class, () -> M3Themes.addListener (null));
	}

	@Test
	public void test_argumentAndThreadChecks () throws Exception {
		assertThrows (IllegalArgumentException.class, () -> M3Themes.apply (null, M3Theme.DARK));
		assertThrows (IllegalArgumentException.class, () -> M3Themes.apply (display, null));
		AtomicReference<Throwable> thrown = new AtomicReference<> ();
		Thread other = new Thread (() -> {
			try {
				M3Themes.apply (display, M3Theme.DARK);
			} catch (Throwable t) {
				thrown.set (t);
			}
		});
		other.start ();
		other.join ();
		assertTrue (thrown.get () instanceof SWTException e && e.code == SWT.ERROR_THREAD_INVALID_ACCESS, String.valueOf (thrown.get ()));
	}

	@Test
	public void test_nativeTreatment (TestReporter reporter) {
		boolean available = M3NativeTheme.available ();
		reporter.publishEntry ("native", "available=" + available + " os=" + SWT.getPlatform ());
		M3Themes.apply (display, M3Theme.DARK);
		assertEquals (available, M3NativeTheme.apply (table, true));
		M3Themes.apply (display, M3Theme.LIGHT);
		assertEquals (available, M3NativeTheme.apply (table, false));
		assertEquals (rgb (M3Theme.LIGHT.get (M3ThemeSlot.LIST_BACKGROUND)), table.getBackground ().getRGB ());
	}

	@Test
	public void test_themeSwitcher () {
		M3ThemeSwitcher switcher = new M3ThemeSwitcher (shell, SWT.NONE);
		assertEquals (java.util.List.of ("stock", "system", "light", "dark", "high-contrast"), switcher.getEntries ());
		assertEquals (0, switcher.getCombo ().getSelectionIndex ());
		assertEquals ("High Contrast", switcher.getCombo ().getItem (4));
		assertTrue (switcher.choose (3) > 0);
		assertEquals (Optional.of (M3Theme.DARK), M3Themes.current (display));
		assertEquals (3, switcher.getCombo ().getSelectionIndex ());
		switcher.choose (1);
		assertEquals (Optional.of (expectedSystem ()), M3Themes.current (display));
		switcher.choose (0);
		assertEquals (Optional.empty (), M3Themes.current (display));
		assertThrows (IllegalArgumentException.class, () -> switcher.choose (5));
		M3Themes.apply (display, M3Theme.HIGH_CONTRAST);
		settle ();
		assertEquals (4, switcher.getCombo ().getSelectionIndex (), "a switch made elsewhere selects its entry");
		switcher.dispose ();
		M3Themes.apply (display, M3Theme.LIGHT);
		settle ();
	}

	@Test
	public void test_drawnClassKeptStock () {
		System.setProperty ("swt.widget.feature.Table.theme-provider", "false");
		RGB stock = table.getBackground ().getRGB ();
		boolean drawn = M3LookAndFeel.getUI (table) != null;
		M3Themes.apply (display, M3Theme.DARK);
		assertEquals (stock, table.getBackground ().getRGB ());
		assertEquals (drawn, Themes.explicit ("Table") != null, "a drawn Table keeps the native look");
		M3Themes.reset (display);
		assertNull (Themes.explicit ("Table"));
	}

	@Test
	public void test_moreMetricsAndColours () throws Exception {
		List list = (List) Arrays.stream (shell.getChildren ()).filter (List.class::isInstance).findFirst ().get ();
		assertEquals (list.getItemHeight (), M3Themes.metric (list, M3ThemeSlot.ROW_HEIGHT));
		M3Themes.apply (display, M3Theme.LIGHT.derive ("big").set (M3ThemeSlot.FONT_HEIGHT, 12).build ());
		assertEquals (12, M3Themes.metric (list, M3ThemeSlot.FONT_HEIGHT));
		Color selection = M3Themes.color (display, M3ThemeSlot.SELECTION);
		selection.dispose ();
		assertFalse (M3Themes.color (display, M3ThemeSlot.SELECTION).isDisposed (), "a colour disposed by a client is made again");
		Button gone = new Button (shell, SWT.PUSH);
		gone.dispose ();
		assertFalse (M3NativeTheme.apply (gone, true));
		java.lang.reflect.Method system = M3Themes.class.getDeclaredMethod ("system", boolean.class, boolean.class);
		system.setAccessible (true);
		assertSame (M3Theme.HIGH_CONTRAST, system.invoke (null, true, true));
		assertSame (M3Theme.DARK, system.invoke (null, false, true));
		assertSame (M3Theme.LIGHT, system.invoke (null, false, false));
		System.setProperty (M3Themes.PROPERTY, " ");
		assertEquals (0, M3Themes.applyStartup (display));
	}

	@Test
	public void test_switcherDisposedWhileASyncIsPending () {
		M3ThemeSwitcher switcher = new M3ThemeSwitcher (shell, SWT.NONE);
		M3Themes.apply (display, M3Theme.DARK);
		switcher.dispose ();
		settle ();
		assertTrue (switcher.isDisposed ());
		assertEquals (Optional.of (M3Theme.DARK), M3Themes.current (display));
	}
}
