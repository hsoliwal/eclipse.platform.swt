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
package org.eclipse.swt.internal.m3;

import static org.junit.Assert.*;

import java.awt.*;

import javax.swing.*;

import org.junit.*;

/**
 * The scroll contract of {@link M3SwingScroll} on a plain Swing scroll pane (lightweight components only, so the
 * test runs headless): 100 rows of 10 pixels in a viewport 95 pixels high.
 */
public class M3SwingScrollTest {
	private static final int ROWS = 100;
	private static final int PITCH = 10;

	private LayingPane pane;
	private JViewport viewport;
	private M3SwingScroll scroll;

	/**
	 * A scroll pane that lays itself out on <code>validate</code>, which AWT does only for displayed components;
	 * records that <code>validate</code> was called.
	 */
	static final class LayingPane extends JScrollPane {
		private static final long serialVersionUID = 1L;
		boolean validated;

		LayingPane (Component view) {
			super (view);
		}

		@Override
		public void validate () {
			validated = true;
			doLayout ();
			getViewport ().doLayout ();
			super.validate ();
		}
	}

	@Before
	public void setUp () {
		JPanel view = new JPanel ();
		view.setPreferredSize (new Dimension (50, ROWS * PITCH));
		pane = new LayingPane (view);
		pane.setBorder (null);
		pane.setSize (80, 95);
		pane.validate ();
		viewport = pane.getViewport ();
		scroll = new M3SwingScroll ();
	}

	private int y () {
		return viewport.getViewPosition ().y;
	}

	@Test
	public void topRowIsTheRowAtTheViewPosition () {
		assertEquals (0, scroll.topRow (viewport, ROWS, PITCH));
		viewport.setViewPosition (new Point (0, 255));
		assertEquals (25, scroll.topRow (viewport, ROWS, PITCH));
	}

	@Test
	public void topRowWithoutRowsIsMinusOne () {
		assertEquals (-1, scroll.topRow (viewport, 0, PITCH));
		assertEquals (-1, scroll.topRow (viewport, ROWS, 0));
	}

	@Test
	public void setTopRowPutsTheRowAtTheTop () {
		scroll.setTopRow (viewport, 40, ROWS, PITCH);
		assertEquals (400, y ());
		assertEquals (40, scroll.topRow (viewport, ROWS, PITCH));
	}

	@Test
	public void setTopRowClampsToTheLastPage () {
		int extent = viewport.getExtentSize ().height;
		scroll.setTopRow (viewport, 99, ROWS, PITCH);
		assertEquals (ROWS * PITCH - extent, y ());
	}

	@Test
	public void setTopRowIgnoresRowsOutOfRange () {
		viewport.setViewPosition (new Point (0, 100));
		scroll.setTopRow (viewport, -1, ROWS, PITCH);
		scroll.setTopRow (viewport, ROWS, ROWS, PITCH);
		scroll.setTopRow (viewport, 3, ROWS, 0);
		assertEquals (100, y ());
	}

	@Test
	public void showRowScrollsTheLeast () {
		int extent = viewport.getExtentSize ().height;
		scroll.showRow (viewport, 50, ROWS, PITCH);
		assertEquals (510 - extent, y ());
		scroll.showRow (viewport, 48, ROWS, PITCH);
		assertEquals ("a shown row does not scroll", 510 - extent, y ());
		scroll.showRow (viewport, 10, ROWS, PITCH);
		assertEquals (100, y ());
	}

	@Test
	public void centerRowKeepsAShownRow () {
		scroll.centerRow (viewport, 3, ROWS, PITCH);
		assertEquals (0, y ());
	}

	@Test
	public void centerRowPutsAHiddenRowInTheMiddle () {
		int shown = viewport.getExtentSize ().height / PITCH;
		scroll.centerRow (viewport, 60, ROWS, PITCH);
		assertEquals ((60 - shown / 2) * PITCH, y ());
		scroll.centerRow (viewport, 99, ROWS, PITCH);
		assertEquals (ROWS * PITCH - viewport.getExtentSize ().height, y ());
		scroll.centerRow (viewport, 1, ROWS, PITCH);
		assertEquals (0, y ());
	}

	@Test
	public void centerRowIgnoresRowsOutOfRange () {
		viewport.setViewPosition (new Point (0, 100));
		scroll.centerRow (viewport, -1, ROWS, PITCH);
		scroll.centerRow (viewport, ROWS, ROWS, PITCH);
		scroll.centerRow (viewport, 3, ROWS, 0);
		assertEquals (100, y ());
	}

	@Test
	public void layoutFollowsTheRowCountAndPitch () {
		scroll.setTopRow (viewport, 20, ROWS, PITCH);
		assertEquals (20, scroll.topRow (viewport, ROWS, PITCH));
		assertEquals (10, scroll.topRow (viewport, 50, 2 * PITCH));
		assertEquals (10, scroll.topRow (viewport, 50, 2 * PITCH));
	}

	@Test
	public void aPendingLayoutIsValidatedFirst () {
		JPanel view = (JPanel) viewport.getView ();
		view.setPreferredSize (new Dimension (50, 2 * ROWS * PITCH));
		pane.invalidate ();
		pane.validated = false;
		assertFalse (pane.isValid ());
		scroll.setTopRow (viewport, 150, 2 * ROWS, PITCH);
		assertTrue (pane.validated);
		assertEquals (1500, y ());
		pane.validated = false;
		scroll.setTopRow (viewport, 10, 2 * ROWS, PITCH);
		assertEquals ("a valid pane is not validated again", pane.isValid (), !pane.validated);
	}

	@Test
	public void aViewportOutsideAScrollPaneIsUsedAsItIs () {
		JViewport alone = new JViewport ();
		JPanel view = new JPanel ();
		view.setPreferredSize (new Dimension (50, ROWS * PITCH));
		alone.setView (view);
		alone.setSize (50, 95);
		alone.doLayout ();
		view.setSize (view.getPreferredSize ());
		scroll.setTopRow (alone, 30, ROWS, PITCH);
		assertEquals (300, alone.getViewPosition ().y);
	}
}
