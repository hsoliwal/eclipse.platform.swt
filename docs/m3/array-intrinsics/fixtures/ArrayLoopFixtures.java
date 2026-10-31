/*
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others
 */
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.function.Supplier;

/**
 * ARRAY_LOOP_INTRINSICS v1 fixture: one method per admitted form and one per skip reason, each marked with the
 * decision the recipe must take. prove_array_intrinsics.sh runs this class before and after the recipe and diffs the
 * traces. The DISCRIMINATOR lines run the naive replacement of every skipped form and must all report DIFFERS.
 */
public class ArrayLoopFixtures {
	static final int THREE = 3;
	static final int BIG = 1000;

	static int[] copyByLength(int[] src) {
		int[] dst = new int[src.length];
		// expect: REWRITE C1
		for (int i = 0; i < src.length; i++) {
			dst[i] = src[i];
		}
		return dst;
	}

	static String[] copyFinalFresh(String seed) {
		final int candidates = 3;
		String[] names = new String[candidates], mapped = new String[candidates];
		// expect: SKIP SKIP_VALUE_INDEX
		for (int i = 0; i < candidates; i++) {
			mapped[i] = seed + i;
		}
		String[] fileNames = new String[candidates];
		// expect: REWRITE C1
		for (int i = 0; i < candidates; i++) {
			fileNames[i] = mapped[i];
		}
		return fileNames;
	}

	static short[] fillFull(short[] blk) {
		// expect: REWRITE F1
		for (int j = 0; j < blk.length; j++) {
			blk[j] = 0;
		}
		return blk;
	}

	static byte[] fillFresh(int count) {
		byte[] padding = new byte[count];
		// expect: REWRITE F2
		for (int i = 0; i < count; i++) {
			padding[i] = (byte) 255;
		}
		return padding;
	}

	static long[] fillTernary(long[] words, boolean value) {
		// expect: REWRITE F1
		for (int i = 0; i < words.length; i++) {
			words[i] = value ? -1L : 0L;
		}
		return words;
	}

	static char[] fillCharTernary(char[] cs, boolean blank) {
		// expect: REWRITE F1
		for (int i = 0; i < cs.length; ++i)
			cs[i] = blank ? ' ' : 'x';
		return cs;
	}

	static Object[] fillNull(Object[] slots) {
		// expect: REWRITE F1
		for (int i = 0; i < slots.length; i += 1) {
			slots[i] = null;
		}
		return slots;
	}

	static float[] copyWidening(int[] dashes) {
		float[] lineDashes = new float[dashes.length];
		// expect: SKIP SKIP_TYPE_MISMATCH
		for (int i = 0; i < dashes.length; i++) {
			lineDashes[i] = dashes[i];
		}
		return lineDashes;
	}

	static int[] shiftUp(int[] lines, int by) {
		// expect: SKIP SKIP_SAME_ARRAY
		for (int i = 0; i < lines.length - by; i++) {
			lines[i + by] = lines[i];
		}
		return lines;
	}

	static byte[] fillFrom(byte[] alphas, int from) {
		// expect: SKIP SKIP_START_NONZERO
		for (int j = from; j < alphas.length; j++) {
			alphas[j] = (byte) 255;
		}
		return alphas;
	}

	static Object[] fillObjects(int size) {
		Object[] objects = new Object[size];
		// expect: SKIP SKIP_VALUE_SIDE_EFFECT
		for (int i = 0; i < size; i++) {
			objects[i] = new Object();
		}
		return objects;
	}

	static int[] copyCount(int[] colors, int colorsLength) {
		int[] gradient = new int[colorsLength];
		// expect: SKIP SKIP_SOURCE_UNPROVEN
		for (int i = 0; i < colorsLength; i++) {
			gradient[i] = colors[i];
		}
		return gradient;
	}

	static int[] fillConstantBound(int[] custom) {
		// expect: SKIP SKIP_BOUND_UNPROVEN
		for (int i = 0; i < THREE; i++) {
			custom[i] = 0x00FFFFFF;
		}
		return custom;
	}

	static int[] fillLe(int[] le, int last) {
		// expect: SKIP SKIP_LE_BOUND
		for (int i = 0; i <= last; i++) {
			le[i] = 7;
		}
		return le;
	}

	static int[] fillField(int[] ids) {
		// expect: SKIP SKIP_VALUE_FIELD
		for (int i = 0; i < ids.length; i++) {
			ids[i] = Holder.UNKNOWN;
		}
		return ids;
	}

	static int[] copyOffset(int[] data, int[] row, int offset) {
		// expect: SKIP SKIP_OFFSET
		for (int i = 0; i < row.length; i++) {
			data[offset + i] = row[i];
		}
		return data;
	}

	static int[] copyNotFresh(int[] target, int[] source) {
		// expect: SKIP SKIP_DEST_NOT_FRESH
		for (int i = 0; i < source.length; i++) {
			target[i] = source[i];
		}
		return target;
	}

	static long[] copyAmbiguous(long[] buf) {
		long[] copy = new long[buf.length];
		// expect: SKIP SKIP_TYPE_UNRESOLVED
		for (int i = 0; i < buf.length; i++) {
			copy[i] = buf[i];
		}
		return copy;
	}

	static int bufLength(int[] buf) {
		return buf.length;
	}

	static int[] fillIndex(int[] seq) {
		// expect: SKIP SKIP_VALUE_INDEX
		for (int i = 0; i < seq.length; i++) {
			seq[i] = i + 1;
		}
		return seq;
	}

	static int[] fillShape(int[] vals, int base) {
		// expect: SKIP SKIP_VALUE_SHAPE
		for (int i = 0; i < vals.length; i++) {
			vals[i] = base + 1;
		}
		return vals;
	}

	static Integer[] fillBoxes(Integer[] boxes, int big) {
		// expect: SKIP SKIP_VALUE_OBJECT
		for (int i = 0; i < boxes.length; i++) {
			boxes[i] = big;
		}
		return boxes;
	}

	static int[] fillComment(int[] commented) {
		// expect: SKIP SKIP_COMMENT_IN_LOOP
		for (int i = 0; i < commented.length; i++) {
			commented[i] = 0; // the note stays with its loop
		}
		return commented;
	}

	static int[] fillTwoStatements(int[] two, int[] counter) {
		// expect: SKIP SKIP_BODY_SHAPE
		for (int i = 0; i < two.length; i++) {
			two[i] = 0;
			counter[0]++;
		}
		return two;
	}

	static int[] fillLayout(int[] lay) {
		// expect: SKIP SKIP_LAYOUT
		int k = 0; for (int i = 0; i < lay.length; i++) lay[i] = 1;
		return lay;
	}

	static final class Holder {
		static final int UNKNOWN = -1;
	}

	static String render(Object r) {
		if (r instanceof int[] a) return Arrays.toString(a);
		if (r instanceof short[] a) return Arrays.toString(a);
		if (r instanceof byte[] a) return Arrays.toString(a);
		if (r instanceof long[] a) return Arrays.toString(a);
		if (r instanceof char[] a) return Arrays.toString(a);
		if (r instanceof float[] a) return Arrays.toString(a);
		if (r instanceof Object[] a) {
			IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
			Arrays.stream(a).filter(x -> x != null).forEach(x -> seen.put(x, Boolean.TRUE));
			return a.getClass().getSimpleName() + " len=" + a.length + " distinct=" + seen.size()
				+ (a instanceof String[] || a instanceof Integer[] ? " " + Arrays.toString(a) : "");
		}
		return String.valueOf(r);
	}

	static void show(String name, Supplier<Object> f) {
		Object r;
		try {
			r = render(f.get());
		} catch (RuntimeException e) {
			r = "EXC " + e.getClass().getSimpleName();
		}
		System.out.println(name + ": " + r);
	}

	static void report(String reason, Supplier<Object> loop, Supplier<Object> naive) {
		String a = outcome(loop), b = outcome(naive);
		System.out.println("DISCRIMINATOR " + reason + (a.equals(b) ? " SAME" : " DIFFERS") + " loop=" + a + " naive=" + b);
	}

	static String outcome(Supplier<Object> f) {
		try {
			return render(f.get());
		} catch (RuntimeException e) {
			return "EXC " + e.getClass().getSimpleName();
		}
	}

	public static void main(String[] args) {
		show("copyByLength 3", () -> copyByLength(new int[] {1, 2, 3}));
		show("copyByLength 0", () -> copyByLength(new int[0]));
		show("copyByLength null", () -> copyByLength(null));
		show("copyFinalFresh", () -> copyFinalFresh("s"));
		show("fillFull 3", () -> fillFull(new short[] {1, 2, 3}));
		show("fillFull 0", () -> fillFull(new short[0]));
		show("fillFull null", () -> fillFull(null));
		show("fillFresh 3", () -> fillFresh(3));
		show("fillFresh 0", () -> fillFresh(0));
		show("fillFresh -1", () -> fillFresh(-1));
		show("fillTernary true", () -> fillTernary(new long[2], true));
		show("fillTernary false", () -> fillTernary(new long[] {5, 6}, false));
		show("fillCharTernary true", () -> fillCharTernary(new char[2], true));
		show("fillCharTernary false", () -> fillCharTernary(new char[2], false));
		show("fillNull", () -> fillNull(new Object[] {"a", "b"}));
		show("fillNull null", () -> fillNull(null));
		show("copyWidening", () -> copyWidening(new int[] {1, 2}));
		show("shiftUp", () -> shiftUp(new int[] {1, 2, 3, 4}, 1));
		show("fillFrom", () -> fillFrom(new byte[3], 1));
		show("fillObjects", () -> fillObjects(3));
		show("copyCount", () -> copyCount(new int[] {4, 5, 6}, 2));
		show("fillConstantBound", () -> fillConstantBound(new int[4]));
		show("fillLe", () -> fillLe(new int[3], 1));
		show("fillField", () -> fillField(new int[2]));
		show("copyOffset", () -> copyOffset(new int[4], new int[] {8, 9}, 1));
		show("copyNotFresh", () -> copyNotFresh(new int[2], new int[] {3, 4}));
		show("copyAmbiguous", () -> copyAmbiguous(new long[] {1, 2}));
		show("bufLength", () -> bufLength(new int[2]));
		show("fillIndex", () -> fillIndex(new int[3]));
		show("fillShape", () -> fillShape(new int[2], 4));
		show("fillBoxes", () -> fillBoxes(new Integer[3], BIG));
		show("fillComment", () -> fillComment(new int[2]));
		show("fillTwoStatements", () -> fillTwoStatements(new int[2], new int[1]));
		show("fillLayout", () -> fillLayout(new int[2]));
		discriminators();
	}

	/** The naive replacement of each skipped form, beside the loop: every line must report DIFFERS. */
	static void discriminators() {
		report("SKIP_SAME_ARRAY", () -> shiftUp(new int[] {1, 2, 3, 4}, 1), () -> {
			int[] a = {1, 2, 3, 4};
			System.arraycopy(a, 0, a, 1, a.length - 1);
			return a;
		});
		report("SKIP_TYPE_MISMATCH", () -> copyWidening(new int[] {1, 2}), () -> {
			Object out = new float[2];
			System.arraycopy(new int[] {1, 2}, 0, out, 0, 2);
			return out;
		});
		report("SKIP_START_NONZERO", () -> fillFrom(new byte[2], 3), () -> {
			byte[] a = new byte[2];
			Arrays.fill(a, 3, a.length, (byte) 255);
			return a;
		});
		report("SKIP_VALUE_SIDE_EFFECT", () -> fillObjects(3), () -> {
			Object[] o = new Object[3];
			Arrays.fill(o, new Object());
			return o;
		});
		report("SKIP_SOURCE_UNPROVEN", () -> copyCount(null, 0), () -> {
			int[] g = new int[0];
			System.arraycopy(null, 0, g, 0, 0);
			return g;
		});
		int[] partialLoop = new int[2], partialNaive = new int[2];
		report("SKIP_BOUND_UNPROVEN", () -> {
			try {
				fillConstantBound(partialLoop);
			} catch (ArrayIndexOutOfBoundsException e) {
				// the loop stored two elements before it failed
			}
			return partialLoop;
		}, () -> {
			try {
				Arrays.fill(partialNaive, 0, THREE, 0x00FFFFFF);
			} catch (ArrayIndexOutOfBoundsException e) {
				// Arrays.fill checks the range first and stores nothing
			}
			return partialNaive;
		});
		report("SKIP_LE_BOUND", () -> fillLe(new int[3], 1), () -> {
			int[] a = new int[3];
			Arrays.fill(a, 0, 1, 7);
			return a;
		});
		int[] offsetLoop = new int[3], offsetNaive = new int[3];
		report("SKIP_OFFSET", () -> {
			try {
				copyOffset(offsetLoop, new int[] {8, 9}, 2);
			} catch (ArrayIndexOutOfBoundsException e) {
				// the loop stored data[2] before it failed
			}
			return offsetLoop;
		}, () -> {
			try {
				System.arraycopy(new int[] {8, 9}, 0, offsetNaive, 2, 2);
			} catch (IndexOutOfBoundsException e) {
				// arraycopy checks the range first and stores nothing
			}
			return offsetNaive;
		});
		report("SKIP_DEST_NOT_FRESH", () -> copyNotFresh(null, new int[0]), () -> {
			System.arraycopy(new int[0], 0, null, 0, 0);
			return null;
		});
		report("SKIP_VALUE_OBJECT", () -> fillBoxes(new Integer[3], BIG), () -> {
			Integer[] b = new Integer[3];
			Arrays.fill(b, BIG);
			return b;
		});
		report("SKIP_VALUE_INDEX", () -> fillIndex(new int[3]), () -> {
			int[] a = new int[3];
			Arrays.fill(a, 1);
			return a;
		});
	}
}
