#!/usr/bin/env bash
set -euo pipefail
name="ffh-dashboard-schema-$$"
trap 'docker rm -f "$name" >/dev/null 2>&1 || true' EXIT
docker run -d --name "$name" -e POSTGRES_PASSWORD=test -e POSTGRES_DB=ffh_schema_test postgres:16-alpine >/dev/null
ready=false
for i in $(seq 1 45); do
  if docker exec "$name" psql -U postgres -d ffh_schema_test -tAc 'SELECT 1' >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
test "$ready" = "true"
grep -Fq 'spring.sql.init.separator=^^' src/main/resources/application.properties
# Start from an existing populated installation that predates the TV flag.
docker exec "$name" psql -U postgres -d ffh_schema_test -v ON_ERROR_STOP=1 -c \
  "CREATE TABLE dashboard_messages(id bigint primary key, title varchar(140)); INSERT INTO dashboard_messages VALUES(1,'Existing message');" >/dev/null
run_migration(){ sed 's/\^\^$/;/' src/main/resources/schema.sql | docker exec -i "$name" psql -U postgres -d ffh_schema_test -v ON_ERROR_STOP=1 >/dev/null; }
run_migration
result=$(docker exec "$name" psql -U postgres -d ffh_schema_test -tAc \
  "SELECT count(*) FROM dashboard_messages WHERE on_wallboard IS NULL OR on_wallboard IS DISTINCT FROM FALSE;")
test "$result" = "0"
# Re-running the startup migration must be harmless.
run_migration
# A fresh installation has no dashboard_messages table yet; Hibernate creates it afterwards.
docker exec "$name" psql -U postgres -d ffh_schema_test -v ON_ERROR_STOP=1 \
  -c 'DROP TABLE dashboard_messages;' >/dev/null
run_migration
echo 'Dashboard migration: populated, idempotent and fresh-database cases passed.'
