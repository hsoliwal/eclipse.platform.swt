import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.widgets.*;

/**
 * Oracle (default package; official 3.135.0 jar and the M3 jar with -Dswt.widget.mode.List=original): the list box's
 * top row index when List.remove (start, end) cuts the tail below a top row it removes (setItemCount in the original
 * mode), and what LB_SETHORIZONTALEXTENT (sent by the stock setScrollWidth () after removing the widest row) and
 * LB_SETTOPINDEX do to a top row left beyond count - page. Same list as Test_..._List_ViewportDifferential:
 * MULTI | BORDER | V_SCROLL | H_SCROLL, 300 x 380 in a 330 x 420 shell.
 * Usage: ListShrinkTopOracle outFile
 */
public class ListShrinkTopOracle {
	static Shell shell;

	public static void main (String [] args) throws Exception {
		Display display = new Display ();
		shell = new Shell (display);
		shell.setBounds (20, 20, 330, 420);
		shell.open ();
		java.util.List<String> out = new ArrayList<> ();
		out.add ("# " + SWT.getVersion () + " " + System.getProperty ("swt.widget.mode.List", "-"));
		/* A: raw deletions to the quirk state (top 110 of 131), then one message */
		for (String message : new String [] {"none", "hext-same", "hext-minus10", "hext-plus10", "hext-1000", "settop-235", "setcaret-108", "settop-120"}) {
			List list = list (295, -1, 0);
			OS.SendMessage (list.handle, OS.LB_SETCARETINDEX, 238, 0);
			list.setTopIndex (235);
			pump (display);
			for (int i = 131; i <= 294; i++) OS.SendMessage (list.handle, OS.LB_DELETESTRING, 131, 0);
			String before = state (list);
			int extent = (int) OS.SendMessage (list.handle, OS.LB_GETHORIZONTALEXTENT, 0, 0);
			long result = switch (message) {
				case "hext-same" -> OS.SendMessage (list.handle, OS.LB_SETHORIZONTALEXTENT, extent, 0);
				case "hext-minus10" -> OS.SendMessage (list.handle, OS.LB_SETHORIZONTALEXTENT, extent - 10, 0);
				case "hext-plus10" -> OS.SendMessage (list.handle, OS.LB_SETHORIZONTALEXTENT, extent + 10, 0);
				case "hext-1000" -> OS.SendMessage (list.handle, OS.LB_SETHORIZONTALEXTENT, 1000, 0);
				case "settop-235" -> OS.SendMessage (list.handle, OS.LB_SETTOPINDEX, 235, 0);
				case "setcaret-108" -> OS.SendMessage (list.handle, OS.LB_SETCARETINDEX, 108, 0);
				case "settop-120" -> OS.SendMessage (list.handle, OS.LB_SETTOPINDEX, 120, 0);
				default -> 0;
			};
			String after = state (list);
			pump (display);
			out.add ("A " + message + " (extent " + extent + "): before " + before + " | result " + result + " after " + after + " | pumped " + state (list));
			list.dispose ();
		}
		/* B: the stock path, List.remove (131, 294), with the widest row removed, tied or kept */
		String [][] widest = {{"widest-removed", "200"}, {"widest-tied", "200,50"}, {"widest-kept", "50"}, {"no-widest", ""}};
		for (String [] w : widest) {
			Set<Integer> wide = new HashSet<> ();
			for (String s : w [1].split (",")) if (!s.isEmpty ()) wide.add (Integer.parseInt (s));
			List list = new List (shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
			list.setBounds (5, 5, 300, 380);
			for (int i = 0; i < 295; i++) list.add (wide.contains (i) ? "wide " + "x".repeat (30) : "row " + (100 + i));
			OS.SendMessage (list.handle, OS.LB_SETCARETINDEX, 238, 0);
			list.setTopIndex (235);
			pump (display);
			int extentBefore = (int) OS.SendMessage (list.handle, OS.LB_GETHORIZONTALEXTENT, 0, 0);
			list.remove (131, 294);
			int extentAfter = (int) OS.SendMessage (list.handle, OS.LB_GETHORIZONTALEXTENT, 0, 0);
			String after = state (list);
			pump (display);
			out.add ("B " + w [0] + ": extent " + extentBefore + " -> " + extentAfter + " | after " + after + " | pumped " + state (list));
			list.dispose ();
		}
		/* C: the caret below the page when the first row goes (ensure-visible rule), then the stock remove path */
		int [][] cases = {{295, 100, 200, 50, 294}, {295, 100, 110, 50, 294}, {295, 235, 140, 131, 294}, {295, 235, 0, 131, 294}, {295, 60, 270, 20, 294}};
		for (int [] c : cases) {
			for (boolean stock : new boolean [] {false, true}) {
				List list = list (c [0], -1, 0);
				OS.SendMessage (list.handle, OS.LB_SETCARETINDEX, c [2], 0);
				list.setTopIndex (c [1]);
				pump (display);
				StringBuilder trace = new StringBuilder ();
				int top = top (list);
				String start = state (list);
				if (stock) {
					list.remove (c [3], c [4]);
				} else {
					for (int i = c [3]; i <= c [4]; i++) {
						OS.SendMessage (list.handle, OS.LB_DELETESTRING, c [3], 0);
						int now = top (list);
						if (now != top) trace.append (" [count ").append (count (list)).append (" top ").append (now).append (" caret ").append (caret (list)).append (']');
						top = now;
					}
				}
				out.add ("C " + Arrays.toString (c) + (stock ? " List.remove" : " LB_DELETESTRING") + ": start " + start + trace + " | end " + state (list));
				list.dispose ();
			}
		}
		Files.write (Paths.get (args [0]), out);
		display.dispose ();
	}

	static List list (int count, int wideRow, int wide) {
		List list = new List (shell, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		list.setBounds (5, 5, 300, 380);
		for (int i = 0; i < count; i++) list.add ("row " + i);
		return list;
	}

	static String state (List list) {
		ScrollBar bar = list.getVerticalBar ();
		return "top " + top (list) + " count " + count (list) + " caret " + caret (list) + " bar " + bar.getSelection () + "/" + bar.getMaximum () + " t" + bar.getThumb ()
			+ " hext " + OS.SendMessage (list.handle, OS.LB_GETHORIZONTALEXTENT, 0, 0) + " hbar " + list.getHorizontalBar ().getVisible () + " client " + list.getClientArea ().height
			+ " item " + list.getItemHeight ();
	}

	static int top (List list) {
		return (int) OS.SendMessage (list.handle, OS.LB_GETTOPINDEX, 0, 0);
	}

	static int caret (List list) {
		return (int) OS.SendMessage (list.handle, OS.LB_GETCARETINDEX, 0, 0);
	}

	static int count (List list) {
		return (int) OS.SendMessage (list.handle, OS.LB_GETCOUNT, 0, 0);
	}

	static void pump (Display display) throws InterruptedException {
		for (int i = 0; i < 10; i++) {
			while (display.readAndDispatch ()) {
				/* drain */
			}
			Thread.sleep (5);
		}
	}
}
