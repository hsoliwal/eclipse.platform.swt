// Manual win32 oracle (default package: the official SWT jar is signed).

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.widgets.*;

/**
 * Which harness action clears a wheel remainder left by a list without scroll bars (a 4-row list takes wheel -360)?
 * Lists as in Test_org_eclipse_swt_widgets_List_ViewportDifferential (MULTI|SIMPLE|BORDER|V_SCROLL|H_SCROLL, 5,5
 * 300x380, shell 330x420). After the action a 300-row subject list at top 181 takes wheel -240: +6 = cleared, +15 = kept.
 * Cases:
 *   seed     - the 4-row list is disposed inside the same shell, the subject is created there (a seed boundary)
 *   test     - the shell is disposed, a new shell holds the subject (a test boundary)
 *   zero     - test boundary, then a throwaway 300-row list in the new shell takes WM_MOUSEWHEEL with delta 0, disposed
 *   zerosubj - test boundary, then the subject itself (top 0) takes delta 0, then setTopIndex 0
 *   focus    - test boundary, then a throwaway list takes the focus, the subject takes it back, throwaway disposed
 * Usage: java ListWheelResetOracle <case>
 */
public class ListWheelResetOracle {
	static Display display;

	static void settle (int millis) throws InterruptedException {
		long end = System.currentTimeMillis () + millis;
		while (System.currentTimeMillis () < end) {
			if (!display.readAndDispatch ()) Thread.sleep (2);
		}
	}

	static Shell shell () {
		Shell shell = new Shell (display);
		shell.setBounds (20, 20, 330, 420);
		return shell;
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

	static void send (List list, int delta) throws InterruptedException {
		OS.SendMessage (list.handle, 0x20A, (long) (delta & 0xFFFF) << 16, 0);
		settle (20);
	}

	static String wheel (String what, List list, int delta) throws InterruptedException {
		int before = list.getTopIndex ();
		send (list, delta);
		int after = list.getTopIndex ();
		return String.format ("%-34s rows %3d vbar %-5s top %3d -> %3d (%+d)", what, list.getItemCount (), list.getVerticalBar ().getVisible (), before, after, after - before);
	}

	static String mode (List list) {
		try {
			java.lang.reflect.Field field = List.class.getDeclaredField ("viewport");
			field.setAccessible (true);
			return field.get (list) != null ? "viewport" : "original";
		} catch (ReflectiveOperationException | RuntimeException e) {
			return "official";
		}
	}

	public static void main (String [] args) throws Exception {
		String kind = args [0];
		display = new Display ();
		Shell first = shell ();
		List small = list (first, 4);
		System.out.println ("SWT " + SWT.getVersion () + " mode " + mode (small) + " case " + kind);
		System.out.println (wheel ("leftover: wheel -360 (no bars)", small, -360));
		Shell second;
		if (kind.equals ("seed")) {
			small.dispose ();
			settle (20);
			second = first;
		} else {
			first.dispose ();
			settle (30);
			second = shell ();
		}
		List subject;
		switch (kind) {
			case "zero" -> {
				List throwaway = list (second, 300);
				System.out.println (wheel ("throwaway: wheel 0", throwaway, 0));
				throwaway.dispose ();
				subject = list (second, 300);
			}
			case "zerosubj" -> {
				subject = list (second, 300);
				System.out.println (wheel ("subject at top 0: wheel 0", subject, 0));
				subject.setTopIndex (0);
				settle (20);
				System.out.println ("subject top after setTopIndex 0: " + subject.getTopIndex ());
			}
			case "focus" -> {
				List throwaway = list (second, 300);
				subject = list (second, 300);
				System.out.println ("focus moved throwaway -> subject: subject focused " + subject.isFocusControl ());
				throwaway.dispose ();
				settle (20);
			}
			default -> subject = list (second, 300);
		}
		subject.setTopIndex (181);
		settle (20);
		System.out.println (wheel ("subject: wheel -240", subject, -240));
		System.out.println (wheel ("subject: wheel -240 again", subject, -240));
		second.dispose ();
		display.dispose ();
	}
}
