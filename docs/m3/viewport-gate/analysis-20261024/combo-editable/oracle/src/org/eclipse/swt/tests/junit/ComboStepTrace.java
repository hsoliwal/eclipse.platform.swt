package org.eclipse.swt.tests.junit;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.Callback;
import org.eclipse.swt.internal.win32.*;
import org.eclipse.swt.widgets.*;

/**
 * Message trace of chosen steps of a Combo differential seed: replays the seed with the test's own Subject and randomOp
 * and, during steps from..to, logs what reaches the drop-down list box, the edit field, the combo and (WM_COMMAND from the
 * combo) the shell, nested, with each result and the edit text after it.
 * Usage: ComboStepTrace <dropdown|readonly> <seed> <from> <to> <out file>
 */
public class ComboStepTrace {
	static final Map<Long, Long> PROCS = new HashMap<> ();
	static final Map<Long, String> NAMES = new HashMap<> ();
	static final StringBuilder OUT = new StringBuilder ();
	static boolean on, reading;
	static int depth;
	static long edit, list, comboHandle, shellHandle;
	static Callback callback;

	public static void main (String [] args) throws Exception {
		int style = args [0].equals ("readonly") ? SWT.DROP_DOWN | SWT.READ_ONLY : SWT.DROP_DOWN;
		int seed = Integer.parseInt (args [1]), from = Integer.parseInt (args [2]), to = Integer.parseInt (args [3]);
		Display display = Display.getDefault ();
		Shell shell = new Shell (display);
		shell.setBounds (20, 20, 330, 420);
		Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Subject subject = new Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Subject (shell, style);
		OUT.append ("# SWT ").append (SWT.getVersion ()).append (" mode ").append (ViewportDifferential.reference ("Combo")).append (" seed ").append (seed).append ('\n');
		subject.reset ();
		Combo c = subject.combo;
		comboHandle = c.handle;
		shellHandle = shell.handle;
		COMBOBOXINFO info = new COMBOBOXINFO ();
		info.cbSize = COMBOBOXINFO.sizeof;
		OS.GetComboBoxInfo (c.handle, info);
		edit = info.hwndItem;
		list = info.hwndList;
		callback = new Callback (ComboStepTrace.class, "hook", 4);
		Random random = new Random (0xC0B0L + seed);
		StringBuilder log = new StringBuilder ("seed " + seed + "\n");
		for (int step = 0; step <= to; step++) {
			ViewportDifferential.Op<Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.Subject> op = Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.randomOp (random, log);
			String [] lines = log.toString ().split ("\n");
			String opName = lines [lines.length - 1].replace ("\b", "\\b");
			boolean trace = step >= from;
			if (trace) {
				OUT.append ("== step ").append (step).append (' ').append (opName).append (" | before: text '").append (c.getText ()).append ("' sel ").append (c.getSelectionIndex ()).append ('\n');
				install (list, "LB");
				install (edit, "ED");
				install (comboHandle, "CB");
				install (shellHandle, "SH");
				on = true;
			}
			subject.events.setLength (0);
			String answer = op.apply (subject);
			Test_org_eclipse_swt_widgets_Combo_ViewportDifferential.flush (display);
			if (trace) {
				on = false;
				uninstall ();
				OUT.append ("== after step ").append (step).append (": ").append (answer).append (" | sel ").append (c.getSelectionIndex ()).append (" text '").append (c.getText ()).append ("' dropped ")
					.append (c.getListVisible ()).append (" events ").append (subject.events).append ('\n');
			}
		}
		try (Writer w = new OutputStreamWriter (new FileOutputStream (args [4]), StandardCharsets.UTF_8)) {
			w.write (OUT.toString ());
		}
		shell.dispose ();
		display.dispose ();
	}

	static void install (long hwnd, String name) {
		long old = OS.SetWindowLongPtr (hwnd, OS.GWLP_WNDPROC, callback.getAddress ());
		PROCS.put (hwnd, old);
		NAMES.put (hwnd, name);
	}

	static void uninstall () {
		for (Map.Entry<Long, Long> e : PROCS.entrySet ()) OS.SetWindowLongPtr (e.getKey (), OS.GWLP_WNDPROC, e.getValue ());
		PROCS.clear ();
	}

	static String editText () {
		reading = true;
		try {
			int length = OS.GetWindowTextLength (edit);
			char [] buffer = new char [length + 1];
			OS.GetWindowText (edit, buffer, length + 1);
			return new String (buffer, 0, length);
		} finally {
			reading = false;
		}
	}

	static String string (long lParam) {
		if (lParam == 0) return "null";
		int length = OS.wcslen (lParam);
		char [] chars = new char [length];
		OS.MoveMemory (chars, lParam, length * 2);
		return "'" + new String (chars) + "'";
	}

	static String describe (String name, int msg, long wParam, long lParam) {
		switch (name) {
			case "LB": {
				return switch (msg) {
					case 0x180 -> "LB_ADDSTRING " + string (lParam);
					case 0x181 -> "LB_INSERTSTRING " + wParam + " " + string (lParam);
					case 0x182 -> "LB_DELETESTRING " + wParam;
					case 0x184 -> "LB_RESETCONTENT";
					case 0x186 -> "LB_SETCURSEL " + (int) wParam;
					case 0x188 -> "LB_GETCURSEL";
					case 0x189 -> "LB_GETTEXT " + wParam;
					case 0x18a -> "LB_GETTEXTLEN " + wParam;
					case 0x18b -> "LB_GETCOUNT";
					case 0x18e -> "LB_GETTOPINDEX";
					case 0x18f -> "LB_FINDSTRING " + (int) wParam + " " + string (lParam);
					case 0x194 -> "LB_SETHORIZONTALEXTENT " + wParam;
					case 0x197 -> "LB_SETTOPINDEX " + wParam;
					case 0x19e -> "LB_SETCARETINDEX " + (int) wParam;
					case 0x19f -> "LB_GETCARETINDEX";
					case 0x1a1 -> "LB_GETITEMHEIGHT";
					case 0x1a2 -> "LB_FINDSTRINGEXACT " + (int) wParam + " " + string (lParam);
					case 0x1a3 -> "LB_CARETON";
					case 0x1ae -> "LBCB_STARTTRACK";
					case 0x1af -> "LBCB_ENDTRACK " + wParam;
					case 0x18 -> "WM_SHOWWINDOW " + wParam;
					case 0x115 -> "WM_VSCROLL " + wParam;
					case 0x20a -> "WM_MOUSEWHEEL";
					case 0x200 -> "WM_MOUSEMOVE";
					case 0x201 -> "WM_LBUTTONDOWN";
					case 0x202 -> "WM_LBUTTONUP";
					case 0x100 -> "WM_KEYDOWN " + wParam;
					case 0x102 -> "WM_CHAR " + wParam;
					default -> null;
				};
			}
			case "ED":
				return switch (msg) {
					case 0x0C -> "WM_SETTEXT " + string (lParam);
					case 0xB1 -> "EM_SETSEL " + wParam + "," + lParam;
					case 0x100 -> "WM_KEYDOWN " + wParam;
					case 0x102 -> "WM_CHAR " + wParam;
					case 0x104 -> "WM_SYSKEYDOWN " + wParam;
					case 0x105 -> "WM_SYSKEYUP " + wParam;
					case 0x101 -> "WM_KEYUP " + wParam;
					default -> null;
				};
			case "CB":
				if (msg >= 0x140 && msg <= 0x165) return "CB_" + Integer.toHexString (msg) + " " + (int) wParam;
				return switch (msg) {
					case 0x0C -> "WM_SETTEXT " + string (lParam);
					case 0x05 -> "WM_SIZE";
					case 0x111 -> "WM_COMMAND " + Integer.toHexString ((int) wParam);
					case 0x20a -> "WM_MOUSEWHEEL";
					default -> null;
				};
			case "SH":
				if (msg == 0x111 && lParam == comboHandle) return "notify " + cbn ((int) (wParam >>> 16) & 0xFFFF);
				return null;
			default:
				return null;
		}
	}

	static String cbn (int code) {
		return switch (code) {
			case 1 -> "CBN_SELCHANGE";
			case 2 -> "CBN_DBLCLK";
			case 3 -> "CBN_SETFOCUS";
			case 4 -> "CBN_KILLFOCUS";
			case 5 -> "CBN_EDITCHANGE";
			case 6 -> "CBN_EDITUPDATE";
			case 7 -> "CBN_DROPDOWN";
			case 8 -> "CBN_CLOSEUP";
			case 9 -> "CBN_SELENDOK";
			case 10 -> "CBN_SELENDCANCEL";
			default -> "CBN_" + code;
		};
	}

	public static long hook (long hwnd, long msg, long wParam, long lParam) {
		Long old = PROCS.get (hwnd);
		if (old == null) return OS.DefWindowProc (hwnd, (int) msg, wParam, lParam);
		String name = NAMES.get (hwnd);
		String text = on && !reading ? describe (name, (int) msg, wParam, lParam) : null;
		if (text == null) return OS.CallWindowProc (old, hwnd, (int) msg, wParam, lParam);
		String indent = "  ".repeat (depth);
		OUT.append (indent).append (name).append (' ').append (text).append ('\n');
		depth++;
		long result;
		try {
			result = OS.CallWindowProc (old, hwnd, (int) msg, wParam, lParam);
		} finally {
			depth--;
		}
		OUT.append (indent).append ("  -> ").append (result).append (" | edit '").append (editText ()).append ("'\n");
		return result;
	}
}
