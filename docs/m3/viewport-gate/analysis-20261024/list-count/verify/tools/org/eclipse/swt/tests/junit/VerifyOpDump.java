package org.eclipse.swt.tests.junit;

import java.util.Random;

/* Verifier tool: the op of every step of a seeded List script (count = countOp, random = randomOp), no display needed. */
public class VerifyOpDump {
	public static void main (String [] args) {
		boolean count = args [0].equals ("count");
		for (int seed = 0; seed < 10; seed++) {
			Random random = new Random (0x1157L + seed);
			StringBuilder log = new StringBuilder ("seed " + seed + "\n");
			for (int step = 0; step < Test_org_eclipse_swt_widgets_List_ViewportDifferential.STEPS; step++) {
				if (count) Test_org_eclipse_swt_widgets_List_ViewportDifferential.countOp (random, log); else Test_org_eclipse_swt_widgets_List_ViewportDifferential.randomOp (random, log);
				String [] lines = log.toString ().split ("\n");
				System.out.println (seed + "\t" + step + "\t" + lines [lines.length - 1]);
			}
		}
	}
}
