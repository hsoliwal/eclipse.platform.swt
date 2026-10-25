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
 * Oracle (official 3.135.0 jar, or this SWT in either mode): a click below the last row of an extended list (20 rows,
 * rows 20..24 of the client area are empty). Which row does the list box select, and what are anchor and caret?
 * Usage: ListBelowClickOracle outFile
 */
public class ListBelowClickOracle {
	public static void main (String [] args) throws Exception {
		java.util.List<String> out = new ArrayList<> ();
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		shell.setBounds (20, 20, 330, 420);
		shell.open ();
		out.add ("# SWT " + SWT.getVersion () + " mode " + System.getProperty ("swt.widget.mode.List", "-"));
		String [][] scripts = {
			{"click 5 none", "click 22 none"},
			{"click 5 none", "click 9 shift", "deselectAll", "click 22 none"},
			{"select 7", "deselect 7", "click 22 none"},
			{"click 5 none", "remove 3", "click 22 none"},
			{"click 22 none"},
			{"click 5 none", "key DOWN none", "key DOWN none", "deselectAll", "click 22 none"},
			{"click 5 none", "click 9 ctrl", "click 22 none"},
			{"click 5 none", "click 22 ctrl"},
			{"click 5 none", "click 22 shift"},
			{"selectRange 2 4", "click 22 none"},
			{"click 5 none", "click 9 shift", "remove 15", "click 22 none"},
			{"click 5 none", "click 9 shift", "remove 7", "click 22 none"},
			{"click 5 none", "drag 22 down 3"},
			{"click 5 none", "drag 22 up 3"},
		};
		for (String [] script : scripts) {
			List list = new List (shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
			list.setBounds (5, 5, 300, 380);
			for (int i = 0; i < 20; i++) list.add ("row " + i);
			StringBuilder events = new StringBuilder ();
			ViewportDifferential.listen (list, events, "S", SWT.Selection);
			list.setFocus ();
			ViewportDifferential.settle (display, 30);
			StringBuilder line = new StringBuilder (String.join (" / ", script) + ":");
			for (String op : script) {
				events.setLength (0);
				Test_org_eclipse_swt_widgets_List_ViewportDifferential.apply (op, list);
				Test_org_eclipse_swt_widgets_List_ViewportDifferential.flush (display);
				long handle = list.handle;
				line.append ("\n   ").append (op).append (" -> anchor ").append (OS.SendMessage (handle, OS.LB_GETANCHORINDEX, 0, 0)).append (" caret ")
					.append (list.getFocusIndex ()).append (" top ").append (list.getTopIndex ()).append (" sel ")
					.append (Test_org_eclipse_swt_widgets_List_ViewportDifferential.selection (list)).append (" events ").append (events);
			}
			out.add (line.toString ());
			list.dispose ();
		}
		Files.write (Paths.get (args [0]), out, StandardCharsets.UTF_8);
		shell.dispose ();
	}
}
