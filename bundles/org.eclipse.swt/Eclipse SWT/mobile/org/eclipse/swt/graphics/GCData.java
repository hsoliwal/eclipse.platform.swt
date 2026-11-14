/*******************************************************************************
 * Copyright (c) 2026 Hitesh Soliwal and Contributors to the Synexia Project.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.graphics;

import org.eclipse.swt.widgets.Control;

/** Platform data attached to one GC lease. Not application API. */
public final class GCData {
	public Device device;
	public Image image;
	public Control control;
	public boolean screen;
	public int style;
}
