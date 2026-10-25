package org.eclipse.swt.tests.junit;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Random;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Replay of the list-count script (Test_org_eclipse_swt_widgets_List_ViewportDifferential#setItemCountAnswersLikeTheOriginal)
 * without JUnit: the same subject, ops, afterStep flush and probes (bars, sel, focus, events); no pixels. Writes
 * <out>/list-count-seed<n>.txt and prints, per seed, the first step that differs from the recording directory.
 * Usage: ListCountReplay outDir recordingDir seeds(0,1,..)
 */
public class ListCountReplay {
	public static void main (String [] args) throws Exception {
		Path out = Paths.get (args [0]), recording = Paths.get (args [1]);
		Files.createDirectories (out);
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		shell.setBounds (20, 20, 330, 420);
		Test_org_eclipse_swt_widgets_List_ViewportDifferential.Subject subject = new Test_org_eclipse_swt_widgets_List_ViewportDifferential.Subject (shell, SWT.MULTI);
		StringBuilder summary = new StringBuilder ();
		for (String s : args [2].split (",")) {
			int seed = Integer.parseInt (s.trim ());
			subject.reset ();
			Random random = new Random (0x1157L + seed);
			StringBuilder log = new StringBuilder ("seed " + seed + "\n");
			java.util.List<String> lines = new ArrayList<> ();
			for (int step = 0; step < Test_org_eclipse_swt_widgets_List_ViewportDifferential.STEPS; step++) {
				ViewportDifferential.Op<Test_org_eclipse_swt_widgets_List_ViewportDifferential.Subject> op = Test_org_eclipse_swt_widgets_List_ViewportDifferential.countOp (random, log);
				subject.events.setLength (0);
				String answer = op.apply (subject);
				Test_org_eclipse_swt_widgets_List_ViewportDifferential.flush (display);
				String line = answer + " | bars " + ViewportDifferential.bar (subject.list.getVerticalBar ()) + " " + ViewportDifferential.bar (subject.list.getHorizontalBar ())
					+ " | sel " + Test_org_eclipse_swt_widgets_List_ViewportDifferential.selection (subject.list)
					+ " | focus " + subject.list.getFocusIndex () + (subject.list.isFocusControl () ? "*" : "")
					+ " | events " + subject.events;
				lines.add (line);
			}
			Files.write (out.resolve ("list-count-seed" + seed + ".txt"), lines, StandardCharsets.UTF_8);
			java.util.List<String> recorded = new ArrayList<> ();
			for (String r : Files.readAllLines (recording.resolve ("list-count-seed" + seed + ".txt"), StandardCharsets.UTF_8)) if (!r.startsWith ("# ")) recorded.add (r);
			int first = -1, differ = 0;
			for (int i = 0; i < lines.size (); i++) {
				if (!lines.get (i).equals (recorded.get (i))) {
					differ++;
					if (first < 0) first = i;
				}
			}
			String ops [] = log.toString ().split ("\n");
			summary.append ("seed ").append (seed).append (": ").append (differ).append (" differing steps, first ").append (first)
				.append (first >= 0 ? " (" + ops [first + 1] + ")\n  recorded: " + recorded.get (first) + "\n  this run: " + lines.get (first) : "").append ('\n');
			System.err.println ("heartbeat seed " + seed + " done");
		}
		Files.write (out.resolve ("summary.txt"), summary.toString ().getBytes (StandardCharsets.UTF_8));
		shell.dispose ();
	}
}
