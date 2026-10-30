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
package org.eclipse.swt.tests.junit.m3combinations;

import java.util.*;
import java.util.List;

import org.eclipse.swt.custom.*;
import org.eclipse.swt.widgets.*;

/** A StyledText whose rows are lines; a range selection spans whole lines. */
final class StyledTextSubject extends Subject {
	StyledText text;

	@Override
	void create (Shell shell, int style) {
		text = new StyledText (shell, style);
	}

	@Override
	Widget widget () {
		return text;
	}

	@Override
	void show (int [] order) {
		text.setText (String.join ("\n", texts (order)));
	}

	@Override
	void scrollTo (int row) {
		text.setTopIndex (row);
	}

	@Override
	void select (int start, int end) {
		int to = end + 1 < text.getLineCount () ? text.getOffsetAtLine (end + 1) : text.getCharCount ();
		text.setSelection (text.getOffsetAtLine (start), to);
	}

	@Override
	void update (int row, String line) {
		int start = text.getOffsetAtLine (row);
		text.replaceTextRange (start, text.getLine (row).length (), line);
	}

	@Override
	String state () {
		if (text.isDisposed ()) return "disposed";
		int top = text.getTopIndex ();
		return "lines=" + text.getLineCount () + " chars=" + text.getCharCount () + " top=" + top + " sel=" + text.getSelection () + " caret=" + text.getCaretOffset () + " text=" + text.getLine (top);
	}

	@Override
	List<Widget> items () {
		return new ArrayList<> ();
	}
}
