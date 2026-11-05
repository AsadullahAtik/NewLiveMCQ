#!/usr/bin/env bash
# Spins up a throw-away PostgreSQL cluster, applies every Flyway migration, runs the SQL checks, then removes it.
# Needs the PostgreSQL server binaries (initdb, pg_ctl, psql). Works as a normal user or as root.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
MIG="$HERE/../src/main/resources/db/migration"
PGBIN="${PGBIN:-$(ls -d /usr/lib/postgresql/*/bin 2>/dev/null | sort -V | tail -1)}"
[ -x "$PGBIN/initdb" ] || PGBIN="$(dirname "$(command -v initdb)")"
PORT="${PGPORT:-54329}"
DATA="$(mktemp -d)"; SQL=""
RUN=""
if [ "$(id -u)" = "0" ]; then chown postgres "$DATA"; RUN="su postgres -c"; fi
as() { if [ -n "$RUN" ]; then $RUN "$*"; else bash -c "$*"; fi; }
cleanup() { as "$PGBIN/pg_ctl -D $DATA -m immediate stop" >/dev/null 2>&1 || true; rm -rf "$DATA" ${SQL:+"$SQL"}; }
trap cleanup EXIT
# copy the SQL into a temp dir the postgres OS user can always read
SQL="$(mktemp -d)"; cp "$MIG"/V*.sql "$HERE/schema_checks.sql" "$SQL"/
[ -n "$RUN" ] && chown -R postgres "$SQL"
as "$PGBIN/initdb -D $DATA -A trust -E UTF8 --locale=C.UTF-8" >/dev/null
as "$PGBIN/pg_ctl -D $DATA -o '-p $PORT -k $DATA -c listen_addresses=' -w -l $DATA/log start" >/dev/null
PSQL="$PGBIN/psql -h $DATA -p $PORT -U postgres -v ON_ERROR_STOP=1 -q"
as "$PSQL -d postgres -c 'CREATE DATABASE livemcq'"
for f in $(ls "$SQL"/V*.sql | sort -V); do echo "applying $(basename "$f")"; as "$PSQL -d livemcq -f $f"; done
OUT="$SQL/checks.out"
set +e
as "$PSQL -d livemcq -f $SQL/schema_checks.sql" >"$OUT" 2>&1
STATUS=$?
set -e
grep -E "PASS|FAIL|==|ERROR" "$OUT" | sed -E 's/^psql:[^ ]+ //' || true
if [ "$STATUS" -ne 0 ]; then echo "DB TESTS FAILED (psql exit $STATUS)"; exit 1; fi
echo "DB TESTS OK"
