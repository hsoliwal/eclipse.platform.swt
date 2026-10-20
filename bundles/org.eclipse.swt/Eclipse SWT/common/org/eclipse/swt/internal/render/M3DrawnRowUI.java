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
package org.eclipse.swt.internal.render;

import org.eclipse.swt.internal.laf.*;
import org.eclipse.swt.widgets.*;

/**
 * The UI delegate of the drawn rows of a Tree, Table or List in the custom, m3 and gpu modes (docs/m3/LOOK_AND_FEEL.md
 * LF6; Swing's <code>ComponentUI</code>). One row delegate type: the LF6 contract is the all-widgets track's
 * <code>org.eclipse.swt.internal.laf.M3RowUI</code>; this class is the drawn planes' implementation of it (it
 * implements that interface once the interface is in integration) and no second row interface exists. A drawn plane
 * installs it with {@link M3LookAndFeel#installUI}; it reads the
 * row colours of its look and feel from {@link M3UIDefaults} into a {@link Theme} ({@link ThemeDefaults}), and the
 * plane's {@link RowRenderer} paints with that theme. Its paint keys are the colour keys it reads, so a look and feel
 * switch that changes one of them installs it again and repaints the widget; it has no metric keys (stage 1: the
 * native control owns the geometry). It keeps no widget state.
 */
public final class M3DrawnRowUI implements M3ComponentUI {
	private final String widget;
	private final int [] keys;
	private volatile Theme theme;

	/** @param widget the widget's simple class name (<code>Tree</code>, <code>Table</code>, <code>List</code>) */
	public M3DrawnRowUI (String widget) {
		this.widget = widget;
		this.keys = ThemeDefaults.keys (widget);
		this.theme = Theme.NATIVE;
	}

	@Override
	public void installUI (Control control, M3UIDefaults defaults) {
		theme = ThemeDefaults.of (widget, ThemeDefaults.look (defaults.name ()), defaults);
	}

	@Override
	public int [] paintKeys () {
		return keys;
	}

	/** The theme of the look and feel this delegate was last installed with. */
	public Theme theme () {
		return theme;
	}
}
