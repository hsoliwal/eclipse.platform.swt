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
 * Derived by recipe (M3jdk21 m3/docs/viewport-everywhere/recipe, SwtHome) from hsoliwal/M3jdk21 m3/viewport-everywhere-20261018 b8313c4f84dc5426f41c259b56c2b864327c3f73,
 * com.m3.collections.M3ScrollLayout (same author; the SWT copy of the viewport collections).
 * Derived by recipe (m3/docs/viewport-everywhere/recipe) from hsoliwal/eclipse.platform.swt 553eefd41d9961d303c41e0e4e181712e64a1e45,
 * ViewportScrollLayout.java; Apache-2.0 elected from its Apache-2.0 OR EPL-2.0 publication
 * (m3/docs/viewport-everywhere/LICENSE_ELECTION.tsv).
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package org.eclipse.swt.internal.m3.collections;

/**
 * Package-local compatibility adapter for the canonical viewport scrollbar
 * fixed-point solver in {@link M3WindowRuntime}.
 */
public final class M3ScrollLayout {
	static final int AUTO = M3WindowRuntime.AUTO;
	static final int ALWAYS = M3WindowRuntime.ALWAYS;
	static final int NEVER = M3WindowRuntime.NEVER;

	public record Result (
			boolean horizontalVisible,
			boolean verticalVisible,
			boolean cornerVisible,
			int bodyWidth,
			int bodyHeight,
			int headerWidth,
			int headerHeight,
			int visibleRows,
			long logicalRows,
			long estimatedContentHeight,
			long logicalContentWidth) {
	}

	private M3ScrollLayout () {
	}

	public static Result solve (
			int outerWidth,
			int outerHeight,
			int headerHeight,
			long logicalRows,
			int sampleRowHeight,
			long logicalContentWidth,
			int horizontalBarHeight,
			int verticalBarWidth,
			int horizontalPolicy,
			int verticalPolicy) {
		M3WindowRuntime.RowLayout layout = M3WindowRuntime.solveRows (
				outerWidth,
				outerHeight,
				headerHeight,
				logicalRows,
				sampleRowHeight,
				logicalContentWidth,
				horizontalBarHeight,
				verticalBarWidth,
				horizontalPolicy,
				verticalPolicy);
		return new Result (
				layout.horizontalVisible (),
				layout.verticalVisible (),
				layout.cornerVisible (),
				layout.bodyWidth (),
				layout.bodyHeight (),
				layout.headerWidth (),
				layout.headerHeight (),
				layout.visibleRows (),
				layout.logicalRows (),
				layout.estimatedContentHeight (),
				layout.logicalContentWidth ());
	}
}
