package org.eclipse.swt.tests.junit;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;

/**
 * Oracle (official 3.135.0 jar, or this SWT in either mode): Ctrl+Shift+click of an extended list without an anchor.
 * Which selected rows does the list box deselect: 0 .. last range end, last range end .. clicked row, or none?
 * Usage: ListCtrlShiftOracle outFile
 */
public class ListCtrlShiftOracle {
	public static void main (String [] args) throws Exception {
		java.util.List<String> out = new ArrayList<> ();
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		shell.setBounds (20, 20, 330, 420);
		Test_org_eclipse_swt_widgets_List_ViewportDifferential.Subject subject = new Test_org_eclipse_swt_widgets_List_ViewportDifferential.Subject (shell, SWT.MULTI);
		out.add ("# SWT " + SWT.getVersion () + " mode " + System.getProperty ("swt.widget.mode.List", "-"));
		String [][] scripts = {
			{"select 40", "remove 30", "selectRange 3 6", "selectRange 50 52", "click 12 ctrlshift"},
			{"select 20", "remove 10", "selectRange 2 4", "click 22 ctrlshift"},
			{"selectRange 0 3", "click 12 ctrlshift"},
			{"click 3 none", "remove 1", "selectRange 5 8", "click 12 ctrlshift"},
			{"click 3 none", "remove 1", "selectRange 5 8", "click 1 ctrlshift"},
			{"select 8", "remove 2", "selectRange 15 20", "selectRange 0 1", "click 10 ctrlshift"},
			{"key DOWN shift", "key DOWN shift", "remove 0", "selectRange 10 14", "click 6 ctrlshift"},
			{"selectAll", "remove 5", "click 12 ctrlshift"},
		};
		for (String [] script : scripts) {
			subject.reset ();
			StringBuilder line = new StringBuilder (String.join (" / ", script) + ":");
			for (String op : script) {
				Test_org_eclipse_swt_widgets_List_ViewportDifferential.apply (op, subject.list);
				Test_org_eclipse_swt_widgets_List_ViewportDifferential.flush (display);
				line.append ("\n   ").append (op).append (" -> ").append (state (subject.list)).append (" events ").append (subject.events);
				subject.events.setLength (0);
			}
			out.add (line.toString ());
		}
		Files.write (Paths.get (args [0]), out, StandardCharsets.UTF_8);
		shell.dispose ();
	}

	static String state (List list) {
		long handle = list.handle;
		return "anchor " + OS.SendMessage (handle, OS.LB_GETANCHORINDEX, 0, 0) + " caret " + list.getFocusIndex () + " sel "
			+ Test_org_eclipse_swt_widgets_List_ViewportDifferential.selection (list);
	}
}
