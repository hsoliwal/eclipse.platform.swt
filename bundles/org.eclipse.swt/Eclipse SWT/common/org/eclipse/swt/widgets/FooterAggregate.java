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
package org.eclipse.swt.widgets;

import java.math.BigDecimal;
import java.util.*;

import org.eclipse.swt.m3.M3Footer;

/**
 * The footer aggregate of one column (M3Footer), kept by deltas: a cell enters with {@link #add(String)}, leaves with
 * {@link #remove(String)}, and the rows of a custom aggregate move with {@link #inserted(int, int)} and
 * {@link #removed(int, int)}. Running values: filled cells, cells that are numbers and their sum (compensated). Indices
 * built only for the aggregates that need them: an ordered value-count tree for MIN and MAX ({@link ValueTree}), a text
 * count table for DISTINCT ({@link TextCounts}), and a position tree with the column's function over every subtree for
 * CUSTOM ({@link PositionTree}). Every delta is O(log n) or better; a removal of the current minimum never recounts.
 * Lanes only: no object per row.
 */
final class FooterAggregate {
	int kind;
	M3Footer.Function function;
	long filled, numbers;
	private double sum, compensation;
	private ValueTree values;
	private TextCounts texts;
	private PositionTree custom;

	FooterAggregate (int kind, M3Footer.Function function) {
		this.kind = kind;
		this.function = function;
		if (kind == M3Footer.MIN || kind == M3Footer.MAX) values = new ValueTree ();
		if (kind == M3Footer.DISTINCT) texts = new TextCounts ();
		if (kind == M3Footer.CUSTOM && function != null) custom = new PositionTree (function);
	}

	/** The number a cell holds, or NaN: a Java double literal after trimming, checked by its first character first. */
	static double number (String text) {
		if (text == null) return Double.NaN;
		String trimmed = text.trim ();
		if (trimmed.isEmpty ()) return Double.NaN;
		char first = trimmed.charAt (0);
		if (!(first >= '0' && first <= '9') && first != '-' && first != '+' && first != '.') return Double.NaN;
		try {
			double value = Double.parseDouble (trimmed);
			return Double.isFinite (value) ? value : Double.NaN;
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	/** Whether the custom aggregate keeps one value per row (its rows follow inserts and removals). */
	boolean positional () {
		return custom != null;
	}

	/** A cell enters the aggregate (a row's text was written, or a row with text came in). */
	void add (String text) {
		if (text == null || text.isEmpty ()) return;
		filled++;
		if (texts != null) texts.add (text);
		double value = number (text);
		if (Double.isNaN (value)) return;
		numbers++;
		accumulate (value);
		if (values != null) values.add (value);
	}

	/** A cell leaves the aggregate. */
	void remove (String text) {
		if (text == null || text.isEmpty ()) return;
		filled--;
		if (texts != null) texts.remove (text);
		double value = number (text);
		if (Double.isNaN (value)) return;
		numbers--;
		accumulate (-value);
		if (values != null) values.remove (value);
		/* no number left: the running sum starts again at an exact zero */
		if (numbers == 0) sum = compensation = 0;
	}

	/* Kahan-Babuska summation */
	private void accumulate (double value) {
		double t = sum + value;
		compensation += Math.abs (sum) >= Math.abs (value) ? (sum - t) + value : (value - t) + sum;
		sum = t;
	}

	/** Rows [row, row + count) came in empty (custom aggregate). */
	void inserted (int row, int count) {
		if (custom != null) custom.insert (row, count);
	}

	/** Rows [row, row + count) left (custom aggregate; their cells left through {@link #remove(String)}). */
	void removed (int row, int count) {
		if (custom != null) custom.remove (row, count);
	}

	/** The cell of a row changed (custom aggregate). */
	void set (int row, String text) {
		if (custom != null) custom.set (row, number (text));
	}

	/**
	 * The aggregate's value over <code>rows</code> rows, or NaN when it has none (no number for SUM, AVERAGE, MIN and
	 * MAX; NONE; CUSTOM without a function).
	 */
	double value (long rows) {
		return switch (kind) {
			case M3Footer.COUNT -> rows;
			case M3Footer.FILLED -> filled;
			case M3Footer.DISTINCT -> texts.distinct;
			case M3Footer.SUM -> numbers == 0 ? Double.NaN : sum + compensation;
			case M3Footer.AVERAGE -> numbers == 0 ? Double.NaN : (sum + compensation) / numbers;
			case M3Footer.MIN -> numbers == 0 ? Double.NaN : values.first ();
			case M3Footer.MAX -> numbers == 0 ? Double.NaN : values.last ();
			case M3Footer.CUSTOM -> custom == null ? Double.NaN : custom.total ();
			default -> Double.NaN;
		};
	}

	/** The sum of the cells that are numbers, or NaN without one (the footer popup's breakdown). */
	double sum () {
		return numbers == 0 ? Double.NaN : sum + compensation;
	}

	static final String [] NAMES = {"", "count", "filled", "distinct", "sum", "avg", "min", "max", ""};

	/** The footer cell's text: the kind and its value; empty without a value. */
	String text (long rows) {
		double value = value (rows);
		if (Double.isNaN (value)) return "";
		String number = format (value);
		return NAMES [kind].isEmpty () ? number : NAMES [kind] + " " + number;
	}

	/** A number as a footer shows it: plain digits, no exponent, no trailing zeros. */
	static String format (double value) {
		if (value == Math.rint (value) && Math.abs (value) < 1e15) return Long.toString ((long) value);
		return BigDecimal.valueOf (value).stripTrailingZeros ().toPlainString ();
	}

	/**
	 * An ordered count per value (a treap in parallel lanes, nodes reused through a free list): add, remove, first and
	 * last in O(log distinct values).
	 */
	static final class ValueTree {
		private static final int NIL = -1;
		private double [] keys = new double [16];
		private int [] counts = new int [16], left = new int [16], right = new int [16], priorities = new int [16];
		private int root = NIL, free = NIL, used;
		private int seed = 0x2545F491;

		private int node (double key) {
			int n;
			if (free != NIL) {
				n = free;
				free = left [n];
			} else {
				if (used == keys.length) grow ();
				n = used++;
			}
			keys [n] = key;
			counts [n] = 1;
			left [n] = right [n] = NIL;
			seed ^= seed << 13;
			seed ^= seed >>> 17;
			seed ^= seed << 5;
			priorities [n] = seed;
			return n;
		}

		private void grow () {
			int length = keys.length * 2;
			keys = Arrays.copyOf (keys, length);
			counts = Arrays.copyOf (counts, length);
			left = Arrays.copyOf (left, length);
			right = Arrays.copyOf (right, length);
			priorities = Arrays.copyOf (priorities, length);
		}

		void add (double key) {
			root = add (root, key);
		}

		private int add (int n, double key) {
			if (n == NIL) return node (key);
			int order = Double.compare (key, keys [n]);
			if (order == 0) {
				counts [n]++;
			} else if (order < 0) {
				left [n] = add (left [n], key);
				if (priorities [left [n]] > priorities [n]) n = rotateRight (n);
			} else {
				right [n] = add (right [n], key);
				if (priorities [right [n]] > priorities [n]) n = rotateLeft (n);
			}
			return n;
		}

		void remove (double key) {
			root = remove (root, key);
		}

		private int remove (int n, double key) {
			if (n == NIL) return NIL;
			int order = Double.compare (key, keys [n]);
			if (order < 0) {
				left [n] = remove (left [n], key);
			} else if (order > 0) {
				right [n] = remove (right [n], key);
			} else if (--counts [n] == 0) {
				int merged = merge (left [n], right [n]);
				left [n] = free;
				free = n;
				return merged;
			}
			return n;
		}

		private int merge (int a, int b) {
			if (a == NIL) return b;
			if (b == NIL) return a;
			if (priorities [a] > priorities [b]) {
				right [a] = merge (right [a], b);
				return a;
			}
			left [b] = merge (a, left [b]);
			return b;
		}

		private int rotateRight (int n) {
			int l = left [n];
			left [n] = right [l];
			right [l] = n;
			return l;
		}

		private int rotateLeft (int n) {
			int r = right [n];
			right [n] = left [r];
			left [r] = n;
			return r;
		}

		double first () {
			int n = root;
			while (left [n] != NIL) n = left [n];
			return keys [n];
		}

		double last () {
			int n = root;
			while (right [n] != NIL) n = right [n];
			return keys [n];
		}
	}

	/** A count per text (open addressing, linear probing); a text whose count drops to zero keeps its slot until a rehash. */
	static final class TextCounts {
		private String [] keys = new String [64];
		private int [] counts = new int [64];
		private int slots;
		long distinct;

		private int slot (String text) {
			int mask = keys.length - 1;
			int i = (text.hashCode () * 0x9E3779B9) >>> 7 & mask;
			while (keys [i] != null && !keys [i].equals (text)) i = (i + 1) & mask;
			return i;
		}

		void add (String text) {
			int i = slot (text);
			if (keys [i] == null) {
				keys [i] = text;
				slots++;
			}
			if (counts [i]++ == 0) distinct++;
			if (slots * 2 > keys.length) rehash (keys.length * 2);
		}

		void remove (String text) {
			int i = slot (text);
			if (keys [i] == null) return;
			if (--counts [i] == 0) distinct--;
			/* slots of texts that are gone are dropped once they are most of the table */
			if (slots > 64 && distinct * 4 < slots) rehash (keys.length);
		}

		private void rehash (int length) {
			String [] oldKeys = keys;
			int [] oldCounts = counts;
			keys = new String [length];
			counts = new int [length];
			slots = 0;
			for (int i = 0; i < oldKeys.length; i++) {
				if (oldKeys [i] == null || oldCounts [i] == 0) continue;
				int j = slot (oldKeys [i]);
				keys [j] = oldKeys [i];
				counts [j] = oldCounts [i];
				slots++;
			}
		}
	}

	/**
	 * The column's function over the rows in order: an implicit treap (keyed by position) whose nodes hold a row's
	 * number, or the identity for a row that is not a number, and the combination of their subtree. Inserting or
	 * removing a run of rows is two splits and two merges plus O(run) for the new nodes; setting a row is one path.
	 */
	static final class PositionTree {
		private static final int NIL = -1;
		private final M3Footer.Function function;
		private final double identity;
		private double [] values = new double [16], totals = new double [16];
		private int [] sizes = new int [16], left = new int [16], right = new int [16], priorities = new int [16];
		private int root = NIL, free = NIL, used;
		private int seed = 0x6C8E9CF5;
		/* the parts of a split */
		private int splitLeft, splitRight;

		PositionTree (M3Footer.Function function) {
			this.function = function;
			this.identity = function.identity ();
		}

		double total () {
			return root == NIL ? identity : totals [root];
		}

		private int node () {
			int n;
			if (free != NIL) {
				n = free;
				free = left [n];
			} else {
				if (used == values.length) grow ();
				n = used++;
			}
			values [n] = totals [n] = identity;
			sizes [n] = 1;
			left [n] = right [n] = NIL;
			seed ^= seed << 13;
			seed ^= seed >>> 17;
			seed ^= seed << 5;
			priorities [n] = seed;
			return n;
		}

		private void grow () {
			int length = values.length * 2;
			values = Arrays.copyOf (values, length);
			totals = Arrays.copyOf (totals, length);
			sizes = Arrays.copyOf (sizes, length);
			left = Arrays.copyOf (left, length);
			right = Arrays.copyOf (right, length);
			priorities = Arrays.copyOf (priorities, length);
		}

		private int size (int n) {
			return n == NIL ? 0 : sizes [n];
		}

		private double totalOf (int n) {
			return n == NIL ? identity : totals [n];
		}

		private void update (int n) {
			sizes [n] = 1 + size (left [n]) + size (right [n]);
			totals [n] = function.combine (function.combine (totalOf (left [n]), values [n]), totalOf (right [n]));
		}

		/* splitLeft gets the first count rows of n, splitRight the rest */
		private void split (int n, int count) {
			if (n == NIL) {
				splitLeft = splitRight = NIL;
				return;
			}
			if (size (left [n]) >= count) {
				split (left [n], count);
				left [n] = splitRight;
				update (n);
				splitRight = n;
			} else {
				split (right [n], count - size (left [n]) - 1);
				right [n] = splitLeft;
				update (n);
				splitLeft = n;
			}
		}

		private int merge (int a, int b) {
			if (a == NIL) return b;
			if (b == NIL) return a;
			if (priorities [a] > priorities [b]) {
				right [a] = merge (right [a], b);
				update (a);
				return a;
			}
			left [b] = merge (a, left [b]);
			update (b);
			return b;
		}

		/* a treap of count identity rows, built from a Cartesian-tree stack in O(count) */
		private int build (int count) {
			int [] stack = new int [64];
			int top = 0;
			for (int i = 0; i < count; i++) {
				int n = node ();
				int last = NIL;
				while (top > 0 && priorities [stack [top - 1]] < priorities [n]) {
					last = stack [--top];
					update (last);
				}
				left [n] = last;
				if (top > 0) right [stack [top - 1]] = n;
				if (top == stack.length) stack = Arrays.copyOf (stack, top * 2);
				stack [top++] = n;
			}
			while (top > 1) update (stack [--top]);
			if (top == 0) return NIL;
			update (stack [0]);
			return stack [0];
		}

		void insert (int row, int count) {
			split (root, row);
			int before = splitLeft, after = splitRight;
			root = merge (merge (before, build (count)), after);
		}

		void remove (int row, int count) {
			split (root, row);
			int before = splitLeft;
			split (splitRight, count);
			release (splitLeft);
			root = merge (before, splitRight);
		}

		private void release (int n) {
			if (n == NIL) return;
			release (left [n]);
			release (right [n]);
			left [n] = free;
			free = n;
		}

		void set (int row, double value) {
			set (root, row, Double.isNaN (value) ? identity : value);
		}

		private void set (int n, int row, double value) {
			int before = size (left [n]);
			if (row < before) {
				set (left [n], row, value);
			} else if (row > before) {
				set (right [n], row - before - 1, value);
			} else {
				values [n] = value;
			}
			update (n);
		}
	}
}
