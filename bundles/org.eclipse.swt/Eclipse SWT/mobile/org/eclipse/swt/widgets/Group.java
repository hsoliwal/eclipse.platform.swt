/*******************************************************************************
 * Copyright (c) 2000, 2025 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Latha Patil (ETAS GmbH) - Custom draw Group widget using SkijaGC
 *     Hitesh Soliwal (Synexia) - Android and iOS port on the control's native peer
 *******************************************************************************/
package org.eclipse.swt.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;

/**
 * SWT Group for Android and iOS. Neither platform has a group box, so the frame
 * and the title are drawn on the control's one native peer through the port's
 * GC. Derived from the Initiative 31 Group and DefaultGroupRenderer: same frame
 * inset, title inset, client inset and corner radius. Unlike the donor,
 * {@link #computeTrim} and {@link #getClientArea} are exact inverses.
 */
public class Group extends Composite {
	private static final int FRAME_INSET = 2;
	private static final int TITLE_INSET = 8;
	private static final int CLIENT_INSET = 3;
	private static final int CORNER = 6;
	private static final int TITLE_GAP = 2;
	private static final int DRAW_FLAGS = SWT.DRAW_MNEMONIC | SWT.DRAW_TAB | SWT.DRAW_TRANSPARENT
			| SWT.DRAW_DELIMITER;
	private static final int NOT_MEASURED = -1;

	private String text = "";
	/* Title extent, measured once per text and font; no object is kept for it. */
	private int titleWidth = NOT_MEASURED;
	private int titleHeight = NOT_MEASURED;

	public Group(Composite parent, int style) {
		super(parent, checkStyle(style));
		addListener(SWT.Paint, this::paintGroup);
		publishAccessibility();
	}

	static int checkStyle(int style) {
		int checked = WidgetStylePolicy.NO_FOCUS_WITHOUT_SCROLLBARS.applyAsInt(style);
		return (checked & SWT.BORDER) != 0 ? checked | SWT.SHADOW_IN : checked;
	}

	@Override protected void checkSubclass() {
		if (!getClass().getName().startsWith("org.eclipse.swt.")) SWT.error(SWT.ERROR_INVALID_SUBCLASS);
	}

	public String getText() {
		checkWidget();
		return text;
	}

	public void setText(String value) {
		checkWidget();
		if (value == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
		if (text.equals(value)) return;
		text = value;
		titleWidth = NOT_MEASURED;
		publishAccessibility();
		redraw();
	}

	@Override public void setFont(Font value) {
		super.setFont(value);
		titleWidth = NOT_MEASURED;
		redraw();
	}

	@Override public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		if (isDisposed()) return;
		publishAccessibility();
		redraw();
	}

	private void publishAccessibility() {
		display.internal_platform().setAccessibility(peer, text, ACC.ROLE_GROUP,
				getEnabled() ? ACC.STATE_NORMAL : ACC.STATE_DISABLED);
	}

	/** Space above the client area: the title line, or the frame alone without a title. */
	private int topTrim() {
		measureTitle();
		return (text.isEmpty() ? FRAME_INSET : titleHeight) + CLIENT_INSET;
	}

	private static int sideTrim() {
		return FRAME_INSET + CLIENT_INSET;
	}

	@Override public Rectangle getClientArea() {
		Rectangle inner = super.getClientArea();
		int top = topTrim(), side = sideTrim();
		return new Rectangle(inner.x + side, inner.y + top, Math.max(0, inner.width - 2 * side),
				Math.max(0, inner.height - top - side));
	}

	@Override public Rectangle computeTrim(int x, int y, int width, int height) {
		int top = topTrim(), side = sideTrim();
		return super.computeTrim(x - side, y - top, width + 2 * side, height + top + side);
	}

	@Override public Point computeSize(int wHint, int hHint, boolean changed) {
		Point size = super.computeSize(wHint, hHint, changed);
		if (wHint == SWT.DEFAULT && !text.isEmpty()) {
			int titled = titleWidth + 2 * (FRAME_INSET + TITLE_INSET + TITLE_GAP) + 2 * getBorderWidth();
			size.x = Math.max(size.x, titled);
		}
		return size;
	}

	private void measureTitle() {
		if (titleWidth != NOT_MEASURED) return;
		Image sample = new Image(display, 1, 1);
		try {
			GC gc = new GC(sample);
			try {
				gc.setFont(getFont());
				Point extent = gc.textExtent(text.isEmpty() ? "Mg" : text, DRAW_FLAGS);
				titleWidth = text.isEmpty() ? 0 : extent.x;
				titleHeight = Math.max(1, extent.y);
			} finally {
				gc.dispose();
			}
		} finally {
			sample.dispose();
		}
	}

	private Color frameColor() {
		int id = !isEnabled() || (style & SWT.SHADOW_ETCHED_IN) != 0 ? SWT.COLOR_WIDGET_NORMAL_SHADOW
				: (style & (SWT.SHADOW_ETCHED_OUT | SWT.SHADOW_OUT)) != 0 ? SWT.COLOR_WIDGET_LIGHT_SHADOW
				: (style & SWT.SHADOW_IN) != 0 ? SWT.COLOR_WIDGET_DARK_SHADOW : SWT.NONE;
		return id == SWT.NONE ? getForeground() : display.getSystemColor(id);
	}

	private void paintGroup(Event event) {
		if (isDisposed()) return;
		measureTitle();
		Rectangle inner = super.getClientArea();
		GC gc = event.gc;
		Color saved = gc.getForeground();
		try {
			int frameTop = text.isEmpty() ? FRAME_INSET : titleHeight / 2;
			gc.setForeground(frameColor());
			gc.drawRoundRectangle(FRAME_INSET, frameTop, Math.max(0, inner.width - 2 * FRAME_INSET - 1),
					Math.max(0, inner.height - frameTop - FRAME_INSET - 1), CORNER, CORNER);
			if (!text.isEmpty()) paintTitle(gc, inner.width, frameTop);
		} finally {
			gc.setForeground(saved);
		}
	}

	/** The title sits on the frame line; the line under it is cleared with the background first. */
	private void paintTitle(GC gc, int width, int frameTop) {
		boolean rightToLeft = (style & SWT.RIGHT_TO_LEFT) != 0;
		int x = rightToLeft ? width - FRAME_INSET - TITLE_INSET - titleWidth : FRAME_INSET + TITLE_INSET;
		gc.fillRectangle(x - TITLE_GAP, frameTop, titleWidth + 2 * TITLE_GAP, gc.getLineWidth() + 1);
		if (isEnabled()) gc.setForeground(getForeground());
		gc.drawText(text, x, 0, DRAW_FLAGS);
	}
}
