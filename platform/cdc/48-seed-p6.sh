#!/usr/bin/env bash
# P6-GATE: seed the monolith long-tail dataset (deterministic; the dataset
# the P6 consistency checkers and the MRP parity drill compare against).
# Idempotent — DELETE-then-INSERT within the P6 seed id namespace.
set -euo pipefail
NS="ofbiz"

psql_file() {
  kubectl -n "${NS}" exec -i deploy/monolith-db -- psql -U ofbiz -d ofbiz -v ON_ERROR_STOP=1
}

echo "==> [1/4] manufacturing (BOM + routing + MRP demand/supply dataset)"
psql_file <<'SQL'
DELETE FROM "PRODUCT_BOM" WHERE "PRODUCT_ID" LIKE 'CG6-%';
DELETE FROM "ROUTING" WHERE "ROUTING_ID" LIKE 'CG6-%';
DELETE FROM "ROUTING_TASK" WHERE "ROUTING_ID" LIKE 'CG6-%';
DELETE FROM "MRP_EVENT" WHERE "MRP_ID" LIKE 'CG6-%';
INSERT INTO "PRODUCT_BOM" VALUES
  ('CG6-BOARD','CG6-CHIP','2','5'),
  ('CG6-BOARD','CG6-CASE','1','0');
INSERT INTO "ROUTING" VALUES ('CG6-RT-BOARD','CG6-BOARD');
INSERT INTO "ROUTING_TASK" VALUES ('CG6-RT-BOARD','00001','assemble','30'),('CG6-RT-BOARD','00002','test','10');
INSERT INTO "MRP_EVENT" VALUES
  ('CG6-MRP','CG6-BOARD','DEMAND','10',now()),
  ('CG6-MRP','CG6-BOARD','SUPPLY','4',now()),
  ('CG6-MRP','CG6-CHIP','DEMAND','20',now()),
  ('CG6-MRP','CG6-CHIP','SUPPLY','4',now()),
  ('CG6-MRP','CG6-CASE','DEMAND','10',now()),
  ('CG6-MRP','CG6-CASE','SUPPLY','12',now());
SQL

echo "==> [2/4] workeffort (project + task + timesheet)"
psql_file <<'SQL'
DELETE FROM "TIME_ENTRY" WHERE "TIME_ENTRY_ID" LIKE 'CG6-%';
DELETE FROM "TIMESHEET" WHERE "TIMESHEET_ID" LIKE 'CG6-%';
DELETE FROM "WORK_EFFORT" WHERE "WORK_EFFORT_ID" LIKE 'CG6-%';
INSERT INTO "WORK_EFFORT" VALUES
  ('CG6-PROJ','PROJECT','CG6 Modernization',NULL,'IN_PROGRESS','2026-10-01','2026-12-31'),
  ('CG6-TASK1','TASK','Migrate workeffort','CG6-PROJ','COMPLETED','2026-10-02','2026-10-10');
INSERT INTO "TIMESHEET" VALUES ('CG6-TS1','demo-user','2026-10-05','2026-10-11','SUBMITTED');
INSERT INTO "TIME_ENTRY" VALUES ('CG6-TE1','CG6-TS1','CG6-TASK1','8','STANDARD');
SQL

echo "==> [3/4] humanres (positions + employments)"
psql_file <<'SQL'
DELETE FROM "EMPLOYMENT" WHERE "EMPLOYMENT_ID" LIKE 'CG6-%';
DELETE FROM "EMPL_POSITION" WHERE "EMPL_POSITION_ID" LIKE 'CG6-%';
INSERT INTO "EMPL_POSITION" VALUES
  ('CG6-POS-ENG','ENGINEER',NULL,'ACTIVE'),
  ('CG6-POS-MGR','MANAGER','CG6-POS-ENG','ACTIVE');
INSERT INTO "EMPLOYMENT" VALUES
  ('CG6-EMP1','demo-user','CG6-POS-ENG','2026-01-01',NULL),
  ('CG6-EMP2','CG6-LEAD','CG6-POS-MGR','2026-02-01',NULL);
SQL

echo "==> [4/4] content + marketing"
psql_file <<'SQL'
DELETE FROM "CONTENT" WHERE "CONTENT_ID" LIKE 'CG6-%';
INSERT INTO "CONTENT" VALUES
  ('CG6-DOC1','DOCUMENT','Migration plan',NULL,'PUBLISHED','contents/CG6-DOC1','text/plain',128),
  ('CG6-FOL1','FOLDER','Reference docs',NULL,'PUBLISHED',NULL,NULL,NULL);
DELETE FROM "MARKETING_CAMPAIGN" WHERE "MARKETING_CAMPAIGN_ID" LIKE 'CG6-%';
DELETE FROM "TRACKING_CODE" WHERE "TRACKING_CODE_ID" LIKE 'CG6-%';
DELETE FROM "SALES_OPPORTUNITY" WHERE "SALES_OPPORTUNITY_ID" LIKE 'CG6-%';
INSERT INTO "MARKETING_CAMPAIGN" VALUES
  ('CG6-CAMP1','Fall campaign','COMPLETED','2026-09-01','2026-09-30'),
  ('CG6-CAMP2','Winter campaign','ACTIVE','2026-11-01','2026-12-31');
INSERT INTO "TRACKING_CODE" VALUES
  ('CG6-PROMO','CG6-CAMP1','fall promo'),
  ('CG6-WINT','CG6-CAMP2','winter promo');
INSERT INTO "SALES_OPPORTUNITY" VALUES
  ('CG6-OPP1','Modernization services','CLOSED_WON',100),
  ('CG6-OPP2','Follow-on support','PROPOSAL',50);
SQL

echo "P6 monolith dataset seeded"
