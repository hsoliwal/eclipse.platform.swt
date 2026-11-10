import java.lang.reflect.Field;
import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.win32.*;
import org.eclipse.swt.widgets.*;

/** Diagnostic only: the drop-down bar of a 100,000-row Combo after an edit-text match, with the viewport state. */
public class ComboProbe {
	static final String [] WORDS = {"alpha ", "bravo ", "elder ", "delta "};
	public static void main (String [] args) throws Exception {
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setBounds (20, 20, 340, 460);
		shell.open ();
		Combo combo = new Combo (shell, SWT.DROP_DOWN);
		combo.setBounds (5, 5, 300, 30);
		combo.setVisibleItemCount (20);
		String [] items = new String [100_000];
		for (int i = 0; i < items.length; i++) items [i] = WORDS [i % WORDS.length] + i;
		combo.setItems (items);
		combo.setFocus ();
		while (display.readAndDispatch ()) { }
		combo.setText ("ELDER 9999");
		dump ("after setText", combo);
		combo.setListVisible (true);
		dump ("after setListVisible", combo);
		while (display.readAndDispatch ()) { }
		dump ("after settle", combo);
		display.dispose ();
	}
	static void dump (String when, Combo combo) throws Exception {
		Object vp = field (combo, "viewport");
		long hwndList = vp == null ? 0 : (long) field (vp, "hwndList");
		SCROLLINFO info = new SCROLLINFO ();
		info.cbSize = SCROLLINFO.sizeof; info.fMask = OS.SIF_ALL;
		if (hwndList != 0) OS.GetScrollInfo (hwndList, OS.SB_VERT, info);
		System.out.println ("PROBE " + when + ": viewport=" + (vp != null) + " installed=" + (vp == null ? "-" : field (vp, "installed"))
			+ " barState=" + (vp == null ? "-" : field (vp, "barState")) + " bar=" + info.nMin + ".." + info.nMax + "/" + info.nPage + "@" + info.nPos
			+ " sel=" + combo.getSelectionIndex () + " dropped=" + combo.getListVisible ());
	}
	static Object field (Object target, String name) throws Exception {
		for (Class<?> c = target.getClass (); c != null; c = c.getSuperclass ()) {
			try { Field f = c.getDeclaredField (name); f.setAccessible (true); return f.get (target); } catch (NoSuchFieldException e) { }
		}
		throw new NoSuchFieldException (name);
	}
}
