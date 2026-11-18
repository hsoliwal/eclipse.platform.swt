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
 *     Hitesh Soliwal (Synexia) - layered viewport example
 *******************************************************************************/
package org.eclipse.swt.tests.manual;

import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.ViewportPaintGraph;
import org.eclipse.swt.internal.ViewportPaintGraph.Affine;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Interactive example of composited image layers in a normal SWT Canvas.
 *
 * <p>Drag a layer, change its opacity, hide it, make it input-transparent, or
 * change its z position. Scrollbars move the content; the viewport clip and
 * status text stay fixed. Picking uses image bounds, including transparent
 * pixels. The Canvas retains capture for the chosen layer throughout a drag.
 * Escape cancels that drag; hiding, disabling input or losing focus ends it.</p>
 *
 * <p>The example owns the flattened layer Images. The paint graph borrows them
 * and never retains the event GC. Every scene change requests a fresh Canvas
 * frame, so old pixels and translucent overlaps are recomposited. This is a
 * usage example, not the automated rendering-acceptance gate.</p>
 */
@SuppressWarnings("restriction")
public final class LayeredViewport {
	private static final int CONTENT_WIDTH = 1200;
	private static final int CONTENT_HEIGHT = 900;
	private static final String [] NAMES = {"Coral card", "Blue card", "Gold card"};
	private static final RGB [] COLORS = {
			new RGB (224, 102, 91), new RGB (76, 137, 205), new RGB (230, 179, 67)};

	private final Display display;
	private final ViewportPaintGraph graph = new ViewportPaintGraph ();
	private final Image [] images = new Image [3];
	private final int [] nodes = new int [3];
	private final int [] order = {0, 1, 2};
	private final int [] x = {80, 220, 400};
	private final int [] y = {60, 160, 80};
	private final int viewport = graph.group (graph.root ());
	private final int content = graph.group (viewport);
	private Canvas canvas;
	private org.eclipse.swt.widgets.List layerList;
	private Button visible;
	private Button passThrough;
	private Scale opacity;
	private Label opacityLabel;
	private Label status;
	private int selected = 2;
	private int dragging = -1;
	private int startX, startY, pointerX, pointerY;

	private LayeredViewport (Display display) {
		this.display = display;
	}

	public static void main (String [] args) {
		Display display = new Display ();
		LayeredViewport example = new LayeredViewport (display);
		try {
			Shell shell = example.createShell ();
			shell.open ();
			while (!shell.isDisposed ()) {
				if (!display.readAndDispatch ()) display.sleep ();
			}
		} finally {
			example.disposeImages ();
			display.dispose ();
		}
	}

	private Shell createShell () {
		Shell shell = new Shell (display);
		shell.setText ("SWT layered viewport");
		shell.setLayout (new GridLayout (2, false));
		shell.setSize (1040, 720);

		canvas = new Canvas (shell, SWT.BORDER | SWT.DOUBLE_BUFFERED | SWT.H_SCROLL | SWT.V_SCROLL);
		canvas.setLayoutData (new GridData (SWT.FILL, SWT.FILL, true, true));
		canvas.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
		for (int i = 0; i < images.length; i++) {
			images [i] = createLayerImage (i, 340 - i * 40, 240 - i * 30);
			nodes [i] = graph.image (content, images [i], x [i], y [i]);
			graph.setZOrder (nodes [i], i);
		}
		graph.setOpacity (nodes [1], 205);
		graph.setOpacity (nodes [2], 220);

		Composite panel = new Composite (shell, SWT.NONE);
		panel.setLayout (new GridLayout (1, false));
		GridData panelData = new GridData (SWT.FILL, SWT.FILL, false, true);
		panelData.widthHint = 245;
		panel.setLayoutData (panelData);
		Label heading = new Label (panel, SWT.NONE);
		heading.setText ("Layers — front to back");
		layerList = new org.eclipse.swt.widgets.List (panel, SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL);
		GridData listData = new GridData (SWT.FILL, SWT.TOP, true, false);
		listData.heightHint = 120;
		layerList.setLayoutData (listData);
		layerList.addListener (SWT.Selection, event -> {
			finishDrag (false);
			int row = layerList.getSelectionIndex ();
			if (row >= 0) select (order [order.length - 1 - row]);
		});

		Composite reorder = new Composite (panel, SWT.NONE);
		reorder.setLayout (new GridLayout (2, true));
		reorder.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false));
		Button raise = new Button (reorder, SWT.PUSH);
		raise.setText ("Raise");
		raise.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
		raise.addListener (SWT.Selection, event -> reorder (1));
		Button lower = new Button (reorder, SWT.PUSH);
		lower.setText ("Lower");
		lower.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
		lower.addListener (SWT.Selection, event -> reorder (-1));

		visible = new Button (panel, SWT.CHECK);
		visible.setText ("Visible");
		visible.addListener (SWT.Selection, event -> {
			finishDrag (false);
			graph.setVisible (nodes [selected], visible.getSelection ());
			refresh ();
		});
		passThrough = new Button (panel, SWT.CHECK);
		passThrough.setText ("Pass input to layers below");
		passThrough.addListener (SWT.Selection, event -> {
			finishDrag (false);
			graph.setPickable (nodes [selected], !passThrough.getSelection ());
			refresh ();
		});
		opacityLabel = new Label (panel, SWT.NONE);
		opacityLabel.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
		opacity = new Scale (panel, SWT.HORIZONTAL);
		opacity.setMinimum (0);
		opacity.setMaximum (255);
		opacity.setLayoutData (new GridData (SWT.FILL, SWT.CENTER, true, false));
		opacity.addListener (SWT.Selection, event -> {
			graph.setOpacity (nodes [selected], opacity.getSelection ());
			refresh ();
		});
		Label instructions = new Label (panel, SWT.WRAP);
		instructions.setText ("Drag a visible card. Escape restores its starting position. "
				+ "Use the scrollbars or mouse wheel to move the viewport. "
				+ "Hold Shift while scrolling to move horizontally.");
		instructions.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false));
		status = new Label (panel, SWT.WRAP);
		status.setLayoutData (new GridData (SWT.FILL, SWT.TOP, true, false));

		canvas.addListener (SWT.Paint, event -> {
			Rectangle client = canvas.getClientArea ();
			event.gc.setBackground (canvas.getBackground ());
			event.gc.fillRectangle (client);
			graph.replay (event.gc, Affine.IDENTITY, client);
			event.gc.setForeground (display.getSystemColor (SWT.COLOR_DARK_GRAY));
			event.gc.drawText ("Viewport  " + canvas.getHorizontalBar ().getSelection ()
					+ ", " + canvas.getVerticalBar ().getSelection (), 12, 12, true);
		});
		canvas.addListener (SWT.Resize, event -> updateViewport ());
		canvas.getHorizontalBar ().addListener (SWT.Selection, event -> updateViewport ());
		canvas.getVerticalBar ().addListener (SWT.Selection, event -> updateViewport ());
		canvas.addListener (SWT.MouseWheel, event -> {
			finishDrag (false);
			ScrollBar bar = (event.stateMask & SWT.SHIFT) != 0
					? canvas.getHorizontalBar () : canvas.getVerticalBar ();
			bar.setSelection (bar.getSelection () - event.count * 24);
			updateViewport ();
			event.doit = false;
		});
		canvas.addListener (SWT.MouseDown, event -> {
			if (event.button != 1) return;
			canvas.setFocus ();
			var hit = graph.hitTestImage (event.x, event.y);
			if (hit == null) return;
			for (int i = 0; i < nodes.length; i++) {
				if (nodes [i] != hit.node ()) continue;
				select (i);
				dragging = i;
				startX = x [i];
				startY = y [i];
				pointerX = event.x;
				pointerY = event.y;
				canvas.setCapture (true);
				break;
			}
		});
		canvas.addListener (SWT.MouseMove, event -> {
			if (dragging < 0) return;
			Rectangle bounds = images [dragging].getBounds ();
			x [dragging] = Math.max (0, Math.min (CONTENT_WIDTH - bounds.width,
					startX + event.x - pointerX));
			y [dragging] = Math.max (0, Math.min (CONTENT_HEIGHT - bounds.height,
					startY + event.y - pointerY));
			graph.setImageLocation (nodes [dragging], x [dragging], y [dragging]);
			refresh ();
		});
		canvas.addListener (SWT.MouseUp, event -> {
			if (event.button == 1) finishDrag (false);
		});
		canvas.addListener (SWT.KeyDown, event -> {
			if (event.keyCode == SWT.ESC) {
				finishDrag (true);
				event.doit = false;
			}
		});
		canvas.addListener (SWT.FocusOut, event -> finishDrag (false));
		canvas.addListener (SWT.Hide, event -> finishDrag (false));
		shell.addListener (SWT.Deactivate, event -> finishDrag (false));
		canvas.addListener (SWT.Dispose, event -> {
			dragging = -1;
			disposeImages ();
		});
		refresh ();
		return shell;
	}

	private Image createLayerImage (int index, int width, int height) {
		Image result = new Image (display, width, height);
		GC painter = null;
		Color color = null;
		boolean complete = false;
		try {
			painter = new GC (result);
			color = new Color (display, COLORS [index]);
			painter.setBackground (color);
			painter.fillRectangle (result.getBounds ());
			painter.setBackground (display.getSystemColor (SWT.COLOR_WHITE));
			painter.setAlpha (90);
			painter.fillOval (24, 58, 120, 120);
			painter.fillOval (90, 58, 120, 120);
			painter.setAlpha (255);
			painter.setForeground (display.getSystemColor (SWT.COLOR_BLACK));
			painter.drawText (NAMES [index], 20, 20, true);
			painter.drawText ("Flattened image layer", 20, height - 30, true);
			complete = true;
			return result;
		} finally {
			if (painter != null) painter.dispose ();
			if (color != null) color.dispose ();
			if (!complete) result.dispose ();
		}
	}

	private void select (int index) {
		selected = index;
		refresh ();
	}

	private void reorder (int direction) {
		finishDrag (false);
		for (int i = 0; i < order.length; i++) {
			if (order [i] != selected) continue;
			int next = i + direction;
			if (next < 0 || next >= order.length) return;
			order [i] = order [next];
			order [next] = selected;
			for (int z = 0; z < order.length; z++) graph.setZOrder (nodes [order [z]], z);
			refresh ();
			return;
		}
	}

	private void finishDrag (boolean cancel) {
		if (dragging < 0) return;
		int layer = dragging;
		dragging = -1;
		if (canvas.isDisposed ()) return;
		canvas.setCapture (false);
		if (cancel) {
			x [layer] = startX;
			y [layer] = startY;
			graph.setImageLocation (nodes [layer], startX, startY);
			refresh ();
		}
	}

	private void updateViewport () {
		finishDrag (false);
		Rectangle client = canvas.getClientArea ();
		configureScrollBar (canvas.getHorizontalBar (), CONTENT_WIDTH, client.width);
		configureScrollBar (canvas.getVerticalBar (), CONTENT_HEIGHT, client.height);
		graph.setClip (viewport, 0, 0, Math.max (0, client.width), Math.max (0, client.height));
		graph.setTransform (content, Affine.translation (
				-canvas.getHorizontalBar ().getSelection (), -canvas.getVerticalBar ().getSelection ()));
		canvas.redraw ();
	}

	private void configureScrollBar (ScrollBar bar, int extent, int visibleExtent) {
		int thumb = Math.max (1, visibleExtent);
		bar.setValues (bar.getSelection (), 0, Math.max (extent, thumb), thumb,
				16, Math.max (16, thumb / 2));
	}

	private void refresh () {
		for (int row = 0; row < order.length; row++) {
			int layer = order [order.length - 1 - row];
			String label = NAMES [layer] + (graph.isVisible (nodes [layer]) ? "" : " (hidden)");
			if (row < layerList.getItemCount ()) layerList.setItem (row, label);
			else layerList.add (label);
			if (layer == selected) layerList.setSelection (row);
		}
		visible.setSelection (graph.isVisible (nodes [selected]));
		passThrough.setSelection (!graph.isPickable (nodes [selected]));
		opacity.setSelection (graph.opacity (nodes [selected]));
		opacityLabel.setText ("Opacity  " + (graph.opacity (nodes [selected]) * 100 / 255) + "%");
		status.setText (NAMES [selected] + "\nPosition: " + x [selected] + ", " + y [selected]
				+ "\nZ: " + graph.zOrder (nodes [selected]));
		canvas.redraw ();
	}

	private void disposeImages () {
		for (Image image : images) {
			if (image != null && !image.isDisposed ()) image.dispose ();
		}
	}
}
