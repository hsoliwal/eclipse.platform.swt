import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.Callback;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.widgets.*;

/**
 * Issue 5 probe (Tree_ViewportDeltas.hiddenTreeAppliesOnePatchOnShow): the hidden gate's deltas, with a WH_CALLWNDPROC
 * hook that keys every TVM message sent to the tree by the SWT frames that sent it and by the delta that was running.
 * Prints the resident row count when parked, then per key: count, message, delta kinds and indexes, the frames.
 * Arg 0: deltas (default 10000).
 */
public class ParkedTrafficProbe {
	static final int WH_CALLWNDPROC = 4;
	static long hwnd;
	static boolean counting;
	static final long [] cwp = new long [4];
	static int deltaIndex = -1;
	static String deltaKind = "-";
	static final Map<String, int []> keys = new LinkedHashMap<> ();
	static final Map<String, String> kinds = new HashMap<> ();
	static final java.util.List<String> roots = new ArrayList<> ();
	static final Map<String, Integer> children = new HashMap<> ();

	static long hookProc (long code, long wParam, long lParam) {
		if (counting && code >= 0 && lParam != 0) {
			OS.MoveMemory (cwp, lParam, 4 * 8);
			int message = (int) cwp [2];
			if (cwp [3] == hwnd && message >= OS.TV_FIRST && message <= OS.TV_FIRST + 0xFF) record (message);
		}
		return OS.CallNextHookEx (0, (int) code, wParam, lParam);
	}

	static void record (int message) {
		StringBuilder key = new StringBuilder (String.format ("0x%X", message));
		int n = 0;
		for (StackTraceElement e : new Throwable ().getStackTrace ()) {
			String c = e.getClassName ();
			if (!c.startsWith ("org.eclipse.swt.widgets.") || c.endsWith ("Display") || e.getMethodName ().equals ("callWindowProc")) continue;
			key.append (" < ").append (c.substring (24)).append ('.').append (e.getMethodName ()).append (':').append (e.getLineNumber ());
			if (++n == 9) break;
		}
		String k = key.toString ();
		int [] slot = keys.computeIfAbsent (k, x -> new int [] {0, deltaIndex});
		slot [0]++;
		kinds.merge (k, deltaKind + "@" + deltaIndex, (a, b) -> a.length () > 200 ? a : a + "," + b);
	}

	public static void main (String [] args) {
		int deltas = args.length > 0 ? Integer.parseInt (args [0]) : 10_000;
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setBounds (20, 20, 360, 460);
		Composite page = new Composite (shell, SWT.NONE);
		page.setBounds (0, 0, 340, 420);
		for (int i = 0; i < 1_000; i++) roots.add ("root " + i);
		Tree tree = new Tree (page, SWT.MULTI | SWT.BORDER | SWT.VIRTUAL);
		tree.setBounds (5, 5, 320, 400);
		tree.addListener (SWT.SetData, event -> {
			TreeItem item = (TreeItem) event.item;
			TreeItem parent = item.getParentItem ();
			item.setText (parent == null ? roots.get (event.index) : parent.getText () + " / " + event.index);
			Integer count = parent == null ? children.get (roots.get (event.index)) : null;
			if (count != null) item.setItemCount (count);
		});
		tree.setItemCount (roots.size ());
		shell.open ();
		tree.setTopItem (tree.getItem (200));
		drain (display);
		tree.setVisible (false);
		drain (display);
		hwnd = tree.handle;
		Callback callback = new Callback (ParkedTrafficProbe.class, "hookProc", 3);
		long hook = OS.SetWindowsHookEx (WH_CALLWNDPROC, callback.getAddress (), 0, OS.GetCurrentThreadId ());
		Random random = new Random (0x41DDE7L);
		counting = true;
		for (int i = 0; i < deltas; i++) {
			deltaIndex = i;
			delta (tree, random);
		}
		deltaIndex = -2;
		deltaKind = "drain";
		drain (display);
		counting = false;
		OS.UnhookWindowsHookEx (hook);
		int total = keys.values ().stream ().mapToInt (v -> v [0]).sum ();
		System.out.println ("TOTAL " + total + " keys " + keys.size ());
		keys.entrySet ().stream ().sorted ((a, b) -> b.getValue () [0] - a.getValue () [0]).forEach (e -> {
			System.out.println ("COUNT " + e.getValue () [0] + " firstDelta " + e.getValue () [1] + " kinds " + kinds.get (e.getKey ()));
			System.out.println ("   " + e.getKey ());
		});
		tree.setVisible (true);
		drain (display);
		callback.dispose ();
		display.dispose ();
	}

	static void drain (Display display) {
		for (int i = 0; i < 3; i++) while (display.readAndDispatch ()) { }
	}

	static void delta (Tree tree, Random random) {
		int count = tree.getItemCount ();
		int index = count == 0 ? 0 : random.nextInt (count);
		switch (random.nextInt (8)) {
			case 0, 1 -> {
				deltaKind = "text";
				if (count == 0) return;
				String label = "root " + random.nextInt (1_000_000);
				roots.set (index, label);
				tree.getItem (index).setText (label);
			}
			case 2 -> {
				deltaKind = "count";
				int next = Math.max (100, Math.min (2_000, count + random.nextInt (21) - 10));
				while (roots.size () < next) roots.add ("root " + roots.size () + "+" + random.nextInt (1000));
				while (roots.size () > next) roots.remove (roots.size () - 1);
				tree.setItemCount (next);
			}
			case 3 -> {
				deltaKind = "insert";
				String label = "new " + random.nextInt (1_000_000);
				roots.add (index, label);
				new TreeItem (tree, SWT.NONE, index).setText (label);
			}
			case 4 -> {
				deltaKind = "dispose";
				if (count <= 100) return;
				roots.remove (index);
				tree.getItem (index).dispose ();
			}
			case 5 -> {
				deltaKind = "children";
				if (count == 0) return;
				TreeItem item = tree.getItem (index);
				int kids = random.nextInt (12);
				children.put (roots.get (index), kids);
				item.setItemCount (kids);
			}
			default -> {
				deltaKind = "expand";
				if (count == 0) return;
				tree.getItem (index).setExpanded (random.nextBoolean ());
			}
		}
	}
}
