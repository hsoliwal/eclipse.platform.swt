package org.eclipse.swt.tests.junit;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;

/*
 * Verifier oracle: is the list box's unused wheel remainder kept per thread or per process? Thread A leaves one
 * (wheel -360 on a 4-row list without bars, shell disposed); thread B, with its own Display, then wheels a 300-row
 * list at top 181 by -240; then thread A does the same. Per thread: B +6, A +15. Per process: B +15, A +6.
 */
public class ListWheelThreadOracle {

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

	static List list (Display display, Shell shell, int rows) {
		List list = new List (shell, SWT.MULTI | SWT.SIMPLE | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		list.setBounds (5, 5, 300, 380);
		for (int i = 0; i < rows; i++) list.add ("row " + i);
		shell.open ();
		list.setFocus ();
		settle (display, 60);
		return list;
	}

	static Shell shell (Display display) {
		Shell shell = new Shell (display);
		shell.setBounds (20, 20, 330, 420);
		return shell;
	}

	static String wheel (Display display, String what, List list, int delta) {
		int before = list.getTopIndex ();
		OS.SendMessage (list.handle, 0x20A, (long) (delta & 0xFFFF) << 16, 0);
		settle (display, 20);
		int after = list.getTopIndex ();
		return String.format ("%-40s rows %3d vbar %-5s top %3d -> %3d (%+d)", what, list.getItemCount (), list.getVerticalBar ().getVisible (), before, after, after - before);
	}

	public static void main (String [] args) throws Exception {
		Display a = new Display ();
		System.out.println ("# mode " + ViewportDifferential.reference ("List") + " SWT " + SWT.getVersion ());
		Shell first = shell (a);
		List small = list (a, first, 4);
		System.out.println (wheel (a, "thread A: leftover wheel -360 (no bars)", small, -360));
		first.dispose ();
		settle (a, 30);
		Thread other = new Thread (() -> {
			Display b = new Display ();
			Shell shell = shell (b);
			List big = list (b, shell, 300);
			big.setTopIndex (181);
			settle (b, 20);
			System.out.println (wheel (b, "thread B (own Display): wheel -240", big, -240));
			shell.dispose ();
			b.dispose ();
		});
		other.start ();
		other.join ();
		Shell third = shell (a);
		List big = list (a, third, 300);
		big.setTopIndex (181);
		settle (a, 20);
		System.out.println (wheel (a, "thread A: wheel -240", big, -240));
		third.dispose ();
		a.dispose ();
	}
}
