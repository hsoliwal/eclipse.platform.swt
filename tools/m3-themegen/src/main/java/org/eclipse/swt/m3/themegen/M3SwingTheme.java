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
package org.eclipse.swt.m3.themegen;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;

import javax.swing.*;
import javax.swing.plaf.*;

import org.eclipse.swt.internal.laf.*;

/**
 * Applies a generated theme to Swing without FlatLaf on the class path (docs/m3/look-and-feel/GENERATOR.md): the
 * FlatLaf-format properties are read ({@link M3ThemeImport#flatLaf} resolves the values), each catalogue key becomes
 * <code>UIManager</code> entries (<code>ColorUIResource</code>, <code>InsetsUIResource</code>, integers, booleans)
 * under the Swing key names, and the open windows are updated (<code>SwingUtilities.updateComponentTreeUI</code>).
 * With FlatLaf present, <code>FlatPropertiesLaf</code> reads the same file directly.
 */
public final class M3SwingTheme {
	private static final Logger LOG = Logger.getLogger (M3SwingTheme.class.getName ());
	/* catalogue keys that feed more than one Swing key */
	private static final Map<String, List<String>> ALSO = Map.ofEntries (
		Map.entry ("Component.background", List.of ("Panel.background", "control", "Viewport.background", "ScrollPane.background", "OptionPane.background", "MenuBar.background", "Menu.background", "MenuItem.background", "PopupMenu.background", "CheckBox.background", "RadioButton.background", "ComboBox.background", "Spinner.background", "Slider.background", "ToolBar.background", "SplitPane.background")),
		Map.entry ("Component.foreground", List.of ("controlText", "textText", "Panel.foreground", "CheckBox.foreground", "RadioButton.foreground", "ComboBox.foreground", "Menu.foreground", "MenuItem.foreground", "TitledBorder.titleColor")),
		Map.entry ("Component.selectionBackground", List.of ("textHighlight", "ComboBox.selectionBackground", "Menu.selectionBackground", "MenuItem.selectionBackground", "TextField.selectionBackground", "TextArea.selectionBackground")),
		Map.entry ("Component.selectionForeground", List.of ("textHighlightText", "ComboBox.selectionForeground", "Menu.selectionForeground", "MenuItem.selectionForeground", "TextField.selectionForeground", "TextArea.selectionForeground")),
		Map.entry ("Component.borderColor", List.of ("controlShadow", "Separator.foreground", "TitledBorder.border")),
		Map.entry ("Component.focusColor", List.of ("Button.focus", "Component.focusColor")),
		Map.entry ("Component.disabledForeground", List.of ("textInactiveText", "Button.disabledText", "Label.disabledForeground", "ComboBox.disabledForeground")),
		Map.entry ("Text.background", List.of ("TextField.background", "TextArea.background", "PasswordField.background", "FormattedTextField.background", "EditorPane.background", "TextPane.background")),
		Map.entry ("Text.foreground", List.of ("TextField.foreground", "TextArea.foreground", "PasswordField.foreground", "FormattedTextField.foreground", "TextField.caretForeground")),
		Map.entry ("Table.headerBackground", List.of ("TableHeader.background")), Map.entry ("Table.headerForeground", List.of ("TableHeader.foreground")),
		Map.entry ("Table.stripeBackground", List.of ("Table.alternateRowColor")), Map.entry ("TabFolder.background", List.of ("TabbedPane.background")),
		Map.entry ("TabFolder.selectedBackground", List.of ("TabbedPane.selected")), Map.entry ("ToolTip.borderColor", List.of ("ToolTip.border")),
		Map.entry ("Link.foreground", List.of ("Component.linkColor")));

	private M3SwingTheme () {
	}

	/**
	 * Puts the theme's values into <code>UIManager</code> and updates every open window.
	 *
	 * @param flatLafProperties the text of the generated <code>.flatlaf.properties</code> (or any FlatLaf theme)
	 * @return the number of <code>UIManager</code> entries put
	 */
	public static int apply (String flatLafProperties) {
		LookAndFeelDefaults table = new LookAndFeelLoader (n -> n.equals ("swing") ? M3ThemeImport.flatLaf (flatLafProperties).text () : LookAndFeelThemes.text (n), name -> 0x808080).load ("swing");
		int entries = 0;
		for (String key : table.keys ()) {
			Object value = value (table, key);
			if (value == null) continue;
			for (String target : targets (key)) {
				UIManager.put (target, value);
				entries++;
			}
		}
		if (!GraphicsEnvironment.isHeadless ()) {
			for (Window window : Window.getWindows ()) SwingUtilities.updateComponentTreeUI (window);
		}
		int count = entries;
		LOG.info (() -> "applied " + count + " UIManager entries");
		return entries;
	}

	/** The Swing keys a catalogue key feeds: the key itself (for the widgets Swing shares) and the aliases. */
	static List<String> targets (String key) {
		List<String> targets = new ArrayList<> ();
		if (!key.startsWith ("Component.") && !key.startsWith ("Text.") && !key.startsWith ("TabFolder.") && !key.startsWith ("Link.")) targets.add (key);
		targets.addAll (ALSO.getOrDefault (key, List.of ()));
		if (key.endsWith (".borderColor") && targets.contains (key)) targets.add (key.substring (0, key.indexOf ('.')) + ".border");
		return targets;
	}

	static Object value (LookAndFeelDefaults table, String key) {
		int index = table.index (key);
		switch (table.kind (index)) {
			case LookAndFeelDefaults.COLOR: {
				int argb = table.value (index);
				return new ColorUIResource (new Color ((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, argb >>> 24));
			}
			case LookAndFeelDefaults.INTEGER: return Integer.valueOf (table.value (index));
			case LookAndFeelDefaults.BOOLEAN: return Boolean.valueOf (table.value (index) != 0);
			case LookAndFeelDefaults.INSETS: {
				int [] insets = table.insets (index);
				return new InsetsUIResource (insets [0], insets [1], insets [2], insets [3]);
			}
			default: return null;
		}
	}
}
