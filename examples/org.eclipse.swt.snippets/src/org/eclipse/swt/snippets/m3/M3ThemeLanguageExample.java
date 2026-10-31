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
package org.eclipse.swt.snippets.m3;

import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.m3.*;
import org.eclipse.swt.widgets.*;

/**
 * Theme and language switched live (docs/m3/theme-language/README.md): a Table (10,000 virtual rows), a Tree, a StyledText,
 * a CTabFolder and a Canvas under <code>M3ThemeSwitcher</code> and <code>M3LanguageSwitcher</code>. Run it in every render
 * mode with <code>-Dswt.widget.mode=viewport|original|custom|m3|gpu</code>. Arguments for probes:
 * <code>--theme=&lt;name&gt; --lang=&lt;tag&gt; --shot=&lt;png&gt; --exit</code>, or
 * <code>--matrix=&lt;theme&gt;:&lt;tag&gt;[:&lt;tab&gt;],... --shots=&lt;dir&gt; --exit</code> (one PNG per entry, named
 * <code>&lt;mode&gt;-&lt;theme&gt;-&lt;tag&gt;[-tab&lt;n&gt;].png</code>).
 */
public class M3ThemeLanguageExample {
	static final String BUNDLE = M3ThemeLanguageExample.class.getName () + "$Messages";
	static final int ROWS = 10_000;

	/** English (root) messages. */
	public static class Messages extends ListResourceBundle {
		@Override
		protected Object [][] getContents () {
			return new Object [][] {{"title", "Theme and language"}, {"table", "Table"}, {"tree", "Tree"}, {"text", "Text"}, {"canvas", "Canvas"},
				{"name", "Name"}, {"amount", "Amount"}, {"root", "Documents"}, {"child", "Report {0}"}, {"file", "&File"}, {"exit", "E&xit"},
				{"sample", "The quick brown fox jumps over the lazy dog."}, {"hint", "Choose a theme and a language above."},
				{"swt.m3.switcher.theme", "Theme"}, {"swt.m3.switcher.language", "Language"}, {"row", "Row {0}"}};
		}
	}

	/** German messages. */
	public static class Messages_de extends ListResourceBundle {
		@Override
		protected Object [][] getContents () {
			return new Object [][] {{"title", "Design und Sprache"}, {"table", "Tabelle"}, {"tree", "Baum"}, {"text", "Text"}, {"canvas", "Zeichenfläche"},
				{"name", "Name"}, {"amount", "Betrag"}, {"root", "Dokumente"}, {"child", "Bericht {0}"}, {"file", "&Datei"}, {"exit", "&Beenden"},
				{"sample", "Zwölf Boxkämpfer jagen Viktor quer über den großen Sylter Deich."}, {"hint", "Oben Design und Sprache wählen."},
				{"swt.m3.switcher.theme", "Design"}, {"swt.m3.switcher.language", "Sprache"}, {"row", "Zeile {0}"},
				{"swt.m3.theme.stock", "Standard"}, {"swt.m3.theme.system", "System"}, {"swt.m3.theme.light", "Hell"}, {"swt.m3.theme.dark", "Dunkel"}, {"swt.m3.theme.high-contrast", "Hoher Kontrast"}};
		}
	}

	/** Arabic messages (right to left). */
	public static class Messages_ar extends ListResourceBundle {
		@Override
		protected Object [][] getContents () {
			return new Object [][] {{"title", "السمة واللغة"}, {"table", "جدول"}, {"tree", "شجرة"}, {"text", "نص"}, {"canvas", "لوحة الرسم"},
				{"name", "الاسم"}, {"amount", "المبلغ"}, {"root", "المستندات"}, {"child", "تقرير {0}"}, {"file", "&ملف"}, {"exit", "&خروج"},
				{"sample", "نص حكيم له سر قاطع وذو شأن عظيم مكتوب على ثوب أخضر ومغلف بجلد أزرق."}, {"hint", "اختر سمة ولغة في الأعلى."},
				{"swt.m3.switcher.theme", "السمة"}, {"swt.m3.switcher.language", "اللغة"}, {"row", "صف {0}"},
				{"swt.m3.theme.stock", "الافتراضي"}, {"swt.m3.theme.system", "النظام"}, {"swt.m3.theme.light", "فاتح"}, {"swt.m3.theme.dark", "داكن"}, {"swt.m3.theme.high-contrast", "تباين عالٍ"}};
		}
	}

	/** Hebrew messages (right to left). */
	public static class Messages_he extends ListResourceBundle {
		@Override
		protected Object [][] getContents () {
			return new Object [][] {{"title", "ערכת נושא ושפה"}, {"table", "טבלה"}, {"tree", "עץ"}, {"text", "טקסט"}, {"canvas", "משטח ציור"},
				{"name", "שם"}, {"amount", "סכום"}, {"root", "מסמכים"}, {"child", "דוח {0}"}, {"file", "&קובץ"}, {"exit", "י&ציאה"},
				{"sample", "דג סקרן שט בים מאוכזב ולפתע מצא חברה."}, {"hint", "בחרו ערכת נושא ושפה למעלה."},
				{"swt.m3.switcher.theme", "ערכת נושא"}, {"swt.m3.switcher.language", "שפה"}, {"row", "שורה {0}"},
				{"swt.m3.theme.stock", "ברירת מחדל"}, {"swt.m3.theme.system", "מערכת"}, {"swt.m3.theme.light", "בהיר"}, {"swt.m3.theme.dark", "כהה"}, {"swt.m3.theme.high-contrast", "ניגודיות גבוהה"}};
		}
	}

	/** Japanese messages. */
	public static class Messages_ja extends ListResourceBundle {
		@Override
		protected Object [][] getContents () {
			return new Object [][] {{"title", "テーマと言語"}, {"table", "表"}, {"tree", "ツリー"}, {"text", "テキスト"}, {"canvas", "キャンバス"},
				{"name", "名前"}, {"amount", "金額"}, {"root", "ドキュメント"}, {"child", "レポート {0}"}, {"file", "ファイル(&F)"}, {"exit", "終了(&X)"},
				{"sample", "いろはにほへと ちりぬるを わかよたれそ つねならむ"}, {"hint", "上でテーマと言語を選んでください。"},
				{"swt.m3.switcher.theme", "テーマ"}, {"swt.m3.switcher.language", "言語"}, {"row", "行 {0}"},
				{"swt.m3.theme.stock", "標準"}, {"swt.m3.theme.system", "システム"}, {"swt.m3.theme.light", "ライト"}, {"swt.m3.theme.dark", "ダーク"}, {"swt.m3.theme.high-contrast", "ハイコントラスト"}};
		}
	}

	/** Hindi messages. */
	public static class Messages_hi extends ListResourceBundle {
		@Override
		protected Object [][] getContents () {
			return new Object [][] {{"title", "थीम और भाषा"}, {"table", "तालिका"}, {"tree", "वृक्ष"}, {"text", "पाठ"}, {"canvas", "कैनवास"},
				{"name", "नाम"}, {"amount", "राशि"}, {"root", "दस्तावेज़"}, {"child", "रिपोर्ट {0}"}, {"file", "&फ़ाइल"}, {"exit", "&बाहर निकलें"},
				{"sample", "ऋषियों को सताने वाले दुष्ट राक्षसों के राजा रावण का सर्वनाश करने वाले विष्णुवतार भगवान श्रीराम"}, {"hint", "ऊपर थीम और भाषा चुनें।"},
				{"swt.m3.switcher.theme", "थीम"}, {"swt.m3.switcher.language", "भाषा"}, {"row", "पंक्ति {0}"},
				{"swt.m3.theme.stock", "मानक"}, {"swt.m3.theme.system", "सिस्टम"}, {"swt.m3.theme.light", "हल्का"}, {"swt.m3.theme.dark", "गहरा"}, {"swt.m3.theme.high-contrast", "उच्च कंट्रास्ट"}};
		}
	}

	public static void main (String [] args) {
		Map<String, String> options = options (args);
		Display display = new Display ();
		M3Language.addBundle (BUNDLE, M3ThemeLanguageExample.class.getClassLoader ());
		Shell shell = create (display);
		shell.setSize (900, 640);
		shell.open ();
		apply (display, options);
		if (options.containsKey ("shot")) shot (display, shell, options.get ("shot"));
		if (options.containsKey ("matrix")) matrix (display, shell, options.get ("matrix"), options.getOrDefault ("shots", "."));
		if (options.containsKey ("exit")) shell.dispose ();
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}

	static Map<String, String> options (String [] args) {
		Map<String, String> options = new HashMap<> ();
		Arrays.stream (args).filter (a -> a.startsWith ("--")).map (a -> a.substring (2).split ("=", 2)).forEach (kv -> options.put (kv [0], kv.length > 1 ? kv [1] : ""));
		return options;
	}

	/* the theme and language of the arguments, then one settled frame */
	static void apply (Display display, Map<String, String> options) {
		if (options.containsKey ("theme")) M3Themes.apply (display, M3Themes.find (options.get ("theme")));
		if (options.containsKey ("lang")) M3Language.setLocale (Locale.forLanguageTag (options.get ("lang")));
		settle (display);
	}

	/* each theme:language[:tab] applied live on the same widgets, then shot */
	static void matrix (Display display, Shell shell, String entries, String dir) {
		String mode = System.getProperty ("swt.widget.mode", "viewport");
		CTabFolder folder = (CTabFolder) shell.getChildren () [1];
		for (String entry : entries.split (",")) {
			String [] parts = entry.split (":");
			int tab = parts.length > 2 ? Integer.parseInt (parts [2]) : 0;
			folder.setSelection (tab);
			apply (display, Map.of ("theme", parts [0], "lang", parts [1]));
			shot (display, shell, dir + "/" + mode + "-" + parts [0] + "-" + parts [1] + (tab == 0 ? "" : "-tab" + tab) + ".png");
		}
	}

	static void settle (Display display) {
		long until = System.currentTimeMillis () + 900;
		while (System.currentTimeMillis () < until) {
			if (!display.readAndDispatch ()) display.update ();
		}
	}

	public static Shell create (Display display) {
		Shell shell = new Shell (display);
		M3Language.register (shell, "title");
		shell.setLayout (new GridLayout (1, false));
		menu (shell);
		Composite bar = new Composite (shell, SWT.NONE);
		bar.setLayout (new RowLayout ());
		bar.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
		new M3ThemeSwitcher (bar, SWT.NONE);
		new M3LanguageSwitcher (bar, SWT.NONE);
		Label mode = new Label (bar, SWT.NONE);
		folder (shell, mode);
		return shell;
	}

	static void menu (Shell shell) {
		Menu bar = new Menu (shell, SWT.BAR);
		shell.setMenuBar (bar);
		MenuItem file = new MenuItem (bar, SWT.CASCADE);
		M3Language.register (file, "file");
		Menu menu = new Menu (shell, SWT.DROP_DOWN);
		file.setMenu (menu);
		MenuItem exit = new MenuItem (menu, SWT.PUSH);
		M3Language.register (exit, "exit");
		exit.addListener (SWT.Selection, e -> shell.close ());
	}

	static void folder (Shell shell, Label mode) {
		CTabFolder folder = new CTabFolder (shell, SWT.BORDER);
		folder.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		Table table = table (folder);
		mode.setText ("mode: " + M3Mode.name (M3Mode.of (table)));
		tab (folder, "table", table);
		tab (folder, "tree", tree (folder));
		tab (folder, "text", text (folder));
		tab (folder, "canvas", canvas (folder));
		folder.setSelection (0);
	}

	static void tab (CTabFolder folder, String key, Control control) {
		CTabItem item = new CTabItem (folder, SWT.NONE);
		M3Language.register (item, key);
		item.setControl (control);
	}

	/* virtual rows: the visible ones are asked for again after a language switch (numbers in the locale's format) */
	static Table table (Composite parent) {
		Table table = new Table (parent, SWT.VIRTUAL | SWT.FULL_SELECTION | SWT.MULTI);
		table.setHeaderVisible (true);
		table.setLinesVisible (true);
		column (table, "name", 220);
		column (table, "amount", 160);
		table.addListener (SWT.SetData, e -> row ((TableItem) e.item, table.indexOf ((TableItem) e.item)));
		table.setItemCount (ROWS);
		table.setSelection (3);
		M3Language.addListener ((from, to) -> table.getDisplay ().asyncExec (() -> { if (!table.isDisposed ()) table.clearAll (); }));
		return table;
	}

	static void column (Table table, String key, int width) {
		TableColumn column = new TableColumn (table, SWT.NONE);
		M3Language.register (column, key);
		column.setWidth (width);
	}

	static void row (TableItem item, int index) {
		item.setText (0, M3Language.format ("row", index));
		item.setText (1, M3Language.numberFormat ().format (index * 1234.5));
	}

	static Tree tree (Composite parent) {
		Tree tree = new Tree (parent, SWT.BORDER);
		TreeItem root = new TreeItem (tree, SWT.NONE);
		M3Language.register (root, "root");
		for (int i = 1; i <= 5; i++) new TreeItem (root, SWT.NONE).setText (String.valueOf (i));
		root.setExpanded (true);
		tree.setSelection (root.getItem (1));
		return tree;
	}

	static StyledText text (Composite parent) {
		StyledText text = new StyledText (parent, SWT.MULTI | SWT.WRAP | SWT.V_SCROLL);
		Runnable fill = () -> {
			text.setText (M3Language.message ("sample") + "\n\n" + M3Language.message ("hint"));
			text.setSelection (0, 9);
		};
		fill.run ();
		M3Language.addListener ((from, to) -> text.getDisplay ().asyncExec (() -> { if (!text.isDisposed ()) fill.run (); }));
		return text;
	}

	/* painted from the theme slots at paint time (M3Themes.color); the GC mirrors in a right-to-left shell */
	static Canvas canvas (Composite parent) {
		Canvas canvas = new Canvas (parent, SWT.DOUBLE_BUFFERED);
		canvas.addListener (SWT.Paint, e -> paint (canvas, e.gc));
		M3Themes.addListener ((display, from, to) -> { if (!canvas.isDisposed ()) canvas.redraw (); });
		M3Language.addListener ((from, to) -> canvas.getDisplay ().asyncExec (() -> { if (!canvas.isDisposed ()) canvas.redraw (); }));
		return canvas;
	}

	static void paint (Canvas canvas, GC gc) {
		Display display = canvas.getDisplay ();
		int [] slots = {M3ThemeSlot.ACCENT, M3ThemeSlot.ACCENT_2, M3ThemeSlot.SELECTION, M3ThemeSlot.ERROR, M3ThemeSlot.WARNING, M3ThemeSlot.SUCCESS};
		gc.setBackground (M3Themes.color (display, M3ThemeSlot.SURFACE));
		gc.fillRectangle (canvas.getClientArea ());
		for (int i = 0; i < slots.length; i++) {
			gc.setBackground (M3Themes.color (display, slots [i]));
			gc.fillRoundRectangle (16 + i * 56, 16, 48, 48, 8, 8);
		}
		gc.setForeground (M3Themes.color (display, M3ThemeSlot.FG));
		gc.setBackground (M3Themes.color (display, M3ThemeSlot.SURFACE));
		gc.drawText (M3Language.message ("sample"), 16, 80, true);
	}

	/*
	 * The client area as a PNG: each child of the shell printed (Control.print, which also works on a hidden desktop) at its
	 * place. GDI prints a right-to-left control mirrored into the left-to-right image, so its pixels are flipped back.
	 */
	static void shot (Display display, Shell shell, String file) {
		Rectangle area = shell.getClientArea ();
		Image image = new Image (display, area.width, area.height);
		GC gc = new GC (image);
		gc.setBackground (shell.getBackground ());
		gc.fillRectangle (0, 0, area.width, area.height);
		boolean rtl = shell.getOrientation () == SWT.RIGHT_TO_LEFT, printed = true;
		for (Control child : shell.getChildren ()) printed &= part (display, gc, child, rtl, area.width);
		gc.dispose ();
		save (image, file);
		if (Boolean.getBoolean ("m3.probe.screen")) screen (display, shell, file.replace (".png", "-screen.png"));
		System.out.println ("PROBE shot " + file + " printed=" + printed + " theme=" + M3Themes.current (display).map (M3Theme::name).orElse ("stock")
			+ " locale=" + M3Language.getLocale ().toLanguageTag () + " orientation=" + (rtl ? "rtl" : "ltr"));
	}

	static void save (Image image, String file) {
		ImageLoader loader = new ImageLoader ();
		loader.data = new ImageData [] {image.getImageData ()};
		loader.save (file, SWT.IMAGE_PNG);
		image.dispose ();
	}

	/* the screen pixels of the client area (GC on the display), where the desktop composes them */
	static void screen (Display display, Shell shell, String file) {
		Rectangle area = display.map (shell, null, shell.getClientArea ());
		Image image = new Image (display, area.width, area.height);
		GC gc = new GC (display);
		gc.copyArea (image, area.x, area.y);
		gc.dispose ();
		save (image, file);
	}

	static boolean part (Display display, GC gc, Control child, boolean rtl, int width) {
		Rectangle bounds = child.getBounds ();
		Image part = new Image (display, bounds.width, bounds.height);
		GC partGc = new GC (part);
		boolean printed = child.print (partGc);
		partGc.dispose ();
		Image placed = new Image (display, rtl ? flip (part.getImageData ()) : part.getImageData ());
		gc.drawImage (placed, rtl ? width - bounds.x - bounds.width : bounds.x, bounds.y);
		placed.dispose ();
		part.dispose ();
		return printed;
	}

	static ImageData flip (ImageData data) {
		int [] row = new int [data.width], mirrored = new int [data.width];
		for (int y = 0; y < data.height; y++) {
			data.getPixels (0, y, data.width, row, 0);
			for (int x = 0; x < data.width; x++) mirrored [x] = row [data.width - 1 - x];
			data.setPixels (0, y, data.width, mirrored, 0);
		}
		return data;
	}
}
