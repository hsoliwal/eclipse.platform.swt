package org.eclipse.swt.tests.junit;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;

/**
 * Replay of Test_org_eclipse_swt_widgets_Combo_ViewportDifferential (the compiled test classes): every step line exactly as
 * the harness builds it (ViewportDifferential.line: beforeStep, op, afterStep flush, probes state, events, face, list), plus a
 * diagnostic column read after the probes (native list box state; viewport lanes by reflection when this SWT has them).
 * Usage: ComboReplayDump <seeds 0,1,..> <dropdown|readonly> <steps> <out file>
 */
public class ComboReplayDump {
	public static void main (String [] args) throws Exception {
		String [] seeds = args [0].split (",");
		int style = args [1].equals ("readonly") ? SWT.DROP_DOWN | SWT.READ_ONLY : SWT.DROP_DOWN;
		int steps = Integer.parseInt (args [2]);
		try (PrintWriter out = new PrintWriter (new OutputStreamWriter (new FileOutputStream (args [3]), StandardCharsets.UTF_8), true)) {
			Display display = Display.getDefault ();
			Shell shell = new Shell (display);
			shell.setBounds (20, 20, 330, 420);
			Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Subject subject = new Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Subject (shell, style);
			out.println ("# SWT " + SWT.getVersion () + " mode " + ViewportDifferential.reference ("Combo"));
			for (String s : seeds) {
				int seed = Integer.parseInt (s.trim ());
				subject.reset ();
				Random random = new Random (0xC0B0L + seed);
				StringBuilder log = new StringBuilder ("seed " + seed + "\n");
				for (int step = 0; step < steps; step++) {
					ViewportDifferential.Op<Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Subject> op = Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.randomOp (random, log);
					String [] lines = log.toString ().split ("\n");
					String opName = lines [lines.length - 1].replace ("\b", "\b");
					String before = diag (subject.combo);
					subject.events.setLength (0);
					String answer = op.apply (subject);
					Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.flush (subject.shell.getDisplay ());
					StringBuilder line = new StringBuilder (answer == null ? "" : answer);
					Combo c = subject.combo;
					line.append (" | state ").append ("sel " + c.getSelectionIndex () + " count " + c.getItemCount () + " text '" + c.getText () + "' dropped " + c.getListVisible ());
					line.append (" | events ").append (subject.events.toString ());
					Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.hideCues (c);
					line.append (" | face ").append (ViewportDifferential.hash (ViewportDifferential.render (c)));
					line.append (" | list ").append (c.getListVisible () ? Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Native.bar (Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Native.list (c)) + " "
						+ ViewportDifferential.hash (Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Native.print (c, Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Native.list (c))) : "-");
					out.println (seed + "\t" + step + "\t" + opName + "\t" + line + "\t" + diag (c) + "\tbefore " + before);
				}
				/* the harness's end-of-seed pixels: capture until two consecutive captures agree (ViewportDifferential.stable) */
				org.eclipse.swt.graphics.ImageData previous = endCapture (subject), current = previous;
				for (int attempt = 0; attempt < 5; attempt++) {
					current = endCapture (subject);
					if (java.util.Arrays.equals (previous.data, current.data)) break;
					previous = current;
				}
				out.println (seed + "\tEND\tpixels\tpixels " + ViewportDifferential.hash (current));
			}
			shell.dispose ();
			display.dispose ();
		}
	}

	static org.eclipse.swt.graphics.ImageData endCapture (Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Subject s) {
		ViewportDifferential.settle (s.shell.getDisplay (), 30);
		Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.hideCues (s.combo);
		return ViewportDifferential.render (s.combo);
	}

	static String diag (Combo combo) {
		long list = Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Native.list (combo);
		long count = Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Native.send (list, 0x18b, 0, 0);
		long cursel = Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Native.send (list, 0x188, 0, 0);
		long top = Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Native.send (list, 0x18e, 0, 0);
		long caret = Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Native.send (list, 0x19f, 0, 0);
		StringBuilder b = new StringBuilder ("lb n" + count + " sel" + cursel + " top" + top + " caret" + caret);
		try {
			Field f = Combo.class.getDeclaredField ("viewport");
			f.setAccessible (true);
			Object v = f.get (combo);
			if (v == null) return b.append (" vp-").toString ();
			Field pf = v.getClass ().getDeclaredField ("plan");
			pf.setAccessible (true);
			Object plan = pf.get (v);
			Method first = plan.getClass ().getDeclaredMethod ("first"), size = plan.getClass ().getDeclaredMethod ("size");
			first.setAccessible (true);
			size.setAccessible (true);
			b.append (" vp win ").append (first.invoke (plan)).append ('+').append (size.invoke (plan));
			for (String name : new String [] {"caret", "parkedCaret", "parked", "stale", "staleSelection", "recordedRow", "recordedFirst", "tracking", "barState", "lastSetResult"}) {
				Field x = v.getClass ().getDeclaredField (name);
				x.setAccessible (true);
				b.append (' ').append (name).append ('=').append (x.get (v));
			}
		} catch (NoSuchFieldException e) {
			b.append (" official");
		} catch (ReflectiveOperationException e) {
			b.append (" reflect ").append (e);
		}
		return b.toString ();
	}
}
