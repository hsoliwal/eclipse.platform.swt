// Manual win32 oracle (default package: the official SWT jar is signed).

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.widgets.*;

/**
 * Does the list box's wheel remainder survive a test boundary when the last wheel went to a list without scroll bars?
 * Mirrors Test_org_eclipse_swt_widgets_List_ViewportDifferential: shell 330x420, List MULTI|SIMPLE|BORDER|V_SCROLL|
 * H_SCROLL at 5,5 300x380. Phase 1 (end of the previous test): a 4-row list (no bars) takes wheel -360, the shell is
 * disposed. Phase 2 (next test, seed 0): new shell, 300 rows, setFocus; case "carry": top 181, wheel -240; case
 * "reset": first wheel +120 at top 0 (the proposed harness reset), then top 181, wheel -240.
 * Usage: java ListWheelCarryOracle carry|reset
 */
public class ListWheelCarryOracle {
	static Display display;

	static void settle (int millis) throws InterruptedException {
		long end = System.currentTimeMillis () + millis;
		while (System.currentTimeMillis () < end) {
			if (!display.readAndDispatch ()) Thread.sleep (2);
		}
	}

	static List list (Shell shell, int rows) throws InterruptedException {
		List list = new List (shell, SWT.MULTI | SWT.SIMPLE | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		list.setBounds (5, 5, 300, 380);
		for (int i = 0; i < rows; i++) list.add ("row " + i);
		shell.open ();
		list.setFocus ();
		settle (60);
		return list;
	}

	static String wheel (String what, List list, int delta) throws InterruptedException {
		int before = list.getTopIndex ();
		OS.SendMessage (list.handle, 0x20A, (long) (delta & 0xFFFF) << 16, 0);
		settle (20);
		int after = list.getTopIndex ();
		return String.format ("%-28s rows %3d vbar %-5s top %3d -> %3d (%+d)", what, list.getItemCount (), list.getVerticalBar ().getVisible (), before, after, after - before);
	}

	static String mode (List list) {
		try {
			java.lang.reflect.Field field = List.class.getDeclaredField ("viewport");
			field.setAccessible (true);
			return field.get (list) != null ? "viewport" : "original";
		} catch (ReflectiveOperationException | RuntimeException e) {
			return "official (no viewport field)";
		}
	}

	public static void main (String [] args) throws Exception {
		String kind = args [0];
		display = new Display ();
		Shell first = new Shell (display);
		first.setBounds (20, 20, 330, 420);
		List small = list (first, 4);
		System.out.println ("SWT " + SWT.getVersion () + " mode " + mode (small) + " case " + kind);
		System.out.println (wheel ("previous test: wheel -360", small, -360));
		first.dispose ();
		settle (30);
		Shell second = new Shell (display);
		second.setBounds (20, 20, 330, 420);
		List list = list (second, 300);
		if (kind.equals ("reset")) System.out.println (wheel ("seed start: reset +120", list, 120));
		list.setTopIndex (181);
		settle (20);
		System.out.println (wheel ("seed 0 step 57: wheel -240", list, -240));
		System.out.println (wheel ("next: wheel -240", list, -240));
		second.dispose ();
		display.dispose ();
	}
}
