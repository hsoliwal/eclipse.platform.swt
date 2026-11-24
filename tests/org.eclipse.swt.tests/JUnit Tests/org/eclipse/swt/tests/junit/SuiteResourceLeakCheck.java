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
 *     Hitesh Soliwal (Synexia) - shared by the suites of org.eclipse.swt.tests and org.eclipse.swt.tests.win32
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.graphics.Resource;

/**
 * The resource leak check of a test suite: {@link #beforeSuite()} collects every SWT {@link Resource}
 * that is garbage collected without having been disposed, and {@link #afterSuite()} runs the garbage
 * collector and fails with the first leak. A suite calls them from its <code>@BeforeSuite</code> and
 * <code>@AfterSuite</code> methods: {@link AllNonBrowserTests} (and the suites derived from it) and
 * <code>org.eclipse.swt.tests.win32.AllWin32Tests</code>.
 */
public final class SuiteResourceLeakCheck {

	private static List<Error> leakedResources;

	private SuiteResourceLeakCheck () {
	}

	public static void beforeSuite() {
		// Set up ResourceTracked to detect any leaks
		leakedResources = new ArrayList<> ();
		Resource.setNonDisposeHandler (error -> {
			// Can't 'Assert.fail()' here, because Handler is called on
			// some other thread and Exception will simply be ignored.
			// Workaround: collect errors and report later.
			leakedResources.add (error);
		});
	}

	/*
	 * It would be easier to understand if errors here were reported
	 * through  a test and not through @AfterSuite, but this is a
	 * suite class and not a test class, so it can't have tests.
	 */
	public static void afterSuite() {
		// Run GC in order do detect any outstanding leaks
		System.gc ();
		// And wait a bit to let Resource.ResourceTracker, that is
		// queued to another thread, do its job. It is unfortunate that
		// there is no simple better way to wait except waiting some
		// fixed timeout.
		try {
			Thread.sleep (100);
		} catch (InterruptedException e) {
			// Just ignore
		}

		for (Error leak : leakedResources) {
			leak.printStackTrace ();
		}

		if (0 != leakedResources.size ()) {
			// In order to help tools to filter fail reason out of entire log, throw the first leak.
			String hint = leakedResources.size () + " leaks found, the first is:";
			throw new AssertionError(hint, leakedResources.get (0));
		}
	}
}
