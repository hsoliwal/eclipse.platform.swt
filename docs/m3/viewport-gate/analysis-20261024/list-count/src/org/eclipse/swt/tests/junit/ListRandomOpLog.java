package org.eclipse.swt.tests.junit;
import java.util.Random;

/* Prints the op log of the list-count script (countOp, seedBase 0x1157) per seed: "seed step op". No widgets. */
public class ListRandomOpLog {
	public static void main (String [] args) {
		for (int seed = 0; seed < 10; seed++) {
			Random random = new Random (0x1157L + seed);
			StringBuilder log = new StringBuilder ("seed " + seed + "\n");
			for (int step = 0; step < 150; step++) {
				int before = log.length ();
				Test_org_eclipse_swt_widgets_List_ViewportDifferential.randomOp (random, log);
				System.out.println (seed + "\t" + step + "\t" + log.substring (before).trim ());
			}
		}
	}
}
