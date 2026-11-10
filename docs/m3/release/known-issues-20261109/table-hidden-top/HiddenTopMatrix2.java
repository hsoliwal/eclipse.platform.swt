import java.util.Random;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.internal.win32.SCROLLINFO;
import org.eclipse.swt.widgets.*;

/**
 * Second official-SWT matrix for issue 6: the hidden ListView scroll rule. Prints the vertical SCROLLINFO with every top
 * so the rule "a hidden LVM_SCROLL moves by clamp (nPos + dy, 0, nMax - nPage + 1) - nPos, nPos frozen while hidden" can
 * be checked. Q1: scrolled while visible, then hidden; Q2: successive hidden scrolls; Q3: rows removed below the top
 * row's index; Q4: the Table_Splice.hiddenTableAppliesOneWindowPatchWhenShown pattern with a hidden reference.
 */
public class HiddenTopMatrix2 {
	static Display display;
	static Shell shell;

	public static void main (String [] args) {
		display = new Display ();
		System.out.println ("MATRIX2 swt " + SWT.getVersion () + " " + SWT.getPlatform () + " mode=" + System.getProperty ("swt.widget.mode", "-"));
		shell = new Shell (display);
		shell.setBounds (20, 20, 640, 420);
		shell.open ();
		for (int tv : new int [] {0, 20, 43}) for (int x : new int [] {-10, -1, 5, 30}) q1 (tv, tv + x);
		q2 ();
		q3 (40, 5, 30);
		q3 (40, 35, 20);
		q3 (10, 0, 50);
		for (int tv : new int [] {20, 40}) {
			q4 (tv, 2, 3, 0);
			q4 (tv, 2, 0, 3);
			q4 (tv, 2, 3, 1);
			q4 (tv, tv + 2, 2, 0);
		}
		q5 (0x41DE, 60);
		display.dispose ();
	}

	static void settle () {
		for (int i = 0; i < 3; i++) while (display.readAndDispatch ()) { }
	}

	static Table table (int rows) {
		Table table = new Table (shell, SWT.MULTI | SWT.FULL_SELECTION | SWT.BORDER);
		table.setBounds (320, 5, 300, 380);
		for (int i = 0; i < rows; i++) new TableItem (table, SWT.NONE).setText ("row " + i);
		return table;
	}

	static String sb (Table table) {
		SCROLLINFO info = new SCROLLINFO ();
		info.cbSize = SCROLLINFO.sizeof;
		info.fMask = OS.SIF_ALL;
		boolean ok = OS.GetScrollInfo (table.handle, OS.SB_VERT, info);
		return ok ? "[sb " + info.nPos + " " + info.nMin + ".." + info.nMax + "/" + info.nPage + "]" : "[sb -]";
	}

	static String at (Table table) {
		return table.getTopIndex () + sb (table);
	}

	/* scrolled to tv while visible, then hidden, then setTopIndex (x) */
	static void q1 (int tv, int x) {
		Table table = table (62);
		settle ();
		table.setTopIndex (tv);
		settle ();
		String visible = at (table);
		table.setVisible (false);
		String hidden = at (table);
		if (x >= 0) table.setTopIndex (x);
		String set = at (table);
		table.setVisible (true);
		settle ();
		System.out.println ("Q1 rows 62 visibleTop " + tv + " target " + x + " | visible " + visible + " hidden " + hidden + " set " + set + " shown " + at (table));
		table.dispose ();
	}

	/* hidden from the start: successive setTopIndex calls */
	static void q2 () {
		Table table = table (62);
		settle ();
		table.setVisible (false);
		StringBuilder line = new StringBuilder ("Q2 rows 62 |");
		for (int x : new int [] {30, 40, 50, 60, 20, 61, 0, 45}) {
			table.setTopIndex (x);
			line.append (" set ").append (x).append (" -> ").append (at (table));
		}
		table.setVisible (true);
		settle ();
		line.append (" shown ").append (at (table));
		System.out.println (line);
		table.dispose ();
	}

	/* hidden at top, then `removed` rows at `at` */
	static void q3 (int top, int at, int removed) {
		Table table = table (62);
		settle ();
		table.setVisible (false);
		table.setTopIndex (top);
		String t0 = at (table);
		table.remove (at, at + removed - 1);
		String t1 = at (table);
		table.setTopIndex (Math.max (0, table.getItemCount () - 2));
		String t2 = at (table);
		table.setVisible (true);
		settle ();
		System.out.println ("Q3 rows 62 top " + top + " at " + at + " -" + removed + " | t0 " + t0 + " remove " + t1 + " setLast-1 " + t2 + " shown " + at (table) + " count " + table.getItemCount ());
		table.dispose ();
	}

	/* visible at tv, hidden, one run, then the anchored setTopIndex of the item-API reference */
	static void q4 (int tv, int at, int removed, int inserted) {
		Table table = table (62);
		settle ();
		table.setTopIndex (tv);
		settle ();
		table.setVisible (false);
		int top = table.getTopIndex ();
		if (removed > 0) table.remove (at, at + removed - 1);
		String t1 = at (table);
		for (int k = 0; k < inserted; k++) new TableItem (table, SWT.NONE, at + k).setText ("new " + k);
		String t2 = at (table);
		int anchorRow = top;
		if (top >= at && top < at + removed) anchorRow = at;
		else if (top >= at + removed) anchorRow = top - removed;
		if (anchorRow >= at && inserted > 0) anchorRow += inserted;
		table.setTopIndex (anchorRow);
		String t3 = at (table);
		table.setVisible (true);
		settle ();
		System.out.println ("Q4 rows 62 visibleTop " + tv + " at " + at + " -" + removed + " +" + inserted + " anchor " + anchorRow + " | remove " + t1 + " insert " + t2 + " set " + t3 + " shown " + at (table));
		table.dispose ();
	}

	/* Table_Splice.hiddenTableAppliesOneWindowPatchWhenShown with the reference hidden: 2000 rows, top 900, random runs */
	static void q5 (long seed, int deltas) {
		Table table = table (2000);
		table.setSelection (new int [] {100, 101, 1500});
		table.setTopIndex (900);
		settle ();
		table.setVisible (false);
		settle ();
		Random random = new Random (seed);
		StringBuilder line = new StringBuilder ("Q5 rows 2000 top 900 hidden " + at (table) + " |");
		for (int i = 0; i < deltas; i++) {
			int count = table.getItemCount ();
			int at = random.nextInt (count);
			int top = table.getTopIndex ();
			int removed = 0, inserted = 0;
			if (random.nextBoolean () || count < 200) {
				inserted = 1 + random.nextInt (3);
			} else {
				removed = Math.min (1 + random.nextInt (3), count - at);
				if (removed == count) continue;
			}
			int anchorRow = top;
			if (removed > 0) table.remove (at, at + removed - 1);
			if (top >= at && top < at + removed) anchorRow = at;
			else if (top >= at + removed) anchorRow = top - removed;
			for (int k = 0; k < inserted; k++) new TableItem (table, SWT.NONE, at + k).setText ("new");
			if (anchorRow >= at && inserted > 0) anchorRow += inserted;
			table.setTopIndex (Math.min (anchorRow, table.getItemCount () - 1));
			line.append (' ').append (at).append (removed > 0 ? "-" + removed : "+" + inserted).append (">").append (anchorRow).append ("=").append (table.getTopIndex ());
		}
		table.setVisible (true);
		settle ();
		line.append (" | shown ").append (at (table)).append (" count ").append (table.getItemCount ());
		System.out.println (line);
		table.dispose ();
	}
}
