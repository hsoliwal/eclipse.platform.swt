# Serial file visits (multipass, then visit)

Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
SPDX-License-Identifier: Apache-2.0 OR EPL-2.0

User, 2026-10-20: "multipass, fork any Eclipse project, make its files serial using multipass and then visit them",
"who knows, you might find input to enhance all APIs even further, maybe even new widgets".

## The queue

FILE_QUEUE.tsv holds every Java file with marked sites across the forked Eclipse projects: 1,614 files in 12 forks,
built by multipass_files.sh from docs/m3/correction-sites. Each file has a rank, sites per caller class, a priority
score and a permalink.
- Score: 10 x model-listener rebuild, 4 x refresh method, 3 x state-setter rebuild, 3 x DOOP store, 2 x DOOP object,
  2 x thread blocking, 2 x query-path allocation, 1 x review.
- Adding a project: `bash multipass_files.sh <out> <owner/repo>`. It forks the project under hsoliwal if missing
  (standing authorization), clones it shallow, runs the passes and re-ranks the queue.

## A visit (serial, highest rank first, resumable)

1. Take the lowest unvisited rank from FILE_QUEUE.tsv. A visit is claimed by appending a VISIT_LOG.tsv row with
   verdict CLAIMED, so two visitors never take the same file.
2. Read the whole file (NO-SHALLOW). Confirm or reject every marked site; note sites the grep missed.
3. Record one VISIT_LOG.tsv row with these fields:
   - read_lines, sites_confirmed, sites_rejected;
   - verdict: CONVERT_VIA_TEMPLATE, ADAPTER_NEEDED, JUSTIFIED or NOT_A_DEFECT;
   - fix_pattern and owner;
   - behaviour_candidates: decorator, autocomplete, validator, formatter, constrainer, enabler, mover,
     expand-to-level, scroll-to-selection or anything new, per docs/m3/behaviours/BEHAVIOURS.md;
   - api_candidates: what the file had to hand-code that the toolkit should offer;
   - notes.
4. A new behaviour or API candidate seen in 3 or more files gets a row in BEHAVIOURS.md (or the enhanced API map), with an owner.
5. Commit and push after every 10 visits, or sooner.

The log is append-only. Counts per verdict and the top behaviour candidates feed the template feature list
(docs/m3/view-template/VIEW_TEMPLATE.md) and the owners' next leaves.
