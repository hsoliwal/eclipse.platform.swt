import java.util.Random;

/* Prints the op log of Test_org_eclipse_swt_widgets_Table_ViewportDifferential.next for one variant (API script):
 * seed base 0x7AB1E5EEDL + 31L * variant, draws in the order of next (Random): kind, fraction, span, sign, delta, rare. */
public class TableOps {
	public static void main (String [] args) {
		int variant = Integer.parseInt (args [0]), seeds = Integer.parseInt (args [1]), steps = Integer.parseInt (args [2]);
		long base = 0x7AB1E5EEDL + 31L * variant;
		StringBuilder out = new StringBuilder ();
		for (int seed = 0; seed < seeds; seed++) {
			Random random = new Random (base + seed);
			for (int step = 0; step < steps; step++) {
				int kind = random.nextInt (25), fraction = random.nextInt (1 << 20), span = random.nextInt (6);
				int delta = (random.nextBoolean () ? 1 : -1) * random.nextInt (40);
				boolean rare = random.nextInt (4) == 0;
				out.append (seed).append ('\t').append (step).append ('\t').append ("op ").append (kind).append (" at ").append (fraction)
					.append (" span ").append (span).append (" delta ").append (delta).append (rare ? " rare" : "").append ('\n');
			}
		}
		System.out.print (out);
	}
}
