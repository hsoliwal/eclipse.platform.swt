/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
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
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tracker;
import org.junit.jupiter.api.BeforeEach;

/**
 * Automated Test Suite for class org.eclipse.swt.widgets.Tracker
 *
 * @see org.eclipse.swt.widgets.Tracker
 */
public class Test_org_eclipse_swt_widgets_Tracker extends Test_org_eclipse_swt_widgets_Widget {

@Override
@BeforeEach
public void setUp() {
	super.setUp();
	tracker = new Tracker(shell, 0);
	setWidget(tracker);
}

/*
 * A Tracker is not a child control: disposing its parent shell does not dispose it (no
 * platform lists it among the parent's children), so release it before the shell checks.
 */
//FLLDCIM: TOP - the base teardown asserts the widget died with the shell; true for controls, not for Tracker
@Override
protected void afterDispose(Display display) {
	if (!tracker.isDisposed()) {
		tracker.dispose();
	}
	super.afterDispose(display);
}

/* custom */
	Tracker tracker;
}
