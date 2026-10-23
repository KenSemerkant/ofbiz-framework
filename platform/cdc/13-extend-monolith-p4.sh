#!/usr/bin/env bash
# P4-GATE: extend the RUNNING monolith-db stand-in with the order slice
# (init.sql in monolith-db.yaml only runs on first volume init).
# Idempotent — safe to re-run. QUANTITY/UNIT_PRICE are TEXT so the Debezium
# JSON wire form (decimal.handling.mode=string) round-trips byte-faithful
# through the CDC projection. No seed rows: P4 is a write cutover — the
# first orders arrive via order-svc and the reverse-sync consumer.
set -euo pipefail
NS="ofbiz"

psql_file() {
  kubectl -n "${NS}" exec -i deploy/monolith-db -- psql -U ofbiz -d ofbiz -v ON_ERROR_STOP=1
}

echo "==> [1/2] order slice DDL"
psql_file <<'SQL'
CREATE TABLE IF NOT EXISTS "ORDER_HEADER" (
  "ORDER_ID" VARCHAR(255) PRIMARY KEY,
  "CUSTOMER_ID" VARCHAR(255) NOT NULL,
  "STATUS_ID" VARCHAR(32) NOT NULL,
  "CURRENCY_UOM" VARCHAR(20) NOT NULL,
  "GRAND_TOTAL" NUMERIC(18,2) NOT NULL,
  "LAST_UPDATED_STAMP" TIMESTAMP
);
CREATE TABLE IF NOT EXISTS "ORDER_ITEM" (
  "ORDER_ID" VARCHAR(255) NOT NULL,
  "ORDER_ITEM_SEQ_ID" VARCHAR(20) NOT NULL,
  "PRODUCT_ID" VARCHAR(255) NOT NULL,
  "QUANTITY" TEXT,
  "UNIT_PRICE" TEXT,
  "UNIT_LIST_PRICE" TEXT,
  PRIMARY KEY ("ORDER_ID", "ORDER_ITEM_SEQ_ID")
);
CREATE TABLE IF NOT EXISTS "ORDER_STATUS_HISTORY" (
  "ORDER_ID" VARCHAR(255) NOT NULL,
  "STATUS_ID" VARCHAR(32) NOT NULL,
  "NOTE" VARCHAR(255),
  "CHANGED_AT" TIMESTAMP DEFAULT now()
);
SQL

echo "==> [2/2] replica identity (CDC rollback path)"
psql_file <<'SQL'
ALTER TABLE "ORDER_HEADER" REPLICA IDENTITY FULL;
ALTER TABLE "ORDER_ITEM" REPLICA IDENTITY FULL;
ALTER TABLE "ORDER_STATUS_HISTORY" REPLICA IDENTITY FULL;
SQL

echo "DONE — order slice ready (ORDER_HEADER/ORDER_ITEM/ORDER_STATUS_HISTORY)"
