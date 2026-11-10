import java.util.Random;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.internal.win32.RECT;
import org.eclipse.swt.widgets.*;

/**
 * Official-SWT matrix for Table_Splice.spliceAnswersLikeTheItemApi (issue 6): the top index of a hidden Table under the
 * item API. Uses only API and OS messages present in both jars, so the same class runs on the official 3.135.0 jar and
 * on the candidate jar.
 * Part S replays the test's own random scenarios (seed 0x5B1CE, 60 + 20 visible + 10 hidden) and prints the hidden ones
 * step by step. Part P measures the primitives: one removal or one insert run at a position relative to the top, then
 * setTopIndex, then show.
 */
public class HiddenTopMatrix {
	static Display display;
	static Shell shell;

	public static void main (String [] args) {
		display = new Display ();
		System.out.println ("MATRIX swt " + SWT.getVersion () + " " + SWT.getPlatform () + " mode=" + System.getProperty ("swt.widget.mode", "-"));
		shell = new Shell (display);
		shell.setBounds (20, 20, 640, 420);
		shell.open ();
		scenarios ();
		primitives ();
		display.dispose ();
	}

	static void settle () {
		for (int i = 0; i < 3; i++) while (display.readAndDispatch ()) { }
	}

	static Table table (int style, int rows) {
		Table table = new Table (shell, style | SWT.BORDER);
		table.setBounds (320, 5, 300, 380);
		for (int i = 0; i < rows; i++) new TableItem (table, SWT.NONE).setText ("row " + i);
		return table;
	}

	static int top (Table table) {
		return table.getTopIndex ();
	}

	static String raw (Table table) {
		RECT rect = new RECT ();
		rect.left = OS.LVIR_BOUNDS;
		long ok = OS.SendMessage (table.handle, OS.LVM_GETITEMRECT, 0, rect);
		long page = OS.SendMessage (table.handle, OS.LVM_GETCOUNTPERPAGE, 0, 0);
		return "rect0=" + (ok != 0 ? rect.top + "+" + (rect.bottom - rect.top) : "-") + " page=" + page;
	}

	/* the test's generator, call for call; the item-API run on the reference only */
	static void scenarios () {
		Random random = new Random (0x5B1CE);
		for (int i = 0; i < 60; i++) scenario (i, random, SWT.MULTI | SWT.FULL_SELECTION, false);
		for (int i = 0; i < 20; i++) scenario (60 + i, random, SWT.SINGLE, false);
		for (int i = 0; i < 10; i++) scenario (80 + i, random, SWT.MULTI, true);
	}

	static void scenario (int n, Random random, int style, boolean hidden) {
		int rows = 20 + random.nextInt (60);
		Table table = table (style, rows);
		table.setVisible (!hidden);
		int selected = random.nextInt (4);
		int [] selection = new int [selected];
		for (int i = 0; i < selected; i++) selection [i] = random.nextInt (rows);
		int top = random.nextInt (rows);
		if (selected > 0) table.setSelection (selection);
		if (selected > 1 && (style & SWT.MULTI) != 0) table.select (selection [1]);
		table.setTopIndex (top);
		display.readAndDispatch ();
		int asked = top;
		top = table.getTopIndex ();
		int at = random.nextInt (rows);
		int removed = random.nextInt (3) == 0 ? 0 : 1 + random.nextInt (Math.max (1, Math.min (6, rows - at - 1)));
		removed = Math.min (removed, rows - at - 1);
		int insertCount = removed == 0 || random.nextBoolean () ? 1 + random.nextInt (5) : 0;
		StringBuilder line = new StringBuilder ();
		line.append ("S ").append (n).append (hidden ? " hidden" : " shown").append (" rows ").append (rows).append (" asked ").append (asked)
				.append (" top ").append (top).append (" at ").append (at).append (" -").append (removed).append (" +").append (insertCount).append (" |");
		int anchorRow = top;
		if (removed > 0) table.remove (at, at + removed - 1);
		line.append (" afterRemove ").append (top (table));
		if (top >= at && top < at + removed) anchorRow = at;
		else if (top >= at + removed) anchorRow = top - removed;
		for (int k = 0; k < insertCount; k++) {
			new TableItem (table, SWT.NONE, at + k).setText ("new " + k);
			line.append (" ins").append (k).append (' ').append (top (table));
		}
		if (anchorRow >= at && insertCount > 0) anchorRow += insertCount;
		int count = table.getItemCount ();
		int target = Math.min (anchorRow, count - 1);
		if (count > 0) table.setTopIndex (target);
		line.append (" set ").append (target).append (" -> ").append (top (table)).append (" (").append (raw (table)).append (")");
		if (hidden) {
			table.setVisible (true);
			settle ();
			line.append (" shown ").append (top (table)).append (" count ").append (count);
		}
		if (hidden) System.out.println (line);
		table.dispose ();
	}

	/* one run at a position relative to the top of a hidden Table, then setTopIndex (target), then show */
	static void primitive (int rows, int top, int at, int removed, int inserted, int target) {
		Table table = table (SWT.MULTI, rows);
		table.setVisible (false);
		table.setTopIndex (top);
		display.readAndDispatch ();
		int t0 = top (table);
		if (removed > 0) table.remove (at, at + removed - 1);
		int t1 = top (table);
		for (int k = 0; k < inserted; k++) new TableItem (table, SWT.NONE, at + k).setText ("new " + k);
		int t2 = top (table);
		String r = raw (table);
		int t3 = -9;
		if (target >= 0) {
			table.setTopIndex (target);
			t3 = top (table);
		}
		table.setVisible (true);
		settle ();
		int t4 = top (table);
		System.out.println ("P rows " + rows + " top " + top + " at " + at + " -" + removed + " +" + inserted + " target " + target + " | t0 " + t0 + " remove " + t1
				+ " insert " + t2 + " set " + t3 + " shown " + t4 + " count " + table.getItemCount () + " (" + r + ")");
		table.dispose ();
	}

	static void primitives () {
		int [] tops = {0, 3, 12, 25, 40, 43, 50, 61};
		int [] rel = {-8, -3, -1, 0, 1, 4};
		for (int rows : new int [] {62, 30}) {
			for (int top : tops) {
				if (top >= rows) continue;
				/* setTopIndex alone while hidden */
				primitive (rows, top, 0, 0, 0, -1);
				for (int d : rel) {
					int at = top + d;
					if (at < 0 || at >= rows) continue;
					int k = Math.min (3, rows - at - 1);
					if (k > 0) primitive (rows, top, at, k, 0, -1);
					primitive (rows, top, at, 0, 2, -1);
					if (k > 0) primitive (rows, top, at, k, 2, -1);
				}
				/* setTopIndex after an edit above the top: up, down, same */
				if (top >= 4) {
					primitive (rows, top, 1, 2, 0, Math.max (0, top - 5));
					primitive (rows, top, 1, 2, 0, Math.min (rows - 3, top + 2));
					primitive (rows, top, 1, 0, 2, Math.max (0, top - 5));
					primitive (rows, top, 1, 0, 2, Math.min (rows + 1, top + 4));
				}
				/* setTopIndex after an edit below the top */
				if (top + 3 < rows - 1) {
					primitive (rows, top, top + 3, 1, 0, Math.max (0, top - 2));
					primitive (rows, top, top + 3, 0, 1, Math.min (rows, top + 2));
				}
			}
		}
		/* setTopIndex with no edit while hidden (does a hidden table scroll at all?) */
		for (int top : new int [] {5, 30}) {
			for (int target : new int [] {0, 10, 40, 61}) {
				primitive (62, top, 0, 0, 0, target);
			}
		}
	}
}
