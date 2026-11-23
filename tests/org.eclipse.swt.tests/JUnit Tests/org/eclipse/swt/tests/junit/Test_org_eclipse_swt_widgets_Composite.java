/*******************************************************************************
 * Copyright (c) 2000, 2019 IBM Corporation and others.
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;


/**
 * Automated Test Suite for class org.eclipse.swt.widgets.Composite
 *
 * @see org.eclipse.swt.widgets.Composite
 */
public class Test_org_eclipse_swt_widgets_Composite extends Test_org_eclipse_swt_widgets_Scrollable {

Composite composite;

@Override
@BeforeEach
public void setUp() {
	super.setUp();
	composite = new Composite(shell, 0);
	super.setWidget(composite);
}

@Override
@Test
public void test_ConstructorLorg_eclipse_swt_widgets_CompositeI() {
	assertThrows(IllegalArgumentException.class, () -> new Composite(null, 0), "No exception thrown for parent == null");

	int[] cases = {SWT.H_SCROLL, SWT.V_SCROLL, SWT.H_SCROLL | SWT.V_SCROLL};
    for (int style : cases) {
        composite = new Composite(shell, style);
    }
}


@Test
public void test_nativeBevelBorderAndControlReliefShareOnePaintOwner() {
	if (!SwtTestUtil.isWindows) return;
	Composite direct = new Composite(shell, SWT.NONE);
	try {
		direct.setSize(120, 60);
		direct.setBevelBorder(SWT.SHADOW_OUT);
		int originalWidth = direct.getBorderWidth();
		assertTrue(originalWidth > 0, "native Composite bevel has non-client trim");
		for (int relief : new int[] {SWT.SHADOW_IN, SWT.SHADOW_OUT,
				SWT.SHADOW_ETCHED_IN, SWT.SHADOW_ETCHED_OUT}) {
			direct.setBorderRelief(relief);
			assertEquals(relief, direct.getBorderRelief());
			assertEquals(SWT.SHADOW_OUT, direct.getBevelBorder(),
					"Control relief preserves the underlying Composite bevel");
			assertTrue(direct.getBorderWidth() > 0);
		}
		direct.setBorderRelief(SWT.SHADOW_NONE);
		assertEquals(0, direct.getBorderWidth(), "explicit no-border overrides bevel");
		direct.setBorderRelief(SWT.DEFAULT);
		assertEquals(originalWidth, direct.getBorderWidth(),
				"reset recovers the underlying Composite bevel");
		direct.setBorderRelief(SWT.SHADOW_IN);
		direct.setBevelBorder(SWT.SHADOW_ETCHED_IN);
		assertEquals(SWT.DEFAULT, direct.getBorderRelief(),
				"latest Composite request supersedes Control relief");
		assertEquals(SWT.SHADOW_ETCHED_IN, direct.getBevelBorder());
		assertTrue(direct.getBorderWidth() > 0);
		direct.setBorderRelief(SWT.SHADOW_NONE);
		assertEquals(0, direct.getBorderWidth());
		direct.setBorderRelief(SWT.DEFAULT);
		assertEquals(SWT.SHADOW_ETCHED_IN, direct.getBevelBorder());
		assertTrue(direct.getBorderWidth() > 0);
	} finally {
		direct.dispose();
	}
}

@Test
public void test_nativeTinyCanvasBordersPreserveNativeFrame() {
	if (!SwtTestUtil.isWindows) return;
	shell.open();
	for (int size : new int[] {0, 1, 2, 3, 4, 8}) {
		org.eclipse.swt.widgets.Canvas canvas =
				new org.eclipse.swt.widgets.Canvas(shell, SWT.BORDER);
		try {
			canvas.setBounds(5, 5, size, size);
			canvas.redraw();
			canvas.update();
			assertTrue(canvas.getBorderWidth() > 0, "original native border trim");
			canvas.setBevelBorder(SWT.SHADOW_IN);
			canvas.redraw();
			canvas.update();
			assertEquals(SWT.SHADOW_IN, canvas.getBevelBorder());
			canvas.setBevelBorder(SWT.SHADOW_NONE);
			canvas.redraw();
			canvas.update();
			assertEquals(SWT.NONE, canvas.getBevelBorder());
		} finally {
			canvas.dispose();
		}
	}
}

@Test
public void test_nativeBevelBorderStyles() {
	if (!SwtTestUtil.isWindows) return;
	Composite plain = new Composite(shell, SWT.NONE);
	assertEquals(SWT.NONE, plain.getBevelBorder());
	assertEquals(0, plain.getBorderWidth());
	// SWT reuses SHADOW_ETCHED_IN as NO_SCROLL; do not reinterpret constructor bits.
	Composite constructorHint = new Composite(shell, SWT.SHADOW_ETCHED_IN);
	assertEquals(SWT.NONE, constructorHint.getBevelBorder());
	assertEquals(0, constructorHint.getBorderWidth());
	Composite defaultBorder = new Composite(shell, SWT.BORDER);
	int originalWidth = defaultBorder.getBorderWidth();
	assertTrue(originalWidth > 0);
	for (int shadow : new int[] {SWT.SHADOW_IN, SWT.SHADOW_OUT,
			SWT.SHADOW_ETCHED_IN, SWT.SHADOW_ETCHED_OUT}) {
		Composite bevel = new Composite(shell, SWT.NONE);
		bevel.setSize(80, 40);
		bevel.setBevelBorder(shadow);
		assertEquals(shadow, bevel.getBevelBorder());
		assertTrue(bevel.getBorderWidth() > 0, "native bevel must reserve client trim");
		assertTrue(bevel.getClientArea().width < bevel.getSize().x);
		bevel.setBevelBorder(SWT.SHADOW_NONE);
		assertEquals(SWT.NONE, bevel.getBevelBorder());
		assertEquals(0, bevel.getBorderWidth());
		bevel.dispose();
		defaultBorder.setBevelBorder(shadow);
		assertEquals(originalWidth, defaultBorder.getBorderWidth(),
				"Native bevel must not double an existing SWT.BORDER");
		defaultBorder.setBevelBorder(SWT.NONE);
		assertEquals(originalWidth, defaultBorder.getBorderWidth());
	}
	assertThrows(IllegalArgumentException.class,
			() -> plain.setBevelBorder(SWT.SHADOW_IN | SWT.SHADOW_OUT));
	assertThrows(IllegalArgumentException.class,
			() -> plain.setBevelBorder(SWT.BORDER));
	defaultBorder.dispose();
	constructorHint.dispose();
	plain.dispose();
}

@Test
public void test_nativeBorderReliefExplicitAndReset() {
	Composite child = new Composite(shell, SWT.BORDER);
	try {
		int originalWidth = child.getBorderWidth();
		assertEquals(SWT.DEFAULT, child.getBorderRelief());
		assertThrows(IllegalArgumentException.class,
				() -> child.setBorderRelief(SWT.PUSH | SWT.BORDER));
		assertEquals(SWT.DEFAULT, child.getBorderRelief());
		if ("cocoa".equals(SWT.getPlatform())) {
			// Cocoa (master) draws relief over an existing NSScrollView bezel, which a
			// bordered Composite has; SHADOW_NONE is the value it refuses explicitly.
			org.eclipse.swt.SWTError unsupported = assertThrows(
					org.eclipse.swt.SWTError.class, () -> child.setBorderRelief(SWT.SHADOW_NONE));
			assertEquals(SWT.ERROR_NOT_IMPLEMENTED, unsupported.code);
			assertEquals(SWT.DEFAULT, child.getBorderRelief());
			return;
		}
		child.setSize(96, 48);
		for (int relief : new int[] { SWT.SHADOW_IN, SWT.SHADOW_OUT,
				SWT.SHADOW_ETCHED_IN, SWT.SHADOW_ETCHED_OUT }) {
			child.setBorderRelief(relief);
			assertEquals(relief, child.getBorderRelief());
			assertTrue(child.getBorderWidth() > 0, "visible native relief must reserve trim");
		}
		child.setBorderRelief(SWT.SHADOW_NONE);
		assertEquals(SWT.SHADOW_NONE, child.getBorderRelief());
		child.setBorderRelief(SWT.DEFAULT);
		assertEquals(SWT.DEFAULT, child.getBorderRelief());
		assertEquals(originalWidth, child.getBorderWidth());
		child.setBorderRelief(SWT.DEFAULT); // no-op, including native frame
	} finally {
		child.dispose();
	}
}

@Test
public void test_nativeBorderReliefWin32ConstructorPrecedence() {
	if (!SwtTestUtil.isWindows) return;
	Composite child = new Composite(shell, SWT.SHADOW_IN | SWT.BORDER);
	try {
		child.setSize(100, 60);
		int originalWidth = child.getBorderWidth();
		child.setBorderRelief(SWT.SHADOW_OUT);
		assertEquals(SWT.SHADOW_OUT, child.getBorderRelief());
		assertTrue(child.getBorderWidth() > 0);
		child.setBorderRelief(SWT.SHADOW_ETCHED_IN);
		child.setBorderRelief(SWT.SHADOW_ETCHED_OUT);
		child.setBorderRelief(SWT.SHADOW_NONE);
		child.setBorderRelief(SWT.DEFAULT);
		assertEquals(originalWidth, child.getBorderWidth());
		assertEquals(SWT.SHADOW_IN, child.getStyle() & SWT.SHADOW_IN);
	} finally {
		child.dispose();
	}
}

@Test
public void test_nativeBorderReliefGtkGroupShadowOverride() {
	if (!"gtk".equals(SWT.getPlatform())) return;
	org.eclipse.swt.widgets.Group child =
			new org.eclipse.swt.widgets.Group(shell, SWT.SHADOW_IN);
	try {
		child.setSize(120, 70);
		child.setBorderRelief(SWT.SHADOW_OUT);
		assertEquals(SWT.SHADOW_OUT, child.getBorderRelief());
		assertTrue(child.getBorderWidth() > 0, "explicit Group relief must have visible trim");
		child.setBackground(shell.getBackground());
		child.setForeground(shell.getForeground());
		assertEquals(SWT.SHADOW_OUT, child.getBorderRelief(),
				"foreground/background CSS must not discard native border CSS");
		child.setBorderRelief(SWT.DEFAULT);
		assertEquals(SWT.DEFAULT, child.getBorderRelief());
	} finally {
		child.dispose();
	}
}

@Test
public void test_nativeBorderReliefGtkStyleMapping() {
	assertTrue(org.eclipse.swt.internal.NativeBorderRelief.gtkDeclaration(SWT.SHADOW_IN).contains("inset"));
	assertTrue(org.eclipse.swt.internal.NativeBorderRelief.gtkDeclaration(SWT.SHADOW_OUT).contains("outset"));
	assertTrue(org.eclipse.swt.internal.NativeBorderRelief.gtkDeclaration(SWT.SHADOW_ETCHED_IN).contains("groove"));
	assertTrue(org.eclipse.swt.internal.NativeBorderRelief.gtkDeclaration(SWT.SHADOW_ETCHED_OUT).contains("ridge"));
	assertEquals("", org.eclipse.swt.internal.NativeBorderRelief.gtkDeclaration(SWT.DEFAULT));
	assertFalse(org.eclipse.swt.internal.NativeBorderRelief.isValid(SWT.BORDER));
}

@Test
public void test_getChildren() {
	assertArrayEquals(new Control[]{}, composite.getChildren());
	Composite c1 = new Composite(composite, 0);
	assertArrayEquals(new Control[]{c1}, composite.getChildren());

	List c2 = new List(composite, 0);
	assertArrayEquals(new Control[]{c1, c2}, composite.getChildren());

	Button c3 = new Button(composite, 0);
	assertArrayEquals(new Control[]{c1, c2, c3}, composite.getChildren());

	c2.dispose();
	assertArrayEquals(new Control[]{c1, c3}, composite.getChildren());

	Control[] children = composite.getChildren();
    for (Control element : children) {
        element.dispose();
    }

	assertArrayEquals(new Control[]{}, composite.getChildren());
}


@Test
public void test_setVisibility_and_sizing() {

	// Note: This test needs it's own shell/composite because original bug is only
	// reproduced if composite has style SWT.BORDER. (the setup() creates one without border).
	Shell visibilityShell = new Shell();
	visibilityShell.setSize(500, 500);
	Composite visibilityComposite = new Composite(visibilityShell, SWT.BORDER);

	visibilityShell.setLayout(new FillLayout());
	visibilityComposite.setLayout(new FillLayout());

	Button button = new Button(visibilityComposite, SWT.PUSH);
	button.setText("Test Button");

	visibilityComposite.setVisible(false);
	visibilityComposite.setVisible(true);

	visibilityShell.layout();

// 	  // Useful for troubleshooting:											   // should be like:
//    System.out.println("Shell size : " + visibilityShell.getSize().toString());  // >> 500,500
//    System.out.println("SComp size : " + visibilityComposite.getSize().toString()); // >> 500, 463
//    System.out.println("Button size: " + button.getSize().toString());			  // >> 500,463

	Point compSize = visibilityComposite.getSize();
	assertTrue(compSize.x > 100 && compSize.y > 100); // If this is 1x1 or 0x0 then there was some fault in layout.
	visibilityShell.dispose();
}

@Test
@Tag("gtk4-wayland-todo")
public void test_setFocus_toChild_afterOpen() {
	if (SwtTestUtil.isCocoa) {
		//TODO Fix Cocoa failure.
		if (SwtTestUtil.verbose) {
			System.out.println("Excluded test_setFocus_toChild_afterOpen(org.eclipse.swt.tests.junit.Test_org_eclipse_swt_widgets_Composite)");
		}
		return;
	}
	Text focusChild = new Text(composite, SWT.NONE);
	SwtTestUtil.waitShellActivate(shell::open, shell);
	composite.setFocus();
	assertTrue(focusChild.isFocusControl());
}

@Test
@Tag("gtk4-wayland-todo")
public void test_setFocus_toChild_beforeOpen() {
	if (SwtTestUtil.isCocoa) {
		//TODO Fix Cocoa failure.
		if (SwtTestUtil.verbose) {
			System.out.println("Excluded test_setFocus_toChild_beforeOpen(org.eclipse.swt.tests.junit.Test_org_eclipse_swt_widgets_Composite)");
		}
		return;
	}
	Text focusChild = new Text(composite, SWT.NONE);
	composite.setFocus();
	SwtTestUtil.waitShellActivate(shell::open, shell);
	assertTrue(focusChild.isFocusControl());
}

@Test
@Tag("gtk4-wayland-todo")
public void test_setFocus_withInvisibleChild() {
	final AtomicReference<Boolean> wasSetFocusCalledOnInvisibleChildWidget = new AtomicReference<>(false);
	Composite invisibleChildWidget = new Composite(composite, SWT.NONE) {
		@Override
		public boolean setFocus() {
			wasSetFocusCalledOnInvisibleChildWidget.set(true);
			return super.setFocus();
		}
	};
	invisibleChildWidget.setVisible(false);
	SwtTestUtil.waitShellActivate(shell::open, shell);

	composite.setFocus();
	assertFalse(wasSetFocusCalledOnInvisibleChildWidget.get());
}

@Test
@Tag("gtk4-wayland-todo")
public void test_setFocus_withVisibleAndInvisibleChild() {
	final AtomicReference<Boolean> wasSetFocusCalledOnInvisibleChildWidget = new AtomicReference<>(false);
	Composite invisibleChildWidget = new Composite(composite, SWT.NONE) {
		@Override
		public boolean setFocus() {
			wasSetFocusCalledOnInvisibleChildWidget.set(true);
			return super.setFocus();
		}
	};
	invisibleChildWidget.setVisible(false);
	Composite visibleChildWidget = new Composite(composite, SWT.NONE);
	SwtTestUtil.waitShellActivate(shell::open, shell);

	composite.setFocus();
	assertFalse(wasSetFocusCalledOnInvisibleChildWidget.get());
	assertTrue(getElementExpectedToHaveFocusAfterSetFocusOnParent(visibleChildWidget).isFocusControl());
}

@Test
public void test_setTabList$Lorg_eclipse_swt_widgets_Control() {
	Button button1 = new Button(composite, SWT.PUSH);
	Button button2 = new Button(composite, SWT.PUSH);
	Control[] tablist = new Control[] {button1, button2};
	composite.setTabList(tablist);
	assertArrayEquals(tablist, composite.getTabList());
	button1.dispose();
	button2.dispose();
}

/*
 * See https://github.com/eclipse-platform/eclipse.platform.swt/issues/2162
 */
@Test
public void test_bug2162_transparentStyle() {
	Composite c = new Composite(shell, SWT.TRANSPARENT);
	c.addPaintListener(e -> {});

	shell.setLayout(new FillLayout());
	shell.open();
}

@Test
public void test_nativeReliefAndCompositeBevelShareOneOwner() {
	Composite local = new Composite(shell, SWT.BORDER);
	try {
		boolean win32 = "win32".equals(SWT.getPlatform());
		assertEquals(SWT.NONE, local.getBevelBorder());
		assertEquals(SWT.DEFAULT, local.getBorderRelief());
		if ("cocoa".equals(SWT.getPlatform())) {
			// Cocoa native NSScrollView bezel receives a separate real-widget
			// test in Test_org_eclipse_swt_widgets_Control. Do not infer that
			// all Cocoa Composite peers have equivalent native border trim.
			return;
		}
		for (int relief : new int[] {SWT.SHADOW_IN, SWT.SHADOW_OUT,
				SWT.SHADOW_ETCHED_IN, SWT.SHADOW_ETCHED_OUT}) {
			local.setBevelBorder(relief);
			assertEquals(win32 ? SWT.DEFAULT : relief, local.getBorderRelief(),
					"Win32 preserves a stored bevel under the Control override");
			assertEquals(relief, local.getBevelBorder());
			local.setBorderRelief(relief);
			assertEquals(relief, local.getBorderRelief());
			assertEquals(relief, local.getBevelBorder());
		}
		local.setBorderRelief(SWT.SHADOW_NONE);
		assertEquals(win32 ? SWT.SHADOW_ETCHED_OUT : SWT.NONE,
				local.getBevelBorder(), "Win32 preserves its prior bevel");
		assertEquals(0, local.getBorderWidth());
		local.setBorderRelief(SWT.DEFAULT);
		assertEquals(SWT.DEFAULT, local.getBorderRelief());
		if (win32) assertEquals(SWT.SHADOW_ETCHED_OUT, local.getBevelBorder());
		local.setBevelBorder(SWT.SHADOW_OUT);
		assertEquals(win32 ? SWT.DEFAULT : SWT.SHADOW_OUT, local.getBorderRelief());
		local.setBevelBorder(SWT.NONE);
		assertEquals(SWT.DEFAULT, local.getBorderRelief());
		assertEquals(SWT.NONE, local.getBevelBorder());
		assertThrows(IllegalArgumentException.class,
				() -> local.setBevelBorder(SWT.BORDER));
	} finally {
		local.dispose();
	}
}

@Test
public void test_nativeReliefRestoresOriginalTrimAfterRepeatedMixedCalls() {
	if ("cocoa".equals(SWT.getPlatform())) return;
	Composite owned = new Composite(shell, SWT.BORDER);
	new Button(owned, SWT.PUSH);
	try {
		boolean win32 = "win32".equals(SWT.getPlatform());
		int originalWidth = owned.getBorderWidth();
		Control[] children = owned.getChildren();
		for (int i = 0; i < 12; i++) {
			int relief = (i & 1) == 0 ? SWT.SHADOW_OUT : SWT.SHADOW_ETCHED_IN;
			owned.setBorderRelief(relief);
			assertEquals(relief, owned.getBorderRelief());
			assertEquals(win32 ? SWT.NONE : relief, owned.getBevelBorder());
			owned.setBevelBorder(SWT.SHADOW_OUT);
			assertEquals(win32 ? SWT.DEFAULT : SWT.SHADOW_OUT,
					owned.getBorderRelief());
			owned.setBorderRelief(SWT.SHADOW_NONE);
			assertEquals(0, owned.getBorderWidth(), "Explicit none removes native trim");
			assertEquals(win32 ? SWT.SHADOW_OUT : SWT.NONE,
					owned.getBevelBorder(), "Underlying Win32 bevel survives");
			owned.setBorderRelief(SWT.DEFAULT);
			if (win32) {
				assertEquals(SWT.SHADOW_OUT, owned.getBevelBorder());
				assertTrue(owned.getBorderWidth() > 0);
			}
			owned.setBevelBorder(SWT.NONE);
			assertEquals(SWT.DEFAULT, owned.getBorderRelief());
			assertEquals(SWT.NONE, owned.getBevelBorder());
			assertEquals(originalWidth, owned.getBorderWidth(),
					"Each round trip restores the pre-existing border width");
			assertArrayEquals(children, owned.getChildren(),
					"A border transition never creates another SWT control");
		}
	} finally {
		owned.dispose();
	}
}

@Test
public void test_nativeBevelSubclassRefusalDoesNotInvalidateInheritedRelief() {
	if (!"win32".equals(SWT.getPlatform())) return;
	Composite custom = new Composite(shell, SWT.NONE) { };
	try {
		assertThrows(IllegalArgumentException.class,
				() -> custom.setBevelBorder(SWT.SHADOW_IN));
		assertEquals(SWT.DEFAULT, custom.getBorderRelief());
		custom.setBorderRelief(SWT.SHADOW_IN);
		assertEquals(SWT.SHADOW_IN, custom.getBorderRelief());
		custom.setBorderRelief(SWT.DEFAULT);
		assertEquals(SWT.DEFAULT, custom.getBorderRelief());
	} finally {
		custom.dispose();
	}
}

protected Composite getElementExpectedToHaveFocusAfterSetFocusOnParent(Composite visibleChild) {
	return visibleChild;
}

/* custom */
@Override
protected void setWidget(Widget w) {
    if (composite != null) {
        composite.dispose();
    }
	composite = (Composite)w;
	super.setWidget(w);
}
}
