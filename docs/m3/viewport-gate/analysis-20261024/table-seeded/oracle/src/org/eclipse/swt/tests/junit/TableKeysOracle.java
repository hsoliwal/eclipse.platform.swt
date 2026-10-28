package org.eclipse.swt.tests.junit;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

import org.eclipse.swt.widgets.*;

/**
 * Oracle (scratch, read-only for the fork): replays one seed of a Table differential variant exactly as the harness
 * does (Scene.reset, the seeded op generator, ViewportDifferential.line with the same probes) and appends the item
 * store of the Table after every step: the official jar's sparse keys (Table.keys/keyCount, R4_38 store), the fork's
 * original-mode TableStockItems, or the fork's default-mode VirtualItemStorage indices.
 * Usage: java -cp <swt jar>;<tests>;<junit console>;<this> org.eclipse.swt.tests.junit.TableKeysOracle variant seed steps out
 */
public class TableKeysOracle {
	public static void main (String [] args) throws Exception {
		int variant = Integer.parseInt (args [0]), seed = Integer.parseInt (args [1]), steps = Integer.parseInt (args [2]);
		Display display = Display.getDefault ();
		ViewportDifferential<Test_org_eclipse_swt_widgets_Table_ViewportDifferential.Scene> harness = Test_org_eclipse_swt_widgets_Table_ViewportDifferential.harness (variant);
		Test_org_eclipse_swt_widgets_Table_ViewportDifferential.Scene scene = new Test_org_eclipse_swt_widgets_Table_ViewportDifferential.Scene (display, Test_org_eclipse_swt_widgets_Table_ViewportDifferential.VARIANTS [variant]);
		List<String> out = new ArrayList<> ();
		out.add ("# oracle " + ViewportDifferential.reference ("Table") + ", SWT " + org.eclipse.swt.SWT.getVersion () + ", variant " + variant + " seed " + seed);
		try {
			scene.reset ();
			out.add ("start | " + store (scene.table));
			Random random = new Random (0x7AB1E5EEDL + 31L * variant + seed);
			StringBuilder log = new StringBuilder ();
			for (int step = 0; step < steps; step++) {
				ViewportDifferential.Op<Test_org_eclipse_swt_widgets_Table_ViewportDifferential.Scene> op = Test_org_eclipse_swt_widgets_Table_ViewportDifferential.next (random, log);
				String line = harness.line (scene, op);
				out.add (step + "\t" + line + "\t" + store (scene.table));
			}
		} finally {
			scene.dispose ();
			Files.write (Paths.get (args [3]), out, StandardCharsets.UTF_8);
			out.forEach (System.out::println);
		}
	}

	static Field field (Class<?> type, String name) {
		for (Class<?> c = type; c != null; c = c.getSuperclass ()) {
			try {
				Field f = c.getDeclaredField (name);
				f.setAccessible (true);
				return f;
			} catch (NoSuchFieldException e) {
				/* next */
			}
		}
		return null;
	}

	/* keys >= rows are the stale keys of the R4_38 store */
	static String store (Table table) {
		try {
			Object owner = table;
			String keysName = "keys", countName = "keyCount", kind = "official";
			Field stock = field (Table.class, "stockItems"), virtual = field (Table.class, "virtualItems");
			if (stock != null && stock.get (table) != null) {
				owner = stock.get (table);
				kind = "TableStockItems";
			} else if (virtual != null && virtual.get (table) != null) {
				owner = virtual.get (table);
				keysName = "indices";
				countName = "size";
				kind = "VirtualItemStorage";
			}
			int [] keys = (int []) field (owner.getClass (), keysName).get (owner);
			int rows = table.getItemCount ();
			if (keys == null) return kind + " dense items " + ((Object []) field (owner.getClass (), "items").get (owner)).length;
			int n = field (owner.getClass (), countName).getInt (owner);
			int [] held = Arrays.copyOf (keys, n);
			int [] stale = Arrays.stream (held).filter (k -> k >= rows).toArray ();
			return kind + " keys " + n + " stale>=rows " + Arrays.toString (stale) + " " + Arrays.toString (held);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return "store " + e;
		}
	}
}
