package org.eclipse.swt.tests.junit;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;

/*
 * Verifier oracle: does an unused wheel remainder outlive its process on the same desktop? "leave": a 4-row list without
 * bars takes wheel -360 and the JVM exits. "probe" (the next JVM on the same desktop): a 300-row list at top 181 takes
 * wheel -240: +6 = the remainder died with the process, +15 = it is kept outside the process.
 */
public class ListWheelProcessOracle {

	static void settle (Display display, int millis) {
		long end = System.currentTimeMillis () + millis;
		while (System.currentTimeMillis () < end) {
			if (!display.readAndDispatch ()) {
				try {
					Thread.sleep (2);
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}

	public static void main (String [] args) {
		boolean leave = args [0].equals ("leave");
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setBounds (20, 20, 330, 420);
		List list = new List (shell, SWT.MULTI | SWT.SIMPLE | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		list.setBounds (5, 5, 300, 380);
		for (int i = 0; i < (leave ? 4 : 300); i++) list.add ("row " + i);
		shell.open ();
		list.setFocus ();
		settle (display, 60);
		if (!leave) list.setTopIndex (181);
		settle (display, 20);
		int delta = leave ? -360 : -240, before = list.getTopIndex ();
		OS.SendMessage (list.handle, 0x20A, (long) (delta & 0xFFFF) << 16, 0);
		settle (display, 20);
		int after = list.getTopIndex ();
		System.out.println ("# mode " + ViewportDifferential.reference ("List") + " SWT " + SWT.getVersion () + " " + args [0] + " desktop " + System.getenv ("M3_UI_DESKTOP"));
		System.out.println (String.format ("%s: rows %d vbar %s wheel %d top %d -> %d (%+d)", args [0], list.getItemCount (), list.getVerticalBar ().getVisible (), delta, before, after, after - before));
		shell.dispose ();
		display.dispose ();
	}
}
