import java.util.Random;

/* Replica of Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.randomOp (worktree 5bf82c5d38, lines 287-317): op names per seed and step. */
public class OpGen {
	static final int ROWS = 300;
	static final String [] KEYS = {"UP", "DOWN", "PRIOR", "NEXT", "HOME", "END", "LEFT", "RIGHT", "F4", "ESCAPE", "RETURN"};

	static String next (Random random) {
		int i = random.nextInt (ROWS + 3) - 1, j = i + random.nextInt (12), k = random.nextInt (ROWS + 3) - 1;
		int kind = random.nextInt (40);
		return switch (kind) {
			case 0 -> "add " + random.nextInt (1000);
			case 1 -> "addAt " + i + " " + random.nextInt (1000);
			case 2 -> "remove " + i;
			case 3 -> "removeRange " + i + " " + j;
			case 4 -> random.nextInt (10) == 0 ? "removeAll" : "removeString " + i;
			case 5 -> random.nextInt (8) == 0 ? "setItems " + random.nextInt (400) : "setItem " + i + " " + random.nextInt (1000);
			case 6, 7 -> "select " + i;
			case 8 -> "deselect " + i;
			case 9 -> random.nextInt (3) == 0 ? "deselectAll" : "selectionIndex";
			case 10 -> "setText " + i + " " + random.nextInt (4);
			case 11 -> "read " + i + " " + k;
			case 12 -> "visible " + (3 + random.nextInt (25));
			case 13 -> "drop " + random.nextBoolean ();
			case 14, 15, 16, 17 -> "key " + KEYS [random.nextInt (KEYS.length)];
			case 18 -> "altdown";
			case 19, 20 -> "type " + "abcdeABz1 \b".charAt (random.nextInt (11));
			case 21 -> "wheel " + new int [] {-120, 120, -240, 360} [random.nextInt (4)];
			case 22, 23 -> "listwheel " + new int [] {-120, 120, -360, 240} [random.nextInt (4)];
			case 24 -> "vscroll " + random.nextInt (6);
			case 25 -> "thumb " + random.nextInt (ROWS + 20);
			case 26, 27 -> "hover " + random.nextInt (20);
			case 28 -> "click " + random.nextInt (20);
			case 29, 30, 31 -> "drop true";
			case 32 -> "addEmpty " + i;
			case 33 -> "indexOfEmpty " + (random.nextInt (8) - 2);
			default -> "selectionIndex";
		};
	}

	public static void main (String [] args) {
		int seeds = Integer.parseInt (args [0]), steps = Integer.parseInt (args [1]);
		for (int seed = 0; seed < seeds; seed++) {
			Random random = new Random (0xC0B0L + seed);
			for (int step = 0; step < steps; step++) {
				String op = next (random).replace ("\b", "\b");
				System.out.println (seed + "\t" + step + "\t" + op);
			}
		}
	}
}
