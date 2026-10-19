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
package m3.i31.examples;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;

/**
 * A List of 1,000,000 strings on Initiative 31 (SWT on Swing), scrolled to the middle with <code>setTopIndex</code>
 * and <code>showSelection</code>; see {@link M3ExampleWindow} for the arguments.
 */
public final class M3ListExample {
	private M3ListExample () {
	}

	public static void main (String [] args) {
		M3ExampleWindow window = new M3ExampleWindow ("List", args);
		List list = new List (window.shell, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL);
		window.fill (list);
		String [] items = new String [window.rows];
		for (int i = 0; i < items.length; i++) {
			items [i] = String.format ("Line %,d", i);
		}
		long start = System.nanoTime ();
		list.setItems (items);
		long millis = (System.nanoTime () - start) / 1_000_000;
		window.run (() -> {
			list.setTopIndex (window.top);
			list.setSelection (window.top + 3);
			list.showSelection ();
			window.pump ();
			window.describe ("List", millis, "top index " + String.format ("%,d", list.getTopIndex ()));
		});
	}
}
