package org.eclipse.swt.tests.junit;

import java.util.Random;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Shell;

/*
 * Verifier oracle (list-simple, seed 0). Replays steps 0..N-1 of seed 0 of
 * Test_org_eclipse_swt_widgets_List_ViewportDifferential#simpleMultiSelectionListAnswersLikeTheReference with the test's
 * own compiled generator (randomOp), Subject, probes and afterStep, and prints each step line in the recording format.
 *   previous: none    - fresh JVM: wheel remainder 0
 *             barless - the previous test ends with wheel -360 on a 4-row list without bars (list-multi seed 9 step 148),
 *                       its shell is disposed; then an empty test (new shell, disposed), as setItemCount aborts on the
 *                       official jar (recording JVM order)
 *             gate    - barless, then a list-count stand-in: an 88-row list at top 13 takes wheel -120 (list-count
 *                       seed 0 step 76), shell disposed (gate JVM order)
 *   reset:    stock   - Subject.reset as in the test (Test:68-79)
 *             fix     - stock plus ViewportDifferential.send (list, 0x20A, 0, 0); list.setTopIndex (0); right after
 *                       list.setFocus () (the proposed fix, placed before the settle)
 *             alt     - on the first seed a throwaway List is created, the shell opened, the throwaway focused, then the
 *                       existing list.dispose () runs (the proposed alternative)
 * Usage: ListSimpleSeed0Replay <previous> <reset> [steps]
 */
public class ListSimpleSeed0Replay {

	public static void main (String [] args) throws Exception {
		String previous = args [0], reset = args [1];
		int steps = args.length > 2 ? Integer.parseInt (args [2]) : 70;
		/* the style of the subject: simple (list-simple, default), multi (list-multi), single (list-single) */
		String styleName = args.length > 3 ? args [3] : "simple";
		int style = switch (styleName) {
			case "multi" -> SWT.MULTI;
			case "single" -> SWT.SINGLE;
			default -> SWT.MULTI | SWT.SIMPLE;
		};
		Display display = Display.getDefault ();
		System.out.println ("# mode " + ViewportDifferential.reference ("List") + " SWT " + SWT.getVersion () + " previous " + previous + " reset " + reset + " style " + styleName);
		if (!previous.equals ("none")) previousTests (display, previous);
		Shell shell = new Shell (display);
		shell.setBounds (20, 20, 330, 420);
		boolean reference = ViewportDifferential.isReference ("List");
		if (!reference) System.out.println ("# viewport active " + Test_org_eclipse_swt_widgets_List_ViewportDifferential.viewportActive (shell));
		Test_org_eclipse_swt_widgets_List_ViewportDifferential.Subject subject = new Test_org_eclipse_swt_widgets_List_ViewportDifferential.Subject (shell, style);
		ViewportDifferential<Test_org_eclipse_swt_widgets_List_ViewportDifferential.Subject> harness = ViewportDifferential
			.<Test_org_eclipse_swt_widgets_List_ViewportDifferential.Subject> of ("list-simple", 0x1157L, Test_org_eclipse_swt_widgets_List_ViewportDifferential::randomOp)
			.widget ("List").seeds (1).steps (steps)
			.beforeStep (s -> s.events.setLength (0))
			.afterStep (s -> {
				Test_org_eclipse_swt_widgets_List_ViewportDifferential.flush (s.shell.getDisplay ());
				if (!reference) Test_org_eclipse_swt_widgets_List_ViewportDifferential.assertNoFullRebuild (s.list);
			})
			.probe ("bars", s -> ViewportDifferential.bar (s.list.getVerticalBar ()) + " " + ViewportDifferential.bar (s.list.getHorizontalBar ()))
			.probe ("sel", s -> Test_org_eclipse_swt_widgets_List_ViewportDifferential.selection (s.list))
			.probe ("focus", s -> s.list.getFocusIndex () + (s.list.isFocusControl () ? "*" : ""))
			.probe ("events", s -> s.events.toString ());
		reset (subject, reset);
		Random random = new Random (0x1157L);
		StringBuilder log = new StringBuilder ("seed 0\n");
		for (int step = 0; step < steps; step++) {
			ViewportDifferential.Op<Test_org_eclipse_swt_widgets_List_ViewportDifferential.Subject> op = Test_org_eclipse_swt_widgets_List_ViewportDifferential.randomOp (random, log);
			System.out.println (harness.line (subject, op));
		}
		shell.dispose ();
		display.dispose ();
	}

	static void previousTests (Display display, String kind) {
		Shell first = new Shell (display);
		first.setBounds (20, 20, 330, 420);
		List small = new List (first, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		small.setBounds (5, 5, 300, 380);
		for (int i = 0; i < 4; i++) small.add ("row " + i);
		first.open ();
		small.setFocus ();
		ViewportDifferential.settle (display, 60);
		int top = small.getTopIndex ();
		/* barlessup: the leftover is one notch up (wheel +120), the other direction */
		int delta = kind.equals ("barlessup") ? 120 : -360;
		ViewportDifferential.send (small, 0x20A, (long) (delta & 0xFFFF) << 16, 0);
		Test_org_eclipse_swt_widgets_List_ViewportDifferential.flush (display);
		System.out.println ("# previous test: 4-row list, bars " + ViewportDifferential.bar (small.getVerticalBar ()) + " " + ViewportDifferential.bar (small.getHorizontalBar ())
			+ ", wheel " + delta + ": top " + top + " -> " + small.getTopIndex ());
		first.dispose ();
		ViewportDifferential.settle (display, 30);
		/* setItemCountAnswersLikeTheOriginal on the official jar: setUp and tearDown only (assumption) */
		Shell aborted = new Shell (display);
		aborted.setBounds (20, 20, 330, 420);
		aborted.dispose ();
		if (kind.equals ("gate")) {
			Shell count = new Shell (display);
			count.setBounds (20, 20, 330, 420);
			List list = new List (count, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
			list.setBounds (5, 5, 300, 380);
			for (int i = 0; i < 88; i++) list.add ("row " + i);
			count.open ();
			list.setFocus ();
			ViewportDifferential.settle (display, 60);
			list.setTopIndex (13);
			int before = list.getTopIndex ();
			ViewportDifferential.send (list, 0x20A, (long) (-120 & 0xFFFF) << 16, 0);
			Test_org_eclipse_swt_widgets_List_ViewportDifferential.flush (display);
			System.out.println ("# list-count stand-in: 88 rows, wheel -120: top " + before + " -> " + list.getTopIndex ());
			count.dispose ();
			ViewportDifferential.settle (display, 30);
		}
	}

	static void reset (Test_org_eclipse_swt_widgets_List_ViewportDifferential.Subject s, String kind) {
		if (kind.equals ("alt") && s.list == null) {
			s.list = new List (s.shell, s.style | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
			s.shell.open ();
			s.list.setFocus ();
		}
		if (s.list != null) s.list.dispose ();
		s.list = new List (s.shell, s.style | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		s.list.setBounds (5, 5, 300, 380);
		for (int i = 0; i < Test_org_eclipse_swt_widgets_List_ViewportDifferential.ROWS; i++) s.list.add (Test_org_eclipse_swt_widgets_List_ViewportDifferential.text (i, i));
		ViewportDifferential.listen (s.list, s.events, "S", SWT.Selection);
		ViewportDifferential.listen (s.list, s.events, "D", SWT.DefaultSelection);
		s.shell.open ();
		s.list.setFocus ();
		if (kind.equals ("fix")) {
			ViewportDifferential.send (s.list, 0x20A, 0, 0);
			s.list.setTopIndex (0);
		}
		ViewportDifferential.settle (s.shell.getDisplay (), 60);
		s.events.setLength (0);
	}
}
