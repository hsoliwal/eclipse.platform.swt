package org.eclipse.swt.tests.junit;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.*;

/**
 * Verifier oracle (scratch): replays one seed of a Table differential variant as the harness does (Scene.reset, the
 * seeded op generator, ViewportDifferential.line), then the end-of-seed pixel hash (captured until two agree, as
 * ViewportDifferential.stable), then the text of every row from the top row (after the capture).
 * Usage: java -cp <swt jar>;<tests>;<junit console>;<this> org.eclipse.swt.tests.junit.VisibleRowsOracle variant seed steps from(-1 = top row) rows out
 */
public class VisibleRowsOracle {
	public static void main (String [] args) throws Exception {
		int variant = Integer.parseInt (args [0]), seed = Integer.parseInt (args [1]), steps = Integer.parseInt (args [2]), from = Integer.parseInt (args [3]), rows = Integer.parseInt (args [4]);
		Display display = Display.getDefault ();
		ViewportDifferential<Test_org_eclipse_swt_widgets_Table_ViewportDifferential.Scene> harness = Test_org_eclipse_swt_widgets_Table_ViewportDifferential.harness (variant);
		Test_org_eclipse_swt_widgets_Table_ViewportDifferential.Scene scene = new Test_org_eclipse_swt_widgets_Table_ViewportDifferential.Scene (display, Test_org_eclipse_swt_widgets_Table_ViewportDifferential.VARIANTS [variant]);
		List<String> out = new ArrayList<> ();
		out.add ("# verify-oracle " + ViewportDifferential.reference ("Table") + ", SWT " + org.eclipse.swt.SWT.getVersion () + ", variant " + variant + " seed " + seed);
		try {
			scene.reset ();
			Random random = new Random (0x7AB1E5EEDL + 31L * variant + seed);
			StringBuilder log = new StringBuilder ();
			for (int step = 0; step < steps; step++) {
				out.add (step + "\t" + harness.line (scene, Test_org_eclipse_swt_widgets_Table_ViewportDifferential.next (random, log)));
			}
			ImageData previous = scene.pixels ();
			for (int attempt = 0; attempt < 5; attempt++) {
				ImageData current = scene.pixels ();
				if (Arrays.equals (previous.data, current.data)) break;
				previous = current;
			}
			out.add ("pixels " + ViewportDifferential.hash (previous));
			Table table = scene.table;
			int top = table.getTopIndex (), count = table.getItemCount (), first = from < 0 ? top : from;
			out.add ("top " + top + " count " + count);
			for (int i = first; i < Math.min (count, first + rows); i++) {
				out.add ("row " + i + "\t" + table.getItem (i).getText ());
			}
		} finally {
			scene.dispose ();
			Files.write (Paths.get (args [5]), out, StandardCharsets.UTF_8);
		}
	}
}
