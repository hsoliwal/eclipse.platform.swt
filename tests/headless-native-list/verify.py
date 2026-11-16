#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
"""Compile original native List.setItemCount bodies in isolated Java contract peers."""
import pathlib
import subprocess
import sys
import tempfile

TOKEN = 'public void setItemCount (int count) {'


def method_from(source):
    if source.count(TOKEN) != 1:
        raise RuntimeError('Expected exactly one native List.setItemCount declaration')
    start = source.index(TOKEN)
    brace = source.index('{', start)
    depth, state, escape = 0, 'normal', False
    i = brace
    while i < len(source):
        ch = source[i]
        nxt = source[i + 1] if i + 1 < len(source) else ''
        if state == 'line':
            if ch == '\n':
                state = 'normal'
        elif state == 'block':
            if ch == '*' and nxt == '/':
                state = 'normal'
                i += 1
        elif state in ('string', 'character'):
            if escape:
                escape = False
            elif ch == '\\':
                escape = True
            elif ch == ('"' if state == 'string' else "'"):
                state = 'normal'
        elif ch == '/' and nxt == '/':
            state = 'line'
            i += 1
        elif ch == '/' and nxt == '*':
            state = 'block'
            i += 1
        elif ch == '"':
            state = 'string'
        elif ch == "'":
            state = 'character'
        elif ch == '{':
            depth += 1
        elif ch == '}':
            depth -= 1
            if depth == 0:
                return source[start:i + 1]
        i += 1
    raise RuntimeError('Unterminated setItemCount method')


COCOA_PREFIX = '''public final class CocoaProof {
    static final class List {
        String[] items = { "a", "b", "c" };
        int itemCount = 3;
        int updates, widthUpdates, removals;
        void checkWidget() { }
        int getItemCount() { return itemCount; }
        void remove(int start, int end) {
            if (start < 0 || end >= itemCount || end < start) throw new AssertionError("bad shrink");
            System.arraycopy(items, end + 1, items, start, itemCount - end - 1);
            itemCount -= end - start + 1;
            removals++;
        }
        void updateRowCount() { updates++; }
        void setScrollWidth(String text) { if (!"".equals(text)) throw new AssertionError(); widthUpdates++; }
'''
COCOA_SUFFIX = '''
    }
    public static void main(String[] args) {
        List list = new List();
        int allocations = 0;
        for (int count = 4; count <= 10_000; count++) {
            String[] previous = list.items;
            list.setItemCount(count);
            if (list.items != previous) allocations++;
            if (list.itemCount != count || !"b".equals(list.items[1])
                    || !"".equals(list.items[count - 1])) throw new AssertionError("Cocoa count/text");
            if ((long) list.items.length - count > 65_536L) throw new AssertionError("excess spare slots");
        }
        if (allocations >= 100) throw new AssertionError("excess copies: " + allocations);
        if (list.updates != 9_997 || list.widthUpdates != 9_997) throw new AssertionError("native update count");
        list.items[9_997] = "edited";
        list.setItemCount(10_005);
        if (!"edited".equals(list.items[9_997])) throw new AssertionError("lost late edit");
        list.setItemCount(10_000);
        if (list.removals != 1 || list.itemCount != 10_000) throw new AssertionError("shrink behavior");
        System.out.println("PASS Cocoa original-method count/text/capacity: " + allocations + " growth allocations");
    }
}
'''

GTK_PREFIX = '''public final class GtkProof {
    static final class SWT { static final int ERROR_ITEM_NOT_ADDED = 10; }
    static final class Converter {
        static int conversions;
        static byte[] wcsToMbcs(String value, boolean nul) {
            if (!"".equals(value) || !nul) throw new AssertionError("encoding");
            conversions++;
            return new byte[] {0};
        }
    }
    static final class OS {
        static long nextPointer;
        static int allocations, releases;
        static long g_malloc(int n) { if (n <= 0) throw new AssertionError(); allocations++; return ++nextPointer; }
        static void g_free(long pointer) { if (pointer <= 0) throw new AssertionError(); releases++; }
    }
    static final class GTK {
        static int rows = 3, appends, writes;
        static int GtkTreeIter_sizeof() { return 16; }
        static void gtk_list_store_append(long model, long iter) {
            if (model != 1L || iter <= 0) throw new AssertionError("native owner");
            rows++;
            appends++;
        }
        static void gtk_list_store_set(long model, long iter, int column, byte[] data, int terminator) {
            if (model != 1L || iter <= 0 || column != 0 || terminator != -1
                    || data.length != 1 || data[0] != 0) throw new AssertionError("empty native text");
            writes++;
        }
    }
    static final class List {
        static final int TEXT_COLUMN = 0;
        long modelHandle = 1L;
        int checks, removals;
        void checkWidget() { checks++; }
        int getItemCount() { return GTK.rows; }
        void remove(int first, int last) { GTK.rows -= last - first + 1; removals++; }
        void error(int code) { throw new AssertionError("GTK allocation error: " + code); }
'''
GTK_SUFFIX = '''
    }
    public static void main(String[] args) {
        List list = new List();
        list.setItemCount(2_048);
        if (GTK.rows != 2_048 || GTK.appends != 2_045 || GTK.writes != 2_045
                || Converter.conversions != 1 || OS.allocations != 1 || OS.releases != 1
                || list.checks != 2_046) throw new AssertionError("not batched at native boundary");
        list.setItemCount(2_048);
        if (OS.allocations != 1 || Converter.conversions != 1) throw new AssertionError("unchanged count churn");
        list.setItemCount(2_050);
        if (GTK.rows != 2_050 || GTK.appends != 2_047 || GTK.writes != 2_047
                || OS.allocations != 2 || OS.releases != 2 || Converter.conversions != 2)
            throw new AssertionError("second batch behavior");
        list.setItemCount(2_048);
        if (GTK.rows != 2_048 || list.removals != 1 || OS.allocations != 2)
            throw new AssertionError("GTK shrink behavior");
        System.out.println("PASS GTK original-method native-call-counts: " + GTK.appends + " native row appends / 2 iter allocations");
    }
}
'''


def main(root):
    swt = pathlib.Path(root)
    common = swt / 'bundles/org.eclipse.swt/Eclipse SWT'
    pairs = [
        ('cocoa', common / 'cocoa/org/eclipse/swt/widgets/List.java', COCOA_PREFIX, COCOA_SUFFIX, 'CocoaProof'),
        ('gtk', common / 'gtk/org/eclipse/swt/widgets/List.java', GTK_PREFIX, GTK_SUFFIX, 'GtkProof'),
    ]
    with tempfile.TemporaryDirectory() as temporary:
        directory = pathlib.Path(temporary)
        for family, source, prefix, suffix, cls in pairs:
            body = method_from(source.read_text(encoding='utf-8'))
            (directory / (cls + '.java')).write_text(prefix + body + suffix, encoding='utf-8')
            subprocess.run(['javac', '--release', '17', '-Xlint:all', '-Werror',
                            '-d', str(directory), str(directory / (cls + '.java'))], check=True)
            subprocess.run(['java', '-ea', '-cp', str(directory), cls], check=True)
        print('PASS actual Cocoa/GTK List method bodies; native platform behavior NOT verified')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('usage: verify.py <SWT repository root>')
    main(sys.argv[1])
