import java.util.Random;

/* Replays the op generators of Test_org_eclipse_swt_widgets_List_ViewportDifferential (seed base 0x1157): random, count, resize, zoom. */
public class OpLogAll {
	static final int ROWS = 300;
	static final String [] KEYS = {"UP", "DOWN", "PRIOR", "NEXT", "HOME", "END", "SPACE"};
	static final String [] MODIFIERS = {"none", "none", "shift", "ctrl", "ctrlshift"};
	static final int [] FONT_HEIGHTS = {7, 9, 12, 16};

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

	static String countOp (Random random) {
		if (random.nextInt (4) != 0) return randomOp (random);
		return "setItemCount " + random.nextInt (ROWS + 60);
	}

	static String resizeOp (Random random) {
		int i = random.nextInt (ROWS + 3) - 1;
		int kind = random.nextInt (16);
		return switch (kind) {
			case 0, 1 -> "setTopIndex " + i;
			case 2 -> "vscroll " + random.nextInt (7);
			case 3 -> "wheel " + new int [] {-120, 120, -360} [random.nextInt (3)];
			case 4, 5 -> "resize " + (40 + random.nextInt (420)) + " " + (120 + random.nextInt (300));
			case 6, 7 -> "font " + FONT_HEIGHTS [random.nextInt (FONT_HEIGHTS.length)];
			case 8, 9 -> "font " + FONT_HEIGHTS [random.nextInt (FONT_HEIGHTS.length)];
			case 10 -> "select " + i;
			case 11 -> "showSelection";
			case 12 -> "key " + KEYS [random.nextInt (KEYS.length)] + " none";
			case 13 -> "add " + random.nextInt (1000);
			case 14 -> "remove " + i;
			default -> "getTop";
		};
	}

	static String zoomOp (Random random, int step) {
		int i = random.nextInt (ROWS + 3) - 1;
		if (step == 10) return "zoom " + (random.nextBoolean () ? 120 : 144);
		if (step == 23) return "zoom 96";
		return switch (random.nextInt (step > 10 ? 8 : 5)) {
			case 0 -> "setTopIndex " + i;
			case 1 -> "select " + i;
			case 2 -> "wheel " + new int [] {-120, 120, -360} [random.nextInt (3)];
			case 3 -> "vscroll " + random.nextInt (7);
			case 4 -> "showSelection";
			case 5 -> "resize " + (40 + random.nextInt (420)) + " " + (120 + random.nextInt (300));
			case 6 -> "key " + KEYS [random.nextInt (KEYS.length)] + " none";
			default -> "add " + random.nextInt (1000);
		};
	}

	/* args: kind (random|count|resize|zoom) seeds steps */
	public static void main (String [] args) {
		String kind = args [0];
		int seeds = Integer.parseInt (args [1]), steps = Integer.parseInt (args [2]);
		for (int seed = 0; seed < seeds; seed++) {
			Random random = new Random (0x1157L + seed);
			for (int step = 0; step < steps; step++) {
				String op = switch (kind) {
					case "count" -> countOp (random);
					case "resize" -> resizeOp (random);
					case "zoom" -> zoomOp (random, step);
					default -> randomOp (random);
				};
				System.out.println (seed + "\t" + step + "\t" + op);
			}
		}
	}
}
