# Culprits: where Eclipse spends its objects (user 2026-10-20)

Ranked by the user: collections first ("the number one culprit"; "Eclipse has not resisted the urge to put an
object every possible place"), then StringBuilder/StringBuffer, IO, regex, loops, method length. Counts are
main-code sites from pass four (`../code-mapping`), the method-length pass is `method_length_stream.sh`
(brace-depth over masked source, streamed from the bare forks; hard limit 15 body lines).

| Rank | Culprit | Main sites | Files | in e4 bundles | in EMF |
| --- | --- | --- | --- | --- | --- |
| 1 | collections (objects every possible place) | 27358 | 7324 | 744 | 1898 |
| 2 | StringBuilder, StringBuffer, concatenation | 90948 | 9411 | 2062 | 21987 |
| 3 | IO | 7481 | 1622 | 113 | 206 |
| 4 | regex | 3005 | 1031 | 117 | 369 |
| 5 | loops (hard limit: banned) | 60073 | 10310 | 1295 | 6189 |

| Repo | Methods over 15 lines | over 50 | over 100 | Longest |
| --- | --- | --- | --- | --- |
| eclipse.platform.ui | 7685 | 1120 | 248 | doSwitch 530 |
| eclipse.jdt.ui | 6703 | 1128 | 260 | process 607 |
| org.eclipse.emf | 6455 | 1574 | 554 | generate 4771 |
| eclipse.platform | 6141 | 821 | 154 | createKeyManagementPage 543 |
| eclipse.platform.swt | 6054 | 1351 | 396 | create 830 |
| eclipse.jdt.core | 5774 | 1466 | 454 | consumeRule 1703 |
| initiative31-eclipse.pde | 5090 | 791 | 179 | processComponent 816 |
| nebula | 3188 | 674 | 191 | getVisitor 778 |
| equinox | 2030 | 294 | 40 | parseActions 205 |
| p2 | 1751 | 267 | 51 | traverse 989 |
| egit | 1746 | 309 | 97 | createPartControl 706 |
| eclipse.jdt.debug | 1565 | 238 | 44 | visit 333 |
| gef-classic | 1141 | 148 | 24 | layout 522 |
| m2e-core | 1030 | 175 | 42 | initializePackageContents 630 |
| eclipse.platform.releng.aggregator | 63 | 8 | 4 | describeJavaTarget 105 |

Files: `CULPRITS.tsv` (the ranking and its lanes), `CULPRITS_COUNTS.tsv`, `CULPRITS_BY_REPO.tsv`,
`METHOD_LENGTH.tsv.gz` (every method over the limit: repo, path, line, method, body lines),
`METHOD_LENGTH_SUMMARY.tsv`. The lane order of the transform recipes follows this ranking: collections, m3string,
m3file, precompute, loops into lanes, method split (brief rule 32; LANES.tsv in `../plan`).
