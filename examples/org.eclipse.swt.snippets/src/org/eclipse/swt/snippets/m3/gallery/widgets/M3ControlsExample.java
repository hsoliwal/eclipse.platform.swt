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
import org.eclipse.swt.browser.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Every basic SWT control in its common styles: buttons, labels, text, spinner, scale, slider, progress bar, date and
 * time, link, group, sash and browser. With an argument only that control is shown.
 */
public class M3ControlsExample {
	//GALLERY: Button | push, check, radio, toggle, arrow and flat buttons
	//GALLERY: Label | text, separator and wrapped labels
	//GALLERY: Text | single, password, search and multi-line text
	//GALLERY: Spinner | spinner with digits and limits
	//GALLERY: Scale | horizontal and vertical scales
	//GALLERY: Slider | sliders with thumb and increment
	//GALLERY: ProgressBar | smooth, indeterminate and vertical progress bars
	//GALLERY: DateTime | date, time and calendar
	//GALLERY: Link | link with listener
	//GALLERY: Group | group with title and children
	//GALLERY: Sash | sash between two panes
	//GALLERY: Browser | browser with inline HTML (shows the reason when no browser engine is installed)
	//GALLERY: all | every control in one window

	static final Map<String, Consumer<Composite>> CONTROLS = new LinkedHashMap<> ();
	static {
		CONTROLS.put ("Button", M3ControlsExample::buttons);
		CONTROLS.put ("Label", M3ControlsExample::labels);
		CONTROLS.put ("Text", M3ControlsExample::texts);
		CONTROLS.put ("Spinner", M3ControlsExample::spinner);
		CONTROLS.put ("Scale", M3ControlsExample::scales);
		CONTROLS.put ("Slider", M3ControlsExample::sliders);
		CONTROLS.put ("ProgressBar", M3ControlsExample::progress);
		CONTROLS.put ("DateTime", M3ControlsExample::dates);
		CONTROLS.put ("Link", M3ControlsExample::link);
		CONTROLS.put ("Group", M3ControlsExample::group);
		CONTROLS.put ("Sash", M3ControlsExample::sash);
		CONTROLS.put ("Browser", M3ControlsExample::browser);
	}

	public static void main (String [] args) {
		Display display = new Display ();
		Shell shell = new Shell (display);
		String kind = args.length > 0 ? args [0] : "all";
		shell.setText ("M3ControlsExample: " + kind);
		shell.setLayout (new GridLayout (4, true));
		CONTROLS.forEach ((name, factory) -> {
			if ("all".equals (kind) || name.equals (kind)) cell (shell, name, factory);
		});
		shell.pack ();
		shell.open ();
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}

	static void cell (Composite parent, String name, Consumer<Composite> factory) {
		Group group = new Group (parent, SWT.NONE);
		group.setText (name);
		group.setLayout (new GridLayout (1, false));
		group.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		factory.accept (group);
	}

	static void buttons (Composite parent) {
		int [] styles = {SWT.PUSH, SWT.CHECK, SWT.RADIO, SWT.TOGGLE, SWT.ARROW | SWT.UP, SWT.PUSH | SWT.FLAT};
		String [] texts = {"Push", "Check", "Radio", "Toggle", "", "Flat"};
		for (int i = 0; i < styles.length; i++) new Button (parent, styles [i]).setText (texts [i]);
	}

	static void labels (Composite parent) {
		new Label (parent, SWT.NONE).setText ("Plain label");
		new Label (parent, SWT.SEPARATOR | SWT.HORIZONTAL).setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
		Label wrap = new Label (parent, SWT.WRAP);
		wrap.setText ("A wrapping label whose text is longer than its column.");
		wrap.setLayoutData (new GridData (160, SWT.DEFAULT));
	}

	static void texts (Composite parent) {
		new Text (parent, SWT.SINGLE | SWT.BORDER).setText ("single");
		new Text (parent, SWT.PASSWORD | SWT.BORDER).setText ("secret");
		new Text (parent, SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL).setMessage ("search");
		Text multi = new Text (parent, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.WRAP);
		multi.setText ("line 1\nline 2\nline 3");
		multi.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
	}

	static void spinner (Composite parent) {
		Spinner spinner = new Spinner (parent, SWT.BORDER);
		spinner.setValues (1234, 0, 100000, 2, 10, 100);
	}

	static void scales (Composite parent) {
		new Scale (parent, SWT.HORIZONTAL).setSelection (30);
		new Scale (parent, SWT.VERTICAL).setSelection (70);
	}

	static void sliders (Composite parent) {
		Slider slider = new Slider (parent, SWT.HORIZONTAL);
		slider.setValues (40, 0, 110, 10, 1, 10);
		new Slider (parent, SWT.VERTICAL).setSelection (20);
	}

	static void progress (Composite parent) {
		new ProgressBar (parent, SWT.SMOOTH).setSelection (60);
		new ProgressBar (parent, SWT.INDETERMINATE);
		new ProgressBar (parent, SWT.VERTICAL).setSelection (40);
	}

	static void dates (Composite parent) {
		new DateTime (parent, SWT.DATE | SWT.MEDIUM);
		new DateTime (parent, SWT.TIME | SWT.SHORT);
		new DateTime (parent, SWT.CALENDAR);
	}

	static void link (Composite parent) {
		Link link = new Link (parent, SWT.NONE);
		link.setText ("Visit <a href=\"https://www.eclipse.org/swt\">SWT</a> or <a>this</a>.");
		link.addListener (SWT.Selection, e -> link.setToolTipText ("clicked " + e.text));
	}

	static void group (Composite parent) {
		Group inner = new Group (parent, SWT.SHADOW_ETCHED_IN);
		inner.setText ("Inner group");
		inner.setLayout (new FillLayout (SWT.VERTICAL));
		for (String s : new String [] {"one", "two", "three"}) new Button (inner, SWT.RADIO).setText (s);
	}

	static void sash (Composite parent) {
		Composite area = new Composite (parent, SWT.BORDER);
		area.setLayoutData (new GridData (200, 80));
		Label left = new Label (area, SWT.NONE), right = new Label (area, SWT.NONE);
		left.setText ("left");
		right.setText ("right");
		Sash sash = new Sash (area, SWT.VERTICAL);
		sash.setBounds (98, 0, 4, 80);
		left.setBounds (0, 0, 98, 80);
		right.setBounds (102, 0, 98, 80);
		sash.addListener (SWT.Selection, e -> sash.setBounds (e.x, 0, 4, 80));
	}

	static void browser (Composite parent) {
		try {
			Browser browser = new Browser (parent, SWT.NONE);
			browser.setText ("<html><body><h3>Browser</h3><p>inline HTML</p></body></html>");
			browser.setLayoutData (new GridData (220, 120));
		} catch (SWTError e) {
			new Label (parent, SWT.WRAP).setText ("No browser engine: " + e.getMessage ());
		}
	}
}
