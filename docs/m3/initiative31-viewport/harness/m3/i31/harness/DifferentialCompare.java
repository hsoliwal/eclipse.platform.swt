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
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Compares two differential records (the reference, then the candidate) line by line.
 * <p>
 * Columns <code>0..compared-1</code> must be equal, except the divergences listed in an optional accept file: tab
 * separated rows <code>operation-regex, column, defect-id</code>, one per divergence of
 * <code>UPSTREAM_DEFECTS.tsv</code>. A listed column of a step whose operation matches is counted under its defect,
 * not failed. Columns from <code>compared</code> on are always accepted divergences (counted). The last column is
 * the number of SetData calls; the report sums it for both sides.
 * </p>
 * <p>
 * Usage: <code>DifferentialCompare &lt;reference&gt; &lt;candidate&gt; &lt;compared-columns&gt; &lt;report&gt; [accept-file]</code>;
 * the exit status is 1 when a compared column differs.
 * </p>
 */
public final class DifferentialCompare {
	private DifferentialCompare () {
	}

	/** A divergence the comparison accepts: a column of the steps whose operation matches. */
	private static final class Accept {
		final Pattern operation;
		final int column;
		final String defect;

		Accept (String [] row) {
			operation = Pattern.compile (row [0]);
			column = Integer.parseInt (row [1]);
			defect = row [2];
		}
	}

	public static void main (String [] args) throws IOException {
		List<String> reference = Files.readAllLines (Paths.get (args [0]), StandardCharsets.UTF_8);
		List<String> candidate = Files.readAllLines (Paths.get (args [1]), StandardCharsets.UTF_8);
		int compared = Integer.parseInt (args [2]);
		List<Accept> accepts = new ArrayList<> ();
		if (args.length > 4) {
			for (String row : Files.readAllLines (Paths.get (args [4]), StandardCharsets.UTF_8)) {
				if (!row.isBlank () && !row.startsWith ("#")) accepts.add (new Accept (row.split ("\t")));
			}
		}
		List<String> report = new ArrayList<> ();
		Map<String, Integer> byDefect = new TreeMap<> ();
		int mismatches = 0;
		int accepted = 0;
		long setDataReference = 0;
		long setDataCandidate = 0;
		int lines = Math.max (reference.size (), candidate.size ());
		for (int i = 0; i < lines; i++) {
			String [] a = i < reference.size () ? reference.get (i).split ("\t", -1) : new String [0];
			String [] b = i < candidate.size () ? candidate.get (i).split ("\t", -1) : new String [0];
			boolean mismatch = false;
			for (int c = 0; c < compared; c++) {
				if (same (a, b, c)) continue;
				String defect = acceptedBy (accepts, a, c);
				if (defect == null) {
					mismatch = true;
				} else {
					byDefect.merge (defect, 1, Integer::sum);
				}
			}
			if (mismatch) {
				if (mismatches++ < 20) report.add ("MISMATCH line " + (i + 1) + "\n  reference: " + String.join (" | ", a) + "\n  candidate: " + String.join (" | ", b));
				continue;
			}
			for (int c = compared; c < Math.max (a.length, b.length) - 1; c++) {
				if (!same (a, b, c)) {
					accepted++;
					break;
				}
			}
			setDataReference += last (a);
			setDataCandidate += last (b);
		}
		report.add (0, "lines=" + lines + " mismatches=" + mismatches + " accepted_divergences=" + accepted + " accepted_by_defect=" + byDefect
			+ " setdata_sum_reference=" + setDataReference + " setdata_sum_candidate=" + setDataCandidate);
		Files.write (Paths.get (args [3]), report, StandardCharsets.UTF_8);
		System.exit (mismatches == 0 ? 0 : 1);
	}

	private static String acceptedBy (List<Accept> accepts, String [] reference, int column) {
		String operation = reference.length > 2 ? reference [2] : "";
		for (Accept accept : accepts) {
			if (accept.column == column && accept.operation.matcher (operation).find ()) return accept.defect;
		}
		return null;
	}

	private static boolean same (String [] a, String [] b, int c) {
		String x = c < a.length ? a [c] : null;
		String y = c < b.length ? b [c] : null;
		return x == null ? y == null : x.equals (y);
	}

	private static long last (String [] columns) {
		try {
			return columns.length == 0 ? 0 : Long.parseLong (columns [columns.length - 1]);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
