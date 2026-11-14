/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 ******************************************************************************/
package org.eclipse.swt.tests.junit;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * The differential M3 contracts, selected once by {@link AllNonBrowserTests}.
 * The ordinary M3 contracts remain selected by {@link AllWidgetTests}.
 * <p>
 * Every class but {@link Test_M3SortFilterDifferential} requires independently qualified official SWT recordings through
 * {@code swt.viewport.recording}. Missing recordings can produce skipped tests;
 * registration does not make those skips release evidence. The release report
 * gate requires every recorded comparison to pass in both widget modes.
 * </p>
 * <p>
 * {@link Test_M3SortFilterDifferential} is a self-contained seeded campaign up
 * to one million rows. Its normal-suite runtime and memory cost require platform
 * qualification together with the other newly selected tests.
 * </p>
 */
@Suite
@SelectClasses({
	Test_M3BulkUpdatesDifferential.class,
	Test_M3DiffApplyDifferential.class,
	Test_M3PrefetchDifferential.class,
	Test_M3ProxyModelDifferential.class,
	Test_M3ResidencyDifferential.class,
	Test_M3RowMathDifferential.class,
	Test_M3ScrollBarModelDifferential.class,
	Test_M3ScrollMementoDifferential.class,
	Test_M3SortFilterDifferential.class
})
public class AllM3DifferentialTests {
}
