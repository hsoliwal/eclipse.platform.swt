/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
package m3.i31.harness;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Compares two differential records (the reference, then the candidate) line by line.
 * <p>
 * Columns <code>0..compared-1</code> must be equal. The remaining columns are accepted divergences, listed in
 * <code>UPSTREAM_DEFECTS.tsv</code>: they are counted, not failed. The last column is the SetData count; the
 * report sums it for both sides.
 * </p>
 * <p>
 * Usage: <code>DifferentialCompare &lt;reference&gt; &lt;candidate&gt; &lt;compared-columns&gt; &lt;report&gt;</code>;
 * the exit status is 1 when a compared column differs.
 * </p>
 */
public final class DifferentialCompare {
	private DifferentialCompare () {
	}

	public static void main (String [] args) throws IOException {
		List<String> reference = Files.readAllLines (Paths.get (args [0]), StandardCharsets.UTF_8);
		List<String> candidate = Files.readAllLines (Paths.get (args [1]), StandardCharsets.UTF_8);
		int compared = Integer.parseInt (args [2]);
		List<String> report = new ArrayList<> ();
		int mismatches = 0;
		int accepted = 0;
		long setDataReference = 0;
		long setDataCandidate = 0;
		int lines = Math.max (reference.size (), candidate.size ());
		for (int i = 0; i < lines; i++) {
			String [] a = i < reference.size () ? reference.get (i).split ("\t", -1) : new String [0];
			String [] b = i < candidate.size () ? candidate.get (i).split ("\t", -1) : new String [0];
			if (!same (a, b, 0, compared)) {
				if (mismatches++ < 20) report.add ("MISMATCH line " + (i + 1) + "\n  reference: " + String.join (" | ", a) + "\n  candidate: " + String.join (" | ", b));
				continue;
			}
			if (!same (a, b, compared, Math.max (a.length, b.length) - 1)) accepted++;
			setDataReference += last (a);
			setDataCandidate += last (b);
		}
		report.add (0, "lines=" + lines + " mismatches=" + mismatches + " accepted_divergences=" + accepted + " setdata_sum_reference=" + setDataReference + " setdata_sum_candidate=" + setDataCandidate);
		Files.write (Paths.get (args [3]), report, StandardCharsets.UTF_8);
		System.exit (mismatches == 0 ? 0 : 1);
	}

	private static boolean same (String [] a, String [] b, int from, int to) {
		for (int c = from; c < to; c++) {
			String x = c < a.length ? a [c] : null;
			String y = c < b.length ? b [c] : null;
			if (x == null ? y != null : !x.equals (y)) return false;
		}
		return true;
	}

	private static long last (String [] columns) {
		try {
			return columns.length == 0 ? 0 : Long.parseLong (columns [columns.length - 1]);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
