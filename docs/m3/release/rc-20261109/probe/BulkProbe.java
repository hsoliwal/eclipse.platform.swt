import java.lang.reflect.Field;
import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.win32.*;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

/** Diagnostic only: the native row of node 39 before the test's raw TVM_EXPAND (handle, cChildren, state). */
public class BulkProbe {
	static final int ROOTS = 40;
	public static void main (String [] args) throws Exception {
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setLayout (new FillLayout ());
		shell.setBounds (300, 200, 520, 420);
		Tree lazy = new Tree (shell, SWT.VIRTUAL | SWT.BORDER);
		VirtualPageSource source = new VirtualPageSource (16, 32, 1, VirtualPageSource.TREE, request -> {
			int parent = request.getParentKey ();
			if (parent == VirtualPageSource.ROOT) {
				request.setTotal (ROOTS);
				for (int i = request.getStart (), end = Math.min (ROOTS, i + request.getCount ()); i < end; i++) {
					request.setText (i, 0, "n" + i); request.setKey (i, i); request.setChildCount (i, VirtualPageSource.UNKNOWN);
				}
				return;
			}
			int total = 5 + parent;
			request.setTotal (total);
			for (int i = request.getStart (), end = Math.min (total, i + request.getCount ()); i < end; i++) {
				request.setText (i, 0, "n" + parent + "." + i); request.setKey (i, parent * 1000 + i); request.setChildCount (i, 0);
			}
		});
		source.attach (lazy);
		shell.open ();
		drain (display, source);
		TreeItem small = lazy.getItem (0);
		long hs = (long) field (small, "handle");
		System.out.println ("PROBE small.handle=" + hs + " expand=" + OS.SendMessage (lazy.handle, OS.TVM_EXPAND, OS.TVE_EXPAND, hs));
		drain (display, source);
		System.out.println ("PROBE after small: pageBatches=" + field (lazy, "pageBatches") + " small.expanded=" + small.getExpanded () + " count=" + small.getItemCount ());
		lazy.setTopItem (lazy.getItem (ROOTS - 1));
		drain (display, source);
		TreeItem large = lazy.getItem (ROOTS - 1);
		long h = (long) field (large, "handle");
		TVITEM tv = new TVITEM ();
		tv.mask = OS.TVIF_HANDLE | OS.TVIF_CHILDREN | OS.TVIF_STATE | OS.TVIF_PARAM;
		tv.stateMask = OS.TVIS_EXPANDED;
		tv.hItem = h;
		long ok = OS.SendMessage (lazy.handle, OS.TVM_GETITEM, 0, tv);
		long last = OS.SendMessage (lazy.handle, OS.TVM_GETNEXTITEM, OS.TVGN_LASTVISIBLE, 0);
		System.out.println ("PROBE large.handle=" + h + " getItemOk=" + ok + " cChildren=" + tv.cChildren + " state=" + tv.state + " lParam=" + tv.lParam
				+ " lastVisible=" + last + " itemCount=" + large.getItemCount () + " expanded=" + large.getExpanded ());
		long r = OS.SendMessage (lazy.handle, OS.TVM_EXPAND, OS.TVE_EXPAND, h);
		drain (display, source);
		System.out.println ("PROBE TVM_EXPAND returned " + r + " expanded=" + large.getExpanded () + " itemCount=" + large.getItemCount ()
				+ " pageBatches=" + field (lazy, "pageBatches"));
		display.dispose ();
	}
	static Object field (Object target, String name) throws Exception {
		for (Class<?> c = target.getClass (); c != null; c = c.getSuperclass ()) {
			try { Field f = c.getDeclaredField (name); f.setAccessible (true); return f.get (target); } catch (NoSuchFieldException e) { }
		}
		throw new NoSuchFieldException (name);
	}
	static void drain (Display display, VirtualPageSource source) throws Exception {
		long deadline = System.currentTimeMillis () + 10_000; int quiet = 0;
		while (System.currentTimeMillis () < deadline) {
			boolean busy = false;
			while (display.readAndDispatch ()) busy = true;
			if (!busy && source.isIdle ()) { if (++quiet > 3) return; } else quiet = 0;
			Thread.sleep (2);
		}
	}
}
