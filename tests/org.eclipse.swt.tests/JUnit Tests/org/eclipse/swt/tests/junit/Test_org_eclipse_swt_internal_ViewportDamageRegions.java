/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.swt.tests.junit;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.swt.internal.*;
import org.junit.jupiter.api.*;

public class Test_org_eclipse_swt_internal_ViewportDamageRegions {

	@Test
	public void test_mergesTouchingAndTransitiveDamageOnSameLayer () {
		ViewportDamageRegions damage = new ViewportDamageRegions ();
		damage.invalidate (7, 0, 0, 10, 10);
		damage.invalidate (7, 10, 0, 5, 10);
		damage.invalidate (7, -5, 2, 5, 5);

		assertEquals (1, damage.count ());
		assertEquals (7, damage.layerAt (0));
		assertEquals (-5, damage.xAt (0));
		assertEquals (0, damage.yAt (0));
		assertEquals (20, damage.widthAt (0));
		assertEquals (10, damage.heightAt (0));
	}

	@Test
	public void test_keepsDifferentLayersIndependent () {
		ViewportDamageRegions damage = new ViewportDamageRegions ();
		damage.invalidate (1, 0, 0, 20, 20);
		damage.invalidate (2, 0, 0, 20, 20);

		assertEquals (2, damage.count ());
		assertFalse (damage.isFullDamage ());
		assertEquals (1, damage.layerAt (0));
		assertEquals (2, damage.layerAt (1));
	}

	@Test
	public void test_clipsBeforeAccumulation () {
		ViewportDamageRegions damage = new ViewportDamageRegions ();
		damage.invalidateClipped (3, -10, -10, 30, 30, 0, 0, 100, 100);

		assertEquals (1, damage.count ());
		assertEquals (0, damage.xAt (0));
		assertEquals (0, damage.yAt (0));
		assertEquals (20, damage.widthAt (0));
		assertEquals (20, damage.heightAt (0));
	}

	@Test
	public void test_budgetOverflowFallsBackToAllLayerUnion () {
		ViewportDamageRegions damage = new ViewportDamageRegions (2);
		damage.invalidate (1, 0, 0, 10, 10);
		damage.invalidate (2, 100, 100, 10, 10);
		damage.invalidate (3, 50, 50, 10, 10);

		assertEquals (1, damage.count ());
		assertEquals (ViewportDamageRegions.ALL_LAYERS, damage.layerAt (0));
		assertFalse (damage.isFullDamage ());
		assertEquals (0, damage.xAt (0));
		assertEquals (0, damage.yAt (0));
		assertEquals (110, damage.widthAt (0));
		assertEquals (110, damage.heightAt (0));
	}

	@Test
	public void test_unrepresentableUnionFallsBackToFullIncomingClip () {
		ViewportDamageRegions damage = new ViewportDamageRegions ();
		damage.invalidate (4, Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 10);
		damage.invalidate (4, -1, 0, Integer.MAX_VALUE, 10);

		assertTrue (damage.isFullDamage ());
		assertEquals (1, damage.count ());
		assertEquals (ViewportDamageRegions.ALL_LAYERS, damage.layerAt (0));

		damage.clear ();
		assertTrue (damage.isEmpty ());
		assertFalse (damage.isFullDamage ());
	}

	@Test
	public void test_rawCoordinateOverflowFallsBackToFullIncomingClip () {
		ViewportDamageRegions damage = new ViewportDamageRegions ();
		damage.invalidate (2, Integer.MAX_VALUE, 10, 1, 1);
		assertTrue (damage.isFullDamage ());
	}
}
