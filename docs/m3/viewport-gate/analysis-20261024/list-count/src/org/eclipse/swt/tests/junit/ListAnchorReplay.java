package org.eclipse.swt.tests.junit;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Random;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;

/**
 * Oracle (official 3.135.0 jar, or this SWT in either mode): the native anchor of the list-count script and of fresh
 * lists before a Ctrl+Shift+click. setItemCount is the fork's when List has it, else the original mode's path
 * (remove (count, itemCount - 1) or add ("") per row).
 * Usage: ListAnchorReplay outFile seed steps
 */
public class ListAnchorReplay {
	static Display display;

	public static void main (String [] args) throws Exception {
		java.util.List<String> out = new ArrayList<> ();
		display = Display.getDefault ();
		Shell shell = new Shell (display);
		shell.setBounds (20, 20, 330, 420);
		Test_org_eclipse_swt_widgets_List_ViewportDifferential.Subject subject = new Test_org_eclipse_swt_widgets_List_ViewportDifferential.Subject (shell, SWT.MULTI);
		out.add ("# SWT " + SWT.getVersion () + " mode " + System.getProperty ("swt.widget.mode.List", "-") + " setItemCount " + Test_org_eclipse_swt_widgets_List_ViewportDifferential.hasSetItemCount ());
		/* fresh lists: the anchor before any input, and a Ctrl+Shift+click with it */
		String [][] fresh = {
			{"fresh"},
			{"fresh", "selectRange 124 135", "key DOWN ctrl", "click 12 ctrlshift"},
			{"fresh", "select 40", "remove 30", "selectRange 124 135", "key DOWN ctrl", "click 12 ctrlshift"},
			{"fresh", "select 40", "deselect 40", "selectRange 124 135", "key DOWN ctrl", "click 12 ctrlshift"},
			{"fresh", "click 3 none", "selectRange 124 135", "key DOWN ctrl", "click 12 ctrlshift"},
			{"fresh", "remove 0", "selectRange 124 135", "key DOWN ctrl", "click 12 ctrlshift"},
		};
		for (String [] script : fresh) {
			subject.reset ();
			StringBuilder line = new StringBuilder (String.join (" / ", script) + ":");
			for (String op : script) {
				if (!op.equals ("fresh")) Test_org_eclipse_swt_widgets_List_ViewportDifferential.apply (op, subject.list);
				Test_org_eclipse_swt_widgets_List_ViewportDifferential.flush (display);
				line.append ("\n   ").append (op).append (" -> ").append (state (subject.list));
			}
			out.add (line.toString ());
		}
		/* the seed's script, with the native anchor after every step */
		int seed = Integer.parseInt (args [1]), steps = Integer.parseInt (args [2]);
		subject.reset ();
		Random random = new Random (0x1157L + seed);
		StringBuilder log = new StringBuilder ("seed " + seed + "\n");
		for (int step = 0; step < steps; step++) {
			int before = log.length ();
			ViewportDifferential.Op<Test_org_eclipse_swt_widgets_List_ViewportDifferential.Subject> op = Test_org_eclipse_swt_widgets_List_ViewportDifferential.countOp (random, log);
			String name = log.substring (before).trim ();
			String answer;
			if (name.startsWith ("setItemCount") && !Test_org_eclipse_swt_widgets_List_ViewportDifferential.hasSetItemCount ()) {
				int count = Integer.parseInt (name.split (" ") [1]), itemCount = subject.list.getItemCount ();
				if (count < itemCount) subject.list.remove (count, itemCount - 1);
				for (int i = itemCount; i < count; i++) subject.list.add ("");
				answer = "top " + subject.list.getTopIndex () + " count " + subject.list.getItemCount ();
			} else {
				answer = op.apply (subject);
			}
			Test_org_eclipse_swt_widgets_List_ViewportDifferential.flush (display);
			out.add (seed + "\t" + step + "\t" + name + "\t" + answer + " | sel " + Test_org_eclipse_swt_widgets_List_ViewportDifferential.selection (subject.list) + " | " + state (subject.list));
		}
		Files.write (Paths.get (args [0]), out, StandardCharsets.UTF_8);
		shell.dispose ();
	}

	static String state (List list) {
		long handle = list.handle;
		return "native top " + OS.SendMessage (handle, OS.LB_GETTOPINDEX, 0, 0) + " caret " + OS.SendMessage (handle, OS.LB_GETCARETINDEX, 0, 0)
			+ " anchor " + OS.SendMessage (handle, OS.LB_GETANCHORINDEX, 0, 0) + " count " + OS.SendMessage (handle, OS.LB_GETCOUNT, 0, 0)
			+ " | api top " + list.getTopIndex () + " focus " + list.getFocusIndex () + " sel " + Test_org_eclipse_swt_widgets_List_ViewportDifferential.selection (list);
	}
}
