/* SPDX-License-Identifier: EPL-2.0 */
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.swt.internal.mobile.MobileWidgetRuntime;
import org.eclipse.swt.internal.mobile.MobileWidgetRuntime.*;

public final class MobileWidgetRuntimeTest {
	static final class FakeHost implements Host {
		final Thread ui = Thread.currentThread();
		final List<Runnable> queue = new ArrayList<>();
		final Set<Long> live = new HashSet<>();
		final List<Long> removed = new ArrayList<>();
		EventSink sink;
		boolean failCreate;
		volatile java.util.concurrent.CountDownLatch posted;
		int boundsUpdates;
		@Override public boolean isUiThread() { return Thread.currentThread() == ui; }
		@Override public synchronized void post(Runnable task) {
			queue.add(task);
			if (posted != null) posted.countDown();
		}
		@Override public void attach(EventSink sink) { this.sink = sink; }
		@Override public void create(long id, long parent, Kind kind) {
			if (failCreate) { failCreate = false; throw new IllegalStateException("forced creation failure"); }
			if (parent != 0 && !live.contains(parent)) throw new AssertionError("orphan");
			if (!live.add(id)) throw new AssertionError("duplicate handle");
		}
		@Override public void setBounds(long id, Bounds bounds) { boundsUpdates++; }
		@Override public void setText(long id, String text) { }
		@Override public void setEnabled(long id, boolean value) { }
		@Override public void setVisible(long id, boolean value) { }
		@Override public void setAccessibilityLabel(long id, String value) { }
		@Override public void remove(long id) { removed.add(id); live.remove(id); }
		synchronized void drain() {
			while (!queue.isEmpty()) queue.remove(0).run();
		}
	}
	static void check(boolean b, String what) { if (!b) throw new AssertionError(what); }
	static void expect(Runnable runnable) {
		try { runnable.run(); throw new AssertionError("expected exception"); }
		catch (IllegalStateException | IllegalArgumentException ok) { }
	}
	public static void main(String[] args) throws Exception {
		FakeHost host = new FakeHost();
		MobileWidgetRuntime runtime = new MobileWidgetRuntime(host);
		MobileWidgetRuntime.Node shell = runtime.createShell();
		MobileWidgetRuntime.Node composite = shell.createChild(Kind.COMPOSITE);
		MobileWidgetRuntime.Node text = composite.createChild(Kind.TEXT);
		MobileWidgetRuntime.Node button = composite.createChild(Kind.BUTTON);
		check(host.live.size() == 4, "create native nodes");
		check(text.id() != button.id(), "unique stable IDs");
		text.setBounds(new Bounds(2, 3, 100, 30));
		text.setBounds(new Bounds(2, 3, 100, 30));
		check(host.boundsUpdates == 1, "skip equal bounds");
		expect(() -> text.setBounds(new Bounds(0, 0, -1, 1)));
		text.setText("programmatic");
		ArrayList<String> modifications = new ArrayList<>();
		text.on(EventType.MODIFY, e -> modifications.add(e.text()));
		host.sink.accept(new Event(text.id(), EventType.MODIFY, "typed", 0, 0));
		check(text.text().equals("typed") && modifications.equals(List.of("typed")), "text callback");
		button.on(EventType.SELECT, e -> button.close());
		host.sink.accept(new Event(button.id(), EventType.SELECT, null, 0, 0));
		check(button.isDisposed() && host.live.size() == 3, "reentrant dispose");
		runtime.setForeground(false);
		host.sink.accept(new Event(text.id(), EventType.MODIFY, "ignored", 0, 0));
		check(text.text().equals("typed"), "background input fenced");
		runtime.setForeground(true);
		host.failCreate = true;
		expect(() -> composite.createChild(Kind.LABEL));
		check(composite.children().size() == 1 && host.live.size() == 3, "creation rollback");
		final boolean[] async = {false};
		runtime.asyncExec(() -> async[0] = true);
		check(!async[0], "async does not run inline");
		host.drain();
		check(async[0], "async dispatched on UI");
		final boolean[] wrongThread = {false};
		Thread thread = new Thread(() -> {
			try { text.setText("bad"); } catch (IllegalStateException expected) { wrongThread[0] = true; }
		});
		thread.start(); thread.join();
		check(wrongThread[0], "UI-thread enforcement");
		long oldId = text.id();
		composite.close();
		check(!host.live.contains(oldId) && host.live.size() == 1, "descendant disposal");
		check(host.removed.indexOf(oldId) < host.removed.indexOf(composite.id()), "postorder native teardown");
		// A syncExec caller queued on a background thread must be released when
		// the UI closes without draining the queue (e.g. Android Activity teardown).
		java.util.concurrent.CountDownLatch posted = new java.util.concurrent.CountDownLatch(1);
		host.posted = posted;
		java.util.concurrent.atomic.AtomicBoolean didRun = new java.util.concurrent.atomic.AtomicBoolean();
		java.util.concurrent.atomic.AtomicBoolean canceled = new java.util.concurrent.atomic.AtomicBoolean();
		Thread waiter = new Thread(() -> {
			try { runtime.syncExec(() -> didRun.set(true)); }
			catch (IllegalStateException expected) { canceled.set(true); }
		}, "mobile-sync-waiter");
		waiter.start();
		check(posted.await(5, java.util.concurrent.TimeUnit.SECONDS), "syncExec task enqueued");
		runtime.close();
		waiter.join(5000);
		check(!waiter.isAlive() && canceled.get() && !didRun.get(),
				"dispose cancels pending syncExec without hanging its caller");
		host.posted = null;
		check(host.live.isEmpty() && runtime.lifecycle() == Lifecycle.DISPOSED, "display teardown");
		runtime.asyncExec(() -> { throw new AssertionError("disposed callback"); });
		host.drain();
		System.out.println("MobileWidgetRuntimeTest PASSED");
	}
}
