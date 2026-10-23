#!/usr/bin/env bash
# Serial multipass file queue (user 2026-10-20: "multipass, fork any eclipse project, make its files serial using
# multipass and then visit them"). For each repo (owner/name): fork it under hsoliwal if missing (standing
# authorization 2026-10-20), run the correction-site passes (correction_sites.sh), then aggregate per file into a
# priority-ordered FILE_QUEUE.tsv. Files are visited serially, highest priority first, and each visit is appended to
# VISIT_LOG.tsv (see VISIT_PROTOCOL.md).
# Usage: multipass_files.sh <out-dir> <owner/repo>...   (an existing hsoliwal fork name works too: eclipse.platform.ui)
OUT=$1; shift; mkdir -p "$OUT"
for spec in "$@"; do
  case "$spec" in */*) up=$spec; name=${spec#*/};; *) up=""; name=$spec;; esac
  if [ ! -d "S:/m3-scratch/forks/$name.git" ] && ! gh api "repos/hsoliwal/$name" >/dev/null 2>&1; then
    [ -n "$up" ] || { echo "no fork and no upstream for $name" >&2; continue; }
    timeout 180 gh repo fork "$up" --clone=false --default-branch-only >/dev/null 2>&1 || { echo "fork failed $up" >&2; continue; }
    sleep 3; echo "forked $up -> hsoliwal/$name" >&2
  fi
  bash C:/m3-superset/tools/correction_sites.sh "$OUT/$name" "$name" >/dev/null 2>"$OUT/$name.log"
done
# aggregate all repos into one serial queue; score weights follow the fix priority of correction_classes.awk
printf 'rank\trepo\tpath\tscore\tsites\tmodel_listener\tasync_refresh\tstate_setter\tdoop_store\tdoop_objects\tthread_blocking\tquery_alloc\treview\tjustified\tfirst_permalink\n' > "$OUT/FILE_QUEUE.tsv"
cat "$OUT"/*/CORRECTION_SITES.tsv 2>/dev/null | awk -F'\t' -v OFS='\t' '
  $1 == "repo" { next }
  { k = $1 "\t" $5; n[k]++; if (!(k in perm)) perm[k] = $7; c = $3
    if (c == "MODEL_LISTENER") ml[k]++; else if (c == "ASYNC_OR_REFRESH_METHOD") ar[k]++; else if (c == "STATE_SETTER_REBUILD") ss[k]++
    else if (c == "DOOP_STORE") ds[k]++; else if (c == "DOOP_OBJECTS") dob[k]++; else if (c == "THREAD_BLOCKING") tb[k]++
    else if (c == "QUERY_PATH_ALLOCATION") qa[k]++; else if (c == "REVIEW") rv[k]++; else js[k]++ }
  END { for (k in n) { s = 10*ml[k] + 4*ar[k] + 3*ss[k] + 3*ds[k] + 2*dob[k] + 2*tb[k] + 2*qa[k] + rv[k]
          print s, k, n[k], ml[k]+0, ar[k]+0, ss[k]+0, ds[k]+0, dob[k]+0, tb[k]+0, qa[k]+0, rv[k]+0, js[k]+0, perm[k] } }' |
  sort -t$'\t' -k1,1nr -k2,2 -k3,3 | awk -F'\t' -v OFS='\t' '{ r = NR; print r, $2, $3, $1, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14 }' >> "$OUT/FILE_QUEUE.tsv"
[ -f "$OUT/VISIT_LOG.tsv" ] || printf 'visited_at\trank\trepo\tpath\tvisitor\tread_lines\tsites_confirmed\tsites_rejected\tverdict\tfix_pattern\towner\tnotes\n' > "$OUT/VISIT_LOG.tsv"
echo "queue: $(($(wc -l < "$OUT/FILE_QUEUE.tsv")-1)) files" >&2
