import java.lang.reflect.Method;
import org.eclipse.swt.SWT;
import org.eclipse.swt.internal.win32.OS;
import org.eclipse.swt.widgets.*;

/**
 * Official-SWT oracle for three viewport tests whose expectation conflicted with the implementation.
 * The same source runs against the official jar (oracle) and the candidate jar; it uses only API and
 * the package-private Tree.getFocusItem that both have.
 */
public class Oracle {
	static Display display;
	static Shell shell;

	public static void main (String [] args) throws Exception {
		display = new Display ();
		System.out.println ("ORACLE swt " + SWT.getVersion () + " " + SWT.getPlatform ());
		caretAfterShrink ();
		zeroHeightCaret ();
		hiddenTableTop ();
		display.dispose ();
	}

	static Shell shell () {
		if (shell != null) shell.dispose ();
		shell = new Shell (display);
		shell.setBounds (20, 20, 340, 460);
		return shell;
	}

	static void settle () {
		while (display.readAndDispatch ()) { }
	}

	static Tree tree (int style, int roots, boolean grandChildren) {
		Tree tree = new Tree (shell, style | SWT.VIRTUAL | SWT.BORDER);
		tree.setBounds (5, 5, 300, 380);
		tree.addListener (SWT.SetData, event -> {
			TreeItem item = (TreeItem) event.item;
			TreeItem parent = item.getParentItem ();
			item.setText ((parent == null ? "root " : parent.getText () + " / ") + event.index);
			if (grandChildren && parent == null && event.index % 7 == 0) item.setItemCount (20);
			if (grandChildren && parent != null && parent.getParentItem () == null && event.index % 5 == 0) item.setItemCount (6);
		});
		tree.setItemCount (roots);
		return tree;
	}

	static String focusText (Tree tree) throws Exception {
		Method m = Tree.class.getDeclaredMethod ("getFocusItem");
		m.setAccessible (true);
		TreeItem item = (TreeItem) m.invoke (tree);
		return item == null ? null : item.getText ();
	}

	/* Tree_ViewportCoverage.aSingleTreeSelectsTheCaretAfterRemoval */
	static void caretAfterShrink () throws Exception {
		shell ();
		Tree tree = tree (SWT.SINGLE, 40, false);
		shell.open ();
		settle ();
		tree.setSelection (tree.getItem (5));
		settle ();
		tree.getItem (5).dispose ();
		settle ();
		System.out.println ("ORACLE caretAfterShrink afterDispose selectionCount=" + tree.getSelectionCount () + " selection="
				+ (tree.getSelectionCount () > 0 ? tree.getSelection () [0].getText () : "-") + " focus=" + focusText (tree));
		tree.setItemCount (3);
		settle ();
		System.out.println ("ORACLE caretAfterShrink afterShrink selectionCount=" + tree.getSelectionCount () + " focus=" + focusText (tree));
		tree.setItemCount (0);
		settle ();
		System.out.println ("ORACLE caretAfterShrink empty focus=" + focusText (tree));
	}

	/* Tree_ViewportEdges.keysOnEmptyAndZeroHeightTrees, up to the first caret assertion */
	static void zeroHeightCaret () throws Exception {
		shell ();
		Button focusOwner = new Button (shell, SWT.PUSH);
		focusOwner.setBounds (310, 5, 24, 24);
		Tree flat = tree (SWT.MULTI, 50, true);
		flat.setSize (300, 0);
		shell.open ();
		focusOwner.setFocus ();
		settle ();
		OS.SendMessage (flat.handle, OS.WM_KEYDOWN, OS.VK_DOWN, 0);
		OS.SendMessage (flat.handle, OS.WM_KEYUP, OS.VK_DOWN, 0xC0000001L);
		settle ();
		flat.setSelection (flat.getItem (20));
		flat.setTopItem (flat.getItem (5));
		long caret = OS.SendMessage (flat.handle, OS.TVM_GETNEXTITEM, OS.TVGN_CARET, 0);
		System.out.println ("ORACLE zeroHeightCaret focus=" + focusText (flat) + " nativeCaret=" + (caret != 0) + " selection="
				+ (flat.getSelectionCount () > 0 ? flat.getSelection () [0].getText () : "-"));
	}

	/* Table_Splice.spliceAnswersLikeTheItemApi, the failing hidden scenario: 62 rows, top 43, remove 17..20, top -> 39 */
	static void hiddenTableTop () {
		shell ();
		shell.open ();
		Table table = new Table (shell, SWT.MULTI | SWT.BORDER);
		table.setBounds (5, 5, 300, 380);
		for (int i = 0; i < 62; i++) new TableItem (table, SWT.NONE).setText ("row " + i);
		table.setVisible (false);
		table.setTopIndex (43);
		display.readAndDispatch ();
		int before = table.getTopIndex ();
		table.remove (17, 20);
		int afterRemove = table.getTopIndex ();
		table.setTopIndex (39);
		int afterSet = table.getTopIndex ();
		table.setVisible (true);
		settle ();
		System.out.println ("ORACLE hiddenTableTop before=" + before + " afterRemove=" + afterRemove + " afterSetTopIndex39=" + afterSet
				+ " afterShow=" + table.getTopIndex () + " count=" + table.getItemCount ());
	}
}
