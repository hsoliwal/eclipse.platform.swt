# Sort and filter measurements (2026-10-19)

Machine: the shared programme workstation under load from other agents (memory headroom often below 2 GB), so
the numbers are indicative; medians of 7 runs for the micro benchmark. JDK 21, no coverage agent.

## Permutation build (micro benchmark, `bench/M3SortBench.java.txt`, raw: `measurements/bench-20261019.tsv`)

Microseconds per sort of rows `0 .. n-1`; every candidate's permutation is checked equal to the radix one.

| keys | n | radix (M3) | merge (M3 small path) | packed DPQS | parallel packed | TimSort Integer[] |
|---|---|---|---|---|---|---|
| int | 16 | 0.33 | 0.48 | 0.29 | 0.44 | 0.99 |
| int,int | 32 | 1.24 | 0.97 | - | - | 2.95 |
| int,int | 48 | 1.35 | 1.72 | - | - | 6.88 |
| int | 1024 | 91 | 160 | 22 | 20 | 165 |
| int,int | 1024 | 25 | 147 | - | - | 287 |
| int | 10^4 | 263 | 8100 | 1917 | 557 | 2991 |
| int,int | 10^4 | 367 | 2444 | - | - | 4003 |
| int | 10^5 | 1333 | 28292 | 8646 | 2121 | 54616 |
| int,int | 10^5 | 6764 | 47160 | - | - | 108102 |
| text | 10^4 | 3705 | 3795 | - | - | 4084 |

The run stopped at n = 10^5 when the JVM could not commit more memory (machine-wide paging limit), so the
10^6 rows come from the differential below. The text rows predate the distinct-text ranking.

## Filter + sort chain against the reference (`Test_M3SortFilterDifferential`, raw: `measurements/differential-20261019.tsv`)

Two criteria (text wildcard `w1*,w2*` and a negated contains on an int column), three sort keys (text ascending,
int descending, double ascending); the reference filters a `List<Integer>` with a predicate and sorts it with
`List.sort` and a comparator chain. Milliseconds; every step's result is asserted equal to the reference.

| step | 10^3 | 10^4 | 10^5 | 10^6 |
|---|---|---|---|---|
| reference: filter + sort | 12.6 | 6.0 | 62.7 | 628.3 |
| M3: two criteria (masks) | 2.1 | 3.9 | 33.3 | 134.5 |
| M3: three sort keys (radix, distinct-text ranks) | 2.6 | 6.1 | 21.0 | 73.5 |
| M3: 1000 single-row updates (total) | 6.4 | 33.4 | 55.7 | 477.2 |
| M3: 1% rows changed in one batch | 0.06 | 0.41 | 3.4 | 23.9 |
| M3: narrow then widen the text criterion | 0.66 | 6.3 | 25.5 | 118.5 |
| reference: rebuild after the updates | 0.56 | 5.8 | 55.9 | 425.4 |

Before the distinct-text ranking the three-key sort took 395 ms at 10^6 (all texts merge-sorted).
