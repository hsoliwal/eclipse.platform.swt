import java.util.Random;

import org.eclipse.swt.SWT;
import org.eclipse.swt.m3.M3Patch;
import org.eclipse.swt.widgets.*;

/**
 * Issue 4 probe (Table_Splice.hiddenTableAppliesOneWindowPatchWhenShown): a hidden Table takes M3Model runs, then shows.
 * Every SWT.Paint after setVisible (true) prints its bounds and the stack frames that led to it, so the source of the
 * second paint pass is visible. Arg 0: number of deltas (default 200).
 */
public class ShowPaintProbe {
	static long nextId = 1_000_000L;

	public static void main (String [] args) {
		int deltas = args.length > 0 ? Integer.parseInt (args [0]) : 200;
		Display display = new Display ();
		Shell shell = new Shell (display);
		shell.setBounds (20, 20, 640, 420);
		shell.open ();
		Table model = new Table (shell, SWT.MULTI | SWT.FULL_SELECTION | SWT.BORDER);
		model.setBounds (5, 5, 300, 380);
		for (int i = 0; i < 2000; i++) new TableItem (model, SWT.NONE).setText ("row " + i);
		model.setSelection (new int [] {100, 101, 1500});
		model.setTopIndex (900);
		boolean [] shown = {false};
		int [] paints = {0};
		model.addListener (SWT.Paint, event -> {
			paints [0]++;
			if (!shown [0]) return;
			System.out.println ("PAINT " + paints [0] + " bounds " + event.x + "," + event.y + " " + event.width + "x" + event.height + " count " + event.count);
			StackTraceElement [] stack = new Throwable ().getStackTrace ();
			for (int i = 1; i < Math.min (stack.length, 40); i++) System.out.println ("  at " + stack [i]);
		});
		while (display.readAndDispatch ()) { }
		model.setVisible (false);
		while (display.readAndDispatch ()) { }
		Random random = new Random (0x41DE);
		for (int i = 0; i < deltas; i++) {
			int count = model.getItemCount ();
			int at = random.nextInt (count);
			M3Patch.Builder builder = M3Patch.builder (M3Model.ROOT);
			if (random.nextBoolean () || count < 200) {
				int n = 1 + random.nextInt (3);
				long [] ids = new long [n];
				for (int k = 0; k < n; k++) ids [k] = nextId++;
				builder.insert (at, ids);
			} else {
				int removed = Math.min (1 + random.nextInt (3), count - at);
				if (removed == count) continue;
				builder.remove (at, removed);
			}
			M3Model.of (model).apply (builder.build (), (id, row) -> row.setText (0, "row " + id));
		}
		while (display.readAndDispatch ()) { }
		paints [0] = 0;
		shown [0] = true;
		System.out.println ("SHOW top " + model.getTopIndex ());
		model.setVisible (true);
		for (int i = 0; i < 3; i++) {
			while (display.readAndDispatch ()) { }
			System.out.println ("DRAIN " + i + " paints " + paints [0]);
		}
		System.out.println ("RESULT paints " + paints [0] + " top " + model.getTopIndex ());
		display.dispose ();
	}
}
