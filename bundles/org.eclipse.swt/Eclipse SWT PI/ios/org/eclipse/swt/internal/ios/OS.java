/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and contributors.
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.ios;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.internal.mobile.MobilePlatform;
import org.eclipse.swt.internal.mobile.MobilePlatform.EventSink;

/**
 * UIKit/CoreGraphics platform interface. The embedding process registers this
 * class with {@code SWTUIKitRegister} after creating its JVM. No second JVM,
 * runtime downloader, or desktop Cocoa library is loaded here.
 *
 * View methods marshal to UIKit's main thread. Image and GC methods are confined
 * to the calling SWT Display thread. Callbacks only enqueue EventSink work.
 * Run javac -h to generate the declarations consumed by SWTUIKit.m.
 */
public final class OS {
	private OS() {}
	// javac -h binds native event values to the original SWT constants.
	public static final int KEY_DOWN = SWT.KeyDown, KEY_UP = SWT.KeyUp;
	public static final int MOUSE_DOWN = SWT.MouseDown, MOUSE_UP = SWT.MouseUp,
		MOUSE_MOVE = SWT.MouseMove, MOUSE_ENTER = SWT.MouseEnter, MOUSE_EXIT = SWT.MouseExit;
	public static final int FOCUS_IN = SWT.FocusIn, FOCUS_OUT = SWT.FocusOut;
	public static final int SHIFT = SWT.SHIFT, CTRL = SWT.CTRL, ALT = SWT.ALT,
		COMMAND = SWT.COMMAND, BUTTON1 = SWT.BUTTON1;
	public static final int ARROW_UP = SWT.ARROW_UP, ARROW_DOWN = SWT.ARROW_DOWN,
		ARROW_LEFT = SWT.ARROW_LEFT, ARROW_RIGHT = SWT.ARROW_RIGHT,
		HOME = SWT.HOME, END = SWT.END, PAGE_UP = SWT.PAGE_UP, PAGE_DOWN = SWT.PAGE_DOWN,
		INSERT = SWT.INSERT;
	public static final int DRAW_TRANSPARENT = SWT.DRAW_TRANSPARENT,
		DRAW_DELIMITER = SWT.DRAW_DELIMITER, DRAW_TAB = SWT.DRAW_TAB, DRAW_MNEMONIC = SWT.DRAW_MNEMONIC;
	public static final int ACTION_ACTIVATE = MobilePlatform.ACTION_ACTIVATE;
	public static final int ACTION_INCREMENT = MobilePlatform.ACTION_INCREMENT,
		ACTION_DECREMENT = MobilePlatform.ACTION_DECREMENT;
	public static final int NO_FOCUS = SWT.NO_FOCUS;
	public static final int SHELL = MobilePlatform.SHELL, COMPOSITE = MobilePlatform.COMPOSITE,
		CANVAS = MobilePlatform.CANVAS;
	public static final int ROLE_LIST = ACC.ROLE_LIST, ROLE_LABEL = ACC.ROLE_LABEL, ROLE_PUSHBUTTON = ACC.ROLE_PUSHBUTTON,
		ROLE_CHECKBUTTON = ACC.ROLE_CHECKBUTTON, ROLE_RADIOBUTTON = ACC.ROLE_RADIOBUTTON,
		ROLE_SLIDER = ACC.ROLE_SLIDER, ROLE_SPINBUTTON = ACC.ROLE_SPINBUTTON;
	public static final int STATE_SELECTED = ACC.STATE_SELECTED, STATE_CHECKED = ACC.STATE_CHECKED,
		STATE_DISABLED = ACC.STATE_DISABLED;

	public static native void connect(EventSink sink);
	public static native void disconnect();
	public static native void checkFailure();
	public static native int[] displayBounds();
	public static native int[] dpi();
	public static native long createControl(int kind, long parent, int style);
	public static native void disposeControl(long peer);
	public static native void setBounds(long peer, int x, int y, int width, int height);
	public static native void setVisible(long peer, boolean visible);
	public static native void setEnabled(long peer, boolean enabled);
	public static native void setTitle(long peer, String title);
	public static native boolean requestFocus(long peer);
	public static native void setAccessibility(long peer, String label, int role, int state);
	public static native void present(long peer, long image);

	public static native long createImage(int width, int height, int[] argb);
	public static native void disposeImage(long image);
	public static native int[] imagePixels(long image);
	public static native long createGraphics(long image);
	public static native void disposeGraphics(long graphics);
	public static native void setForeground(long graphics, int argb);
	public static native void setBackground(long graphics, int argb);
	public static native void setAlpha(long graphics, int alpha);
	public static native void setLineWidth(long graphics, float width);
	public static native void setAntialias(long graphics, boolean enabled);
	public static native void setFont(long graphics, String family, float size, int style);
	public static native void setTransform(long graphics, float[] matrix);
	public static native void setClipping(long graphics, boolean enabled, int x, int y, int width, int height);
	public static native void drawLine(long graphics, int x1, int y1, int x2, int y2);
	/** Kind 0 is a rectangle; kind 1 is an oval. */
	public static native void drawShape(long graphics, int kind, boolean fill, int x, int y, int width, int height);
	public static native void drawPolygon(long graphics, int[] points, boolean fill);
	public static native void drawText(long graphics, String text, int x, int y, int flags);
	public static native void drawImage(long graphics, long image, int sx, int sy, int sw, int sh,
		int dx, int dy, int dw, int dh);
	public static native int[] textExtent(long graphics, String text, int flags);
	public static native int[] fontMetrics(long graphics);
}
