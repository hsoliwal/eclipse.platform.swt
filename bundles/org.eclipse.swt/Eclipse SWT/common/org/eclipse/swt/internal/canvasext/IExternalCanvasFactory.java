/*******************************************************************************
 * Copyright (c) 2025 SAP SE and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.internal.canvasext;

import org.eclipse.swt.*;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.widgets.*;

/**
 * Defines a factory interface for creating external canvas handlers.
 * Implementations of this interface can be provided to enable support for
 * different types of canvas extensions based on the style of the canvas and the
 * availability of an extension factory.
 */
public interface IExternalCanvasFactory {

	IExternalCanvasHandler createCanvasExtension(Canvas c);

	/**
	 * M3 (GC seam, leaf 5): whether this factory handles a canvas being constructed (its runtime class is known, the
	 * subclass constructor has not run yet). The default is the Initiative 31 rule the seam was adopted with: the canvas
	 * was created with {@link SWT#M3} and is not a <code>StyledText</code>. The M3 factory decides by the widget mode of
	 * the canvas's class instead, so every <code>Canvas</code> subclass is drawn through it in the <code>m3</code> and
	 * <code>gpu</code> modes.
	 */
	default boolean accepts(Canvas canvas, int style) {
		return (style & SWT.M3) != 0 && !(canvas instanceof StyledText);
	}

}
