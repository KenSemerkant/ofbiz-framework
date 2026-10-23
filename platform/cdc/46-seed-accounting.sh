#!/usr/bin/env bash
# P5-GATE: seed the monolith staging accounting slice — the "identical
# dataset" both sides of the CG-5 checks migrate from. Quoted UPPERCASE
# identifiers per the monolith DDL convention (P4 lesson).
set -euo pipefail
NS="ofbiz"
M() { kubectl -n "${NS}" exec deploy/monolith-db -- psql -U ofbiz -d ofbiz -q -c "$1"; }

M 'CREATE TABLE IF NOT EXISTS "GL_ACCOUNT" ("GL_ACCOUNT_ID" varchar(20) PRIMARY KEY,
    "ACCOUNT_CODE" varchar(20) NOT NULL, "ACCOUNT_NAME" varchar(100) NOT NULL,
    "GL_ACCOUNT_TYPE_ID" varchar(20) NOT NULL);'
M 'CREATE TABLE IF NOT EXISTS "CUSTOM_TIME_PERIOD" ("CUSTOM_TIME_PERIOD_ID" varchar(20) PRIMARY KEY,
    "PERIOD_NAME" varchar(100) NOT NULL, "FROM_DATE" date NOT NULL, "THRU_DATE" date NOT NULL,
    "IS_CLOSED" char(1) NOT NULL DEFAULT chr(78));'
M 'CREATE TABLE IF NOT EXISTS "INVOICE" ("INVOICE_ID" varchar(20) PRIMARY KEY,
    "PARTY_ID" varchar(20) NOT NULL, "STATUS_ID" varchar(32) NOT NULL,
    "CURRENCY_UOM" varchar(10) NOT NULL, "INVOICE_TOTAL" numeric(18,2) NOT NULL);'
M 'CREATE TABLE IF NOT EXISTS "PAYMENT" ("PAYMENT_ID" varchar(20) PRIMARY KEY,
    "INVOICE_ID" varchar(20) NOT NULL, "STATUS_ID" varchar(32) NOT NULL,
    "AMOUNT" numeric(18,2) NOT NULL);'
M 'CREATE TABLE IF NOT EXISTS "ACCTG_TRANS" ("ACCTG_TRANS_ID" varchar(30) PRIMARY KEY,
    "ACCTG_TRANS_TYPE_ID" varchar(30) NOT NULL, "DESCRIPTION" varchar(255),
    "CUSTOM_TIME_PERIOD_ID" varchar(20) NOT NULL, "IS_POSTED" char(1) NOT NULL);'
M 'CREATE TABLE IF NOT EXISTS "ACCTG_TRANS_ENTRY" ("ACCTG_TRANS_ID" varchar(30) NOT NULL,
    "ACCTG_TRANS_ENTRY_SEQ_ID" numeric(5) NOT NULL, "GL_ACCOUNT_ID" varchar(20) NOT NULL,
    "AMOUNT" numeric(18,2) NOT NULL, "DEBIT_CREDIT_FLAG" char(1) NOT NULL,
    PRIMARY KEY ("ACCTG_TRANS_ID", "ACCTG_TRANS_ENTRY_SEQ_ID"));'

# deterministic dataset (idempotent re-run)
M 'INSERT INTO "GL_ACCOUNT" VALUES ('"'1200','1200','Accounts Receivable','AR_RECEIVABLE'"')
   ON CONFLICT ("GL_ACCOUNT_ID") DO NOTHING;'
M 'INSERT INTO "GL_ACCOUNT" VALUES ('"'4000','4000','Sales Revenue','REVENUE'"')
   ON CONFLICT ("GL_ACCOUNT_ID") DO NOTHING;'
M 'INSERT INTO "GL_ACCOUNT" VALUES ('"'1100','1100','Cash','CASH_EQUIVALENT'"')
   ON CONFLICT ("GL_ACCOUNT_ID") DO NOTHING;'
M 'INSERT INTO "CUSTOM_TIME_PERIOD" VALUES ('"'P-2025','FY2025 (closed)','2025-01-01','2025-12-31','Y'"')
   ON CONFLICT ("CUSTOM_TIME_PERIOD_ID") DO NOTHING;'
M 'INSERT INTO "CUSTOM_TIME_PERIOD" VALUES ('"'P-2026','FY2026 (open)','2026-01-01','2026-12-31','N'"')
   ON CONFLICT ("CUSTOM_TIME_PERIOD_ID") DO NOTHING;'

seed_invoice() { # id status total
  M "INSERT INTO \"INVOICE\" VALUES ('$1','CUST-ACC','$2','USD',$3)
     ON CONFLICT (\"INVOICE_ID\") DO NOTHING;"
}
seed_invoice INV-S1 INVOICE_PAID    100.00
seed_invoice INV-S2 INVOICE_READY    50.00
seed_invoice INV-S3 INVOICE_PAID     75.50

seed_payment() { # id invoice amount
  M "INSERT INTO \"PAYMENT\" VALUES ('$1','$2','PMNT_SENT',$3)
     ON CONFLICT (\"PAYMENT_ID\") DO NOTHING;"
}
seed_payment PAY-S1 INV-S1 100.00
seed_payment PAY-S3 INV-S3 75.50

# journal mirroring the billing semantics: issue → D AR / C revenue,
# settle → D cash / C AR (identical postings land in finance-svc on import)
seed_trans() { # id type desc period
  M "INSERT INTO \"ACCTG_TRANS\" VALUES ('$1','$2','$3','$4','Y')
     ON CONFLICT (\"ACCTG_TRANS_ID\") DO NOTHING;"
}
entry() { # trans seq account amount flag
  M "INSERT INTO \"ACCTG_TRANS_ENTRY\" VALUES ('$1',$2,'$3',$4,'$5')
     ON CONFLICT (\"ACCTG_TRANS_ID\",\"ACCTG_TRANS_ENTRY_SEQ_ID\") DO NOTHING;"
}
seed_trans ATT-INV-S1-issued  SALES_INVOICE    'invoice INV-S1' P-2026
entry ATT-INV-S1-issued 1 1200 100.00 D
entry ATT-INV-S1-issued 2 4000 100.00 C
seed_trans ATT-INV-S1-settled RECEIPT          'settle INV-S1'  P-2026
entry ATT-INV-S1-settled 1 1100 100.00 D
entry ATT-INV-S1-settled 2 1200 100.00 C
seed_trans ATT-INV-S2-issued  SALES_INVOICE    'invoice INV-S2' P-2026
entry ATT-INV-S2-issued 1 1200 50.00 D
entry ATT-INV-S2-issued 2 4000 50.00 C
seed_trans ATT-INV-S3-issued  SALES_INVOICE    'invoice INV-S3' P-2026
entry ATT-INV-S3-issued 1 1200 75.50 D
entry ATT-INV-S3-issued 2 4000 75.50 C
seed_trans ATT-INV-S3-settled RECEIPT          'settle INV-S3'  P-2026
entry ATT-INV-S3-settled 1 1100 75.50 D
entry ATT-INV-S3-settled 2 1200 75.50 C

echo "SEEDED — monolith accounting slice: 3 invoices, 2 payments, 6 GL accounts/entries"
