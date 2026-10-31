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
package org.eclipse.swt.internal.render;

import java.util.*;
import java.util.stream.*;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.*;
import org.eclipse.swt.widgets.*;

/**
 * The registry of the web-UI feature switches (docs/m3/feature-switches/FEATURE_SWITCH_RULES.md): every feature of the
 * harvested web kits is an opt-in, configurable option of the widgets. A feature is a dense int id ({@link M3FeatureIds}:
 * the row order of the ledger FEATURE_SWITCH_MATRIX.tsv, append-only) and is <code>off</code> unless the stock toolkit owns
 * it (<code>NATIVE_HAS</code>: <code>on</code>, read-only). A switch that is off leaves the widget the stock contract.
 * <p>
 * Configuration. The system property <code>swt.widget.feature.&lt;Widget&gt;.&lt;id&gt;</code> (one widget class, the most
 * specific class of the instance first) wins over <code>swt.widget.feature.&lt;id&gt;</code> (every widget), which wins over
 * the row's alias (the property the feature was hard-wired to before it had a switch, kept), which wins over the ledger
 * default. The properties are read once per widget class, when its first widget asks (like {@link WidgetMode#of}), and
 * are the class defaults; {@link #set(Widget, int, boolean)} configures one instance. A value takes effect only when the
 * feature's seam is shipped (docs/m3/feature-switches/SHIPPED.tsv) and never switches a <code>NATIVE_HAS</code> row off:
 * both are logged at <code>info</code> and ignored, as is a key that names no feature.
 * <code>-Dswt.widget.feature.list=true</code> logs the registry table once, when this class initializes.
 * </p>
 * <p>
 * Storage (DOOP, brief rule 18): one <code>long []</code> of class defaults per widget class, one <code>long []</code> per
 * widget instance in {@link Widget#m3Features}, <code>null</code> while every switch is at its class default. No object per
 * feature, no map. {@link #isOn(long[], int)} is one word read and one bit test.
 * </p>
 */
//FLLDCIM: BOP — feature switches as discoverable, per-instance configurable behaviour; FEATURE_SWITCH_RULES.md rules 1-5
public final class M3Features implements M3FeatureIds {
	/** The property prefix of the global key; the key of one widget class inserts <code>"&lt;Widget&gt;."</code> before the id. */
	public static final String PREFIX = "swt.widget.feature.";
	/** <code>-Dswt.widget.feature.list=true</code> logs the registry table once, when this class initializes. */
	public static final String LIST_PROPERTY = PREFIX + "list";
	/** The number of features: the bits of a bitset, the rows of the ledger. */
	public static final int COUNT = M3FeatureTable.COUNT;
	/** The columns of {@link #describe} and {@link #table}, tab separated. */
	public static final String TABLE_HEADER = "bit\tfeature_id\tconcept\twidget_family\tcurrent_state\tdefault\tnative_owner\tenhanced_api_id\talias\tshipped";
	private static final int WORD_SHIFT = 6, BIT_MASK = (1 << WORD_SHIFT) - 1;
	private static final int WORDS = (COUNT + BIT_MASK) >>> WORD_SHIFT;
	/* the columns of M3FeatureTable.ROWS */
	private static final int ID = 0, STATE = 3, DEFAULT = 4, ALIAS = 7, SHIPPED_COLUMN = 8;
	private static final String TAB = "\t", ON = "on", TRUE = "true", YES = "yes", NATIVE_HAS = "NATIVE_HAS", NONE = "-";
	private static final SwtLog LOG = SwtLog.getLog ("org.eclipse.swt.internal.render.M3Features");
	private static final String [][] CELLS = Arrays.stream (M3FeatureTable.ROWS).map (row -> row.split (TAB, -1)).toArray (String [][]::new);
	/** The ledger defaults: every <code>NATIVE_HAS</code> row on, every other row off. */
	static final long [] DEFAULTS = column (DEFAULT, ON);
	/** The read-only rows: the stock toolkit owns the feature; never switched off. */
	static final long [] NATIVE = column (STATE, NATIVE_HAS);
	/** The rows whose seam is shipped: the only rows a property value or {@link #set(Widget, int, boolean)} switches. */
	static final long [] SHIPPED = column (SHIPPED_COLUMN, YES);
	/* the class defaults, from the properties, resolved once per widget class on its first use */
	private static final ClassValue<long []> CLASS_BITS = new ClassValue<> () {
		@Override
		protected long [] computeValue (Class<?> type) {
			return resolve (type);
		}
	};

	static {
		if (Boolean.getBoolean (LIST_PROPERTY)) LOG.info (M3Features::table);
		System.getProperties ().stringPropertyNames ().stream ().filter (M3Features::unknownKey).sorted ()
			.forEach (key -> LOG.info (() -> "ignoring " + key + ": not a feature id"));
	}

	private M3Features () {
	}

	/**
	 * Answers whether a feature is on for a widget: the instance's switches when one differs from the class defaults, else the
	 * class defaults (the properties, resolved once per widget class).
	 *
	 * @param widget the widget
	 * @param id the feature ({@link M3FeatureIds})
	 * @return whether the feature is on for this widget
	 */
	public static boolean isOn (Widget widget, int id) {
		long [] bits = widget.m3Features;
		return isOn (bits != null ? bits : CLASS_BITS.get (widget.getClass ()), id);
	}

	/**
	 * The kernel of every seam: one word read, one bit test.
	 *
	 * @param bits a bitset of {@link #COUNT} bits (class defaults or instance switches)
	 * @param id the feature
	 * @return whether the bit is set
	 */
	//FLLDCIM: HPCPOP — the hot path of every seam; no allocation, no call
	public static boolean isOn (long [] bits, int id) {
		checkId (id);
		return (bits [id >>> WORD_SHIFT] & (1L << (id & BIT_MASK))) != 0;
	}

	/**
	 * Configures one feature of one widget. Refused with {@link SWT#ERROR_INVALID_ARGUMENT} when it would switch a
	 * <code>NATIVE_HAS</code> row off (the stock contract is not switchable) and with {@link SWT#ERROR_NOT_IMPLEMENTED} when the
	 * feature's seam is not shipped. The instance keeps no bitset while every switch is back at its class default.
	 *
	 * @param widget the widget
	 * @param id the feature
	 * @param on the switch
	 */
	public static void set (Widget widget, int id, boolean on) {
		widget.m3Features = set (widget.m3Features, CLASS_BITS.get (widget.getClass ()), id, on);
	}

	/**
	 * The kernel of {@link #set(Widget, int, boolean)} over explicit bitsets.
	 *
	 * @param bits the instance switches, or null while they equal the defaults
	 * @param defaults the class defaults
	 * @param id the feature
	 * @param on the switch
	 * @return the instance switches to keep: null when every switch equals its default (the one sanctioned null, rule 4)
	 */
	//FLLDCIM: DOOP — a null bitset is "every switch at its class default": the stock widget allocates nothing
	public static long [] set (long [] bits, long [] defaults, int id, boolean on) {
		checkId (id);
		if (isOn (NATIVE, id)) {
			if (!on) SWT.error (SWT.ERROR_INVALID_ARGUMENT);
			return bits;
		}
		if (!isOn (SHIPPED, id)) SWT.error (SWT.ERROR_NOT_IMPLEMENTED);
		long [] next = (bits != null ? bits : defaults).clone ();
		apply (next, id, on);
		return Arrays.equals (next, defaults) ? null : next;
	}

	/**
	 * Resolves the class defaults of a widget class from the properties: the most specific class wins (the class chain up to
	 * <code>Object</code>), then the global key, then the row's alias, then the ledger default.
	 *
	 * @param type the widget class
	 * @return a fresh bitset
	 */
	public static long [] resolve (Class<?> type) {
		Stream<Class<?>> chain = Stream.iterate (type, c -> c != null && c != Object.class, Class::getSuperclass);
		return resolve (chain.map (Class::getSimpleName).toArray (String []::new));
	}

	/**
	 * Resolves the class defaults for simple class names, most specific first (headless: reads the properties now).
	 *
	 * @param widgets the simple class names, for example <code>"Tree", "Composite", "Scrollable", "Control", "Widget"</code>
	 * @return a fresh bitset
	 */
	public static long [] resolve (String... widgets) {
		long [] bits = DEFAULTS.clone ();
		IntStream.range (0, COUNT).forEach (id -> requested (id, widgets).ifPresent (value -> configure (bits, id, value, widgets)));
		return bits;
	}

	private static Optional<String> requested (int id, String [] widgets) {
		Stream<String> specific = Arrays.stream (widgets).map (widget -> key (widget, id));
		Stream<String> general = Stream.of (key (id), alias (id)).filter (key -> !NONE.equals (key));
		return Stream.concat (specific, general).map (System::getProperty).filter (Objects::nonNull).findFirst ();
	}

	private static void configure (long [] bits, int id, String value, String [] widgets) {
		boolean on = TRUE.equalsIgnoreCase (value.trim ());
		String widget = widgets.length > 0 ? widgets [0] : "";
		if (isOn (NATIVE, id)) {
			if (!on) LOG.info (() -> widget + ": ignoring " + key (id) + "=" + value + ": the stock toolkit owns this feature, it is not switchable");
			return;
		}
		if (!isOn (SHIPPED, id)) {
			if (on != isOn (DEFAULTS, id)) LOG.info (() -> widget + ": ignoring " + key (id) + "=" + value + ": no seam shipped for this feature yet (SHIPPED.tsv)");
			return;
		}
		apply (bits, id, on);
	}

	private static void apply (long [] bits, int id, boolean on) {
		long bit = 1L << (id & BIT_MASK);
		int word = id >>> WORD_SHIFT;
		bits [word] = on ? bits [word] | bit : bits [word] & ~bit;
	}

	/**
	 * @param id the feature
	 * @return the global property key, <code>swt.widget.feature.&lt;id&gt;</code>
	 */
	public static String key (int id) {
		checkId (id);
		return PREFIX + CELLS [id][ID];
	}

	/**
	 * @param widget the simple class name of a widget class
	 * @param id the feature
	 * @return the property key of that class, <code>swt.widget.feature.&lt;Widget&gt;.&lt;id&gt;</code>
	 */
	public static String key (String widget, int id) {
		checkId (id);
		return PREFIX + widget + "." + CELLS [id][ID];
	}

	/**
	 * @param id the feature
	 * @return the property the feature was hard-wired to before it had a switch (kept as an alias), or <code>"-"</code>
	 */
	public static String alias (int id) {
		checkId (id);
		return CELLS [id][ALIAS];
	}

	/** @return every feature id, in bit order */
	public static int [] ids () {
		return IntStream.range (0, COUNT).toArray ();
	}

	/**
	 * @param feature the ledger name of a feature (<code>feature_id</code>, for example <code>"frame-overlay"</code>)
	 * @return its id, when known
	 */
	public static OptionalInt id (String feature) {
		return IntStream.range (0, COUNT).filter (id -> CELLS [id][ID].equals (feature)).findFirst ();
	}

	/**
	 * @param id the feature
	 * @return the feature's row: the columns of {@link #TABLE_HEADER}, tab separated
	 */
	public static String describe (int id) {
		checkId (id);
		return id + TAB + String.join (TAB, CELLS [id]);
	}

	/** @return the registry table: {@link #TABLE_HEADER}, then one line per feature in bit order */
	public static String table () {
		return Stream.concat (Stream.of (TABLE_HEADER), IntStream.range (0, COUNT).mapToObj (M3Features::describe)).collect (Collectors.joining ("\n"));
	}

	private static long [] column (int column, String value) {
		long [] bits = new long [WORDS];
		IntStream.range (0, COUNT).filter (id -> value.equals (CELLS [id][column])).forEach (id -> apply (bits, id, true));
		return bits;
	}

	private static boolean unknownKey (String key) {
		if (!key.startsWith (PREFIX) || key.equals (LIST_PROPERTY)) return false;
		return id (key.substring (key.lastIndexOf ('.') + 1)).isEmpty ();
	}

	private static void checkId (int id) {
		if (id < 0 || id >= COUNT) SWT.error (SWT.ERROR_INVALID_RANGE);
	}
}
