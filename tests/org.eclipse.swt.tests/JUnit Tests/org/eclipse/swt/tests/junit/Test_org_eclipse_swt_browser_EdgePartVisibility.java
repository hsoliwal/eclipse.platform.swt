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
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.function.BooleanSupplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every-widget H leaf of the Edge browser (win32, WebView2): the page follows the browser's part visibility, so a page
 * under a hidden parent or in a minimized shell is hidden for WebView2 (document.visibilityState is "hidden", no
 * rendering, throttled timers) and visible again when shown or restored.
 */
public class Test_org_eclipse_swt_browser_EdgePartVisibility {

	private Display display;
	private Shell shell;
	private Composite holder;
	private Browser browser;

	@BeforeEach
	public void setUp() {
		assumeTrue(SWT.getPlatform().equals("win32"), "Edge is win32 only");
		display = Display.getDefault();
		shell = new Shell(display);
		shell.setLayout(new FillLayout());
		holder = new Composite(shell, SWT.NONE);
		holder.setLayout(new FillLayout());
		try {
			browser = new Browser(holder, SWT.EDGE);
		} catch (SWTError e) {
			assumeTrue(false, "no WebView2 runtime: " + e.getMessage());
		}
		shell.setSize(400, 300);
		shell.open();
		boolean[] loaded = new boolean[1];
		browser.addProgressListener(new ProgressAdapter() {
			@Override
			public void completed(ProgressEvent event) {
				loaded[0] = true;
			}
		});
		browser.setText("<html><body>page</body></html>");
		assumeTrue(spinUntil(() -> loaded[0], 20000), "the page did not load");
	}

	@AfterEach
	public void tearDown() {
		if (shell != null) {
			shell.dispose();
		}
	}

	@Test
	public void test_pageUnderAHiddenParentIsHiddenForWebView2() {
		assertEquals("visible", state(), "a shown page is visible");
		holder.setVisible(false);
		spinUntil(() -> "hidden".equals(state()), 5000);
		assertEquals("hidden", state(), "a page under a hidden parent is hidden");
		holder.setVisible(true);
		spinUntil(() -> "visible".equals(state()), 5000);
		assertEquals("visible", state(), "the page is visible again when its parent shows");
	}

	@Test
	public void test_pageInAMinimizedShellIsHiddenForWebView2() {
		shell.setMinimized(true);
		spinUntil(() -> false, 300);
		assumeTrue(shell.getMinimized(), "the desktop refused to minimize");
		spinUntil(() -> "hidden".equals(state()), 5000);
		assertEquals("hidden", state(), "a page in a minimized shell is hidden");
		shell.setMinimized(false);
		spinUntil(() -> "visible".equals(state()), 5000);
		assertEquals("visible", state(), "the page is visible again when the shell is restored");
	}

	private String state() {
		return String.valueOf(browser.evaluate("return document.visibilityState;"));
	}

	private boolean spinUntil(BooleanSupplier condition, long milliseconds) {
		long end = System.currentTimeMillis() + milliseconds;
		while (System.currentTimeMillis() < end) {
			if (condition.getAsBoolean()) {
				return true;
			}
			if (!display.readAndDispatch()) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					return false;
				}
			}
		}
		return condition.getAsBoolean();
	}
}
