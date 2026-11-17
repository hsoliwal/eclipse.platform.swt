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
package org.eclipse.swt.internal;

import java.text.*;
import java.util.*;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;

/**
 * Binds a {@link ColumnArrangement} to the columns of a {@link Table} or a {@link Tree}, both ways, and offers
 * it as a menu of check items over the header: a column chooser.
 * <p>
 * The control follows the arrangement. A hidden column gets width 0 and cannot be resized; a shown column gets
 * the width the arrangement keeps for it and its own resizable flag back; the display order is the
 * arrangement's order. A change of the arrangement touches only the column it names. When a column whose
 * width the arrangement does not know yet is hidden, its current width is stored in the arrangement first, so
 * that showing it again restores it.
 * </p><p>
 * The arrangement follows the user: moving a column header sets the order and resizing a shown column sets
 * its width. The binding's own changes to the control are not reported back to the arrangement.
 * </p><p>
 * The menu lists every column, shown or hidden, by its header text in the order of a {@link Collator}, the way
 * the column chooser of NetBeans' {@code ETable} does ({@code ColumnSelectionPanel}, Apache-2.0). Columns with
 * the same text keep their index order. A locked column cannot be hidden from the menu, and neither can the
 * last shown column, as in Eclipse's column dialog. {@link #installHeaderMenu()} shows the menu over the header
 * and leaves the control's own menu over the rows, choosing on {@link SWT#MenuDetect} the way SWT's Snippet311
 * does.
 * </p>
 */
public final class ColumnChooser {

	private final Composite owner;
	private final ColumnArrangement arrangement;
	/* columns the menu may not hide; columns this binding hid; their resizable flag before */
	private final BitSet locked = new BitSet (), hiddenHere = new BitSet (), wasResizable = new BitSet ();
	private final Listener columnListener = this::columnChanged;
	private final Listener ownerListener = this::ownerEvent;
	private final ColumnArrangement.Listener arrangementListener = this::arrangementChanged;
	private Menu headerMenu, bodyMenu;
	private boolean applying, disposed;

	/**
	 * Binds an arrangement to the columns of a table and applies it.
	 *
	 * @param table the table
	 * @param arrangement an arrangement with as many columns as the table
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the table or the arrangement is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the column counts differ</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the table has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the table</li>
	 * </ul>
	 */
	public ColumnChooser (Table table, ColumnArrangement arrangement) {
		this ((Composite) table, arrangement);
	}

	/**
	 * Binds an arrangement to the columns of a tree and applies it.
	 *
	 * @param tree the tree
	 * @param arrangement an arrangement with as many columns as the tree
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the tree or the arrangement is null</li>
	 *    <li>ERROR_INVALID_ARGUMENT - if the column counts differ</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if the tree has been disposed</li>
	 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the tree</li>
	 * </ul>
	 */
	public ColumnChooser (Tree tree, ColumnArrangement arrangement) {
		this ((Composite) tree, arrangement);
	}

	private ColumnChooser (Composite owner, ColumnArrangement arrangement) {
		if (owner == null || arrangement == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		this.owner = owner;
		this.arrangement = arrangement;
		if (columnCount () != arrangement.getColumnCount ()) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
		arrangement.addListener (arrangementListener);
		owner.addListener (SWT.Dispose, ownerListener);
		apply ();
	}

	/**
	 * Applies the whole arrangement to the control again: visibility and width of every column, then the
	 * order. Call it after columns were created or disposed together with the arrangement's
	 * {@link ColumnArrangement#insertColumn(int, int)} or {@link ColumnArrangement#removeColumn(int)}; until
	 * the counts agree again, the binding leaves the control alone.
	 *
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if this binding or its control has been disposed</li>
	 * </ul>
	 */
	public void apply () {
		checkLive ();
		if (columnCount () != arrangement.getColumnCount ()) return;
		applying = true;
		try {
			for (int index = 0; index < columnCount (); index++) {
				Item column = column (index);
				column.removeListener (SWT.Move, columnListener);
				column.removeListener (SWT.Resize, columnListener);
				column.addListener (SWT.Move, columnListener);
				column.addListener (SWT.Resize, columnListener);
				showOrHide (index);
			}
			setColumnOrder (arrangement.getOrder ());
		} finally {
			applying = false;
		}
	}

	/**
	 * Locks a column so that the menu cannot hide it, or unlocks it. A locked column's item is disabled.
	 *
	 * @param column the column
	 * @param locked <code>true</code> to lock it
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column</li>
	 * </ul>
	 */
	public void setLocked (int column, boolean locked) {
		this.locked.set (RowChecks.range (column, arrangement.getColumnCount ()), locked);
	}

	/**
	 * Returns whether the menu may not hide a column.
	 *
	 * @param column the column
	 * @return <code>true</code> if it is locked
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_INVALID_RANGE - if the column is not a column</li>
	 * </ul>
	 */
	public boolean isLocked (int column) {
		return locked.get (RowChecks.range (column, arrangement.getColumnCount ()));
	}

	/**
	 * Replaces the items of a menu by one check item per column, checked when the column is shown. Selecting
	 * an item shows or hides its column in the arrangement.
	 *
	 * @param menu the menu to fill
	 *
	 * @exception IllegalArgumentException <ul>
	 *    <li>ERROR_NULL_ARGUMENT - if the menu is null</li>
	 * </ul>
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if this binding, its control or the menu has been disposed</li>
	 * </ul>
	 */
	public void fillMenu (Menu menu) {
		checkLive ();
		if (menu == null) SWT.error (SWT.ERROR_NULL_ARGUMENT);
		for (MenuItem item : menu.getItems ()) item.dispose ();
		int shownCount = arrangement.getVisibleCount ();
		for (int index : byHeaderText ()) {
			MenuItem item = new MenuItem (menu, SWT.CHECK);
			boolean shown = arrangement.isVisible (index);
			item.setText (text (column (index)).replace ("&", "&&")); //$NON-NLS-1$ //$NON-NLS-2$
			item.setSelection (shown);
			item.setEnabled (!locked.get (index) && !(shown && shownCount == 1));
			item.addListener (SWT.Selection, event -> arrangement.setVisible (index, item.getSelection ()));
		}
	}

	/**
	 * Shows the column menu when the user asks for a menu over the header. Over the rows, and for a menu asked
	 * for with the keyboard, the control keeps the menu it has.
	 *
	 * @exception SWTException <ul>
	 *    <li>ERROR_WIDGET_DISPOSED - if this binding or its control has been disposed</li>
	 * </ul>
	 */
	public void installHeaderMenu () {
		checkLive ();
		if (headerMenu != null) return;
		headerMenu = new Menu (owner);
		owner.addListener (SWT.MenuDetect, ownerListener);
	}

	/**
	 * Returns the menu shown over the header, or <code>null</code> before {@link #installHeaderMenu()}.
	 *
	 * @return the header menu
	 */
	public Menu getHeaderMenu () {
		return headerMenu;
	}

	/**
	 * Ends the binding: the arrangement and the control no longer follow each other, the header menu is
	 * disposed and the control gets its own menu back. Disposing the control ends the binding too. Columns stay
	 * as they are.
	 */
	public void dispose () {
		if (disposed) return;
		disposed = true;
		arrangement.removeListener (arrangementListener);
		if (owner.isDisposed ()) return;
		owner.removeListener (SWT.Dispose, ownerListener);
		owner.removeListener (SWT.MenuDetect, ownerListener);
		for (int index = 0; index < columnCount (); index++) {
			column (index).removeListener (SWT.Move, columnListener);
			column (index).removeListener (SWT.Resize, columnListener);
		}
		if (headerMenu != null) {
			if (owner.getMenu () == headerMenu) owner.setMenu (bodyMenu);
			headerMenu.dispose ();
		}
	}

	/**
	 * Returns whether the binding has ended.
	 *
	 * @return <code>true</code> after {@link #dispose()} or after the control was disposed
	 */
	public boolean isDisposed () {
		return disposed;
	}

	/* the control follows the arrangement */

	private void arrangementChanged (int kind, int column, int from, int to) {
		if (applying || disposed) return;
		if (kind == ColumnArrangement.INSERTED || kind == ColumnArrangement.REMOVED) shiftBits (column, kind == ColumnArrangement.INSERTED);
		if (columnCount () != arrangement.getColumnCount ()) return;
		applying = true;
		try {
			switch (kind) {
				case ColumnArrangement.SHOWN, ColumnArrangement.HIDDEN -> showOrHide (column);
				case ColumnArrangement.MOVED -> setColumnOrder (arrangement.getOrder ());
				case ColumnArrangement.RESIZED -> resize (column, to);
				default -> {
					applying = false;
					apply ();
				}
			}
		} finally {
			applying = false;
		}
	}

	private void showOrHide (int index) {
		Item column = column (index);
		if (arrangement.isVisible (index)) {
			int stored = arrangement.getWidth (index);
			if (stored != ColumnArrangement.DEFAULT_WIDTH && width (column) != stored) setWidth (column, stored);
			else if (stored == ColumnArrangement.DEFAULT_WIDTH && hiddenHere.get (index)) pack (column);
			if (hiddenHere.get (index)) {
				hiddenHere.clear (index);
				setResizable (column, wasResizable.get (index));
			}
		} else if (!hiddenHere.get (index)) {
			if (arrangement.getWidth (index) == ColumnArrangement.DEFAULT_WIDTH) arrangement.setWidth (index, width (column));
			wasResizable.set (index, isResizable (column));
			hiddenHere.set (index);
			setWidth (column, 0);
			setResizable (column, false);
		}
	}

	private void resize (int index, int width) {
		if (arrangement.isVisible (index) && width != ColumnArrangement.DEFAULT_WIDTH) setWidth (column (index), width);
	}

	private void shiftBits (int column, boolean inserted) {
		for (BitSet bits : new BitSet [] {locked, hiddenHere, wasResizable}) {
			BitSet tail = bits.get (inserted ? column : column + 1, Math.max (column + 1, bits.length ()));
			bits.clear (column, Math.max (column + 1, bits.length ()));
			for (int bit = tail.nextSetBit (0); bit >= 0; bit = tail.nextSetBit (bit + 1)) bits.set (column + bit + (inserted ? 1 : 0));
		}
	}

	/* the arrangement follows the user */

	private void columnChanged (Event event) {
		if (applying || disposed) return;
		applying = true;
		try {
			if (event.type == SWT.Move) {
				arrangement.setOrder (columnOrder ());
			} else {
				int index = indexOf ((Item) event.widget);
				if (index >= 0 && arrangement.isVisible (index)) arrangement.setWidth (index, width (column (index)));
			}
		} finally {
			applying = false;
		}
	}

	private void ownerEvent (Event event) {
		if (event.type == SWT.Dispose) {
			dispose ();
		} else if (event.type == SWT.MenuDetect && !disposed) {
			chooseMenu (event);
		}
	}

	private void chooseMenu (Event event) {
		Menu current = owner.getMenu ();
		if (current != headerMenu) bodyMenu = current;
		boolean header = event.detail != SWT.MENU_KEYBOARD && inHeader (event.x, event.y);
		if (header) fillMenu (headerMenu);
		owner.setMenu (header ? headerMenu : bodyMenu);
	}

	private boolean inHeader (int x, int y) {
		if (!headerVisible ()) return false;
		Point point = owner.getDisplay ().map (null, owner, x, y);
		Rectangle area = owner.getClientArea ();
		return area.y <= point.y && point.y < area.y + headerHeight ();
	}

	private int [] byHeaderText () {
		Collator collator = Collator.getInstance ();
		String [] texts = new String [columnCount ()];
		Integer [] indices = new Integer [texts.length];
		for (int index = 0; index < texts.length; index++) {
			texts [index] = text (column (index));
			indices [index] = index;
		}
		Arrays.sort (indices, (a, b) -> {
			int byText = collator.compare (texts [a], texts [b]);
			return byText != 0 ? byText : Integer.compare (a, b);
		});
		return Arrays.stream (indices).mapToInt (Integer::intValue).toArray ();
	}

	private void checkLive () {
		if (disposed || owner.isDisposed ()) SWT.error (SWT.ERROR_WIDGET_DISPOSED);
	}

	/* Table and Tree have no common column type */

	private int columnCount () {
		return owner instanceof Table table ? table.getColumnCount () : ((Tree) owner).getColumnCount ();
	}

	private Item column (int index) {
		return owner instanceof Table table ? table.getColumn (index) : ((Tree) owner).getColumn (index);
	}

	private int indexOf (Item column) {
		return owner instanceof Table table ? table.indexOf ((TableColumn) column) : ((Tree) owner).indexOf ((TreeColumn) column);
	}

	private int [] columnOrder () {
		return owner instanceof Table table ? table.getColumnOrder () : ((Tree) owner).getColumnOrder ();
	}

	private void setColumnOrder (int [] order) {
		if (Arrays.equals (order, columnOrder ())) return;
		if (owner instanceof Table table) table.setColumnOrder (order); else ((Tree) owner).setColumnOrder (order);
	}

	private boolean headerVisible () {
		return owner instanceof Table table ? table.getHeaderVisible () : ((Tree) owner).getHeaderVisible ();
	}

	private int headerHeight () {
		return owner instanceof Table table ? table.getHeaderHeight () : ((Tree) owner).getHeaderHeight ();
	}

	private static String text (Item column) {
		return column.getText ();
	}

	private static int width (Item column) {
		return column instanceof TableColumn c ? c.getWidth () : ((TreeColumn) column).getWidth ();
	}

	private static void setWidth (Item column, int width) {
		if (column instanceof TableColumn c) c.setWidth (width); else ((TreeColumn) column).setWidth (width);
	}

	private static void pack (Item column) {
		if (column instanceof TableColumn c) c.pack (); else ((TreeColumn) column).pack ();
	}

	private static boolean isResizable (Item column) {
		return column instanceof TableColumn c ? c.getResizable () : ((TreeColumn) column).getResizable ();
	}

	private static void setResizable (Item column, boolean resizable) {
		if (column instanceof TableColumn c) c.setResizable (resizable); else ((TreeColumn) column).setResizable (resizable);
	}
}
