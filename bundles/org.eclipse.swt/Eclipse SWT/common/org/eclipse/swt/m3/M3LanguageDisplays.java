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
package org.eclipse.swt.m3;

import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.*;

import org.eclipse.swt.widgets.*;

/*
 * The displays that have language registrations (a switch relabels the others on their threads) and the orientation flip of a
 * display's shells (M3Language.flip).
 */
final class M3LanguageDisplays {
	private static final List<Display> DISPLAYS = new CopyOnWriteArrayList<> ();

	private M3LanguageDisplays () {
	}

	static void add (Display display) {
		if (DISPLAYS.contains (display)) return;
		DISPLAYS.add (display);
		display.disposeExec (() -> DISPLAYS.remove (display));
	}

	static Stream<Display> others (Display current) {
		return DISPLAYS.stream ().filter (display -> display != current && !display.isDisposed ());
	}

	static int flip (Display display, int orientation) {
		return (int) Arrays.stream (display.getShells ()).filter (M3Language::follows)
			.filter (shell -> shell.getOrientation () != orientation).filter (shell -> flip (shell, orientation)).count ();
	}

	/* the shell and its children take the orientation; descendants whose class does not follow keep theirs */
	private static boolean flip (Shell shell, int orientation) {
		Map<Control, Integer> kept = new LinkedHashMap<> ();
		M3ThemeApplier.walk (shell).filter (control -> !M3Language.follows (control)).forEach (control -> kept.put (control, control.getOrientation ()));
		shell.setOrientation (orientation);
		kept.forEach (Control::setOrientation);
		shell.layout (true, true);
		return true;
	}
}
