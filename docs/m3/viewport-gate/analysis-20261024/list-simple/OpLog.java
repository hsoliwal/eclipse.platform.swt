import java.util.Random;

/* Replays the random draws of Test_org_eclipse_swt_widgets_List_ViewportDifferential.randomOp (seed base 0x1157). */
public class OpLog {
	static final int ROWS = 300;
	static final String [] KEYS = {"UP", "DOWN", "PRIOR", "NEXT", "HOME", "END", "SPACE"};
	static final String [] MODIFIERS = {"none", "none", "shift", "ctrl", "ctrlshift"};

	static String randomOp (Random random) {
		int i = random.nextInt (ROWS + 3) - 1, j = i + random.nextInt (30), k = random.nextInt (ROWS + 3) - 1;
		int kind = random.nextInt (44);
		return switch (kind) {
			case 0 -> "add " + random.nextInt (1000);
			case 1 -> "addAt " + i + " " + random.nextInt (1000);
			case 2, 3 -> "remove " + i;
			case 4 -> "removeRange " + i + " " + j;
			case 5 -> "removeIndices " + i + " " + k + " " + j;
			case 6 -> random.nextInt (8) == 0 ? "removeAll" : "setItem " + i + " " + random.nextInt (1000);
			case 7 -> random.nextInt (6) == 0 ? "setItems " + random.nextInt (400) : "setTopIndex " + k;
			case 8, 9 -> "select " + i;
			case 10 -> "selectRange " + i + " " + j;
			case 11 -> "selectIndices " + i + " " + k;
			case 12 -> "deselect " + i;
			case 13 -> "deselectRange " + i + " " + j;
			case 14 -> "deselectIndices " + i + " " + k;
			case 15 -> random.nextInt (3) == 0 ? "selectAll" : "deselectAll";
			case 16, 17 -> "setSelection " + i;
			case 18 -> "setSelectionRange " + i + " " + j;
			case 19 -> "setSelectionIndices " + k + " " + i;
			case 20 -> "setSelectionStrings " + i + " " + k;
			case 21, 22 -> "setTopIndex " + i;
			case 23 -> "showSelection";
			case 24 -> "read " + i;
			case 25 -> "indexOf " + i + " " + k;
			case 26 -> "selectionIndex";
			case 27 -> "getTop";
			case 28 -> "setEmpty " + i;
			case 29 -> "addEmpty " + i;
			case 30 -> "addNul " + i;
			case 31 -> "menu";
			case 32, 33, 42 -> "key " + KEYS [random.nextInt (KEYS.length)] + " " + MODIFIERS [random.nextInt (MODIFIERS.length)];
			case 34 -> "type " + "rnsxR".charAt (random.nextInt (5));
			case 35, 36, 37, 43 -> "click " + random.nextInt (25) + " " + MODIFIERS [random.nextInt (MODIFIERS.length)];
			case 38 -> "drag " + random.nextInt (25) + " " + (random.nextBoolean () ? "down" : "up") + " " + (1 + random.nextInt (40));
			case 39 -> "vscroll " + random.nextInt (7);
			case 40 -> "wheel " + new int [] {-120, 120, -240, -360} [random.nextInt (4)];
			case 41 -> "dblclick " + random.nextInt (25);
			default -> "getTop";
		};
	}

	public static void main (String [] args) {
		int seed = Integer.parseInt (args [0]), steps = args.length > 1 ? Integer.parseInt (args [1]) : 150;
		Random random = new Random (0x1157L + seed);
		for (int step = 0; step < steps; step++) System.out.println (step + ": " + randomOp (random));
	}
}
