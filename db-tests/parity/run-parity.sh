#!/usr/bin/env bash
# Prototype-vs-database parity test.
#   1. node runs the HTML prototype's own logic on its demo data (+ bonus, + a class change) and exports it
#   2. a throw-away PostgreSQL gets every migration and the exported data
#   3. python compares the prototype's numbers with the database views, number by number
# usage: ./run-parity.sh [path/to/prototype.html]     (default: ./prototype.html)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
HTML="${1:-$HERE/prototype.html}"
MIG="$HERE/../../src/main/resources/db/migration"
PGBIN="${PGBIN:-$(ls -d /usr/lib/postgresql/*/bin 2>/dev/null | sort -V | tail -1)}"
[ -x "$PGBIN/initdb" ] || PGBIN="$(dirname "$(command -v initdb)")"
PORT="${PGPORT:-54331}"
DATA="$(mktemp -d)"; WORK="$(mktemp -d)"
RUN=""
if [ "$(id -u)" = "0" ]; then chown postgres "$DATA"; RUN="su postgres -c"; fi
as() { if [ -n "$RUN" ]; then $RUN "$*"; else bash -c "$*"; fi; }
cleanup() { as "$PGBIN/pg_ctl -D $DATA -m immediate stop" >/dev/null 2>&1 || true; rm -rf "$DATA" "$WORK"; }
trap cleanup EXIT

node "$HERE/export-prototype.js" "$HTML" "$WORK"
cp "$MIG"/V*.sql "$WORK"/
cat > "$WORK/dump.sql" <<'SQL'
\pset tuples_only on
\pset format unaligned
SELECT json_build_object(
  'attempt_ranks',      (SELECT coalesce(json_agg(r), '[]') FROM attempt_ranks r),
  'overall_ranks',      (SELECT coalesce(json_agg(o), '[]') FROM overall_ranks o),
  'published_exam_ids', (SELECT coalesce(json_agg(id), '[]') FROM exams WHERE published_at IS NOT NULL),
  'counts',             json_build_object('users', (SELECT count(*) FROM users), 'attempts', (SELECT count(*) FROM attempts)));
SQL
[ -n "$RUN" ] && chown -R postgres "$WORK"
as "$PGBIN/initdb -D $DATA -A trust -E UTF8 --locale=C.UTF-8" >/dev/null
as "$PGBIN/pg_ctl -D $DATA -o '-p $PORT -k $DATA -c listen_addresses=' -w -l $DATA/log start" >/dev/null
PSQL="$PGBIN/psql -h $DATA -p $PORT -U postgres -v ON_ERROR_STOP=1 -q"
as "$PSQL -d postgres -c 'CREATE DATABASE livemcq'"
for f in $(ls "$WORK"/V*.sql | sort -V); do as "$PSQL -d livemcq -f $f"; done
as "$PSQL -d livemcq -f $WORK/fixture.sql"
as "$PSQL -d livemcq -f $WORK/dump.sql" > "$WORK/actual.json"
chmod a+r "$WORK/actual.json" 2>/dev/null || true
python3 -I "$HERE/compare.py" "$WORK/expected.json" "$WORK/actual.json"
