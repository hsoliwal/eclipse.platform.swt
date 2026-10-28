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
 * Debug oracle: replays the list-count script of a seed up to a step, then sends that step's click or drag message by
 * message, logging the native list box (top, caret, anchor, count, selected entries) and, in the viewport mode, the
 * resident window, around every message. Usage: ListClickDebug outFile seed step
 */
public class ListClickDebug {
	static java.util.List<String> out = new ArrayList<> ();

	public static void main (String [] args) throws Exception {
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		shell.setBounds (20, 20, 330, 420);
		Test_org_eclipse_swt_widgets_List_ViewportDifferential.Subject subject = new Test_org_eclipse_swt_widgets_List_ViewportDifferential.Subject (shell, SWT.MULTI);
		out.add ("# SWT " + SWT.getVersion () + " mode " + System.getProperty ("swt.widget.mode.List", "-"));
		int seed = Integer.parseInt (args [1]), stop = Integer.parseInt (args [2]);
		subject.reset ();
		Random random = new Random (0x1157L + seed);
		StringBuilder log = new StringBuilder ("seed " + seed + "\n");
		String name = null;
		for (int step = 0; step <= stop; step++) {
			int before = log.length ();
			ViewportDifferential.Op<Test_org_eclipse_swt_widgets_List_ViewportDifferential.Subject> op = Test_org_eclipse_swt_widgets_List_ViewportDifferential.countOp (random, log);
			name = log.substring (before).trim ();
			if (step == stop) break;
			op.apply (subject);
			Test_org_eclipse_swt_widgets_List_ViewportDifferential.flush (display);
		}
		List list = subject.list;
		out.add ("step " + stop + " " + name);
		note ("before", list);
		String [] a = name.split (" ");
		int height = list.getItemHeight ();
		if (a [0].equals ("click")) {
			long point = ViewportDifferential.makeLParam (40, Integer.parseInt (a [1]) * height + height / 2);
			ViewportDifferential.send (list, 0x200, 0, point);
			note ("WM_MOUSEMOVE", list);
			ViewportDifferential.send (list, 0x201, 1, point);
			note ("WM_LBUTTONDOWN", list);
			ViewportDifferential.send (list, 0x202, 0, point);
			note ("WM_LBUTTONUP", list);
		} else if (a [0].equals ("drag")) {
			long start = ViewportDifferential.makeLParam (40, Integer.parseInt (a [1]) * height + height / 2);
			long outside = ViewportDifferential.makeLParam (40, a [2].equals ("down") ? list.getClientArea ().height + 6 : -6);
			ViewportDifferential.send (list, 0x201, 1, start);
			note ("WM_LBUTTONDOWN", list);
			int moves = Integer.parseInt (a [3]);
			for (int i = 0; i < moves; i++) {
				ViewportDifferential.send (list, 0x200, 1, outside);
				if (i < 3 || i == moves - 1) note ("WM_MOUSEMOVE " + i, list);
			}
			ViewportDifferential.send (list, 0x202, 0, outside);
			note ("WM_LBUTTONUP", list);
		}
		Test_org_eclipse_swt_widgets_List_ViewportDifferential.flush (display);
		note ("flushed", list);
		Files.write (Paths.get (args [0]), out, StandardCharsets.UTF_8);
		shell.dispose ();
	}

	static void note (String what, List list) {
		long handle = list.handle;
		int count = (int) OS.SendMessage (handle, OS.LB_GETCOUNT, 0, 0);
		int n = (int) OS.SendMessage (handle, OS.LB_GETSELCOUNT, 0, 0);
		int [] items = new int [Math.max (0, Math.min (n, 40))];
		if (items.length > 0) OS.SendMessage (handle, OS.LB_GETSELITEMS, items.length, items);
		String window = "-";
		try {
			java.lang.reflect.Field viewport = List.class.getDeclaredField ("viewport");
			viewport.setAccessible (true);
			Object v = viewport.get (list);
			if (v != null) {
				java.lang.reflect.Field plan = v.getClass ().getDeclaredField ("plan");
				plan.setAccessible (true);
				Object p = plan.get (v);
				java.lang.reflect.Method first = p.getClass ().getDeclaredMethod ("first"), size = p.getClass ().getDeclaredMethod ("size");
				first.setAccessible (true);
				size.setAccessible (true);
				StringBuilder lanes = new StringBuilder ();
				for (String f : new String [] {"top", "caret", "anchor", "rangeEnd", "parkedCaret", "pastPage"}) {
					try {
						java.lang.reflect.Field field = v.getClass ().getDeclaredField (f);
						field.setAccessible (true);
						lanes.append (' ').append (f).append (' ').append (field.get (v));
					} catch (NoSuchFieldException e) {
						/* not in this build */
					}
				}
				window = "first " + first.invoke (p) + " size " + size.invoke (p) + lanes;
			}
		} catch (ReflectiveOperationException e) {
			window = "? " + e;
		}
		out.add (String.format ("  %-16s native top %d caret %d anchor %d count %d sel %d %s | window %s | api top %d focus %d sel %s", what,
			OS.SendMessage (handle, OS.LB_GETTOPINDEX, 0, 0), OS.SendMessage (handle, OS.LB_GETCARETINDEX, 0, 0), OS.SendMessage (handle, OS.LB_GETANCHORINDEX, 0, 0),
			count, n, java.util.Arrays.toString (items), window, list.getTopIndex (), list.getFocusIndex (), Test_org_eclipse_swt_widgets_List_ViewportDifferential.selection (list)));
	}
}
