import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

/** Diagnostic only (not committed into product): VirtualPageSource SWAP counters while a 200k-row Table scrolls. */
public class SwapProbe {
	public static void main (String [] args) throws Exception {
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setLayout (new FillLayout ());
		shell.setBounds (300, 200, 520, 420);
		Table lazy = new Table (shell, SWT.VIRTUAL | SWT.CHECK | SWT.MULTI | SWT.BORDER | SWT.FULL_SELECTION);
		for (int c = 0; c < 2; c++) new TableColumn (lazy, SWT.NONE).setWidth (80);
		lazy.setItemCount (200_000);
		VirtualPageSource source = new VirtualPageSource (64, 6, 2, VirtualPageSource.SWAP, request -> {
			for (int i = request.getStart (), end = i + request.getCount (); i < end; i++) request.setText (i, 0, "r" + i);
		});
		source.setOverscanBlocks (0);
		source.attach (lazy);
		shell.open ();
		drain (display, source);
		for (int top : new int [] {0, 5_000, 10_000, 15_000, 20_000, 25_000, 0, 10_000}) {
			lazy.setTopIndex (top);
			drain (display, source);
			lazy.getItem (lazy.getTopIndex ()).getText (0);
			System.out.printf ("top %d: requested %d fetched %d evicted %d resident %d released %d swappedOut %d swappedIn %d%n", top,
					source.getCount (VirtualPageSource.REQUESTED), source.getCount (VirtualPageSource.FETCHED),
					source.getCount (VirtualPageSource.EVICTED), source.getCount (VirtualPageSource.RESIDENT),
					source.getCount (VirtualPageSource.RELEASED), source.getCount (VirtualPageSource.SWAPPED_OUT),
					source.getCount (VirtualPageSource.SWAPPED_IN));
		}
		display.dispose ();
	}
	static void drain (Display display, VirtualPageSource source) throws Exception {
		long deadline = System.currentTimeMillis () + 10_000;
		int quiet = 0;
		while (System.currentTimeMillis () < deadline) {
			boolean busy = false;
			while (display.readAndDispatch ()) busy = true;
			if (!busy && source.isIdle ()) { if (++quiet > 3) return; } else quiet = 0;
			Thread.sleep (2);
		}
	}
}
