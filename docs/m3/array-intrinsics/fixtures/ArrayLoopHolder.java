/*
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others
 */

/**
 * ARRAY_LOOP_INTRINSICS fixture: a holder declared in its own compilation unit. The recipe reads one file at a time,
 * so the element types of these arrays cannot be proven from ArrayLoopFixtures.java, which reaches them as
 * {@code o.items} and {@code o.cells} and also declares unrelated {@code long[] items} and {@code short[] cells}.
 */
public class ArrayLoopHolder {
	public int[] items = {1, 2, 3};
	public int[] cells = new int[2];
}
