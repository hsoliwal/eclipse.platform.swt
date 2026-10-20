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
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.function.*;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

/**
 * Brief rule 25 (docs/m3/visibility/VISIBILITY_AND_VIEWPORT.md): SWT fires <code>SWT.PartVisible</code> and
 * <code>SWT.PartHidden</code> itself. A matrix of every trigger (setVisible of an ancestor, CTabFolder and TabFolder
 * selection, StackLayout top control, ExpandItem expand and collapse, zero size, shell minimize and restore,
 * setParent, setPartHidden) at nesting depths 0, 1, 3 and 8: exactly one event per transition with its cause and
 * origin, none for no-op changes, none for a listening control outside the changed subtree, ancestors before
 * descendants, and isPartVisible agreeing with the events.
 */
public class Test_org_eclipse_swt_widgets_Control_PartVisibility {
	Display display;
	Shell shell;
	final java.util.List<String> log = new ArrayList<> ();

	@BeforeEach
	public void setUp () {
		display = Display.getDefault ();
		shell = new Shell (display, SWT.SHELL_TRIM);
		shell.setBounds (40, 40, 400, 400);
		shell.setLayout (new FillLayout ());
	}

	@AfterEach
	public void tearDown () {
		if (shell != null && !shell.isDisposed ()) shell.dispose ();
	}

	/* depth nested composites under the parent, then the listening leaf; returns the leaf */
	Control nest (Composite parent, int depth, String name) {
		Composite current = parent;
		for (int i = 0; i < depth; i++) {
			Composite inner = new Composite (current, SWT.NONE);
			inner.setLayout (new FillLayout ());
			current = inner;
		}
		Canvas leaf = new Canvas (current, SWT.NONE);
		listen (leaf, name);
		return leaf;
	}

	void listen (Control control, String name) {
		control.addPartVisibilityListener (new PartVisibilityListener () {
			@Override
			public void partVisible (PartVisibilityEvent e) {
				log.add (name + " visible " + e.cause + " " + (e.origin == null ? "-" : e.origin.getData ("name")));
			}

			@Override
			public void partHidden (PartVisibilityEvent e) {
				log.add (name + " hidden " + e.cause + " " + (e.origin == null ? "-" : e.origin.getData ("name")));
			}
		});
	}

	static <T extends Widget> T named (T widget, String name) {
		widget.setData ("name", name);
		return widget;
	}

	void open () {
		shell.open ();
		ViewportDifferential.drain (display);
	}

	/* runs the change, drains, and checks the log equals the expected lines (then clears it) */
	void expect (Runnable change, String... lines) {
		log.clear ();
		change.run ();
		ViewportDifferential.drain (display);
		assertEquals (java.util.List.of (lines), log);
		log.clear ();
	}

	static final int V = PartVisibilityEvent.VISIBILITY, Z = PartVisibilityEvent.SIZE, M = PartVisibilityEvent.MINIMIZE,
		R = PartVisibilityEvent.REPARENT, C = PartVisibilityEvent.CONTAINER;

	@ParameterizedTest
	@ValueSource (ints = {0, 1, 3, 8})
	public void setVisibleOfAnAncestor (int depth) {
		Composite page = named (new Composite (shell, SWT.NONE), "page");
		page.setLayout (new FillLayout ());
		Control leaf = nest (page, depth, "leaf");
		Control outside = nest (shell, 0, "outside");
		open ();
		assertTrue (leaf.isPartVisible ());
		expect (() -> page.setVisible (false), "leaf hidden " + V + " page");
		assertFalse (leaf.isPartVisible ());
		assertTrue (outside.isPartVisible (), "a sibling subtree is untouched");
		expect (() -> page.setVisible (false));
		expect (() -> page.setVisible (true), "leaf visible " + V + " page");
		expect (() -> page.setVisible (true));
		expect (() -> leaf.setVisible (false), "leaf hidden " + V + " -");
		expect (() -> page.setVisible (false));
		expect (() -> leaf.setVisible (true));
		expect (() -> page.setVisible (true), "leaf visible " + V + " page");
	}

	@ParameterizedTest
	@ValueSource (ints = {0, 1, 3, 8})
	public void cTabFolderSelection (int depth) {
		CTabFolder folder = new CTabFolder (shell, SWT.BORDER);
		CTabItem first = new CTabItem (folder, SWT.NONE), second = new CTabItem (folder, SWT.NONE);
		first.setControl (named (new Label (folder, SWT.NONE), "first"));
		Composite page = named (new Composite (folder, SWT.NONE), "page");
		page.setLayout (new FillLayout ());
		Control leaf = nest (page, depth, "leaf");
		second.setControl (page);
		folder.setSelection (second);
		open ();
		assertTrue (leaf.isPartVisible ());
		expect (() -> folder.setSelection (first), "leaf hidden " + V + " page");
		expect (() -> folder.setSelection (first));
		expect (() -> folder.setSelection (second), "leaf visible " + V + " page");
	}

	@ParameterizedTest
	@ValueSource (ints = {0, 1, 3, 8})
	public void tabFolderSelection (int depth) {
		TabFolder folder = new TabFolder (shell, SWT.NONE);
		TabItem first = new TabItem (folder, SWT.NONE), second = new TabItem (folder, SWT.NONE);
		first.setControl (named (new Label (folder, SWT.NONE), "first"));
		Composite page = named (new Composite (folder, SWT.NONE), "page");
		page.setLayout (new FillLayout ());
		Control leaf = nest (page, depth, "leaf");
		second.setControl (page);
		folder.setSelection (1);
		open ();
		assertTrue (leaf.isPartVisible ());
		expect (() -> folder.setSelection (0), "leaf hidden " + V + " page");
		expect (() -> folder.setSelection (0));
		expect (() -> folder.setSelection (1), "leaf visible " + V + " page");
	}

	@ParameterizedTest
	@ValueSource (ints = {0, 1, 3, 8})
	public void stackLayoutTopControl (int depth) {
		Composite stack = new Composite (shell, SWT.NONE);
		StackLayout layout = new StackLayout ();
		stack.setLayout (layout);
		Composite top = named (new Composite (stack, SWT.NONE), "top"), page = named (new Composite (stack, SWT.NONE), "page");
		page.setLayout (new FillLayout ());
		Control leaf = nest (page, depth, "leaf");
		layout.topControl = page;
		open ();
		assertTrue (leaf.isPartVisible ());
		expect (() -> {
			layout.topControl = top;
			stack.layout ();
		}, "leaf hidden " + V + " page");
		expect (stack::layout);
		expect (() -> {
			layout.topControl = page;
			stack.layout ();
		}, "leaf visible " + V + " page");
	}

	@ParameterizedTest
	@ValueSource (ints = {0, 1, 3, 8})
	public void expandItemCollapseAndExpand (int depth) {
		ExpandBar bar = new ExpandBar (shell, SWT.V_SCROLL);
		ExpandItem item = new ExpandItem (bar, SWT.NONE);
		item.setHeight (150);
		Composite page = named (new Composite (bar, SWT.NONE), "page");
		page.setLayout (new FillLayout ());
		Control leaf = nest (page, depth, "leaf");
		item.setControl (page);
		item.setExpanded (true);
		open ();
		assertTrue (leaf.isPartVisible ());
		expect (() -> item.setExpanded (false), "leaf hidden " + V + " page");
		expect (() -> item.setExpanded (false));
		expect (() -> item.setExpanded (true), "leaf visible " + V + " page");
	}

	@ParameterizedTest
	@ValueSource (ints = {0, 1, 3, 8})
	public void zeroSize (int depth) {
		Composite holder = new Composite (shell, SWT.NONE);
		Composite page = named (new Composite (holder, SWT.NONE), "page");
		page.setLayout (new FillLayout ());
		page.setBounds (0, 0, 200, 200);
		Control leaf = nest (page, depth, "leaf");
		page.layout (true, true);
		open ();
		assertTrue (leaf.isPartVisible ());
		expect (() -> page.setSize (0, 200), "leaf hidden " + Z + " page");
		expect (() -> page.setSize (0, 100));
		expect (() -> page.setSize (200, 200), "leaf visible " + Z + " page");
		expect (() -> page.setSize (210, 200));
	}

	@ParameterizedTest
	@ValueSource (ints = {0, 1, 3, 8})
	public void shellMinimizeAndRestore (int depth) {
		named (shell, "shell");
		Control leaf = nest (shell, depth, "leaf");
		open ();
		assertTrue (leaf.isPartVisible ());
		expect (() -> shell.setMinimized (true), "leaf hidden " + M + " shell");
		assertFalse (leaf.isPartVisible ());
		expect (() -> shell.setMinimized (false), "leaf visible " + M + " shell");
		expect (() -> shell.setVisible (false), "leaf hidden " + V + " shell");
		expect (() -> shell.setVisible (true), "leaf visible " + V + " shell");
	}

	@ParameterizedTest
	@ValueSource (ints = {0, 1, 3, 8})
	public void setParentIntoAHiddenComposite (int depth) {
		Composite visible = named (new Composite (shell, SWT.NONE), "visible"), hidden = named (new Composite (shell, SWT.NONE), "hidden");
		visible.setLayout (new FillLayout ());
		hidden.setLayout (new FillLayout ());
		Composite branch = named (new Composite (visible, SWT.NONE), "branch");
		branch.setLayout (new FillLayout ());
		Control leaf = nest (branch, depth, "leaf");
		open ();
		hidden.setVisible (false);
		ViewportDifferential.drain (display);
		assertTrue (leaf.isPartVisible ());
		expect (() -> assertTrue (branch.setParent (hidden)), "leaf hidden " + R + " branch");
		expect (() -> hidden.setVisible (true), "leaf visible " + V + " hidden");
		expect (() -> assertTrue (branch.setParent (visible)));
	}

	@ParameterizedTest
	@ValueSource (ints = {0, 1, 3, 8})
	public void setPartHiddenByAContainer (int depth) {
		Composite page = named (new Composite (shell, SWT.NONE), "page");
		page.setLayout (new FillLayout ());
		Control leaf = nest (page, depth, "leaf");
		open ();
		expect (() -> page.setPartHidden (true), "leaf hidden " + C + " page");
		assertTrue (page.getPartHidden ());
		expect (() -> page.setPartHidden (true));
		expect (() -> page.setVisible (false));
		expect (() -> page.setPartHidden (false));
		expect (() -> page.setVisible (true), "leaf visible " + V + " page");
		assertFalse (page.getPartHidden ());
	}

	@Test
	public void ancestorsAreToldBeforeDescendantsAndOnlyOnce () {
		Composite page = named (new Composite (shell, SWT.NONE), "page");
		page.setLayout (new FillLayout ());
		Composite middle = new Composite (page, SWT.NONE);
		middle.setLayout (new FillLayout ());
		listen (middle, "middle");
		nest (middle, 2, "leaf");
		listen (page, "pageself");
		open ();
		expect (() -> page.setVisible (false), "pageself hidden " + V + " page", "middle hidden " + V + " page", "leaf hidden " + V + " page");
		expect (() -> page.setVisible (true), "pageself visible " + V + " page", "middle visible " + V + " page", "leaf visible " + V + " page");
	}

	@Test
	public void noEventWhenTheFirstListenerIsAddedOrOnDisposeAndTheTypedListenerCanBeRemoved () {
		Composite page = named (new Composite (shell, SWT.NONE), "page");
		page.setLayout (new FillLayout ());
		Canvas leaf = new Canvas (page, SWT.NONE);
		open ();
		int [] events = {0};
		PartVisibilityListener listener = PartVisibilityListener.partHiddenAdapter (e -> events [0]++);
		expect (() -> leaf.addPartVisibilityListener (listener));
		page.setVisible (false);
		ViewportDifferential.drain (display);
		assertEquals (1, events [0]);
		page.setVisible (true);
		leaf.removePartVisibilityListener (listener);
		page.setVisible (false);
		ViewportDifferential.drain (display);
		assertEquals (1, events [0], "removed");
		PartVisibilityListener visible = PartVisibilityListener.partVisibleAdapter (e -> events [0] += 10);
		leaf.addPartVisibilityListener (visible);
		page.setVisible (true);
		ViewportDifferential.drain (display);
		assertEquals (11, events [0]);
		expect (page::dispose);
		assertThrows (IllegalArgumentException.class, () -> shell.removePartVisibilityListener (null));
	}

	@Test
	public void untypedListenersCarryCauseAndOrigin () {
		Composite page = named (new Composite (shell, SWT.NONE), "page");
		page.setLayout (new FillLayout ());
		Canvas leaf = new Canvas (page, SWT.NONE);
		java.util.List<String> events = new ArrayList<> ();
		BiFunction<String, Event, String> line = (kind, e) -> kind + " " + e.detail + " " + ((Widget) e.item).getData ("name");
		leaf.addListener (SWT.PartHidden, e -> events.add (line.apply ("hidden", e)));
		leaf.addListener (SWT.PartVisible, e -> events.add (line.apply ("visible", e)));
		open ();
		page.setVisible (false);
		page.setVisible (true);
		assertEquals (java.util.List.of ("hidden " + V + " page", "visible " + V + " page"), events);
	}
}
