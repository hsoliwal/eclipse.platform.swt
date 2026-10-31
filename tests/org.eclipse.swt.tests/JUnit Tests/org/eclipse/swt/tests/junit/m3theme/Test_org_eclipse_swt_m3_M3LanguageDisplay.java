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

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.m3.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

/**
 * The runtime language switch on a display (hidden desktop): registered texts, tooltips, menu items and messages are set again
 * without recreating any widget (same objects, same native handles), right-to-left shells flip and flip back, a class switched
 * off keeps its orientation, disposed widgets drop out, the argument checks, and the language switcher. The assertions are the
 * same in every render mode.
 */
public class Test_org_eclipse_swt_m3_M3LanguageDisplay {
	static final String BASE = Test_org_eclipse_swt_m3_M3LanguageDisplay.class.getName () + "$Labels";
	Display display;
	Shell shell;

	/** Root labels. */
	public static class Labels extends ListResourceBundle {
		@Override
		protected Object [][] getContents () {
			return new Object [][] {{"title", "Window"}, {"ok", "OK"}, {"tip", "Tip"}, {"hint", "Search"}, {"file", "File"}, {"name", "Name"},
				{"swt.m3.switcher.language", "Language"}};
		}
	}

	/** German labels. */
	public static class Labels_de extends ListResourceBundle {
		@Override
		protected Object [][] getContents () {
			return new Object [][] {{"title", "Fenster"}, {"ok", "Gut"}, {"tip", "Hinweis"}, {"hint", "Suchen"}, {"file", "Datei"}, {"name", "Name "},
				{"swt.m3.switcher.language", "Sprache"}};
		}
	}

	/** Arabic labels. */
	public static class Labels_ar extends ListResourceBundle {
		@Override
		protected Object [][] getContents () {
			return new Object [][] {{"title", "نافذة"}, {"ok", "موافق"}, {"tip", "تلميح"}, {"hint", "بحث"}, {"file", "ملف"}, {"name", "الاسم"},
				{"swt.m3.switcher.language", "اللغة"}};
		}
	}

	/** A widget class of its own for the feature switch. */
	static class Fixed extends Canvas {
		Fixed (Composite parent) {
			super (parent, SWT.NONE);
		}
	}

	@BeforeEach
	public void setUp () {
		M3Language.reset ();
		M3Language.setLocale (Locale.ENGLISH);
		M3Language.addBundle (BASE, getClass ().getClassLoader ());
		display = Display.getDefault ();
		shell = new Shell (display);
		shell.setLayout (new GridLayout (2, false));
	}

	@AfterEach
	public void tearDown () {
		shell.dispose ();
		M3Language.reset ();
		System.getProperties ().stringPropertyNames ().stream ().filter (k -> k.startsWith ("swt.widget.feature.")).forEach (System::clearProperty);
	}

	void settle () {
		long until = System.currentTimeMillis () + 200;
		while (System.currentTimeMillis () < until) {
			if (!display.readAndDispatch ()) display.update ();
		}
	}

	static Object handle (Widget widget) throws ReflectiveOperationException {
		Field field = Arrays.stream (widget.getClass ().getFields ()).filter (f -> f.getName ().equals ("handle")).findFirst ().orElse (null);
		return field == null ? widget : field.get (widget);
	}

	@Test
	public void test_relabelWithoutRecreate () throws Exception {
		M3Language.register (shell, "title");
		Button ok = new Button (shell, SWT.PUSH);
		M3Language.register (ok, "ok");
		M3Language.register (ok, M3Language.TOOLTIP, "tip");
		Label label = new Label (shell, SWT.NONE);
		M3Language.register (label, M3Language.TEXT, "name", "Name");
		Text search = new Text (shell, SWT.SEARCH);
		M3Language.register (search, M3Language.MESSAGE, "hint");
		Group group = new Group (shell, SWT.NONE);
		M3Language.register (group, "file");
		Link link = new Link (shell, SWT.NONE);
		M3Language.register (link, "ok");
		CLabel clabel = new CLabel (shell, SWT.NONE);
		M3Language.register (clabel, "ok");
		ToolBar bar = new ToolBar (shell, SWT.NONE);
		ToolItem tool = new ToolItem (bar, SWT.PUSH);
		M3Language.register (tool, "file");
		M3Language.register (tool, M3Language.TOOLTIP, "tip");
		Menu menuBar = new Menu (shell, SWT.BAR);
		shell.setMenuBar (menuBar);
		MenuItem file = new MenuItem (menuBar, SWT.CASCADE);
		M3Language.register (file, "file");
		M3Language.register (file, M3Language.TOOLTIP, "tip");
		Table table = new Table (shell, SWT.NONE);
		TableColumn column = new TableColumn (table, SWT.NONE);
		M3Language.register (column, "name");
		M3Language.register (column, M3Language.TOOLTIP, "tip");
		Tree tree = new Tree (shell, SWT.NONE);
		TreeColumn treeColumn = new TreeColumn (tree, SWT.NONE);
		M3Language.register (treeColumn, M3Language.TOOLTIP, "tip");
		CTabFolder folder = new CTabFolder (shell, SWT.NONE);
		CTabItem tab = new CTabItem (folder, SWT.NONE);
		M3Language.register (tab, "file");
		M3Language.register (tab, M3Language.TOOLTIP, "tip");
		TabFolder tabs = new TabFolder (shell, SWT.NONE);
		TabItem tabItem = new TabItem (tabs, SWT.NONE);
		M3Language.register (tabItem, "ok");
		M3Language.register (tabItem, M3Language.TOOLTIP, "tip");
		M3Language.register (table, M3Language.TOOLTIP, "tip");
		shell.pack ();
		shell.open ();
		assertEquals ("Window", shell.getText ());
		assertEquals ("Search", search.getMessage ());
		Widget [] widgets = {shell, ok, label, search, group, link, clabel, tool, file, column, treeColumn, tab, tabItem, table};
		Object [] handles = new Object [widgets.length];
		for (int i = 0; i < widgets.length; i++) handles [i] = handle (widgets [i]);
		assertEquals (20, M3Language.registered (display));
		assertEquals (20, M3Language.setLocale (Locale.GERMAN));
		settle ();
		assertEquals ("Fenster", shell.getText ());
		assertEquals ("Gut", ok.getText ());
		assertEquals ("Hinweis", ok.getToolTipText ());
		assertEquals ("Name ", label.getText ());
		assertEquals ("Suchen", search.getMessage ());
		assertEquals ("Datei", group.getText ());
		assertEquals ("Gut", link.getText ());
		assertEquals ("Gut", clabel.getText ());
		assertEquals ("Datei", tool.getText ());
		assertEquals ("Hinweis", tool.getToolTipText ());
		assertEquals ("Datei", file.getText ());
		assertEquals ("Hinweis", file.getToolTipText ());
		assertEquals ("Name ", column.getText ());
		assertEquals ("Hinweis", column.getToolTipText ());
		assertEquals ("Hinweis", treeColumn.getToolTipText ());
		assertEquals ("Datei", tab.getText ());
		assertEquals ("Hinweis", tab.getToolTipText ());
		assertEquals ("Gut", tabItem.getText ());
		assertEquals ("Hinweis", tabItem.getToolTipText ());
		assertEquals ("Hinweis", table.getToolTipText ());
		for (int i = 0; i < widgets.length; i++) {
			assertFalse (widgets [i].isDisposed ());
			assertEquals (handles [i], handle (widgets [i]), "same native handle: " + widgets [i]);
		}
	}

	@Test
	public void test_rightToLeftFlipAndBack () {
		Table table = new Table (shell, SWT.NONE);
		Fixed fixed = new Fixed (shell);
		Button ok = new Button (shell, SWT.PUSH);
		M3Language.register (ok, "ok");
		System.setProperty ("swt.widget.feature.Fixed.language-switch", "false");
		shell.open ();
		assertEquals (SWT.LEFT_TO_RIGHT, shell.getOrientation ());
		M3Language.setLocale (Locale.forLanguageTag ("ar"));
		settle ();
		assertEquals ("موافق", ok.getText ());
		assertEquals (SWT.RIGHT_TO_LEFT, shell.getOrientation ());
		assertEquals (SWT.RIGHT_TO_LEFT, table.getOrientation ());
		assertEquals (SWT.RIGHT_TO_LEFT, ok.getOrientation ());
		assertEquals (SWT.LEFT_TO_RIGHT, fixed.getOrientation (), "language-switch=false keeps the class");
		assertFalse (M3Language.follows (fixed));
		assertTrue (M3Language.follows (table));
		assertEquals (0, M3Language.flip (display), "already right to left");
		M3Language.setLocale (Locale.forLanguageTag ("he"));
		assertEquals (SWT.RIGHT_TO_LEFT, shell.getOrientation ());
		M3Language.setLocale (Locale.ENGLISH);
		settle ();
		assertEquals ("OK", ok.getText ());
		assertEquals (SWT.LEFT_TO_RIGHT, shell.getOrientation ());
		assertEquals (SWT.LEFT_TO_RIGHT, table.getOrientation ());
	}

	@Test
	public void test_disposedAndUnregistered () {
		Button a = new Button (shell, SWT.PUSH), b = new Button (shell, SWT.PUSH);
		M3Language.register (a, "ok");
		M3Language.register (a, "ok");
		M3Language.register (b, "ok");
		assertEquals (2, M3Language.registered (display), "registering a label again replaces its key");
		a.dispose ();
		assertEquals (1, M3Language.registered (display));
		M3Language.unregister (b);
		assertEquals (0, M3Language.registered (display));
		M3Language.setLocale (Locale.GERMAN);
		assertEquals ("OK", b.getText ());
		M3Language.unregister (a);
		for (int i = 0; i < 40; i++) M3Language.register (new Label (shell, SWT.NONE), "ok");
		assertEquals (40, M3Language.relabel (display));
	}

	@Test
	public void test_registerChecks () throws Exception {
		Table table = new Table (shell, SWT.NONE);
		Button button = new Button (shell, SWT.PUSH);
		TableItem item = new TableItem (table, SWT.NONE);
		assertThrows (IllegalArgumentException.class, () -> M3Language.register (table, "ok"), "a Table has no text label");
		assertThrows (IllegalArgumentException.class, () -> M3Language.register (button, M3Language.MESSAGE, "hint"));
		assertThrows (IllegalArgumentException.class, () -> M3Language.register (item, M3Language.TOOLTIP, "tip"));
		assertThrows (IllegalArgumentException.class, () -> M3Language.register (button, (byte) 7, "ok"));
		assertThrows (IllegalArgumentException.class, () -> M3Language.register (button, M3Language.TEXT, "ok", null));
		Button gone = new Button (shell, SWT.PUSH);
		gone.dispose ();
		assertThrows (SWTException.class, () -> M3Language.register (gone, "ok"));
		M3Language.unregister (gone);
		AtomicReference<Throwable> thrown = new AtomicReference<> ();
		Thread other = new Thread (() -> {
			try {
				M3Language.register (button, "ok");
			} catch (Throwable t) {
				thrown.set (t);
			}
		});
		other.start ();
		other.join ();
		assertTrue (thrown.get () instanceof SWTException e && e.code == SWT.ERROR_THREAD_INVALID_ACCESS, String.valueOf (thrown.get ()));
	}

	@Test
	public void test_languageSwitcher () {
		M3LanguageSwitcher switcher = new M3LanguageSwitcher (shell, SWT.NONE);
		assertTrue (switcher.getLanguages ().contains ("en"));
		assertEquals (M3LanguageSwitcher.DEFAULT_COUNT, switcher.getLanguages ().size (), "English is among the most translated");
		switcher.setLanguages ("en", "de", "ar");
		assertEquals (java.util.List.of ("en", "de", "ar"), switcher.getLanguages ());
		assertEquals ("Deutsch — German", switcher.getCombo ().getItem (1));
		assertEquals ("English", switcher.getCombo ().getItem (0));
		assertEquals (0, switcher.getCombo ().getSelectionIndex ());
		shell.open ();
		switcher.choose (2);
		settle ();
		assertEquals (Locale.forLanguageTag ("ar"), M3Language.getLocale ());
		assertEquals (SWT.RIGHT_TO_LEFT, shell.getOrientation ());
		assertTrue (switcher.getPreview ().getText ().endsWith ("RTL"), switcher.getPreview ().getText ());
		assertEquals ("اللغة", ((Label) switcher.getChildren () [0]).getText ());
		M3Language.setLocale (Locale.GERMAN);
		settle ();
		assertEquals (1, switcher.getCombo ().getSelectionIndex (), "a switch made elsewhere selects its entry");
		assertEquals ("Sprache", ((Label) switcher.getChildren () [0]).getText ());
		assertThrows (IllegalArgumentException.class, () -> switcher.setLanguages ("en", "xx"));
		assertThrows (IllegalArgumentException.class, () -> switcher.choose (3));
		switcher.dispose ();
		M3Language.setLocale (Locale.ENGLISH);
		settle ();
	}

	@Test
	public void test_switcherOffersTheActiveLanguage () {
		M3Language.setLocale (Locale.forLanguageTag ("dv"));
		M3LanguageSwitcher switcher = new M3LanguageSwitcher (shell, SWT.NONE);
		assertEquals (M3LanguageSwitcher.DEFAULT_COUNT + 1, switcher.getLanguages ().size ());
		assertEquals ("dv", switcher.getLanguages ().get (M3LanguageSwitcher.DEFAULT_COUNT));
		assertEquals (M3LanguageSwitcher.DEFAULT_COUNT, switcher.getCombo ().getSelectionIndex ());
		M3Language.setLocale (Locale.forLanguageTag ("xx"));
		switcher.setLanguages ("en");
		assertEquals ("", switcher.getPreview ().getText (), "an active language that is not offered selects nothing");
	}

	/** A widget class switched on explicitly. */
	static class OptIn extends Canvas {
		OptIn (Composite parent) {
			super (parent, SWT.NONE);
		}
	}

	@Test
	public void test_classSwitchedOnFollows () {
		System.setProperty ("swt.widget.feature.OptIn.language-switch", "true");
		assertTrue (M3Language.follows (new OptIn (shell)));
	}

	@Test
	public void test_otherDisplayRelabelledOnItsThread () throws Exception {
		AtomicReference<Display> other = new AtomicReference<> ();
		AtomicReference<Button> button = new AtomicReference<> ();
		java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch (1);
		AtomicBoolean stop = new AtomicBoolean ();
		Thread thread = new Thread (() -> {
			Display second = new Display ();
			Shell secondShell = new Shell (second);
			Button b = new Button (secondShell, SWT.PUSH);
			M3Language.register (b, "ok");
			other.set (second);
			button.set (b);
			ready.countDown ();
			while (!stop.get ()) {
				if (!second.readAndDispatch ()) second.sleep ();
			}
			second.dispose ();
		});
		thread.start ();
		assertTrue (ready.await (30, java.util.concurrent.TimeUnit.SECONDS));
		assertEquals ("OK", text (other.get (), button.get ()));
		M3Language.setLocale (Locale.GERMAN);
		long until = System.currentTimeMillis () + 10_000;
		while (!"Gut".equals (text (other.get (), button.get ())) && System.currentTimeMillis () < until) Thread.sleep (20);
		assertEquals ("Gut", text (other.get (), button.get ()), "the other display relabelled on its own thread");
		stop.set (true);
		other.get ().wake ();
		thread.join (30_000);
		assertTrue (other.get ().isDisposed ());
		M3Language.setLocale (Locale.ENGLISH);
	}

	static String text (Display display, Button button) {
		AtomicReference<String> text = new AtomicReference<> ();
		display.syncExec (() -> text.set (button.getText ()));
		return text.get ();
	}

	@Test
	public void test_switcherDisposedWhileASyncIsPending () {
		M3LanguageSwitcher switcher = new M3LanguageSwitcher (shell, SWT.NONE);
		M3Language.setLocale (Locale.GERMAN);
		switcher.dispose ();
		settle ();
		assertTrue (switcher.isDisposed ());
		M3Language.setLocale (Locale.ENGLISH);
	}
}
