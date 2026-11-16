/* SPDX-License-Identifier: Apache-2.0 OR EPL-2.0 */
package org.eclipse.swt.widgets;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.List;

/** Exercises original VirtualListModel/VirtualListWidth, with only the native planner stubbed. */
public final class VirtualListSparseDifferential {
    private static final Field TEXTS;
    private static final Field ROW_IDS;
    private static final Field RESIDENT;
    static {
        try {
            TEXTS = VirtualListModel.class.getDeclaredField("items");
            ROW_IDS = VirtualListModel.class.getDeclaredField("itemRows");
            RESIDENT = VirtualListModel.class.getDeclaredField("materialized");
            TEXTS.setAccessible(true);
            ROW_IDS.setAccessible(true);
            RESIDENT.setAccessible(true);
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
    private static long steps;

    private static void require(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }

    private static void assertSparse(VirtualListModel model, List<String> expected)
            throws ReflectiveOperationException {
        require(model.getItemCount() == expected.size(), "logical count at " + steps);
        String[] actual = model.getItems();
        require(Arrays.equals(actual, expected.toArray(new String[0])), "text mismatch at " + steps);
        String[] stored = (String[]) TEXTS.get(model);
        int[] ids = (int[]) ROW_IDS.get(model);
        int used = RESIDENT.getInt(model);
        require(used >= 0 && used <= ids.length && used <= stored.length, "invalid residency");
        int nonNull = 0;
        for (int index = 0; index < expected.size(); index++) {
            String text = expected.get(index);
            require(java.util.Objects.equals(text, model.getItem(index)), "item " + index + " step " + steps);
            require(model.isMaterialized(index) == (text != null), "resident status " + index);
            if (text != null) nonNull++;
        }
        require(used == nonNull, "resident count mismatch");
        for (int index = 0; index < used; index++) {
            require(ids[index] >= 0 && ids[index] < expected.size(), "invalid logical row");
            require(index == 0 || ids[index - 1] < ids[index], "unsorted row IDs");
            require(java.util.Objects.equals(expected.get(ids[index]), stored[index]), "row-to-text mapping");
        }
        require(model.indexOf("v7", 0) == expected.indexOf("v7"), "indexed search mismatch");
        steps++;
    }

    private static void hugeSparse() throws ReflectiveOperationException {
        VirtualListModel model = new VirtualListModel(String::length, true);
        model.setGeometry(20, 100);
        model.setItemCount(100_000_000);
        require(((String[]) TEXTS.get(model)).length < 64, "100m logical rows allocated dense texts");
        require(((int[]) ROW_IDS.get(model)).length < 64, "100m logical rows allocated dense IDs");
        require(RESIDENT.getInt(model) == 0, "empty list must have no text objects");
        require(model.getItemCount() == 100_000_000, "lost logical cardinality");
        require(model.getItem(99_999_999) == null, "unset last item");
        model.setItem(99_999_999, "last");
        model.setItem(1, "first");
        model.insert(2, new String[] { "head", "mid" });
        require(((String[]) TEXTS.get(model)).length < 64, "sparse edits overallocated");
        require("last".equals(model.getItem(100_000_001)), "last row shifted");
        require("mid".equals(model.getItem(3)), "inserted row");
        model.remove(3, 99_999_999);
        require(model.getItemCount() == 5, "range removal cardinality");
        require(Arrays.equals(new String[] { null, "first", "head", null, "last" },
                model.getItems()), "range removal text identity");
        model.clear(1);
        require(!model.isMaterialized(1), "clear retained text");
        model.clearAll();
        require(RESIDENT.getInt(model) == 0, "clearAll retained resident slots");
        require(((int[]) ROW_IDS.get(model)).length == 0, "clearAll retained row IDs");
        require(model.getItemCount() == 5, "clearAll must preserve logical count");
    }

    private static void randomSplices() throws ReflectiveOperationException {
        for (int seed = 1; seed <= 20; seed++) {
            VirtualListModel model = new VirtualListModel(String::length, true);
            ArrayList<String> expected = new ArrayList<>();
            Random random = new Random(0x55e7a100L + seed);
            for (int step = 0; step < 800; step++) {
                int count = expected.size();
                switch (random.nextInt(9)) {
                    case 0 -> {
                        int at = random.nextInt(count + 1);
                        String text = "v" + random.nextInt(40);
                        model.add(text, at);
                        expected.add(at, text);
                    }
                    case 1 -> {
                        int at = random.nextInt(count + 1);
                        String[] inserted = { "q" + random.nextInt(20), "q" + random.nextInt(20) };
                        model.insert(at, inserted);
                        expected.addAll(at, Arrays.asList(inserted));
                    }
                    case 2 -> {
                        if (count > 0) {
                            int at = random.nextInt(count);
                            String text = "v" + random.nextInt(40);
                            model.setItem(at, text);
                            expected.set(at, text);
                        }
                    }
                    case 3 -> {
                        if (count > 0) {
                            int at = random.nextInt(count);
                            model.clear(at);
                            expected.set(at, null);
                        }
                    }
                    case 4 -> {
                        if (count > 0) {
                            int start = random.nextInt(count);
                            int end = start + random.nextInt(count - start);
                            model.remove(start, end);
                            expected.subList(start, end + 1).clear();
                        }
                    }
                    case 5 -> {
                        int next = random.nextInt(180);
                        model.setItemCount(next);
                        while (expected.size() > next) expected.remove(expected.size() - 1);
                        while (expected.size() < next) expected.add(null);
                    }
                    case 6 -> {
                        model.clearAll();
                        java.util.Collections.fill(expected, null);
                    }
                    case 7 -> {
                        model.removeAll();
                        expected.clear();
                    }
                    case 8 -> {
                        String[] items = new String[random.nextInt(20)];
                        for (int i = 0; i < items.length; i++) items[i] = "s" + random.nextInt(30);
                        model.setItems(items);
                        expected.clear();
                        expected.addAll(Arrays.asList(items));
                    }
                    default -> throw new AssertionError();
                }
                assertSparse(model, expected);
            }
        }
    }

    private static void eagerCompatibility() {
        VirtualListModel model = new VirtualListModel(String::length, false);
        model.add("alpha");
        model.add("gamma");
        model.add("beta", 1);
        require(Arrays.equals(new String[] { "alpha", "beta", "gamma" }, model.getItems()),
                "original eager insertion");
        model.insert(2, new String[] { "x", "y" });
        require(Arrays.equals(new String[] { "alpha", "beta", "x", "y", "gamma" },
                model.getItems()), "original eager batch insertion");
        model.remove(1, 3);
        require(Arrays.equals(new String[] { "alpha", "gamma" }, model.getItems()),
                "original eager removal");
        require(model.indexOf("gamma", 0) == 1, "original eager indexed search");
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        hugeSparse();
        randomSplices();
        eagerCompatibility();
        require(steps == 16_000L, "unexpected reference coverage: " + steps);
        System.out.println("PASS SWT VirtualListModel/Width: 100m-row sparse + "
                + steps + " randomized differential edits + eager compatibility");
    }
}
