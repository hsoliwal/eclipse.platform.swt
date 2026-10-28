import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.win32.COMBOBOXINFO;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.internal.win32.SCROLLINFO;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Oracle of unit combo-closed (Test_org_eclipse_swt_widgets_Combo_ViewportDifferential#closedDeltasAnswerLikeTheReference).
 * Replays the closed script of one seed (closedOp / closedDelta, same Random draws) on the jar of the class path and
 * attributes the viewport counters (m3NativeRowOps, m3WindowPatches; -1 on a jar without them) to the delta kinds;
 * "split" decomposes Combo.setItem (getSelectionIndex, remove (index, false), add, select (selection)) as the stock
 * method does. Mode "reselect" probes the stock rule of a re-select of the selected row on a closed combo after
 * inserts above it: CB_SETCURSEL answer, Modify, list-box caret, top index and selection before and after.
 * Usage: java ComboClosedOracle <seed> plain|split|reselect
 */
public class ComboClosedOracle {
	static final String [] WORDS = {"apple ", "berry ", "cherry ", "Apple ", "date ", "elder "};
	static int modify;
	static Method ops, patches, removeQuiet;

	public static void main (String [] args) throws Exception {
		int seed = Integer.parseInt (args [0]);
		String mode = args.length > 1 ? args [1] : "plain";
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		shell.setBounds (20, 20, 330, 420);
		Combo combo = new Combo (shell, SWT.DROP_DOWN | SWT.READ_ONLY);
		combo.setBounds (5, 5, 300, 30);
		combo.setVisibleItemCount (20);
		for (int i = 0; i < 300; i++) combo.add (WORDS [i % WORDS.length] + i);
		combo.addListener (SWT.Modify, e -> modify++);
		shell.open ();
		combo.setFocus ();
		settle (display, 60);
		try {
			ops = Combo.class.getDeclaredMethod ("m3NativeRowOps");
			patches = Combo.class.getDeclaredMethod ("m3WindowPatches");
			ops.setAccessible (true);
			patches.setAccessible (true);
		} catch (NoSuchMethodException e) {
			ops = patches = null;
		}
		removeQuiet = Combo.class.getDeclaredMethod ("remove", int.class, boolean.class);
		removeQuiet.setAccessible (true);
		System.out.println ("# jar " + Combo.class.getProtectionDomain ().getCodeSource ().getLocation () + " SWT " + SWT.getVersion ()
			+ " viewport " + viewport (combo) + " seed " + seed + " mode " + mode);
		if (mode.equals ("reselect")) {
			reselect (display, combo);
			shell.dispose ();
			return;
		}
		Random random = new Random (0xC0B0L + seed);
		for (int step = 0; step < 13; step++) {
			long deltaSeed = random.nextLong ();
			modify = 0;
			long ops0 = count (ops, combo), patches0 = count (patches, combo);
			String name;
			String kinds = "";
			if (step == 0) {
				int index = 100 + random.nextInt (100);
				name = "select " + index;
				combo.select (index);
			} else if (step == 11) {
				name = "drop";
				combo.setListVisible (true);
			} else if (step == 12) {
				name = "close";
				combo.setListVisible (false);
			} else {
				name = "deltas " + deltaSeed;
				kinds = deltas (combo, new Random (deltaSeed), mode.equals ("split"));
			}
			flush (display);
			long ops1 = count (ops, combo), patches1 = count (patches, combo);
			String list = combo.getListVisible () ? bar (list (combo)) : "-";
			System.out.println ("step " + step + " " + name + " | state sel " + combo.getSelectionIndex () + " count " + combo.getItemCount () + " text '"
				+ combo.getText () + "' dropped " + combo.getListVisible () + " | modify " + modify + " | list " + list + " | rowOps +"
				+ (ops == null ? "n/a" : String.valueOf (ops1 - ops0)) + " patches +" + (patches == null ? "n/a" : String.valueOf (patches1 - patches0))
				+ (kinds.isEmpty () ? "" : " | " + kinds) + " | lanes " + lanes (combo));
			if (step == 11 && mode.startsWith ("capture")) captureDrop (display, combo, args [2]);
			if (step == 11 && mode.equals ("capturecues")) {
				/* the same list with its keyboard cues shown (the UI state a list starts with after keyboard input) */
				long hwnd = list (combo);
				for (int flags : new int [] {0x1, 0x3}) {
					/* the list box is a child of the desktop: WM_CHANGEUISTATE goes to its parent, WM_UPDATEUISTATE sets its own state */
					OS.SendMessage (hwnd, 0x0128 /* WM_UPDATEUISTATE */, 2 /* UIS_CLEAR */ | (flags << 16), 0);
					settle (display, 30);
					org.eclipse.swt.graphics.ImageData pixels = print (combo, hwnd, true);
					System.out.println ("cues cleared 0x" + Integer.toHexString (flags) + ": uistate " + OS.SendMessage (hwnd, 0x0129, 0, 0) + " list " + bar (hwnd) + " "
						+ Integer.toHexString (java.util.Arrays.hashCode (pixels.data)));
					org.eclipse.swt.graphics.ImageLoader loader = new org.eclipse.swt.graphics.ImageLoader ();
					loader.data = new org.eclipse.swt.graphics.ImageData [] {pixels};
					loader.save (args [2] + "-list-cues" + flags + ".png", SWT.IMAGE_PNG);
				}
			}
		}
		shell.dispose ();
	}

	/* the drop step's probes as the test reads them (face with cues hidden, then the list's bar and WM_PRINT), saved and itemized */
	static void captureDrop (Display display, Combo combo, String prefix) throws Exception {
		hideCues (combo);
		org.eclipse.swt.graphics.ImageData face = print (combo, combo.handle, false);
		long hwnd = list (combo);
		org.eclipse.swt.graphics.ImageData pixels = print (combo, hwnd, true);
		System.out.println ("face hash " + Integer.toHexString (java.util.Arrays.hashCode (face.data)) + " | list " + bar (hwnd) + " "
			+ Integer.toHexString (java.util.Arrays.hashCode (pixels.data)) + " " + pixels.width + "x" + pixels.height + " depth " + pixels.depth);
		org.eclipse.swt.graphics.ImageLoader loader = new org.eclipse.swt.graphics.ImageLoader ();
		loader.data = new org.eclipse.swt.graphics.ImageData [] {pixels};
		loader.save (prefix + "-list.png", SWT.IMAGE_PNG);
		org.eclipse.swt.internal.win32.RECT window = new org.eclipse.swt.internal.win32.RECT (), client = new org.eclipse.swt.internal.win32.RECT ();
		OS.GetWindowRect (hwnd, window);
		OS.GetClientRect (hwnd, client);
		int top = (int) OS.SendMessage (hwnd, OS.LB_GETTOPINDEX, 0, 0), height = (int) OS.SendMessage (hwnd, OS.LB_GETITEMHEIGHT, 0, 0);
		long uiState = OS.SendMessage (hwnd, 0x0129 /* WM_QUERYUISTATE */, 0, 0), comboUi = OS.SendMessage (combo.handle, 0x0129, 0, 0);
		org.eclipse.swt.internal.win32.POINT cursor = new org.eclipse.swt.internal.win32.POINT ();
		OS.GetCursorPos (cursor);
		System.out.println ("list window " + window.left + "," + window.top + " " + (window.right - window.left) + "x" + (window.bottom - window.top) + " client "
			+ (client.right - client.left) + "x" + (client.bottom - client.top) + " itemHeight " + height + " count " + OS.SendMessage (hwnd, OS.LB_GETCOUNT, 0, 0)
			+ " top " + top + " cursel " + OS.SendMessage (hwnd, OS.LB_GETCURSEL, 0, 0) + " caret " + OS.SendMessage (hwnd, OS.LB_GETCARETINDEX, 0, 0)
			+ " uistate list " + uiState + " combo " + comboUi + " cursor " + cursor.x + "," + cursor.y + " style 0x" + Long.toHexString (OS.GetWindowLongPtr (hwnd, OS.GWL_STYLE))
			+ " exstyle 0x" + Long.toHexString (OS.GetWindowLongPtr (hwnd, OS.GWL_EXSTYLE)));
		StringBuilder rows = new StringBuilder ("visible rows:");
		for (int entry = top; entry < top + 22; entry++) {
			int length = (int) OS.SendMessage (hwnd, OS.LB_GETTEXTLEN, entry, 0);
			if (length < 0) break;
			char [] chars = new char [length + 1];
			OS.SendMessage (hwnd, OS.LB_GETTEXT, entry, chars);
			rows.append (" [").append (new String (chars, 0, length)).append (']');
		}
		System.out.println (rows);
	}

	static void hideCues (Combo combo) throws InterruptedException {
		OS.SendMessage (combo.getShell ().handle, 0x0127, 1 | ((0x1 | 0x2) << 16), 0);
		settle (combo.getDisplay (), 30);
	}

	/* WM_PRINT of a window into an image of its window size (the list) or of the control size (the face) */
	static org.eclipse.swt.graphics.ImageData print (Combo combo, long hwnd, boolean windowRect) {
		int width, height;
		if (windowRect) {
			org.eclipse.swt.internal.win32.RECT rect = new org.eclipse.swt.internal.win32.RECT ();
			OS.GetWindowRect (hwnd, rect);
			width = rect.right - rect.left;
			height = rect.bottom - rect.top;
		} else {
			width = combo.getSize ().x;
			height = combo.getSize ().y;
		}
		org.eclipse.swt.graphics.Image image = new org.eclipse.swt.graphics.Image (combo.getDisplay (), Math.max (1, width), Math.max (1, height));
		org.eclipse.swt.graphics.GC gc = new org.eclipse.swt.graphics.GC (image);
		try {
			OS.SendMessage (hwnd, 0x0317, gc.handle, 0x2 | 0x4 | 0x8 | 0x10);
		} finally {
			gc.dispose ();
		}
		org.eclipse.swt.graphics.ImageData data = image.getImageData ();
		image.dispose ();
		return data;
	}

	/* closedDelta of the test with the same draws; counts row operations and patches per kind */
	static String deltas (Combo combo, Random random, boolean split) throws Exception {
		long [] n = new long [6], rowOps = new long [6], patch = new long [6];
		String [] label = {"add", "remove", "setItem", "setItem.remove", "setItem.add", "setItem.select"};
		for (int k = 0; k < 1000; k++) {
			int count = combo.getItemCount (), selected = combo.getSelectionIndex ();
			int i = random.nextInt (count);
			switch (random.nextInt (4)) {
				case 0, 1 -> {
					long o = count (ops, combo), p = count (patches, combo);
					combo.add ("closed " + random.nextInt (1000), random.nextInt (count + 1));
					n [0]++; rowOps [0] += count (ops, combo) - o; patch [0] += count (patches, combo) - p;
				}
				case 2 -> {
					if (count > 50 && i != selected) {
						long o = count (ops, combo), p = count (patches, combo);
						combo.remove (i);
						n [1]++; rowOps [1] += count (ops, combo) - o; patch [1] += count (patches, combo) - p;
					}
				}
				default -> {
					if (i != selected) {
						String text = "set " + random.nextInt (1000);
						long o = count (ops, combo), p = count (patches, combo);
						if (!split) {
							combo.setItem (i, text);
						} else {
							/* Combo.setItem (stock body, Combo.java:2318-2332) step by step */
							int selection = combo.getSelectionIndex ();
							long a = count (ops, combo), ap = count (patches, combo);
							removeQuiet.invoke (combo, i, false);
							n [3]++; rowOps [3] += count (ops, combo) - a; patch [3] += count (patches, combo) - ap;
							a = count (ops, combo); ap = count (patches, combo);
							combo.add (text, i);
							n [4]++; rowOps [4] += count (ops, combo) - a; patch [4] += count (patches, combo) - ap;
							a = count (ops, combo); ap = count (patches, combo);
							if (selection != -1) combo.select (selection);
							n [5]++; rowOps [5] += count (ops, combo) - a; patch [5] += count (patches, combo) - ap;
						}
						n [2]++; rowOps [2] += count (ops, combo) - o; patch [2] += count (patches, combo) - p;
					}
				}
			}
		}
		StringBuilder out = new StringBuilder ("kinds");
		for (int k = 0; k < label.length; k++) {
			if (n [k] == 0) continue;
			out.append (' ').append (label [k]).append (' ').append (n [k]).append ("x rowOps ").append (rowOps [k]).append (" patches ").append (patch [k]).append (';');
		}
		return out.toString ();
	}

	/*
	 * Stock rule probe: select 176, insert 3 rows above it while closed (selection 179), then select (179) again as
	 * Combo.setItem does; reports the native list box (caret, top, cursel) and the viewport lanes around it.
	 */
	static void reselect (Display display, Combo combo) throws Exception {
		combo.select (176);
		flush (display);
		System.out.println ("after select 176   | " + nativeState (combo) + " | modify " + modify + " | lanes " + lanes (combo));
		for (int k = 0; k < 3; k++) combo.add ("closed " + k, 0);
		flush (display);
		System.out.println ("after 3 adds at 0  | " + nativeState (combo) + " | modify " + modify + " | lanes " + lanes (combo));
		int selection = combo.getSelectionIndex ();
		long o = count (ops, combo), p = count (patches, combo);
		modify = 0;
		combo.select (selection);
		flush (display);
		System.out.println ("after select " + selection + " (re-select) | " + nativeState (combo) + " | modify " + modify + " | rowOps +"
			+ (ops == null ? "n/a" : String.valueOf (count (ops, combo) - o)) + " patches +" + (patches == null ? "n/a" : String.valueOf (count (patches, combo) - p))
			+ " | lanes " + lanes (combo));
		/* the same through the native message as Combo.select sends it (stock only: the native index is the logical one) */
		if (!viewport (combo)) {
			long code = OS.SendMessage (combo.handle, OS.CB_SETCURSEL, selection, 0);
			System.out.println ("CB_SETCURSEL " + selection + " answers " + code + " | " + nativeState (combo) + " | modify " + modify);
		}
		for (int k = 0; k < 2; k++) combo.add ("closed b" + k, 0);
		flush (display);
		System.out.println ("after 2 more adds  | " + nativeState (combo) + " | lanes " + lanes (combo));
		combo.setListVisible (true);
		flush (display);
		System.out.println ("dropped            | " + nativeState (combo) + " | bar " + bar (list (combo)) + " | lanes " + lanes (combo));
		combo.setListVisible (false);
		flush (display);
		System.out.println ("closed             | " + nativeState (combo) + " | sel " + combo.getSelectionIndex () + " | lanes " + lanes (combo));
	}

	static String nativeState (Combo combo) {
		long hwnd = list (combo);
		return "LB count " + OS.SendMessage (hwnd, OS.LB_GETCOUNT, 0, 0) + " cursel " + OS.SendMessage (hwnd, OS.LB_GETCURSEL, 0, 0) + " caret "
			+ OS.SendMessage (hwnd, OS.LB_GETCARETINDEX, 0, 0) + " top " + OS.SendMessage (hwnd, OS.LB_GETTOPINDEX, 0, 0) + " | combo sel "
			+ combo.getSelectionIndex () + " text '" + combo.getText () + "'";
	}

	/* viewport lanes (fork jar only) */
	static String lanes (Combo combo) {
		try {
			Field field = Combo.class.getDeclaredField ("viewport");
			field.setAccessible (true);
			Object viewport = field.get (combo);
			if (viewport == null) return "-";
			StringBuilder out = new StringBuilder ();
			for (String name : new String [] {"stale", "staleSelection", "caret", "parked", "parkedCaret", "recordedRow", "recordedFirst", "barState"}) {
				Field f = viewport.getClass ().getDeclaredField (name);
				f.setAccessible (true);
				out.append (name).append ('=').append (f.get (viewport)).append (' ');
			}
			Method residentCount = viewport.getClass ().getDeclaredMethod ("residentCount");
			residentCount.setAccessible (true);
			out.append ("resident=").append (residentCount.invoke (viewport));
			return out.toString ();
		} catch (NoSuchFieldException | NoSuchMethodException e) {
			return "-";
		} catch (ReflectiveOperationException e) {
			throw new AssertionError (e);
		}
	}

	static boolean viewport (Combo combo) {
		try {
			Field field = Combo.class.getDeclaredField ("viewport");
			field.setAccessible (true);
			return field.get (combo) != null;
		} catch (ReflectiveOperationException e) {
			return false;
		}
	}

	static long count (Method method, Combo combo) throws Exception {
		return method == null ? -1 : (Long) method.invoke (combo);
	}

	static long list (Combo combo) {
		COMBOBOXINFO info = new COMBOBOXINFO ();
		info.cbSize = COMBOBOXINFO.sizeof;
		OS.GetComboBoxInfo (combo.handle, info);
		return info.hwndList;
	}

	static String bar (long hwnd) {
		SCROLLINFO info = new SCROLLINFO ();
		info.cbSize = SCROLLINFO.sizeof;
		info.fMask = 0x17;
		OS.GetScrollInfo (hwnd, 1, info);
		return info.nMin + ".." + info.nMax + "/" + info.nPage + "@" + info.nPos;
	}

	static void settle (Display display, int millis) throws InterruptedException {
		long end = System.currentTimeMillis () + millis;
		while (System.currentTimeMillis () < end) {
			while (display.readAndDispatch ()) {
				/* drain */
			}
			Thread.sleep (5);
		}
	}

	static void flush (Display display) {
		boolean [] ran = {false};
		display.asyncExec (() -> ran [0] = true);
		long end = System.currentTimeMillis () + 2000;
		while (!ran [0] && System.currentTimeMillis () < end) {
			if (!display.readAndDispatch ()) display.sleep ();
		}
		while (display.readAndDispatch ()) {
			/* drain */
		}
	}
}
