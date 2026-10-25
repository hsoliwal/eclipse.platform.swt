package org.eclipse.swt.tests.junit;

import java.lang.reflect.Field;
import java.util.Random;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Oracle of unit combo-readonly: replays the seeded scripts of Test_org_eclipse_swt_widgets_Combo_ViewportDifferential
 * (its own randomOp, Subject, flush and probes, compiled test classes on the class path) and prints each step line as
 * the harness records it, then a diagnostic line: the drop-down list box's native caret, selection, count, top index
 * and bar, and on a viewport jar the ComboViewport lanes (caret, stale, staleSelection, parked, barState, plan).
 * Arguments: ro:SEED:LAST (read-only) or vf:SEED:LAST (read-only with the Verify listener), in run order.
 */
public class ComboReadOnlyOracle {
	public static void main (String [] args) throws Exception {
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		shell.setBounds (20, 20, 330, 420);
		boolean reference = ViewportDifferential.isReference ("Combo");
		/* the differential's run () probes a throw-away combo first in a viewport run */
		if (!reference) System.out.println ("# viewportActive " + Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.viewportActive (shell));
		System.out.println ("# SWT " + SWT.getVersion () + " reference " + ViewportDifferential.reference ("Combo"));
		Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Subject subject = null;
		String kind = "";
		for (String arg : args) {
			String [] a = arg.split (":");
			if (!a [0].equals (kind)) {
				if (subject != null && subject.combo != null) subject.combo.dispose ();
				subject = new Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Subject (shell, SWT.DROP_DOWN | SWT.READ_ONLY, a [0].equals ("vf"));
				kind = a [0];
			}
			int seed = Integer.parseInt (a [1]), last = Integer.parseInt (a [2]);
			subject.reset ();
			System.out.println (kind + " seed " + seed + " start    diag " + diag (subject.combo));
			Random random = new Random (0xC0B0L + seed);
			StringBuilder log = new StringBuilder ("seed " + seed + "\n");
			for (int step = 0; step <= last; step++) {
				ViewportDifferential.Op<Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Subject> op = Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.randomOp (random, log);
				String [] lines = log.toString ().split ("\n");
				String name = lines [lines.length - 1];
				subject.events.setLength (0);
				String answer = op.apply (subject);
				Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.flush (shell.getDisplay ());
				Combo combo = subject.combo;
				StringBuilder line = new StringBuilder (answer);
				line.append (" | state sel ").append (combo.getSelectionIndex ()).append (" count ").append (combo.getItemCount ()).append (" text '")
					.append (combo.getText ()).append ("' dropped ").append (combo.getListVisible ());
				line.append (" | events ").append (subject.events);
				Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.hideCues (combo);
				line.append (" | face ").append (ViewportDifferential.hash (ViewportDifferential.render (combo)));
				line.append (" | list ").append (combo.getListVisible ()
					? Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Native.bar (Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Native.list (combo)) + " "
						+ ViewportDifferential.hash (Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Native.print (combo, Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Native.list (combo)))
					: "-");
				System.out.println (kind + " seed " + seed + " step " + step + " [" + name.replace ("\b", "\b") + "] " + line);
				System.out.println ("        diag " + diag (combo));
			}
		}
		System.out.println ("# done");
		shell.dispose ();
		display.dispose ();
	}

	/* native list box state, then the viewport lanes when this SWT mediates the combo */
	static String diag (Combo combo) throws Exception {
		long list = Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Native.list (combo);
		StringBuilder b = new StringBuilder ();
		b.append ("native caret ").append (send (list, 0x19F)).append (" cursel ").append (send (list, 0x188)).append (" count ").append (send (list, 0x18B))
			.append (" top ").append (send (list, 0x18E)).append (" bar ").append (Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Native.bar (list));
		Field field;
		try {
			field = Combo.class.getDeclaredField ("viewport");
		} catch (NoSuchFieldException e) {
			return b.append (" (no viewport)").toString ();
		}
		field.setAccessible (true);
		Object v = field.get (combo);
		if (v == null) return b.append (" (viewport null)").toString ();
		b.append (" || lanes caret ").append (get (v, "caret")).append (" parkedCaret ").append (get (v, "parkedCaret")).append (" stale ").append (get (v, "stale"))
			.append (" staleSelection ").append (get (v, "staleSelection")).append (" parked ").append (get (v, "parked")).append (" barState ").append (get (v, "barState"))
			.append (" recordedRow ").append (get (v, "recordedRow"));
		Object plan = get (v, "plan");
		b.append (" plan ").append (get (plan, "first")).append ('+').append (get (plan, "size"));
		return b.toString ();
	}

	static long send (long hwnd, int message) {
		return Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Native.send (hwnd, message, 0, 0);
	}

	static Object get (Object object, String name) throws Exception {
		Field field = object.getClass ().getDeclaredField (name);
		field.setAccessible (true);
		return field.get (object);
	}
}
