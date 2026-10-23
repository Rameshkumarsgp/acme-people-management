#!/bin/sh
# Generates seed.csv and loads it into the database named in .env. Run it on an empty database.
set -eu
ROOT=$(git rev-parse --show-toplevel)
cd "$ROOT"
set -a
. ./.env
set +a
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${ACME_DB_PORT:-5432}/${ACME_DB_NAME}"
export SPRING_DATASOURCE_USERNAME="$ACME_DB_USER"
export SPRING_DATASOURCE_PASSWORD="$ACME_DB_PASSWORD"
FILE="$ROOT/seed.csv"
QUIET="--server.port=0 --spring.main.banner-mode=off --logging.level.root=WARN"
cd backend
./gradlew bootRun --quiet --console=plain --args="$QUIET --acme.seed-csv=$FILE"
./gradlew bootRun --quiet --console=plain --args="$QUIET --acme.load=$FILE"
