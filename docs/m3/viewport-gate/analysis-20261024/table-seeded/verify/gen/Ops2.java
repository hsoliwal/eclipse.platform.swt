import java.util.*;
/* Replays Test_org_eclipse_swt_widgets_Table_ViewportDifferential.next for one variant: variant seed op-log lines (TSV). */
public class Ops2 {
	public static void main (String [] args) {
		int variant = Integer.parseInt (args [0]), seeds = Integer.parseInt (args [1]), steps = Integer.parseInt (args [2]);
		for (int seed = 0; seed < seeds; seed++) {
			Random random = new Random (0x7AB1E5EEDL + 31L * variant + seed);
			for (int step = 0; step < steps; step++) {
				int kind = random.nextInt (25), fraction = random.nextInt (1 << 20), span = random.nextInt (6);
				int delta = (random.nextBoolean () ? 1 : -1) * random.nextInt (40);
				boolean rare = random.nextInt (4) == 0;
				System.out.println (variant + "\t" + seed + "\t" + step + "\top " + kind + " at " + fraction + " span " + span + " delta " + delta + (rare ? " rare" : ""));
			}
		}
	}
}
