/*******************************************************************************
 * Copyright (c) 2000, 2026 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - retained Canvas image layers
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.widgets;


import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.internal.cairo.*;
import org.eclipse.swt.internal.canvasext.*;
import org.eclipse.swt.internal.gtk.*;

/**
 * Instances of this class provide a surface for drawing
 * arbitrary graphics.
 * <dl>
 * <dt><b>Styles:</b></dt>
 * <dd>(none)</dd>
 * <dt><b>Events:</b></dt>
 * <dd>(none)</dd>
 * </dl>
 * <p>
 * This class may be subclassed by custom control implementors
 * who are building controls that are <em>not</em> constructed
 * from aggregates of other controls. That is, they are either
 * painted using SWT graphics calls or are handled by native
 * methods.
 * </p>
 *
 * @see Composite
 * @see <a href="https://eclipse.dev/eclipse/swt/snippets/#canvas">Canvas snippets</a>
 * @see <a href="https://eclipse.dev/eclipse/swt/examples.html">SWT Example: ControlExample</a>
 * @see <a href="https://eclipse.dev/eclipse/swt/">Sample code and further information</a>
 */
public class Canvas extends Composite {
	private CanvasLayerSupport canvasLayers;
	Caret caret;
	IME ime;
	boolean blink, drawFlag;
	private IExternalCanvasHandler externalCanvasHandler;

Canvas () {}

/**
 * Constructs a new instance of this class given its parent
 * and a style value describing its behavior and appearance.
 * <p>
 * The style value is either one of the style constants defined in
 * class <code>SWT</code> which is applicable to instances of this
 * class, or must be built by <em>bitwise OR</em>'ing together
 * (that is, using the <code>int</code> "|" operator) two or more
 * of those <code>SWT</code> style constants. The class description
 * lists the style constants that are applicable to the class.
 * Style bits are also inherited from superclasses.
 * </p>
 *
 * @param parent a composite control which will be the parent of the new instance (cannot be null)
 * @param style the style of control to construct
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the parent is null</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the parent</li>
 * </ul>
 *
 * @see SWT
 * @see Widget#getStyle
 */
public Canvas (Composite parent, int style) {
	super (parent, checkStyle (style));
	if( ExternalCanvasHandler.isActive(this,style))
		externalCanvasHandler = ExternalCanvasHandler.createHandler(this);
}

@Override
public void redraw () {

	if(externalCanvasHandler != null) {
		externalCanvasHandler.redrawTriggered();
	}
	super.redraw();
}

@Override
public void redraw (int x, int y, int width, int height, boolean all) {
	if(externalCanvasHandler != null) {
		externalCanvasHandler.redrawTriggered(x,y,width,height,all);
		super.redraw();
	}
	else {
		super.redraw(x,y,width,height,all);
	}
}

/**
 * Fills the interior of the rectangle specified by the arguments,
 * with the receiver's background.
 *
 * @param gc the gc where the rectangle is to be filled
 * @param x the x coordinate of the rectangle to be filled
 * @param y the y coordinate of the rectangle to be filled
 * @param width the width of the rectangle to be filled
 * @param height the height of the rectangle to be filled
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the gc is null</li>
 *    <li>ERROR_INVALID_ARGUMENT - if the gc has been disposed</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 *
 * @since 3.2
 */
public void drawBackground (GC gc, int x, int y, int width, int height) {
	drawBackground (gc, x, y, width, height, 0, 0);
}

/**
 * Returns the caret.
 * <p>
 * The caret for the control is automatically hidden
 * and shown when the control is painted or resized,
 * when focus is gained or lost and when an the control
 * is scrolled.  To avoid drawing on top of the caret,
 * the programmer must hide and show the caret when
 * drawing in the window any other time.
 * </p>
 *
 * @return the caret for the receiver, may be null
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public Caret getCaret () {
	checkWidget();
	return caret;
}

@Override
Point getIMCaretPos () {
    if (caret == null) {
        return super.getIMCaretPos();
    }
	return new Point (caret.x, caret.y);
}

/**
 * Returns the IME.
 *
 * @return the IME
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 *
 * @since 3.4
 */
public IME getIME () {
	checkWidget ();
	return ime;
}

@Override
long gtk3_button_press_event (long widget, long event) {
	if (ime != null) {
		long result = ime.gtk3_button_press_event (widget, event);
        if (result != 0) {
            return result;
        }
	}
	return  super.gtk3_button_press_event (widget, event);
}

@Override
long gtk_commit (long imcontext, long text) {
	if (ime != null) {
		long result = ime.gtk_commit (imcontext, text);
        if (result != 0) {
            return result;
        }
	}
	return super.gtk_commit (imcontext, text);
}

@Override
long gtk_draw (long widget, long cairo) {
    if ((state & OBSCURED) != 0) {
        return 0;
    }

	if(externalCanvasHandler != null) {
		externalCanvasHandler.paint((e)-> sendEvent(SWT.Paint, e),widget, cairo);
		return 0;
	}

	long result = super.gtk_draw (widget, cairo);
	drawCaretInFocus(cairo);
	return result;
}

@Override
void gtk4_draw (long widget, long cairo, Rectangle bounds) {
    if ((state & OBSCURED) != 0) {
        return;
    }
	super.gtk4_draw (widget, cairo, bounds);
	drawCaretInFocus(cairo);
}

@Override
boolean hooksPaint() {
    if (GTK.GTK4 && caret != null && caret.isShowing && caret.isFocusCaret()) {
        return true;
    }
	return super.hooksPaint();
}

void drawCaretInFocus(long cairo) {
	if (GTK.GTK4) {
		/*
		 * Drawing the caret is a matter of painting it into the snapshot whenever
		 * isShowing is true. The blink effect is driven by the timer toggling isShowing
		 * and queuing a redraw.
		 */
		if (caret != null && caret.isFocusCaret() && caret.isShowing) {
			drawCaret(cairo);
		}
	} else {
		/*
		 * blink is needed to be checked as gtk_draw() signals sent from other parts of
		 * the canvas can interfere with the blinking state. This will ensure that we
		 * are only draw/redrawing the caret when it is intended to. See Bug 517487.
		 *
		 * Additionally, only draw the caret if it has focus. See bug 528819.
		 */
		if (caret != null && blink == true && caret.isFocusCaret()) {
			drawCaret(cairo);
			blink = false;
		}
	}
}

private void drawCaret(long cairo) {
    if (this.isDisposed()) {
        return;
    }
    if (cairo == 0) {
        error(SWT.ERROR_NO_HANDLES);
    }

	/*
	 * The drawFlag toggle is a GTK3 mechanism only.
	 */
	if (drawFlag || GTK.GTK4) {
		Cairo.cairo_save(cairo);

		if (caret.image != null && !caret.image.isDisposed() && caret.image.mask == 0) {
			long surface = Cairo.cairo_get_target(cairo);
			int nWidth = 0;
			switch (Cairo.cairo_surface_get_type(surface)) {
				case Cairo.CAIRO_SURFACE_TYPE_IMAGE:
					nWidth = Cairo.cairo_image_surface_get_width(surface);
					break;
				case Cairo.CAIRO_SURFACE_TYPE_XLIB:
					nWidth = Cairo.cairo_xlib_surface_get_width(surface);
					break;
			}
			int nX = caret.x;
            if ((style & SWT.MIRRORED) != 0) {
                nX = getClientWidth() - nWidth - nX;
            }
			Cairo.cairo_translate(cairo, nX, caret.y);
			if (GTK.GTK4) {
				/*
				 * The snapshot surface starts transparent, thus use CAIRO_OPERATOR_OVER with the image directly.
				 */
				Cairo.cairo_set_operator(cairo, Cairo.CAIRO_OPERATOR_OVER);
			} else {
				Cairo.cairo_set_source_rgb(cairo, 1, 1, 1);
				Cairo.cairo_set_operator(cairo, Cairo.CAIRO_OPERATOR_DIFFERENCE);
			}
			Cairo.cairo_set_source_surface(cairo, caret.image.surface, 0, 0);
			Cairo.cairo_paint(cairo);
		} else {
			int nWidth = caret.width, nHeight = caret.height;
            if (nWidth <= 0) {
                nWidth = Caret.DEFAULT_WIDTH;
            }
			int nX = caret.x;
            if ((style & SWT.MIRRORED) != 0) {
                nX = getClientWidth() - nWidth - nX;
            }
			Cairo.cairo_rectangle(cairo, nX, caret.y, nWidth, nHeight);
			if (GTK.GTK4) {
				/*
				 * The snapshot surface starts transparent, thus OVER with the widget's
				 * foreground color instead so the caret is black on light themes and white on
				 * dark themes.
				 */
				Color fg = getForeground();
				Cairo.cairo_set_source_rgb(cairo, fg.getRed() / 255.0, fg.getGreen() / 255.0, fg.getBlue() / 255.0);
				Cairo.cairo_set_operator(cairo, Cairo.CAIRO_OPERATOR_OVER);
			} else {
				Cairo.cairo_set_source_rgb(cairo, 1, 1, 1);
				Cairo.cairo_set_operator(cairo, Cairo.CAIRO_OPERATOR_DIFFERENCE);
			}
		}

		Cairo.cairo_fill(cairo);
		Cairo.cairo_restore(cairo);
		if (!GTK.GTK4 && caret.embeddedInto == null) {
			drawFlag = false;
		}
	} else {
		drawFlag = true;
	}
}

@Override
long gtk_focus_in_event (long widget, long event) {
	long result = super.gtk_focus_in_event (widget, event);
    if (caret != null) {
        caret.setFocus();
    }
	return result;
}

@Override
long gtk_focus_out_event (long widget, long event) {
	long result = super.gtk_focus_out_event (widget, event);
    if (caret != null) {
        caret.killFocus();
    }
	return result;
}

@Override
void gtk4_focus_enter_event(long handle, long event) {
	super.gtk4_focus_enter_event (handle, event);
    if (caret != null) {
        caret.setFocus();
    }
}

@Override
void gtk4_focus_leave_event(long handle, long event) {
	super.gtk4_focus_leave_event (handle, event);
    if (caret != null) {
        caret.killFocus();
    }
}

@Override
long gtk_preedit_changed (long imcontext) {
	if (ime != null) {
		long result = ime.gtk_preedit_changed (imcontext);
        if (result != 0) {
            return result;
        }
	}
	return super.gtk_preedit_changed (imcontext);
}

@Override
void redrawWidget (int x, int y, int width, int height, boolean redrawAll, boolean all, boolean trim) {
	boolean isFocus = caret != null && caret.isFocusCaret ();
    if (isFocus) {
        caret.killFocus();
    }
	super.redrawWidget (x, y, width, height, redrawAll, all, trim);
    if (isFocus) {
        caret.setFocus();
    }
}

@Override
void releaseChildren (boolean destroy) {
	if (caret != null) {
		caret.release (false);
		caret = null;
	}
	if (ime != null) {
		ime.release (false);
		ime = null;
	}
	super.releaseChildren (destroy);
}

@Override
void reskinChildren (int flags) {
    if (caret != null) {
        caret.reskin(flags);
    }
    if (ime != null) {
        ime.reskin(flags);
    }
	super.reskinChildren (flags);
}

/**
 * Scrolls a rectangular area of the receiver by first copying
 * the source area to the destination and then causing the area
 * of the source which is not covered by the destination to
 * be repainted. Children that intersect the rectangle are
 * optionally moved during the operation. In addition, all outstanding
 * paint events are flushed before the source area is copied to
 * ensure that the contents of the canvas are drawn correctly.
 *
 * @param destX the x coordinate of the destination
 * @param destY the y coordinate of the destination
 * @param x the x coordinate of the source
 * @param y the y coordinate of the source
 * @param width the width of the area
 * @param height the height of the area
 * @param all <code>true</code>if children should be scrolled, and <code>false</code> otherwise
 *
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void scroll (int destX, int destY, int x, int y, int width, int height, boolean all) {
	if(this.externalCanvasHandler != null) {
		this.externalCanvasHandler.scroll(destX, destY, x, y, width, height, all);
		return;
	}
	checkWidget();
    if (width <= 0 || height <= 0) {
        return;
    }
    /*
     * scrollInPixels() doesn't seem to be needed on GTK4, so we can return early.
     * In fact it doesn't seem to be needed on GTK3 either, but it's been left
     * here for stability on older GTK3 versions. The investigation
     * as to why it's unneeded is left as a TODO. See bug 546274.
     */
    if (GTK.GTK4) {
        return;
    }
	if ((style & SWT.MIRRORED) != 0) {
		int clientWidth = getClientWidth ();
		x = clientWidth - width - x;
		destX = clientWidth - width - destX;
	}
	int deltaX = destX - x, deltaY = destY - y;
    if (deltaX == 0 && deltaY == 0) {
        return;
    }
    if (!isVisible()) {
        return;
    }
	boolean isFocus = caret != null && caret.isFocusCaret ();
    if (isFocus) {
        caret.killFocus();
    }
	long window = paintWindow ();
	long visibleRegion = GDK.gdk_window_get_visible_region (window);
	cairo_rectangle_int_t srcRect = new cairo_rectangle_int_t ();
	srcRect.x = x;
	srcRect.y = y;
	/*
	 * Feature in GTK: for 3.16+ the "visible" region in Canvas includes
	 * the scrollbar dimensions in its calculations. This means the "previous"
	 * location the scrollbars are re-painted when scrolling, causing the
	 * hopping effect. See bug 480458.
	 */
	long hBarHandle = 0;
	long vBarHandle = 0;
	if (GTK.GTK_IS_SCROLLED_WINDOW(scrolledHandle)) {
		hBarHandle = GTK.gtk_scrolled_window_get_hscrollbar (scrolledHandle);
		vBarHandle = GTK.gtk_scrolled_window_get_vscrollbar (scrolledHandle);
	}
	GtkRequisition requisition = new GtkRequisition();
	if (hBarHandle != 0) {
		gtk_widget_get_preferred_size (hBarHandle, requisition);
		if (requisition.height > 0) {
			srcRect.y = y - requisition.height;
		}
	}
	if (vBarHandle != 0) {
		gtk_widget_get_preferred_size (vBarHandle, requisition);
		if (requisition.width > 0) {
			srcRect.x = x - requisition.width;
		}
	}
	srcRect.width = width;
	srcRect.height = height;
	long copyRegion = Cairo.cairo_region_create_rectangle (srcRect);
	Cairo.cairo_region_intersect(copyRegion, visibleRegion);
	long invalidateRegion = Cairo.cairo_region_create_rectangle (srcRect);
	Cairo.cairo_region_subtract (invalidateRegion, visibleRegion);
	Cairo.cairo_region_translate (invalidateRegion, deltaX, deltaY);
	cairo_rectangle_int_t copyRect = new cairo_rectangle_int_t();
	Cairo.cairo_region_get_extents (copyRegion, copyRect);
	if (copyRect.width != 0 && copyRect.height != 0) {
		update ();
	}
	Control control = findBackgroundControl ();
    if (control == null) {
        control = this;
    }
	if (control.backgroundImage != null) {
		redrawWidget (x, y, width, height, false, false, false);
		redrawWidget (destX, destY, width, height, false, false, false);
	} else {
		long cairo = GDK.gdk_cairo_create(window);
		if (Cairo.cairo_version() < Cairo.CAIRO_VERSION_ENCODE(1, 12, 0)) {
			GDK.gdk_cairo_set_source_window(cairo, window, 0, 0);
		} else {
			Cairo.cairo_push_group(cairo);
			GDK.gdk_cairo_set_source_window(cairo, window, 0, 0);
			Cairo.cairo_paint(cairo);
			Cairo.cairo_pop_group_to_source(cairo);
		}
		double[] matrix = {1, 0, 0, 1, -deltaX, -deltaY};
		Cairo.cairo_pattern_set_matrix(Cairo.cairo_get_source(cairo), matrix);
		Cairo.cairo_rectangle(cairo, copyRect.x + deltaX, copyRect.y + deltaY, copyRect.width, copyRect.height);
		Cairo.cairo_clip(cairo);
		Cairo.cairo_paint(cairo);
		Cairo.cairo_destroy(cairo);
		boolean disjoint = (destX + width < x) || (x + width < destX) || (destY + height < y) || (y + height < destY);
		if (disjoint) {
			cairo_rectangle_int_t rect = new cairo_rectangle_int_t();
			rect.x = x;
			rect.y = y;
			rect.width = width;
			rect.height = height;
			Cairo.cairo_region_union_rectangle (invalidateRegion, rect);
		} else {
			cairo_rectangle_int_t rect = new cairo_rectangle_int_t();
			if (deltaX != 0) {
				int newX = destX - deltaX;
                if (deltaX < 0) {
                    newX = destX + width;
                }
				rect.x = newX;
				rect.y = y;
				rect.width = Math.abs(deltaX);
				rect.height = height;
				Cairo.cairo_region_union_rectangle (invalidateRegion, rect);
			}
			if (deltaY != 0) {
				int newY = destY - deltaY;
                if (deltaY < 0) {
                    newY = destY + height;
                }
				rect.x = x;
				rect.y = newY;
				rect.width = width;
				rect.height = Math.abs(deltaY);
				Cairo.cairo_region_union_rectangle (invalidateRegion, rect);
			}
		}
		GDK.gdk_window_invalidate_region(window, invalidateRegion, all);
	}
	Cairo.cairo_region_destroy (visibleRegion);
	Cairo.cairo_region_destroy (copyRegion);
	Cairo.cairo_region_destroy (invalidateRegion);
	if (all) {
		for (Control child : _getChildren ()) {
			Rectangle rect = child.getBoundsInPixels ();
			if (Math.min(x + width, rect.x + rect.width) >= Math.max (x, rect.x) &&
				Math.min(y + height, rect.y + rect.height) >= Math.max (y, rect.y)) {
					child.setLocation (rect.x + deltaX, rect.y + deltaY);
			}
		}
	}
    if (isFocus) {
        caret.setFocus();
    }
	/*
	 * Due to overlay drawing of scrollbars current method of scrolling leaves scrollbar and notifiers for them inside the canvas
	 * after scroll. Fix is to redraw once done.
	 */
	redraw(false);
}

@Override
int setBounds (int x, int y, int width, int height, boolean move, boolean resize) {
	boolean isFocus = caret != null && caret.isFocusCaret ();
    if (isFocus) {
        caret.killFocus();
    }
	int result = super.setBounds (x, y, width, height, move, resize);
    if (isFocus) {
        caret.setFocus();
    }
	return result;
}

/**
 * Sets the receiver's caret.
 * <p>
 * The caret for the control is automatically hidden
 * and shown when the control is painted or resized,
 * when focus is gained or lost and when an the control
 * is scrolled.  To avoid drawing on top of the caret,
 * the programmer must hide and show the caret when
 * drawing in the window any other time.
 * </p>
 * @param caret the new caret for the receiver, may be null
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the caret has been disposed</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 */
public void setCaret (Caret caret) {
	checkWidget();
	Caret newCaret = caret;
	Caret oldCaret = this.caret;
	this.caret = newCaret;
	if (hasFocus ()) {
        if (oldCaret != null) {
            oldCaret.killFocus();
        }
		if (newCaret != null) {
            if (newCaret.isDisposed()) {
                error(SWT.ERROR_INVALID_ARGUMENT);
            }
			newCaret.setFocus ();
		}
	}
}

@Override
public void setFont (Font font) {
	checkWidget();
    if (caret != null) {
        caret.setFont(font);
    }
	super.setFont (font);
}

/**
 * Sets the receiver's IME.
 *
 * @param ime the new IME for the receiver, may be null
 *
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_INVALID_ARGUMENT - if the IME has been disposed</li>
 * </ul>
 * @exception SWTException <ul>
 *    <li>ERROR_WIDGET_DISPOSED - if the receiver has been disposed</li>
 *    <li>ERROR_THREAD_INVALID_ACCESS - if not called from the thread that created the receiver</li>
 * </ul>
 *
 * @since 3.4
 */
public void setIME (IME ime) {
	checkWidget ();
    if (ime != null && ime.isDisposed()) {
        error(SWT.ERROR_INVALID_ARGUMENT);
    }
	this.ime = ime;
}

void updateCaret () {
	long imHandle = imHandle ();
    if (imHandle == 0) {
        return;
    }
	GdkRectangle rect = new GdkRectangle ();
	rect.x = caret.x;
	rect.y = caret.y;
	rect.width = caret.width;
	rect.height = caret.height;
	GTK.gtk_im_context_set_cursor_location (imHandle, rect);
}

@Override
void snapshotToDraw(long handle, long snapshot) {
	// Skip drawing if this is being called on fixedHandle to prevent double draws
	// making carret not visible at all
	if (fixedHandle != 0 && handle == fixedHandle) {
		return;
	}
	super.snapshotToDraw(handle, snapshot);
}


/**
 * Adds a retained raster layer to this Canvas and returns its stable handle.
 * The image pixels are copied; subsequent changes to the ImageData do not
 * affect the layer. New layers are visible, input-enabled, fully opaque and
 * have z order zero. Equal z orders paint in creation order.
 * <p>
 * Layers use Canvas client coordinates, nearest-neighbor affine sampling,
 * eight-bit source-over compositing and per-layer opacity. They are painted
 * by a Paint listener installed on the first call to this method, retaining
 * the ordinary ordering of other Paint listeners. Native child Controls keep
 * their original native stacking and are not transformed or composited.
 * </p><p>
 * Global ImageData alpha overrides alphaData; transparent pixels and icon
 * masks remain transparent. A layer is a flattened image, so opacity applies
 * once to its pixels. This does not isolate arbitrary Paint listeners or
 * create perspective, blend effects or nested group opacity.
 * </p><p>
 * Presentation reuses at most sixteen native image tiles of up to 128 by 128
 * pixels. Clearing the layers or disposing this Canvas releases that cache.
 * Moving or changing a layer repaints its affected client regions.
 * </p>
 *
 * @param image the source pixels (cannot be null)
 * @return a Canvas-local handle, never reused by this Canvas
 * @exception IllegalArgumentException if the image is null or invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public int addLayer (ImageData image) {
	checkWidget ();
	int [] pixels = CanvasLayerSupport.copyPixels (image);
	if (canvasLayers == null) canvasLayers = new CanvasLayerSupport (this);
	return canvasLayers.add (image.width, image.height, pixels);
}

/**
 * Returns the live layer handles in back-to-front paint order.
 * The returned array is a caller-owned snapshot.
 *
 * @return the layer handles, or an empty array
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public int [] getLayerIds () {
	checkWidget ();
	return canvasLayers == null ? new int [0] : canvasLayers.ids ();
}

/**
 * Removes a layer and releases its pixels and mask. Its handle becomes invalid.
 *
 * @param layer a live layer handle returned by this Canvas
 * @exception IllegalArgumentException if the handle is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void removeLayer (int layer) {
	checkWidget ();
	layerSupport ().remove (layer);
}

/**
 * Removes all layers and releases their pixels, masks and presentation buffers.
 * Previously returned handles remain invalid after subsequent additions.
 *
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void removeAllLayers () {
	checkWidget ();
	if (canvasLayers != null) canvasLayers.clear ();
}

/**
 * Replaces a layer's pixels with a copy of the supplied image. Placement,
 * clipping, z order, visibility and opacity are preserved. A mask is retained
 * only if the image dimensions are unchanged; otherwise it is cleared.
 *
 * @param layer a live layer handle returned by this Canvas
 * @param image the replacement source pixels
 * @exception IllegalArgumentException if the handle or image is invalid, or image is null
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void setLayerImage (int layer, ImageData image) {
	checkWidget ();
	layerSupport ().setImage (layer, image);
}

/**
 * Sets a layer's stacking depth. Lower values paint first; all int values are
 * valid. Equal depths retain creation order, including after reordering.
 *
 * @param layer a live layer handle returned by this Canvas
 * @param zOrder the stacking depth
 * @exception IllegalArgumentException if the handle is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void setLayerZOrder (int layer, int zOrder) {
	checkWidget ();
	layerSupport ().setZOrder (layer, zOrder);
}

/**
 * Returns a layer's stacking depth.
 * @param layer a live layer handle returned by this Canvas
 * @return the stacking depth
 * @exception IllegalArgumentException if the handle is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public int getLayerZOrder (int layer) {
	checkWidget ();
	return layerSupport ().zOrder (layer);
}

/**
 * Sets the opacity of the flattened layer, multiplied with its pixel and mask
 * alpha. Zero is transparent and 255 is fully opaque.
 * @param layer a live layer handle returned by this Canvas
 * @param opacity an opacity from 0 through 255
 * @exception IllegalArgumentException if the handle or opacity is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void setLayerOpacity (int layer, int opacity) {
	checkWidget ();
	layerSupport ().setOpacity (layer, opacity);
}

/**
 * Returns a layer's opacity.
 * @param layer a live layer handle returned by this Canvas
 * @return an opacity from 0 through 255
 * @exception IllegalArgumentException if the handle is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public int getLayerOpacity (int layer) {
	checkWidget ();
	return layerSupport ().opacity (layer);
}

/**
 * Sets whether a layer participates in painting and picking. Hidden pixels are
 * retained so showing the layer again restores its contents.
 * @param layer a live layer handle returned by this Canvas
 * @param visible whether the layer is visible
 * @exception IllegalArgumentException if the handle is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void setLayerVisible (int layer, boolean visible) {
	checkWidget ();
	layerSupport ().setVisible (layer, visible);
}

/**
 * Returns the explicit visibility of a layer.
 * @param layer a live layer handle returned by this Canvas
 * @return whether the layer is visible
 * @exception IllegalArgumentException if the handle is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public boolean getLayerVisible (int layer) {
	checkWidget ();
	return layerSupport ().visible (layer);
}

/**
 * Sets whether picking may return a layer. This does not change its rendering
 * or deliver synthetic events. Mouse and keyboard events remain Canvas events.
 * @param layer a live layer handle returned by this Canvas
 * @param enabled whether the layer participates in picking
 * @exception IllegalArgumentException if the handle is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void setLayerInputEnabled (int layer, boolean enabled) {
	checkWidget ();
	layerSupport ().setInputEnabled (layer, enabled);
}

/**
 * Returns whether a layer participates in picking.
 * @param layer a live layer handle returned by this Canvas
 * @return whether picking is enabled
 * @exception IllegalArgumentException if the handle is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public boolean getLayerInputEnabled (int layer) {
	checkWidget ();
	return layerSupport ().inputEnabled (layer);
}

/**
 * Moves the layer's local origin, preserving the linear part of its transform.
 * Coordinates use the same float precision as SWT Transform elements.
 * @param layer a live layer handle returned by this Canvas
 * @param x the Canvas x coordinate
 * @param y the Canvas y coordinate
 * @exception IllegalArgumentException if the handle is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void setLayerLocation (int layer, int x, int y) {
	checkWidget ();
	layerSupport ().setLocation (layer, x, y);
}

/**
 * Copies a finite, invertible affine transform from layer-local pixels to Canvas
 * coordinates. Null resets the identity. The caller retains the Transform and
 * may change or dispose it after this call. Sampling uses nearest neighbors.
 * @param layer a live layer handle returned by this Canvas
 * @param transform the transform, or null for identity
 * @exception IllegalArgumentException if the handle, transform or its device is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void setLayerTransform (int layer, Transform transform) {
	checkWidget ();
	layerSupport ().setTransform (layer, transform);
}

/**
 * Copies the layer's transform into a caller-owned Transform on the same Display.
 * @param layer a live layer handle returned by this Canvas
 * @param transform the destination transform (cannot be null)
 * @exception IllegalArgumentException if the handle or destination is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void getLayerTransform (int layer, Transform transform) {
	checkWidget ();
	layerSupport ().getTransform (layer, transform);
}

/**
 * Copies an optional rectangular clip in layer-local pixel coordinates. Null
 * clears the clip. Empty clips hide the layer; negative dimensions are invalid.
 * @param layer a live layer handle returned by this Canvas
 * @param clip the local clip, or null
 * @exception IllegalArgumentException if the handle or clip is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void setLayerClip (int layer, Rectangle clip) {
	checkWidget ();
	layerSupport ().setClip (layer, clip);
}

/**
 * Copies a mask containing one unsigned alpha byte per source pixel, in row-major
 * order. It multiplies the source alpha and layer opacity. Null clears the mask.
 * @param layer a live layer handle returned by this Canvas
 * @param alphaMask the mask, or null
 * @exception IllegalArgumentException if the handle or mask length is invalid
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public void setLayerMask (int layer, byte [] alphaMask) {
	checkWidget ();
	layerSupport ().setMask (layer, alphaMask);
}

/**
 * Returns the topmost visible, input-enabled layer with nonzero effective alpha
 * at a Canvas client pixel, or -1. Clipping, masks and transforms match painting.
 * Pixel centers are sampled. Points outside the client area return -1.
 * @param x the Canvas x coordinate
 * @param y the Canvas y coordinate
 * @return the picked layer handle, or -1
 * @exception SWTException if the receiver is disposed or called from another thread
 * @since 3.138 (fork extension)
 */
public int getLayerAtPoint (int x, int y) {
	checkWidget ();
	return canvasLayers == null ? -1 : canvasLayers.atPoint (x, y);
}

private CanvasLayerSupport layerSupport () {
	if (canvasLayers == null) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
	return canvasLayers;
}

/* Preserve the borrowed GC while presenting source-over through the native image path. */
void drawLayerTile (GC gc, Image tile, int sourceX, int sourceY, int x, int y, int width, int height) {
	boolean xor = gc.getXORMode ();
	try {
		if (xor) gc.setXORMode (false);
		gc.drawImage (tile, sourceX, sourceY, width, height, x, y, width, height);
	} finally {
		if (xor && !gc.isDisposed ()) gc.setXORMode (true);
	}
}

@Override
void releaseWidget () {
	try {
		releaseLayers ();
	} finally {
		super.releaseWidget ();
	}
}

private void releaseLayers () {
	CanvasLayerSupport support = canvasLayers;
	canvasLayers = null;
	if (support != null) support.dispose ();
}
}
