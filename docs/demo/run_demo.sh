#!/usr/bin/env bash
set -euo pipefail

# Demo onboarding script: uses demo schema/data to exercise the full R2O flow
# Requires a running backend at http://localhost:8080/api/v1

NAME=${1:-demo-onboard}
SCHEMA_FILE=${2:-demo_uni5_schema.sql}
DATA_FILE=${3:-demo_uni5_data.sql}

if [[ ! -f "$SCHEMA_FILE" ]]; then
  echo "Schema file not found: $SCHEMA_FILE"; exit 1
fi
if [[ ! -f "$DATA_FILE" ]]; then
  echo "Data file not found: $DATA_FILE"; exit 1
fi

SCHEMA=$(cat "$SCHEMA_FILE" | sed -e ':a' -e 'N' -e '$!ba' -e 's/"/\\\"/g')
DATA=$(cat "$DATA_FILE" | sed -e ':a' -e 'N' -e '$!ba' -e 's/"/\\\"/g')

curl -s -X POST http://localhost:8080/api/v1/onboard/sql \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"$NAME\",\"schemaSql\":\"$SCHEMA\",\"dataSql\":\"$DATA\"}" | python -m json.tool

echo "\nDone. Check target/semantic-output/r2o/$NAME for results."
