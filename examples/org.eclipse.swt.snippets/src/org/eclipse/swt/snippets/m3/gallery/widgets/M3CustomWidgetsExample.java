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
package org.eclipse.swt.snippets.m3.gallery.widgets;

import java.util.*;
import java.util.function.*;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * The SWT custom widgets: CTabFolder, StyledText (styles, 10,000 lines), CCombo, CLabel, SashForm, ScrolledComposite,
 * ViewForm, CBanner and TableCursor. The argument names the widget.
 */
public class M3CustomWidgetsExample {
	//GALLERY: CTabFolder | closable, reorderable tabs with a chevron
	//GALLERY: StyledText | 10,000 styled lines with ranges, line backgrounds and word wrap off
	//GALLERY: CCombo | flat and read-only CCombo
	//GALLERY: CLabel | CLabel with image, gradient and shortening
	//GALLERY: SashForm | horizontal and vertical SashForm
	//GALLERY: ScrolledComposite | ScrolledComposite with 200 buttons
	//GALLERY: ViewForm | ViewForm with top left, top right and content
	//GALLERY: CBanner | CBanner with left and right controls
	//GALLERY: TableCursor | TableCursor over a table

	static final Map<String, Consumer<Shell>> WIDGETS = new LinkedHashMap<> ();
	static {
		WIDGETS.put ("CTabFolder", M3CustomWidgetsExample::tabFolder);
		WIDGETS.put ("StyledText", M3CustomWidgetsExample::styledText);
		WIDGETS.put ("CCombo", M3CustomWidgetsExample::cCombo);
		WIDGETS.put ("CLabel", M3CustomWidgetsExample::cLabel);
		WIDGETS.put ("SashForm", M3CustomWidgetsExample::sashForm);
		WIDGETS.put ("ScrolledComposite", M3CustomWidgetsExample::scrolled);
		WIDGETS.put ("ViewForm", M3CustomWidgetsExample::viewForm);
		WIDGETS.put ("CBanner", M3CustomWidgetsExample::banner);
		WIDGETS.put ("TableCursor", M3CustomWidgetsExample::tableCursor);
	}

	public static void main (String [] args) {
		Display display = new Display ();
		Shell shell = new Shell (display);
		String kind = args.length > 0 ? args [0] : "CTabFolder";
		shell.setText ("M3CustomWidgetsExample: " + kind);
		shell.setLayout (new FillLayout ());
		Consumer<Shell> factory = WIDGETS.get (kind);
		if (factory == null) throw new IllegalArgumentException ("unknown widget " + kind + "; one of " + WIDGETS.keySet ());
		factory.accept (shell);
		shell.setSize (560, 400);
		shell.open ();
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}

	static void tabFolder (Shell shell) {
		CTabFolder folder = new CTabFolder (shell, SWT.BORDER | SWT.CLOSE);
		folder.setMRUVisible (true);
		folder.setSimple (false);
		for (int i = 0; i < 12; i++) {
			CTabItem item = new CTabItem (folder, SWT.NONE);
			item.setText ("Tab " + (i + 1));
			Label label = new Label (folder, SWT.CENTER);
			label.setText ("page " + (i + 1));
			item.setControl (label);
		}
		folder.setSelection (0);
	}

	static void styledText (Shell shell) {
		StyledText text = new StyledText (shell, SWT.MULTI | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
		StringBuilder content = new StringBuilder ();
		for (int i = 0; i < 10_000; i++) content.append ("line ").append (i).append (": the quick brown fox jumps over the lazy dog\n");
		text.setText (content.toString ());
		Display display = shell.getDisplay ();
		for (int line = 0; line < 50; line += 2) {
			int offset = text.getOffsetAtLine (line);
			text.setStyleRange (new StyleRange (offset, 4, display.getSystemColor (SWT.COLOR_BLUE), null, SWT.BOLD));
			text.setLineBackground (line + 1, 1, display.getSystemColor (SWT.COLOR_INFO_BACKGROUND));
		}
		text.setSelection (text.getOffsetAtLine (3), text.getOffsetAtLine (4));
	}

	static void cCombo (Shell shell) {
		Composite area = new Composite (shell, SWT.NONE);
		area.setLayout (new GridLayout (1, false));
		for (int style : new int [] {SWT.BORDER | SWT.FLAT, SWT.BORDER | SWT.READ_ONLY}) {
			CCombo combo = new CCombo (area, style);
			for (int i = 0; i < 40; i++) combo.add ("choice " + i);
			combo.select (2);
			combo.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false));
		}
	}

	static void cLabel (Shell shell) {
		Composite area = new Composite (shell, SWT.NONE);
		area.setLayout (new GridLayout (1, false));
		Display display = shell.getDisplay ();
		CLabel plain = new CLabel (area, SWT.SHADOW_OUT);
		plain.setText ("CLabel with an image and a very long text that is shortened when the label is narrow");
		plain.setImage (display.getSystemImage (SWT.ICON_INFORMATION));
		CLabel gradient = new CLabel (area, SWT.NONE);
		gradient.setText ("gradient background");
		gradient.setBackground (new Color [] {display.getSystemColor (SWT.COLOR_WHITE), display.getSystemColor (SWT.COLOR_LIST_SELECTION)}, new int [] {100});
	}

	static void sashForm (Shell shell) {
		SashForm outer = new SashForm (shell, SWT.HORIZONTAL);
		new Text (outer, SWT.MULTI | SWT.BORDER).setText ("left");
		SashForm inner = new SashForm (outer, SWT.VERTICAL);
		new Text (inner, SWT.MULTI | SWT.BORDER).setText ("top");
		new Text (inner, SWT.MULTI | SWT.BORDER).setText ("bottom");
		outer.setWeights (1, 2);
	}

	static void scrolled (Shell shell) {
		ScrolledComposite scrolled = new ScrolledComposite (shell, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
		Composite content = new Composite (scrolled, SWT.NONE);
		content.setLayout (new GridLayout (10, true));
		for (int i = 0; i < 200; i++) new Button (content, SWT.PUSH).setText ("button " + i);
		scrolled.setContent (content);
		scrolled.setExpandHorizontal (true);
		scrolled.setExpandVertical (true);
		scrolled.setMinSize (content.computeSize (SWT.DEFAULT, SWT.DEFAULT));
	}

	static void viewForm (Shell shell) {
		ViewForm form = new ViewForm (shell, SWT.BORDER | SWT.FLAT);
		CLabel title = new CLabel (form, SWT.NONE);
		title.setText ("View title");
		form.setTopLeft (title);
		form.setTopRight (M3ItemWidgetsExample.toolBar (form));
		Text content = new Text (form, SWT.MULTI);
		content.setText ("view content");
		form.setContent (content);
	}

	static void banner (Shell shell) {
		CBanner banner = new CBanner (shell, SWT.NONE);
		Label left = new Label (banner, SWT.NONE), right = new Label (banner, SWT.NONE);
		left.setText ("left side of the banner");
		right.setText ("right side");
		banner.setLeft (left);
		banner.setRight (right);
	}

	static void tableCursor (Shell shell) {
		Table table = new Table (shell, SWT.BORDER | SWT.FULL_SELECTION);
		table.setHeaderVisible (true);
		for (int c = 0; c < 3; c++) {
			TableColumn column = new TableColumn (table, SWT.NONE);
			column.setText ("Column " + c);
			column.setWidth (120);
		}
		for (int i = 0; i < 100; i++) new TableItem (table, SWT.NONE).setText (new String [] {"a" + i, "b" + i, "c" + i});
		TableCursor cursor = new TableCursor (table, SWT.NONE);
		cursor.setSelection (2, 1);
	}
}
