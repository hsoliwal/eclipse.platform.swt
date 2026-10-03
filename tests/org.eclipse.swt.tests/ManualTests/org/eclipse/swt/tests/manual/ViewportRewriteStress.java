/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.manual;

import java.util.concurrent.atomic.*;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Manual stress harness for the array-backed SWT viewport rewrite.
 *
 * <p>The scenarios are original SWT tests inspired by the behavioral shapes in
 * long-standing SWT/Java2s examples: virtual SetData tables, lazy trees,
 * owner-paint, and ScrolledComposite. No third-party example source is copied.</p>
 */
public final class ViewportRewriteStress {
	private static final int TABLE_ROWS = 2_000_000;
	private static final int TREE_ROOTS = 500_000;
	private static final int TREE_CHILDREN = 10_000;
	private static final int CANVAS_ROWS = 1_000_000;
	private static final int ROW_HEIGHT = 22;
	private static final int OVERSCAN = 8;

	private ViewportRewriteStress () {
	}

	public static void main (String[] args) {
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setText ("SWT viewport rewrite stress");
		shell.setLayout (new FillLayout ());
		shell.setSize (1200, 800);

		TabFolder tabs = new TabFolder (shell, SWT.NONE);
		createTableTab (tabs);
		createTreeTab (tabs);
		createScrolledCanvasTab (tabs);
		createLogicalViewportShellTab (tabs);

		shell.open ();
		while (!shell.isDisposed ()) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		display.dispose ();
	}

	private static void createTableTab (TabFolder tabs) {
		Composite root = tab (tabs, "2M virtual Table");
		root.setLayout (new GridLayout (1, false));

		Label status = new Label (root, SWT.NONE);
		status.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));

		Table table = new Table (root, SWT.VIRTUAL | SWT.CHECK | SWT.FULL_SELECTION | SWT.MULTI | SWT.BORDER);
		table.setHeaderVisible (true);
		table.setLinesVisible (true);
		table.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		for (int i = 0; i < 4; i++) {
			TableColumn column = new TableColumn (table, SWT.NONE);
			column.setText ("Column " + i);
			column.setWidth (220);
		}

		AtomicLong setData = new AtomicLong ();
		AtomicLong paint = new AtomicLong ();
		table.addListener (SWT.SetData, event -> {
			setData.incrementAndGet ();
			int index = event.index;
			TableItem item = (TableItem) event.item;
			item.setText (new String[] {
					"row " + index,
					"group " + (index >>> 10),
					"hex " + Integer.toHexString (index),
					"mask " + (index & 63)
			});
			if ((index & 31) == 0) item.setChecked (true);
		});
		table.addListener (SWT.PaintItem, event -> paint.incrementAndGet ());
		table.setItemCount (TABLE_ROWS);

		Composite buttons = new Composite (root, SWT.NONE);
		buttons.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
		buttons.setLayout (new RowLayout ());
		jumpButton (buttons, "top", () -> redrawLocked (table, () -> table.setTopIndex (0)));
		jumpButton (buttons, "middle", () -> redrawLocked (table, () -> table.setTopIndex (TABLE_ROWS / 2)));
		jumpButton (buttons, "end", () -> redrawLocked (table, () -> table.setTopIndex (TABLE_ROWS - 1)));
		jumpButton (buttons, "select distant", () -> redrawLocked (table, () -> {
			table.setSelection (new int[] {0, TABLE_ROWS / 3, TABLE_ROWS / 2, TABLE_ROWS - 1});
			table.showSelection ();
		}));

		refreshStatus (root.getDisplay (), status, () ->
				"logical=" + table.getItemCount ()
				+ "  top=" + table.getTopIndex ()
				+ "  selected=" + table.getSelectionCount ()
				+ "  SetData=" + setData.get ()
				+ "  PaintItem=" + paint.get ());
	}

	private static void createTreeTab (TabFolder tabs) {
		Composite root = tab (tabs, "500K x lazy Tree");
		root.setLayout (new GridLayout (1, false));

		Label status = new Label (root, SWT.NONE);
		status.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));

		Tree tree = new Tree (root, SWT.VIRTUAL | SWT.CHECK | SWT.MULTI | SWT.BORDER);
		tree.setHeaderVisible (true);
		tree.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		TreeColumn column = new TreeColumn (tree, SWT.NONE);
		column.setText ("Lazy logical tree");
		column.setWidth (700);

		AtomicLong setData = new AtomicLong ();
		AtomicLong paints = new AtomicLong ();
		tree.addListener (SWT.SetData, event -> {
			setData.incrementAndGet ();
			TreeItem item = (TreeItem) event.item;
			int depth = depth (item);
			item.setText ("depth=" + depth + " index=" + event.index);
			if (depth < 3 && item.getItemCount () == 0) item.setItemCount (TREE_CHILDREN);
			if ((event.index & 63) == 0) item.setChecked (true);
		});
		tree.addListener (SWT.PaintItem, event -> paints.incrementAndGet ());
		tree.setItemCount (TREE_ROOTS);

		tree.addListener (SWT.Collapse, event -> {
			TreeItem item = (TreeItem) event.item;
			item.setData ("viewport.collapsed.childCount", item.getItemCount ());
		});

		Composite buttons = new Composite (root, SWT.NONE);
		buttons.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
		buttons.setLayout (new RowLayout ());
		jumpButton (buttons, "first root", () -> redrawLocked (tree, () -> {
			TreeItem item = tree.getItem (0);
			tree.setTopItem (item);
			item.setExpanded (true);
		}));
		jumpButton (buttons, "middle root", () -> redrawLocked (tree, () -> {
			TreeItem item = tree.getItem (TREE_ROOTS / 2);
			tree.setTopItem (item);
			item.setExpanded (true);
		}));
		jumpButton (buttons, "last root", () -> redrawLocked (tree, () -> {
			TreeItem item = tree.getItem (TREE_ROOTS - 1);
			tree.setTopItem (item);
		}));
		jumpButton (buttons, "locked mutate", () -> redrawLocked (tree, () -> {
			TreeItem rootItem = tree.getItem (TREE_ROOTS / 4);
			rootItem.setExpanded (false);
			rootItem.setItemCount (TREE_CHILDREN);
			rootItem.setChecked (!rootItem.getChecked ());
			rootItem.setExpanded (true);
			tree.setTopItem (rootItem);
		}));

		refreshStatus (root.getDisplay (), status, () ->
				"logical roots=" + tree.getItemCount ()
				+ "  selection=" + tree.getSelectionCount ()
				+ "  SetData=" + setData.get ()
				+ "  PaintItem=" + paints.get ());
	}

	private static void createScrolledCanvasTab (TabFolder tabs) {
		Composite root = tab (tabs, "ScrolledComposite paint-only");
		root.setLayout (new GridLayout (1, false));

		Label status = new Label (root, SWT.NONE);
		status.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));

		AtomicLong headerPaints = new AtomicLong ();
		AtomicLong horizontalScrolls = new AtomicLong ();
		AtomicLong verticalScrolls = new AtomicLong ();
		AtomicInteger headerOriginX = new AtomicInteger ();

		Canvas header = new Canvas (root, SWT.DOUBLE_BUFFERED | SWT.BORDER);
		GridData headerData = new GridData (SWT.FILL, SWT.CENTER, true, false);
		headerData.heightHint = 28;
		header.setLayoutData (headerData);
		header.addListener (SWT.Paint, event -> {
			headerPaints.incrementAndGet ();
			int x = -headerOriginX.get ();
			for (int column = 0; column < 8; column++) {
				int width = 200;
				event.gc.drawRectangle (x, 0, width, 27);
				event.gc.drawText ("column " + column, x + 8, 5, true);
				x += width;
			}
		});

		ScrolledComposite scroller = new ScrolledComposite (
				root, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
		scroller.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));

		Canvas canvas = new Canvas (scroller, SWT.DOUBLE_BUFFERED);
		int logicalHeight = Math.multiplyExact (CANVAS_ROWS, ROW_HEIGHT);
		canvas.setSize (1600, logicalHeight);
		scroller.setContent (canvas);
		ScrollBar horizontal = scroller.getHorizontalBar ();
		if (horizontal != null) {
			horizontal.addListener (SWT.Selection, event -> {
				horizontalScrolls.incrementAndGet ();
				int next = horizontal.getSelection ();
				if (headerOriginX.getAndSet (next) != next) header.redraw ();
			});
		}
		ScrollBar vertical = scroller.getVerticalBar ();
		if (vertical != null) {
			vertical.addListener (SWT.Selection, event -> verticalScrolls.incrementAndGet ());
		}

		long [] selectionMasks = new long [(CANVAS_ROWS + Long.SIZE - 1) / Long.SIZE];
		AtomicLong paintEvents = new AtomicLong ();
		AtomicLong paintedRows = new AtomicLong ();

		canvas.addListener (SWT.Paint, event -> {
			paintEvents.incrementAndGet ();
			Rectangle clip = event.gc.getClipping ();
			int firstVisible = Math.max (0, clip.y / ROW_HEIGHT);
			int lastVisible = Math.min (CANVAS_ROWS,
					(clip.y + clip.height + ROW_HEIGHT - 1) / ROW_HEIGHT);
			int firstPaint = Math.max (0, firstVisible - OVERSCAN);
			int lastPaint = Math.min (CANVAS_ROWS, lastVisible + OVERSCAN);
			paintedRows.addAndGet (lastPaint - firstPaint);

			for (int row = firstPaint; row < lastPaint; row++) {
				int y = row * ROW_HEIGHT;
				if (selected (selectionMasks, row)) {
					event.gc.fillRectangle (0, y, canvas.getClientArea ().width, ROW_HEIGHT);
				}
				event.gc.drawText ("logical row " + row, 8, y + 3, true);
				event.gc.drawLine (0, y + ROW_HEIGHT - 1, 1500, y + ROW_HEIGHT - 1);
			}
		});

		canvas.addListener (SWT.MouseDown, event -> {
			int row = event.y / ROW_HEIGHT;
			if (0 <= row && row < CANVAS_ROWS) {
				toggle (selectionMasks, row);
				canvas.redraw (0, row * ROW_HEIGHT, canvas.getClientArea ().width, ROW_HEIGHT, false);
			}
		});

		Composite buttons = new Composite (root, SWT.NONE);
		buttons.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
		buttons.setLayout (new RowLayout ());
		jumpButton (buttons, "top", () -> jumpVertical (scroller, canvas, 0));
		jumpButton (buttons, "middle", () -> jumpVertical (scroller, canvas, CANVAS_ROWS / 2));
		jumpButton (buttons, "end", () -> jumpVertical (scroller, canvas, CANVAS_ROWS - 1));
		jumpButton (buttons, "x=600", () -> jumpHorizontal (scroller, canvas, header, headerOriginX, 600));
		jumpButton (buttons, "x=0", () -> jumpHorizontal (scroller, canvas, header, headerOriginX, 0));

		refreshStatus (root.getDisplay (), status, () -> {
			Point origin = scroller.getOrigin ();
			int first = Math.max (0, origin.y / ROW_HEIGHT);
			int visible = Math.max (1, scroller.getClientArea ().height / ROW_HEIGHT + 1);
			int paintStart = Math.max (0, first - OVERSCAN);
			int paintEnd = Math.min (CANVAS_ROWS, first + visible + OVERSCAN);
			return "logical=" + CANVAS_ROWS
					+ "  viewport rows=[" + first + "," + Math.min (CANVAS_ROWS, first + visible) + ")"
					+ "  paint+overscan=[" + paintStart + "," + paintEnd + ")"
					+ "  bodyPaints=" + paintEvents.get ()
					+ "  headerPaints=" + headerPaints.get ()
					+ "  hScrolls=" + horizontalScrolls.get ()
					+ "  vScrolls=" + verticalScrolls.get ()
					+ "  rowsAttempted=" + paintedRows.get ()
					+ "  stateBytes~=" + (selectionMasks.length * Long.BYTES);
		});
	}


	private static void createLogicalViewportShellTab (TabFolder tabs) {
		Composite root = tab (tabs, "Logical viewport + chrome planes");
		root.setLayout (new GridLayout (1, false));

		Label status = new Label (root, SWT.NONE);
		status.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));

		AtomicInteger horizontalOrigin = new AtomicInteger ();
		AtomicInteger topRow = new AtomicInteger ();
		AtomicLong bodyPaints = new AtomicLong ();
		AtomicLong headerPaints = new AtomicLong ();
		AtomicLong verticalEvents = new AtomicLong ();
		AtomicLong horizontalEvents = new AtomicLong ();

		Canvas header = new Canvas (root, SWT.DOUBLE_BUFFERED | SWT.BORDER);
		GridData headerData = new GridData (SWT.FILL, SWT.CENTER, true, false);
		headerData.heightHint = 28;
		header.setLayoutData (headerData);
		header.addListener (SWT.Paint, event -> {
			headerPaints.incrementAndGet ();
			int x = -horizontalOrigin.get ();
			for (int column = 0; column < 8; column++) {
				int width = 240;
				event.gc.drawRectangle (x, 0, width, 27);
				event.gc.drawText ("logical column " + column, x + 8, 5, true);
				x += width;
			}
		});

		Canvas body = new Canvas (
				root, SWT.DOUBLE_BUFFERED | SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL);
		body.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		long [] selectionMasks = new long [(CANVAS_ROWS + Long.SIZE - 1) / Long.SIZE];

		ScrollBar vertical = body.getVerticalBar ();
		ScrollBar horizontal = body.getHorizontalBar ();

		Runnable configureScrollbars = () -> {
			Rectangle client = body.getClientArea ();
			int visibleRows = Math.max (1,
					Math.min (CANVAS_ROWS, (client.height + ROW_HEIGHT - 1) / ROW_HEIGHT));
			vertical.setValues (
					Math.min (topRow.get (), Math.max (0, CANVAS_ROWS - visibleRows)),
					0, CANVAS_ROWS, visibleRows, 1, visibleRows);

			int logicalWidth = 8 * 240;
			int visibleWidth = Math.max (1, Math.min (logicalWidth, client.width));
			horizontal.setValues (
					Math.min (horizontalOrigin.get (), Math.max (0, logicalWidth - visibleWidth)),
					0, logicalWidth, visibleWidth, 24, visibleWidth);
			topRow.set (vertical.getSelection ());
			horizontalOrigin.set (horizontal.getSelection ());
		};
		body.addListener (SWT.Resize, event -> configureScrollbars.run ());

		vertical.addListener (SWT.Selection, event -> {
			verticalEvents.incrementAndGet ();
			topRow.set (vertical.getSelection ());
			body.redraw ();
			// Deliberately do not redraw the header plane on vertical-only scroll.
		});
		horizontal.addListener (SWT.Selection, event -> {
			horizontalEvents.incrementAndGet ();
			horizontalOrigin.set (horizontal.getSelection ());
			body.redraw ();
			header.redraw ();
		});

		body.addListener (SWT.Paint, event -> {
			bodyPaints.incrementAndGet ();
			Rectangle client = body.getClientArea ();
			int firstVisible = topRow.get ();
			int visibleRows = Math.max (1,
					Math.min (CANVAS_ROWS - firstVisible,
							(client.height + ROW_HEIGHT - 1) / ROW_HEIGHT));
			int firstPaint = Math.max (0, firstVisible - OVERSCAN);
			int lastPaint = Math.min (CANVAS_ROWS, firstVisible + visibleRows + OVERSCAN);
			int xOffset = horizontalOrigin.get ();

			for (int row = firstPaint; row < lastPaint; row++) {
				int screenY = (row - firstVisible) * ROW_HEIGHT;
				if (screenY + ROW_HEIGHT < 0 || screenY > client.height) continue;
				if (selected (selectionMasks, row)) {
					event.gc.fillRectangle (0, screenY, client.width, ROW_HEIGHT);
				}
				event.gc.drawText ("logical row " + row, 8 - xOffset, screenY + 3, true);
				for (int column = 1; column < 8; column++) {
					int x = column * 240 - xOffset;
					event.gc.drawLine (x, screenY, x, screenY + ROW_HEIGHT);
				}
				event.gc.drawLine (0, screenY + ROW_HEIGHT - 1, client.width, screenY + ROW_HEIGHT - 1);
			}
		});

		body.addListener (SWT.MouseDown, event -> {
			int row = topRow.get () + Math.max (0, event.y / ROW_HEIGHT);
			if (row < CANVAS_ROWS) {
				toggle (selectionMasks, row);
				body.redraw (0, (row - topRow.get ()) * ROW_HEIGHT,
						body.getClientArea ().width, ROW_HEIGHT, false);
			}
		});

		Composite buttons = new Composite (root, SWT.NONE);
		buttons.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
		buttons.setLayout (new RowLayout ());
		jumpButton (buttons, "top", () -> {
			vertical.setSelection (0);
			topRow.set (vertical.getSelection ());
			body.redraw ();
		});
		jumpButton (buttons, "middle", () -> {
			vertical.setSelection (CANVAS_ROWS / 2);
			topRow.set (vertical.getSelection ());
			body.redraw ();
		});
		jumpButton (buttons, "end", () -> {
			vertical.setSelection (CANVAS_ROWS);
			topRow.set (vertical.getSelection ());
			body.redraw ();
		});
		jumpButton (buttons, "x=720", () -> {
			horizontal.setSelection (720);
			horizontalOrigin.set (horizontal.getSelection ());
			body.redraw ();
			header.redraw ();
		});
		jumpButton (buttons, "x=0", () -> {
			horizontal.setSelection (0);
			horizontalOrigin.set (horizontal.getSelection ());
			body.redraw ();
			header.redraw ();
		});

		root.getDisplay ().asyncExec (configureScrollbars);
		refreshStatus (root.getDisplay (), status, () -> {
			Rectangle client = body.getClientArea ();
			int visibleRows = Math.max (1,
					Math.min (CANVAS_ROWS, (client.height + ROW_HEIGHT - 1) / ROW_HEIGHT));
			long estimatedPixelHeight = (long) CANVAS_ROWS * ROW_HEIGHT;
			return "logicalRows=" + CANVAS_ROWS
					+ "  sampleRow=" + ROW_HEIGHT + "px"
					+ "  estimatedPixels=" + estimatedPixelHeight
					+ "  nativeBodyHeight=" + client.height
					+ "  topRow=" + topRow.get ()
					+ "  visibleRows=" + visibleRows
					+ "  vThumb=" + vertical.getThumb ()
					+ "  bodyPaints=" + bodyPaints.get ()
					+ "  headerPaints=" + headerPaints.get ()
					+ "  vEvents=" + verticalEvents.get ()
					+ "  hEvents=" + horizontalEvents.get ();
		});
	}

	private static Composite tab (TabFolder folder, String text) {
		TabItem item = new TabItem (folder, SWT.NONE);
		item.setText (text);
		Composite composite = new Composite (folder, SWT.NONE);
		item.setControl (composite);
		return composite;
	}

	private static void jumpButton (Composite parent, String text, Runnable action) {
		Button button = new Button (parent, SWT.PUSH);
		button.setText (text);
		button.addListener (SWT.Selection, event -> action.run ());
	}

	private static void jumpVertical (ScrolledComposite scroller, Canvas canvas, int row) {
		Point origin = scroller.getOrigin ();
		redrawLocked (canvas, () -> scroller.setOrigin (origin.x, row * ROW_HEIGHT));
		canvas.redraw ();
	}

	private static void jumpHorizontal (
			ScrolledComposite scroller, Canvas canvas, Canvas header,
			AtomicInteger headerOriginX, int x) {
		Point origin = scroller.getOrigin ();
		redrawLocked (canvas, () -> scroller.setOrigin (x, origin.y));
		int actual = scroller.getOrigin ().x;
		if (headerOriginX.getAndSet (actual) != actual) header.redraw ();
		canvas.redraw ();
	}

	private static void redrawLocked (Control control, Runnable mutation) {
		control.setRedraw (false);
		try {
			mutation.run ();
		} finally {
			if (!control.isDisposed ()) control.setRedraw (true);
		}
	}

	private static int depth (TreeItem item) {
		int result = 0;
		while ((item = item.getParentItem ()) != null) result++;
		return result;
	}

	private static boolean selected (long[] masks, int index) {
		return (masks [index >>> 6] & (1L << (index & 63))) != 0;
	}

	private static void toggle (long[] masks, int index) {
		masks [index >>> 6] ^= 1L << (index & 63);
	}

	private static void refreshStatus (Display display, Label label, java.util.function.Supplier<String> text) {
		Runnable refresh = new Runnable () {
			@Override
			public void run () {
				if (label.isDisposed ()) return;
				label.setText (text.get ());
				display.timerExec (250, this);
			}
		};
		display.timerExec (250, refresh);
	}
}
